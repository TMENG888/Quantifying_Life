package com.insight.quantlife;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Durable, explicitly requested image jobs; no credentials or signed URLs in jobs. */
public final class ImageGeneration {
 private static final String PREFS="illustration-jobs";
 private static final Set<String> refreshing=new HashSet<>();
 private static final Set<String> submitting=new HashSet<>();
 private static final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"illustration-deadline");t.setDaemon(true);return t;});
 private static final class RejectedRequest extends Exception {RejectedRequest(String message){super(message);}}
 private static final class NotSubmitted extends Exception {}
 public static JSONObject configure(JSONObject old,JSONObject p)throws Exception{
  String key=p.optString("apiKey").trim();if(key.isEmpty())key=old==null?"":old.optString("apiKey");if(p.optBoolean("clearKey"))key="";
  if(key.length()>1024||key.matches("(?s).*[\\r\\n].*"))throw new Exception("生图 Key 格式无效");
  String w=p.optString("workspaceId",old==null?"":old.optString("workspaceId")).trim();if(!w.isEmpty()&&!w.matches("[a-zA-Z0-9_-]{1,100}"))throw new Exception("请填写业务空间 ID，而不是网址");
  String model=p.optString("model","qwen-image-2.1-turbo");validateModel(model);return new JSONObject().put("apiKey",key).put("workspaceId",w).put("model",model);
 }
 public static JSONObject publicConfig(JSONObject c)throws Exception{return new JSONObject().put("keyConfigured",c!=null&&!c.optString("apiKey").isEmpty()).put("model",c==null?"qwen-image-2.1-turbo":c.optString("model","qwen-image-2.1-turbo")).put("workspaceId",c==null?"":c.optString("workspaceId"));}
 private static void validateModel(String m)throws Exception{if(!m.matches("qwen-image-(2\\.1-turbo|3\\.0)"))throw new Exception("请选择支持的千问生图模型");}
 private static String base(JSONObject c){String w=c.optString("workspaceId");return w.isEmpty()?"https://dashscope.aliyuncs.com":"https://"+w+".cn-beijing.maas.aliyuncs.com";}
 private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 private static synchronized void put(Context c,JSONObject j)throws Exception{if(!prefs(c).edit().putString(j.getString("id"),j.toString()).commit())throw new Exception("生图进度保存失败，请勿重复提交");}
 private static JSONObject get(Context c,String id)throws Exception{String s=prefs(c).getString(id,null);if(s==null)throw new Exception("配图任务不存在");return new JSONObject(s);}
 public static synchronized JSONArray jobs(Context c)throws Exception{List<JSONObject> list=new ArrayList<>();for(Object v:prefs(c).getAll().values())if(v instanceof String){JSONObject j=new JSONObject((String)v);if(j.optString("status").equals("SUBMITTING")&&!submitting.contains(j.optString("id"))){j.put("status","UNCERTAIN").put("error","上次提交结果未确认，请先在百炼控制台核对生成与扣费，不会自动重发。");put(c,j);}list.add(j);}Collections.sort(list,(a,b)->Long.compare(b.optLong("createdAt"),a.optLong("createdAt")));JSONArray out=new JSONArray();for(JSONObject j:list)out.put(j);return out;}
 private static void key(JSONObject c)throws Exception{if(c==null||c.optString("apiKey").isEmpty())throw new Exception("请先在模型配置中保存百炼生图 API Key");}
 private static byte[] bytes(InputStream src,int limit)throws Exception{try(InputStream in=src;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new Exception("生图返回文件过大");out.write(b,0,n);}return out.toByteArray();}}
 private static JSONObject request(URL url,String method,String secret,JSONObject body)throws Exception{
  HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(15000);conn.setReadTimeout(20000);conn.setRequestMethod(method);conn.setRequestProperty("Authorization","Bearer "+secret);
  // Read timeout alone does not bound a stalled upload or a trickling response.
  ScheduledFuture<?> deadline=deadlines.schedule(conn::disconnect,45,TimeUnit.SECONDS);
  boolean sending=false;
  try{if(body!=null){byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);conn.setDoOutput(true);conn.setFixedLengthStreamingMode(payload.length);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.setRequestProperty("X-DashScope-Async","enable");try(OutputStream out=conn.getOutputStream()){sending=true;out.write(payload);}}
   int code=conn.getResponseCode();if(code<200||code>=300){if(code==401||code==403)throw new RejectedRequest("百炼认证失败，未受理本次请求；请检查北京地域 Key 和模型权限后重试。");if(code==429)throw new RejectedRequest("百炼限流或额度不足，未受理本次请求；请检查额度或稍后重试。");if(code>=400&&code<500&&code!=408)throw new RejectedRequest("百炼未受理本次请求（HTTP "+code+"），请检查模型和参数后重试。");throw new Exception("百炼请求未成功（HTTP "+code+"），请查询原任务，不要连续重发");}
   return new JSONObject(new String(bytes(conn.getInputStream(),2*1024*1024),StandardCharsets.UTF_8));
  }catch(UnknownHostException|ConnectException|NoRouteToHostException ex){if(body!=null&&!sending)throw new NotSubmitted();throw ex;}finally{deadline.cancel(false);conn.disconnect();}
 }
 public static JSONObject create(Context c,JSONObject config,JSONObject p)throws Exception{
  key(config);String id=p.getString("id"),recordId=p.getString("recordId"),prompt=p.getString("prompt").trim(),model=p.optString("model","qwen-image-2.1-turbo"),size=p.optString("size","1536*1152");
  if(!id.matches("[a-zA-Z0-9_-]{1,100}")||recordId.isEmpty()||recordId.length()>200)throw new Exception("配图任务编号无效");validateModel(model);
  if(prompt.isEmpty()||prompt.length()>12000)throw new Exception("提示词应为1至12000字");
  if(!size.matches("1024\\*1024|1536\\*1152|1152\\*1536|1536\\*864|2048\\*1536|1536\\*2048|2048\\*1152|1536\\*1536"))throw new Exception("图片尺寸不支持");
  int count=p.optInt("count",1);if(count<1||count>2)throw new Exception("每次生成1至2张图片");JSONArray content=new JSONArray();String ref=p.optString("referenceId");
  if(!ref.isEmpty()){byte[] b=bytes(new FileInputStream(RecordMedia.file(c,ref)),4*1024*1024);content.put(new JSONObject().put("image","data:image/jpeg;base64,"+Base64.encodeToString(b,Base64.NO_WRAP)));prompt="输入图是风格参考：借鉴背景、配色、线条、图标、箭头和图解布局语言；不复制旧主体、文字或结论，不擅自改为其他配色。新内容：\n"+prompt;}
  content.put(new JSONObject().put("text",prompt));String title=p.optString("recordTitle");
  JSONObject job=new JSONObject().put("id",id).put("recordId",recordId).put("recordTitle",title.substring(0,Math.min(100,title.length()))).put("model",model).put("size",size).put("count",count).put("prompt",prompt).put("createdAt",System.currentTimeMillis()).put("status","SUBMITTING").put("referenceId",ref).put("workspaceId",config.optString("workspaceId"));
  JSONObject body=new JSONObject().put("model",model).put("input",new JSONObject().put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",content)))).put("parameters",new JSONObject().put("size",size).put("n",count).put("prompt_extend",false).put("watermark",false));
  synchronized(ImageGeneration.class){if(prefs(c).contains(id))return get(c,id);JSONArray previous=jobs(c);if(previous.length()>=200)throw new Exception("图片库已达到200条任务，请先清理不需要的任务");for(int i=0;i<previous.length();i++){JSONObject other=previous.getJSONObject(i);if(recordId.equals(other.optString("recordId"))&&other.optString("status").matches("SUBMITTING|PENDING|RUNNING|SUCCEEDED|UNCERTAIN"))throw new Exception(other.optString("status").equals("UNCERTAIN")?"本记录上次提交结果未确认；请核对控制台后，在下方解除占用。":"本记录已有进行中的配图，请在下方查询原任务。");}put(c,job);submitting.add(id);}
  try{
  try{JSONObject response=request(new URL(base(config)+"/api/v1/services/aigc/image-generation/generation"),"POST",config.getString("apiKey"),body);JSONObject output=response.optJSONObject("output");if(output==null||output.optString("task_id").isEmpty())throw new Exception("百炼未返回任务编号，请在控制台核对，不要重复点击");job.put("taskId",output.getString("task_id")).put("status",output.optString("task_status","PENDING"));}
  catch(RejectedRequest ex){job.put("status","FAILED").put("error",ex.getMessage());}
  catch(NotSubmitted ex){job.put("status","FAILED").put("error","未能连接百炼服务，未提交生图；请恢复网络后重试。");}
  catch(Exception ex){job.put("status","UNCERTAIN").put("error","提交结果未确认；请到百炼控制台核对生成或扣费，不会自动重发。"+(ex.getMessage()!=null&&ex.getMessage().startsWith("百炼")?ex.getMessage():"请检查网络。"));}
  put(c,job);return job;
  }finally{synchronized(ImageGeneration.class){submitting.remove(id);}}
 }
 public static JSONObject refresh(Context c,JSONObject config,String id)throws Exception{
  synchronized(ImageGeneration.class){if(!refreshing.add(id))throw new Exception("此配图正在查询，请稍候，不会重新生成");}
  try{return refreshJob(c,config,id);}finally{synchronized(ImageGeneration.class){refreshing.remove(id);}}
 }
 private static JSONObject refreshJob(Context c,JSONObject config,String id)throws Exception{
  JSONObject job=get(c,id);if(job.optString("status").equals("READY")||job.optString("taskId").isEmpty())return job;key(config);
  JSONObject original=new JSONObject().put("workspaceId",job.optString("workspaceId"));JSONObject result=request(new URL(base(original)+"/api/v1/tasks/"+URLEncoder.encode(job.getString("taskId"),"UTF-8")),"GET",config.getString("apiKey"),null);
  JSONObject output=result.getJSONObject("output");String status=output.getString("task_status");job.remove("error");job.put("status",status);put(c,job);
  if(status.equals("SUCCEEDED")){
   JSONArray choices=output.optJSONArray("choices"),images=new JSONArray();if(choices==null)throw new Exception("生成已成功，但未读到图片；请查询原任务");
   Set<String> seen=new HashSet<>();for(int i=0;i<choices.length();i++){JSONArray parts=choices.getJSONObject(i).getJSONObject("message").getJSONArray("content");for(int j=0;j<parts.length();j++){String url=parts.getJSONObject(j).optString("image");if(!url.isEmpty()){JSONObject image=RecordMedia.fromBytes(c,download(url),"AI 插画");if(seen.add(image.getString("id")))images.put(image);}}}
   if(images.length()==0||images.length()>2)throw new Exception("返回图片数量异常，请查询原任务");job.put("images",images).put("status","READY");if(result.has("usage"))job.put("usage",result.get("usage"));
  }else if(status.matches("FAILED|CANCELED|UNKNOWN"))job.put("error","百炼任务失败、取消或已过期，请检查控制台；不会自动重发。");put(c,job);return job;
 }
 static boolean allowedDownload(URL u){String h=u.getHost().toLowerCase(Locale.ROOT);return u.getProtocol().equals("https")&&u.getUserInfo()==null&&u.getPort()==-1&&(h.endsWith(".aliyuncs.com")||h.endsWith(".aliyun.com"));}
 private static byte[] download(String location)throws Exception{
  for(int hop=0;hop<4;hop++){URL u=new URL(location);if(!allowedDownload(u))throw new Exception("生图图片地址未通过安全校验");HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);
   try{int status=c.getResponseCode();if(status>=300&&status<400){location=new URL(u,c.getHeaderField("Location")).toString();continue;}if(status!=200)throw new Exception("图片暂未下载，请查询原任务，勿重新生成");return bytes(c.getInputStream(),20*1024*1024);}finally{c.disconnect();}}
  throw new Exception("图片跳转次数过多");
 }
 public static boolean acknowledge(Context c,String id)throws Exception{return acknowledge(c,id,false);}
 public static synchronized boolean acknowledge(Context c,String id,boolean confirmed)throws Exception{if(refreshing.contains(id)||submitting.contains(id))throw new Exception("正在提交或查询图片，请稍后再处理此任务");JSONObject job=get(c,id);if(job.optString("status").matches("PENDING|RUNNING|SUBMITTING|SUCCEEDED"))throw new Exception("生成或下载中的任务不能移除，请先查询结果");if(job.optString("status").equals("UNCERTAIN")&&(!confirmed||!job.optString("taskId").isEmpty()))throw new Exception("请先核对百炼控制台；有任务编号的配图请查询原任务。");if(!prefs(c).edit().remove(id).commit())throw new Exception("解除占用失败，请稍后重试");return true;}
 private ImageGeneration(){}
}
