package com.insight.quantlife;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Restricted anonymous HTTP MCP client. No OAuth, keys, model context or arbitrary tools. */
public final class ExaMcp {
    private static final String ENDPOINT="https://mcp.exa.ai/mcp", TOOL="web_search_exa";
    private static final int LIMIT=256*1024;
    public static final class Failure extends Exception {
        public final String status;
        public Failure(String s){super(s);status=s;}
    }
    public static final class Reply {
        public final int code; public final String mime,session,body;
        public Reply(int c,String m,String s,String b){code=c;mime=m;session=s;body=b;}
    }
    public interface Transport {Reply post(JSONObject rpc,String session,String protocol,long deadline)throws Exception;}
    private final Transport transport;
    private String session="",protocol="2024-11-05";
    private JSONObject schema,fetchSchema;
    private int nextId;
    private long limitedUntil;
    private final LinkedHashMap<String,JSONObject> cache=new LinkedHashMap<>();
    private static final LinkedHashMap<String,JSONObject> SHARED_CACHE=new LinkedHashMap<>();
    private static long sharedLimitedUntil;
    private final boolean shared;
    public ExaMcp(){transport=ExaMcp::post;shared=true;}
    public ExaMcp(Transport t){transport=t;shared=false;}

    public synchronized JSONObject search(String input)throws Exception {
        return search(input,System.currentTimeMillis()+22000);
    }
    public synchronized JSONObject search(String input,long overall)throws Exception {
        String url=LinkReader.safeUrl(input).toString();long now=System.currentTimeMillis();
        synchronized(SHARED_CACHE){
            JSONObject cached=(shared?SHARED_CACHE:cache).get(url);
            if(cached!=null&&now-cached.getLong("time")<10*60*1000)return new JSONObject(cached.getJSONObject("response").toString());
            if(now<(shared?sharedLimitedUntil:limitedUntil))throw new Failure("rate_limited");
        }
        long deadline=Math.min(now+22000,overall);
        try {
            if(schema==null)initialize(deadline);
            JSONObject args=arguments(url,schema);
            JSONObject result=call("tools/call",new JSONObject().put("name",TOOL).put("arguments",args),deadline);
            if(result.optBoolean("isError"))throw new Failure("tool_error");
            JSONArray rows=parseSearch(result);
            JSONObject response=new JSONObject().put("results",rows);
            if(rows.length()>0)synchronized(SHARED_CACHE){
                LinkedHashMap<String,JSONObject> target=shared?SHARED_CACHE:cache;
                target.put(url,new JSONObject().put("time",System.currentTimeMillis()).put("response",response));
                while(target.size()>32)target.remove(target.keySet().iterator().next());
            }
            return new JSONObject(response.toString());
        } catch(Failure e){
            if(e.status.equals("rate_limited"))synchronized(SHARED_CACHE){if(shared)sharedLimitedUntil=System.currentTimeMillis()+120000;else limitedUntil=System.currentTimeMillis()+120000;}
            // No automatic retries. A later user attempt can negotiate a fresh session/schema.
            schema=null;session="";protocol="2024-11-05";throw e;
        } catch(SocketTimeoutException e){schema=null;session="";throw new Failure("timeout");}
        catch(Exception e){schema=null;session="";throw new Failure("unavailable");}
    }
    public synchronized JSONObject fetch(String input,long overall)throws Exception {
        String url=LinkReader.safeUrl(input).toString(),cacheKey="fetch:"+url;long now=System.currentTimeMillis();
        synchronized(SHARED_CACHE){JSONObject cached=(shared?SHARED_CACHE:cache).get(cacheKey);if(cached!=null&&now-cached.getLong("time")<600000)return new JSONObject(cached.getJSONObject("response").toString());if(now<(shared?sharedLimitedUntil:limitedUntil))throw new Failure("rate_limited");}
        try {
            long deadline=Math.min(now+18000,overall);if(schema==null)initialize(deadline);
            if(fetchSchema==null)throw new Failure("fetch_unsupported");
            JSONObject result=call("tools/call",new JSONObject().put("name","web_fetch_exa").put("arguments",fetchArguments(url,fetchSchema)),deadline);
            if(result.optBoolean("isError"))throw new Failure("tool_error");JSONObject page=parsePage(result,url);
            synchronized(SHARED_CACHE){LinkedHashMap<String,JSONObject> target=shared?SHARED_CACHE:cache;target.put(cacheKey,new JSONObject().put("time",System.currentTimeMillis()).put("response",page));while(target.size()>32)target.remove(target.keySet().iterator().next());}
            return new JSONObject(page.toString());
        }catch(Failure e){if(e.status.equals("rate_limited"))synchronized(SHARED_CACHE){if(shared)sharedLimitedUntil=System.currentTimeMillis()+120000;else limitedUntil=System.currentTimeMillis()+120000;}throw e;}
        catch(SocketTimeoutException e){throw new Failure("timeout");}catch(Exception e){throw new Failure("unavailable");}
    }
    public static JSONObject fetchArguments(String url,JSONObject s)throws Exception {
        if(s==null||!s.optString("type").equals("object"))throw new Failure("protocol_error");JSONObject props=s.optJSONObject("properties");
        if(props==null||props.optJSONObject("urls")==null||!props.getJSONObject("urls").optString("type").equals("array"))throw new Failure("protocol_error");
        JSONObject args=new JSONObject().put("urls",new JSONArray().put(LinkReader.safeUrl(url).toString()));
        if(props.has("maxCharacters")){if(!props.getJSONObject("maxCharacters").optString("type").matches("number|integer"))throw new Failure("protocol_error");args.put("maxCharacters",5000);}
        JSONArray required=s.optJSONArray("required");if(required!=null)for(int i=0;i<required.length();i++)if(!args.has(required.getString(i)))throw new Failure("protocol_error");return args;
    }
    public static JSONObject parsePage(JSONObject result,String requested)throws Exception {
        JSONArray blocks=result.optJSONArray("content");if(blocks==null)throw new Failure("protocol_error");
        for(int i=0;i<blocks.length();i++){JSONObject b=blocks.optJSONObject(i);if(b==null||!b.optString("type").equals("text"))continue;String raw=b.optString("text");if(raw.length()>LIMIT)throw new Failure("response_too_large");String[] lines=raw.replace("\r\n","\n").trim().split("\n",3);
            if(lines.length<3||!lines[0].startsWith("# ")||!lines[1].startsWith("URL: "))continue;String url=lines[1].substring(5).trim();if(!LinkReader.samePage(requested,url))continue;
            String title=lines[0].substring(2).trim(),body=lines[2].trim();if(title.isEmpty()||body.length()<80)continue;
            return new JSONObject().put("url",LinkReader.safeUrl(url).toString()).put("title",title.substring(0,Math.min(300,title.length()))).put("excerpt",body.substring(0,Math.min(5000,body.length()))).put("textTruncated",body.length()>5000).put("contentStatus","limited_text");
        }
        throw new Failure("no_readable_content");
    }
    private void initialize(long deadline)throws Exception {
        JSONObject init=call("initialize",new JSONObject().put("protocolVersion",protocol).put("capabilities",new JSONObject()).put("clientInfo",new JSONObject().put("name","Zhishi").put("version","1.4.1")),deadline);
        String negotiated=init.optString("protocolVersion");
        if(!negotiated.matches("2024-11-05|2025-03-26|2025-06-18"))throw new Failure("protocol_error");
        protocol=negotiated;
        JSONObject notification=new JSONObject().put("jsonrpc","2.0").put("method","notifications/initialized");
        Reply reply=transport.post(notification,session,protocol,deadline);httpStatus(reply.code);
        JSONObject listing=call("tools/list",new JSONObject(),deadline);JSONArray tools=listing.optJSONArray("tools");
        schema=null;fetchSchema=null;
        if(tools!=null)for(int i=0;i<tools.length();i++){JSONObject tool=tools.optJSONObject(i);if(tool==null)continue;if(TOOL.equals(tool.optString("name")))schema=tool.optJSONObject("inputSchema");if("web_fetch_exa".equals(tool.optString("name")))fetchSchema=tool.optJSONObject("inputSchema");}
        if(schema==null)throw new Failure("protocol_error");
        arguments("https://example.com/",schema); // Validate compatibility before making a search call.
    }
    public static JSONObject arguments(String url,JSONObject s)throws Exception {
        if(s==null||!"object".equals(s.optString("type")))throw new Failure("protocol_error");
        JSONObject props=s.optJSONObject("properties");JSONArray required=s.optJSONArray("required");
        if(props==null||props.optJSONObject("query")==null||!"string".equals(props.getJSONObject("query").optString("type")))throw new Failure("protocol_error");
        JSONObject args=new JSONObject().put("query",LinkReader.safeUrl(url).toString());
        if(props.has("objective")){if(!"string".equals(props.getJSONObject("objective").optString("type")))throw new Failure("protocol_error");args.put("objective","核对该准确链接的公开标题、URL、平台及内容类型。优先原始页面，排除无关页面。不要推断用户的学习时长、个人笔记或阅读完成情况。");}
        if(props.has("numResults")){if(!props.getJSONObject("numResults").optString("type").matches("number|integer"))throw new Failure("protocol_error");args.put("numResults",3);}else throw new Failure("protocol_error");
        if(required!=null)for(int i=0;i<required.length();i++)if(!args.has(required.getString(i)))throw new Failure("protocol_error");
        return args;
    }
    private JSONObject call(String method,JSONObject params,long deadline)throws Exception {
        int id=++nextId;JSONObject rpc=new JSONObject().put("jsonrpc","2.0").put("id",id).put("method",method).put("params",params);
        Reply reply=transport.post(rpc,session,protocol,deadline);httpStatus(reply.code);
        if(!reply.session.isEmpty()){if(reply.session.length()>512||!reply.session.matches("[\\x21-\\x7e]+"))throw new Failure("protocol_error");session=reply.session;}
        JSONObject envelope=decode(reply.body,reply.mime,id);
        if(envelope.has("error"))throw new Failure("tool_error");
        JSONObject result=envelope.optJSONObject("result");if(result==null)throw new Failure("protocol_error");return result;
    }
    private static void httpStatus(int code)throws Exception {if(code==429)throw new Failure("rate_limited");if(code<200||code>=300)throw new Failure(code==401||code==403?"authentication_required":"unavailable");}
    private static JSONObject envelope(String text,int id)throws Exception {
        JSONObject obj=new JSONObject(text);
        if(!"2.0".equals(obj.optString("jsonrpc"))||!(obj.opt("id") instanceof Number)||obj.getInt("id")!=id)throw new Failure("protocol_error");
        if(!obj.has("result")&&!obj.has("error"))throw new Failure("protocol_error");return obj;
    }
    public static JSONObject decode(String raw,String mime,int id)throws Exception {
        if(raw==null||raw.getBytes(StandardCharsets.UTF_8).length>LIMIT)throw new Failure("response_too_large");
        String type=mime.toLowerCase(Locale.ROOT);
        if(type.startsWith("application/json"))return envelope(raw,id);
        if(!type.startsWith("text/event-stream"))throw new Failure("protocol_error");
        StringBuilder data=new StringBuilder();
        for(String line:(raw+"\n\n").split("\\r?\\n",-1)){
            if(line.isEmpty()){
                if(data.length()>0){String value=data.toString();data.setLength(0);JSONObject obj=new JSONObject(value);if(obj.has("id")&&obj.opt("id") instanceof Number&&obj.getInt("id")==id)return envelope(value,id);}
            }else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).replaceFirst("^ ",""));}
        }
        throw new Failure("protocol_error");
    }
    public static JSONArray parseSearch(JSONObject result)throws Exception {
        JSONArray content=result.optJSONArray("content");if(content==null)throw new Failure("protocol_error");
        JSONArray rows=new JSONArray();int size=0;
        for(int i=0;i<content.length()&&rows.length()<3;i++){
            JSONObject block=content.optJSONObject(i);if(block==null||!"text".equals(block.optString("type")))continue;
            String text=block.optString("text");size+=text.length();if(size>LIMIT)throw new Failure("response_too_large");
            // Only explicit result headers, never a URL mentioned inside the highlights body.
            for(String hit:text.replace("\r\n","\n").split("\\n\\n---\\n\\n")){
                if(rows.length()>=3)break;String[] lines=hit.trim().split("\n",-1);
                if(lines.length<2||!lines[0].startsWith("Title: ")||!lines[1].startsWith("URL: "))continue;
                String title=lines[0].substring(7).trim(),url=lines[1].substring(5).trim();
                if(title.isEmpty()||title.equalsIgnoreCase("N/A"))continue;
                try{url=LinkReader.safeUrl(url).toString();}catch(Exception unsafe){continue;}
                StringBuilder snippet=new StringBuilder();boolean highlights=false;
                for(int n=2;n<lines.length&&snippet.length()<1500;n++){if(lines[n].equals("Highlights:")){highlights=true;continue;}if(highlights)snippet.append(lines[n]).append('\n');}
                rows.put(new JSONObject().put("url",url).put("title",title).put("content",snippet.substring(0,Math.min(1500,snippet.length()))));
            }
        }
        return rows;
    }
    private static int remaining(long deadline)throws Exception {long ms=deadline-System.currentTimeMillis();if(ms<=0)throw new SocketTimeoutException();return (int)Math.min(ms,8000);}
    private static Reply post(JSONObject rpc,String session,String protocol,long deadline)throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(remaining(deadline));conn.setReadTimeout(remaining(deadline));conn.setRequestMethod("POST");conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Accept","application/json, text/event-stream");conn.setRequestProperty("MCP-Protocol-Version",protocol);
        conn.setRequestProperty("User-Agent","Zhishi/1.4.1 (anonymous link search)");if(!session.isEmpty())conn.setRequestProperty("Mcp-Session-Id",session);
        byte[] bytes=rpc.toString().getBytes(StandardCharsets.UTF_8);conn.setFixedLengthStreamingMode(bytes.length);
        try {
            try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
            conn.setReadTimeout(remaining(deadline));int code=conn.getResponseCode();String mime=conn.getContentType();if(mime==null)mime="";
            String sid=conn.getHeaderField("Mcp-Session-Id");if(sid==null)sid="";
            if(code!=200||!rpc.has("id"))return new Reply(code,mime,sid,"");
            if(!mime.toLowerCase(Locale.ROOT).startsWith("application/json")&&!mime.toLowerCase(Locale.ROOT).startsWith("text/event-stream"))throw new Failure("protocol_error");
            if(conn.getContentLengthLong()>LIMIT)throw new Failure("response_too_large");
            try(InputStream in=conn.getInputStream()){
                ByteArrayOutputStream body=new ByteArrayOutputStream();byte[] buffer=new byte[4096];boolean sse=mime.toLowerCase(Locale.ROOT).startsWith("text/event-stream");
                while(true){conn.setReadTimeout(remaining(deadline));int n=in.read(buffer);if(n==-1)break;if(body.size()+n>LIMIT)throw new Failure("response_too_large");body.write(buffer,0,n);
                    // Streamable HTTP may keep SSE open. Finish on the matching complete event.
                    if(sse){String raw=new String(body.toByteArray(),StandardCharsets.UTF_8);int end=raw.replace("\r\n","\n").lastIndexOf("\n\n");if(end>=0){String complete=raw.replace("\r\n","\n").substring(0,end+2);try{decode(complete,mime,rpc.getInt("id"));return new Reply(code,mime,sid,complete);}catch(Failure pending){if(!pending.status.equals("protocol_error"))throw pending;}}}
                }
                return new Reply(code,mime,sid,new String(body.toByteArray(),StandardCharsets.UTF_8));
            }
        }finally{conn.disconnect();}
    }
}
