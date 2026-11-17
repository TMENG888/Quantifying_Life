package com.insight.quantlife;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;

/** Information extraction uses the active encrypted assistant profile; credentials never move. */
public final class InformationTransport {
 public static final int MAX_PARALLEL=2;
 private static final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"information-deadline");t.setDaemon(true);return t;});
 public static JSONObject config(JSONObject settings)throws Exception{
  JSONObject profiles=settings.optJSONObject("profiles"),p=profiles==null?null:profiles.optJSONObject(settings.optString("activeProfile"));
  if(p==null||p.optString("apiKey").trim().isEmpty()||!deepseek(p.optString("model")))return null;
  String protocol=p.optString("protocol","chat");if(!protocol.equals("chat")&&!protocol.equals("responses"))return null;
  String endpoint=p.optString("baseUrl").replaceAll("/+$","")+(protocol.equals("responses")?"/responses":"/chat/completions");ModelEndpoint.validate(new URL(endpoint));
  return new JSONObject().put("model",p.getString("model")).put("apiKey",p.getString("apiKey")).put("endpoint",endpoint).put("protocol",protocol);
 }
 public static boolean deepseek(String name){return name.toLowerCase(Locale.ROOT).contains("deepseek");}
 public static String fingerprint(JSONObject config)throws Exception{return config==null?"":QwenLabels.hash(new JSONObject().put("type",config.optString("endpoint")).put("title",config.optString("model")+"\n"+config.optString("apiKey")));}
 public static JSONObject body(JSONObject config,String task,JSONObject input)throws Exception{
  if(!task.equals("extract_information_units")&&!task.equals("merge_information_units"))throw new Exception("不支持的信息分析任务");
  String prompt=InformationUnits.prompt(task)+"\n仅输出严格JSON对象。blocks、units、groups均为JSON数组，不能用字符串替代；不要输出解释或Markdown围栏。";
  JSONObject fn=new JSONObject().put("name",task).put("description","从原文提取或去重独立信息，不修改记录").put("parameters",InformationUnits.schema(task));
  JSONObject b=new JSONObject().put("model",config.getString("model")).put("stream",false);
  if(config.optString("protocol","chat").equals("responses")){
   b.put("instructions",prompt).put("input",new JSONArray().put(new JSONObject().put("role","user").put("content",input.toString()))).put("store",false).put("max_output_tokens",8192);
   fn.put("type","function");b.put("tools",new JSONArray().put(fn)).put("tool_choice",new JSONObject().put("type","function").put("name",task));
  }else{
   b.put("messages",new JSONArray().put(new JSONObject().put("role","system").put("content",prompt)).put(new JSONObject().put("role","user").put("content",input.toString()))).put("max_tokens",8192).put("temperature",0);
   b.put("tools",new JSONArray().put(new JSONObject().put("type","function").put("function",fn))).put("tool_choice",new JSONObject().put("type","function").put("function",new JSONObject().put("name",task)));
   URL url=new URL(config.getString("endpoint"));if(ModelEndpoint.bailian(url))b.put("enable_thinking",false);else b.put("thinking",new JSONObject().put("type","disabled"));
  }return b;
 }
 public static JSONObject answer(JSONObject response,String task)throws Exception{
  if("incomplete".equals(response.optString("status")))throw new QwenTransport.Failure("信息单元输出未完成，将缩小片段重试",422,0);
  String text="";JSONArray choices=response.optJSONArray("choices");
  if(choices!=null&&choices.length()>0){JSONObject choice=choices.getJSONObject(0);if("length".equals(choice.optString("finish_reason")))throw new QwenTransport.Failure("信息单元输出未完成，将缩小片段重试",422,0);
   JSONObject msg=choice.getJSONObject("message");JSONArray calls=msg.optJSONArray("tool_calls");if(calls!=null)for(int i=0;i<calls.length();i++){JSONObject f=calls.getJSONObject(i).getJSONObject("function");if(task.equals(f.optString("name"))){text=f.getString("arguments");break;}}if(text.isEmpty())text=msg.optString("content","");
  }else{JSONArray output=response.optJSONArray("output");if(output!=null)for(int i=0;i<output.length();i++){JSONObject item=output.getJSONObject(i);if("function_call".equals(item.optString("type"))&&task.equals(item.optString("name"))){text=item.getString("arguments");break;}JSONArray content=item.optJSONArray("content");if(content!=null)for(int k=0;k<content.length();k++)if("output_text".equals(content.getJSONObject(k).optString("type")))text+=content.getJSONObject(k).optString("text");}}
  try{return new JSONObject(text.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$",""));}catch(JSONException invalid){throw new QwenTransport.Failure("信息单元结果格式不完整，将缩小片段重试",422,0);}
 }
 public static JSONObject information(Context context,JSONObject config,String task,JSONObject input)throws Exception{
  byte[] bytes=body(config,task,input).toString().getBytes(StandardCharsets.UTF_8);URL url=new URL(config.getString("endpoint"));ModelEndpoint.validate(url);HttpURLConnection conn=(HttpURLConnection)url.openConnection();
  conn.setConnectTimeout(15000);conn.setReadTimeout(45000);conn.setInstanceFollowRedirects(false);conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setFixedLengthStreamingMode(bytes.length);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.setRequestProperty("Authorization","Bearer "+config.getString("apiKey"));ScheduledFuture<?> deadline=deadlines.schedule(conn::disconnect,65,TimeUnit.SECONDS);
  try{
   try(OutputStream out=conn.getOutputStream()){out.write(bytes);}int status=conn.getResponseCode();if(status<200||status>=300){long retry=30000;String header=conn.getHeaderField("Retry-After");try{retry=Math.max(retry,Long.parseLong(header)*1000);}catch(Exception ignored){try{retry=Math.max(retry,java.time.ZonedDateTime.parse(header,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis());}catch(Exception ignoredDate){}}
    if(status==401||status==403)throw new QwenTransport.Failure("DeepSeek认证失败，请检查AI助手的模型配置",status,0);
    if(status==404)throw new QwenTransport.Failure("DeepSeek接口或模型不存在，请检查AI助手的模型配置",404,0);
    if(status==429)throw new QwenTransport.Failure("DeepSeek限流或额度不足，已降低并发并等待重试",429,Math.min(24*3600000L,retry));
    throw new QwenTransport.Failure("DeepSeek请求失败（HTTP "+status+"），已保留信息单元进度",status,retry);
   }
   try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("oversized response");out.write(buffer,0,n);}return answer(new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)),task);}
  }catch(QwenTransport.Failure failure){throw failure;}catch(Exception error){throw new QwenTransport.Failure("DeepSeek连接或响应未完成，已保留进度待重试",0,30000);}finally{deadline.cancel(false);conn.disconnect();}
 }
 private InformationTransport(){}
}
