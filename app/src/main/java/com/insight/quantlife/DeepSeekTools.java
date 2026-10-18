package com.insight.quantlife;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Separate read-only DeepSeek tools. Credentials never go to a page or a configurable endpoint. */
public final class DeepSeekTools {
    private static final String ORIGIN="https://api.deepseek.com";
    public interface Transport {JSONObject post(String operation,JSONObject body,String key,long deadline)throws Exception;}
    public static final class Failure extends Exception {
        public final String status;
        public Failure(String status){super(message(status));this.status=status;}
    }
    private final Transport transport;
    public DeepSeekTools(){this(DeepSeekTools::post);}
    public DeepSeekTools(Transport t){transport=t;}
    public static boolean official(JSONObject profile){
        if(profile==null||profile.optString("apiKey").trim().isEmpty())return false;
        try{URI u=new URI(profile.optString("baseUrl"));String path=u.getPath();return "https".equalsIgnoreCase(u.getScheme())&&"api.deepseek.com".equalsIgnoreCase(u.getHost())&&(u.getPort()==-1||u.getPort()==443)&&u.getRawUserInfo()==null&&u.getRawQuery()==null&&u.getRawFragment()==null&&(path==null||path.isEmpty()||path.equals("/")||path.equals("/v1")||path.equals("/v1/"));}catch(Exception e){return false;}
    }
    public static JSONObject settings(JSONObject settings)throws Exception{
        JSONObject stored=settings.optJSONObject("linkSearch"),effective=LinkReader.searchSettings(stored),profiles=settings.optJSONObject("profiles");
        JSONObject p=profiles==null?null:profiles.optJSONObject(settings.optString("activeProfile"));
        if(official(p))return new JSONObject().put("provider","deepseek").put("enabled",stored==null||stored.optBoolean("enabled",true)).put("apiKey",p.getString("apiKey"));
        // A DeepSeek credential must never be reused for a proxy, another vendor or Exa.
        if(effective.optString("provider").equals("deepseek"))return effective.put("enabled",false).put("apiKey","");
        return effective;
    }
    public JSONObject fetch(String url,String key,long deadline)throws Exception{return request("fetch",new JSONObject().put("url",LinkReader.safeUrl(url).toString()),key,deadline);}
    public JSONObject search(String url,String key,long deadline)throws Exception{
        // Only the public URL is searched: no notes, chat history, original question or location.
        String query=LinkReader.safeUrl(url).toString();if(query.codePointCount(0,query.length())>128)throw new Failure("query_too_long");
        return request("search",new JSONObject().put("query",query).put("count",3).put("need_content",true),key,deadline);
    }
    private JSONObject request(String operation,JSONObject body,String key,long deadline)throws Exception{
        if(key==null||key.isEmpty()||key.length()>512||key.matches("(?s).*[\\r\\n].*"))throw new Failure("authentication_required");
        for(int attempt=0;attempt<2;attempt++){
            if(System.currentTimeMillis()>=deadline)throw new Failure("timeout");
            try{return transport.post(operation,body,key,deadline);}catch(Failure e){if(attempt==1||!e.status.matches("rate_limited|timeout|server_error"))throw e;}
            catch(SocketTimeoutException e){if(attempt==1)throw new Failure("timeout");}
            catch(Exception e){throw new Failure("unavailable");}
            if(deadline-System.currentTimeMillis()<1000)throw new Failure("timeout");Thread.sleep(400);
        }
        throw new Failure("unavailable");
    }
    public static String message(String status){
        switch(status){
            case "authentication_required":return "DeepSeek Key 无效或接口未获授权，请核对官方 API Key";
            case "insufficient_balance":return "DeepSeek 余额不足，未继续搜索；可补充文章正文或截图";
            case "blocked":return "DeepSeek 拒绝读取此链接，可能受安全或站点访问策略限制";
            case "not_found":return "DeepSeek 未读到正文，可能需要登录、验证或网站限制抓取";
            case "rate_limited":return "DeepSeek 搜索／抓取已限流，请稍后重试";
            case "timeout":return "DeepSeek 搜索／抓取超时，请稍后重试";
            case "query_too_long":return "链接超过搜索词长度限制，未截断链接搜索；请补充正文或截图";
            case "protocol_error":return "DeepSeek 工具返回格式异常，未将其作为文章内容";
            default:return "DeepSeek 搜索／抓取暂不可用，请补充正文或截图";
        }
    }
    private static JSONObject post(String operation,JSONObject body,String key,long deadline)throws Exception{
        if(!operation.matches("fetch|search"))throw new Failure("blocked");
        int remaining=(int)Math.min(12000,deadline-System.currentTimeMillis());if(remaining<=0)throw new Failure("timeout");
        HttpURLConnection c=(HttpURLConnection)new URL(ORIGIN+"/v1/tools/"+operation).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(Math.min(6000,remaining));c.setReadTimeout(remaining);c.setRequestMethod("POST");c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+key);
        try{
            try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode();if(code!=200)throw new Failure(code==401?"authentication_required":code==402?"insufficient_balance":code==403?"blocked":code==404?"not_found":code==429?"rate_limited":code==408||code==504?"timeout":code>=500?"server_error":"unavailable");
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(System.currentTimeMillis()>deadline)throw new Failure("timeout");if(out.size()+n>1024*1024)throw new Failure("protocol_error");out.write(b,0,n);}
                try{return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}catch(Exception e){throw new Failure("protocol_error");}
            }
        }finally{c.disconnect();}
    }
}
