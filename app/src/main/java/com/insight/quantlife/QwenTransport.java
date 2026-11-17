package com.insight.quantlife;

import android.content.Context;import org.json.*;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.concurrent.*;

/** Fixed Qwen text model, reuses only the encrypted Aliyun illustration credential. */
public final class QwenTransport {
 public static final class Failure extends Exception{public final int code;public final long retryMs;public Failure(String text,int code,long retry){super(text);this.code=code;this.retryMs=retry;}}
 private static final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"qwen-deadline");t.setDaemon(true);return t;});
 public static JSONObject config(JSONObject settings)throws Exception{
  JSONObject image=settings.optJSONObject("imageGeneration");if(image==null||image.optString("apiKey").isEmpty())return null;
  String workspace=image.optString("workspaceId");if(!workspace.isEmpty()&&!workspace.matches("[a-zA-Z0-9_-]{1,100}"))throw new Exception("阿里云业务空间格式无效");
  return new JSONObject().put("apiKey",image.getString("apiKey")).put("endpoint",workspace.isEmpty()?"https://cn-beijing.maas.qianwenaiapi.com/compatible-mode/v1/chat/completions":"https://"+workspace+".cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions");
 }
 public static String fingerprint(JSONObject c)throws Exception{return QwenLabels.hash(new JSONObject().put("type",c.optString("endpoint")).put("title",c.optString("apiKey")));}
 public static JSONObject body(JSONObject input)throws Exception{
  JSONObject anchored=new JSONObject(input.toString());if(!anchored.has("sources"))anchored.put("sources",QwenLabels.sources(input.optString("note"),input.optInt("offset")));anchored.remove("note");
  return structuredBody("label_record_evidence",QwenLabels.prompt(),QwenLabels.schema(),anchored,4096);
 }
 public static JSONObject structuredBody(String task,String prompt,JSONObject schema,JSONObject input,int output)throws Exception{
  JSONObject fn=new JSONObject().put("name",task).put("description","依据原文整理派生信息，不改写记录").put("parameters",schema);
  return new JSONObject().put("model",QwenLabels.MODEL).put("stream",false).put("enable_thinking",false).put("max_tokens",output)
   .put("messages",new JSONArray().put(new JSONObject().put("role","system").put("content",prompt)).put(new JSONObject().put("role","user").put("content",input.toString())))
   .put("tools",new JSONArray().put(new JSONObject().put("type","function").put("function",fn)))
   .put("tool_choice",new JSONObject().put("type","function").put("function",new JSONObject().put("name",task)));
 }
 public static JSONObject answer(JSONObject response)throws Exception{
  JSONObject choice=response.getJSONArray("choices").getJSONObject(0);if("length".equals(choice.optString("finish_reason")))throw new Failure("Qwen打标输出不完整，将分段重试",422,30000);
  JSONObject message=choice.getJSONObject("message");JSONArray calls=message.optJSONArray("tool_calls");String text=message.optString("content");
  if(calls!=null)for(int i=0;i<calls.length();i++){JSONObject f=calls.getJSONObject(i).getJSONObject("function");if(java.util.Arrays.asList("label_record_evidence","extract_information_units","merge_information_units").contains(f.optString("name"))){text=f.getString("arguments");break;}}
  return new JSONObject(text.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$",""));
 }
 private static byte[] read(InputStream stream)throws Exception{try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)throw new Exception("Qwen响应过大");out.write(b,0,n);}return out.toByteArray();}}
 public static JSONObject ask(Context context,JSONObject config,JSONObject input)throws Exception{
  return request(context,config,body(input));
 }
 public static JSONObject information(Context context,JSONObject config,String task,JSONObject input)throws Exception{
  if(!task.equals("extract_information_units")&&!task.equals("merge_information_units"))throw new Exception("不支持的信息分析任务");
  JSONObject schema=InformationUnits.schema(task);JSONArray ids=new JSONArray(),items=input.getJSONArray(task.equals("extract_information_units")?"blocks":"units");for(int i=0;i<items.length();i++)ids.put(items.getJSONObject(i).getString("id"));
  if(task.equals("extract_information_units")){JSONObject blocks=schema.getJSONObject("properties").getJSONObject("blocks");blocks.put("minItems",ids.length()).put("maxItems",ids.length()).getJSONObject("items").getJSONObject("properties").getJSONObject("id").put("enum",ids);}
  else schema.getJSONObject("properties").getJSONObject("groups").getJSONObject("items").put("minItems",2).getJSONObject("items").put("enum",ids);
  JSONObject body=structuredBody(task,InformationUnits.prompt(task)+"\n输出严格JSON对象，blocks/units/groups必须为真正的JSON数组，不是数组字符串。",schema,input,8192);
  // Strict schema avoids compatible tool gateways double-encoding nested array arguments.
  body.remove("tools");body.remove("tool_choice");body.put("response_format",new JSONObject().put("type","json_schema").put("json_schema",new JSONObject().put("name",task).put("strict",true).put("schema",schema))).put("temperature",0);
  return request(context,config,body);
 }
 private static JSONObject request(Context context,JSONObject config,JSONObject body)throws Exception{
  byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);String ticket=QwenBudget.acquire(context,bytes.length+body.getInt("max_tokens")+1024);
  URL url=new URL(config.getString("endpoint"));ModelEndpoint.validate(url);HttpURLConnection conn=(HttpURLConnection)url.openConnection();
  conn.setConnectTimeout(15000);conn.setReadTimeout(45000);conn.setInstanceFollowRedirects(false);conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setFixedLengthStreamingMode(bytes.length);
  conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.setRequestProperty("Authorization","Bearer "+config.getString("apiKey"));ScheduledFuture<?> timer=deadlines.schedule(conn::disconnect,65,TimeUnit.SECONDS);
  try{
   try(OutputStream out=conn.getOutputStream()){out.write(bytes);}int status=conn.getResponseCode();if(status<200||status>=300){long retry=30000;String header=conn.getHeaderField("Retry-After");try{retry=Math.max(retry,Long.parseLong(header)*1000);}catch(Exception ignored){try{retry=Math.max(retry,java.time.ZonedDateTime.parse(header,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis());}catch(Exception ignored2){}}
    if(status==401||status==403)throw new Failure("阿里云密钥未获授权，请检查生图密钥的文本模型权限",status,0);
    if(status==404)throw new Failure("当前阿里云业务空间未提供qwen3.7-flash，请检查模型开通与地域",404,0);
    if(status==429)throw new Failure("Qwen限流或额度不足，已降低并发并等待重试",429,Math.min(24*3600000L,retry));
    throw new Failure("Qwen请求失败（HTTP "+status+"），保留进度重试",status,retry);
   }
   JSONObject response=new JSONObject(new String(read(conn.getInputStream()),StandardCharsets.UTF_8));JSONObject usage=response.optJSONObject("usage");if(usage!=null&&usage.has("total_tokens"))QwenBudget.finish(context,ticket,usage.getInt("total_tokens"));return answer(response);
  }catch(Failure failure){throw failure;}catch(Exception error){throw new Failure("Qwen连接或响应未完成，已保留进度待重试",0,30000);}finally{timer.cancel(false);conn.disconnect();}
 }
 private QwenTransport(){}
}
