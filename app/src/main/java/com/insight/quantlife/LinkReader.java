package com.insight.quantlife;

import android.text.Html;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.*;

/** Read-only, bounded public-page metadata. No WebView, cookies, JS or model credentials. */
public final class LinkReader {
    private static final int LIMIT=512*1024;
    public static final class Page {
        public final int code; public final String location,mime; public final byte[] bytes;
        public Page(int c,String l,String m,byte[] b){code=c;location=l;mime=m;bytes=b;}
    }
    public interface Transport {Page get(URL url,long deadline)throws Exception;}
    public interface SearchTransport {JSONObject search(JSONObject request,String key)throws Exception;}
    private final Transport transport; private final SearchTransport search; private final ExaMcp exa;
    public LinkReader(){this(LinkReader::getPage,LinkReader::searchTavily,new ExaMcp());}
    public LinkReader(Transport t,SearchTransport s){this(t,s,null);}
    public LinkReader(Transport t,SearchTransport s,ExaMcp e){transport=t;search=s;exa=e;}
    public static JSONObject searchSettings(JSONObject stored)throws Exception {
        if(stored==null)return new JSONObject().put("provider","exa").put("enabled",true).put("apiKey","");
        JSONObject s=new JSONObject(stored.toString());String provider=s.optString("provider","tavily");
        if(!provider.matches("exa|tavily"))provider="exa";
        return s.put("provider",provider).put("enabled",s.optBoolean("enabled")).put("apiKey",s.optString("apiKey"));
    }

    public static URL safeUrl(String input)throws Exception {
        if(input==null||input.length()>2048||input.matches("(?s).*[\\x00-\\x20\\x7f\\\\].*"))throw new Exception("链接格式无效或过长");
        URI uri=new URI(input);String scheme=uri.getScheme();
        if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))throw new Exception("仅支持公开 HTTP/HTTPS 网页");
        if(uri.getRawUserInfo()!=null)throw new Exception("不读取含用户名或密钥的链接");
        URL url=new URL(uri.toASCIIString());String host=url.getHost().toLowerCase(Locale.ROOT);
        if(host.isEmpty()||host.endsWith(".")||host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local")||host.endsWith(".internal")||!host.contains(".")||host.contains(":"))throw new Exception("不读取本机、内网或IP地址链接");
        if(host.matches("[0-9.]+")||host.matches("(?i)(0x[0-9a-f]+\\.)*0x[0-9a-f]+"))throw new Exception("不读取IP地址链接");
        int port=url.getPort();if(port!=-1&&!("https".equalsIgnoreCase(scheme)&&port==443)&&!("http".equalsIgnoreCase(scheme)&&port==80))throw new Exception("链接只允许标准网页端口");
        String query=uri.getRawQuery();if(query!=null&&URLDecoder.decode(query,"UTF-8").matches("(?is).*(^|[&;])(api[_-]?key|access[_-]?token|token|password|authorization|auth|session|ticket|signature|sign|key)=.*"))throw new Exception("链接含敏感访问参数，请改发公开分享链接");
        // Upgrade cleartext links; never send arbitrary page requests over HTTP.
        return new URL("https",host,-1,(url.getPath().isEmpty()?"/":url.getPath())+(url.getQuery()==null?"":"?"+url.getQuery()));
    }
    public static boolean publicAddress(InetAddress address){
        if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
        byte[] b=address.getAddress();int a=b[0]&255;
        if(b.length==4){int c=b[1]&255;return a!=0&&a!=10&&a!=127&&a<224&&!(a==100&&c>=64&&c<=127)&&!(a==169&&c==254)&&!(a==172&&c>=16&&c<=31)&&!(a==192&&(c==168||c==0||c==2))&&!(a==198&&(c==18||c==19||c==51))&&!(a==203&&c==0);}
        // Only native global unicast; excludes ULA, mapped IPv4, NAT64 and tunnel ranges.
        return b.length==16&&(a&0xe0)==0x20&&!(a==0x20&&(b[1]&255)==0x02)&&!(a==0x20&&(b[1]&255)==1&&((b[2]&255)<2||(b[2]&255)==0x0d));
    }
    public JSONObject resolve(String input,JSONObject config)throws Exception {
        JSONObject result=new JSONObject().put("url",input).put("status","unavailable").put("contentStatus","unavailable").put("title","").put("author","").put("contentType","other").put("platform","").put("searchResults",new JSONArray());
        URL url;
        try{url=safeUrl(input);}catch(Exception e){return result.put("status","blocked").put("message",e.getMessage());}
        URL requested=url;result.put("platform",platform(url.getHost()));long overall=System.currentTimeMillis()+40000,deadline=System.currentTimeMillis()+18000;
        try{
            for(int n=0;n<4;n++){
                Page page=transport.get(url,deadline);
                if(page.code>=300&&page.code<400){if(page.location.isEmpty()||n==3)throw new Exception("网页重定向过多或缺少目标");URL target=new URL(url,page.location);if(target.getHost().equals("mp.weixin.qq.com")&&target.getPath().contains("captcha")){result.put("directStatus","verification_required");throw new Exception("微信返回验证页，未读取文章正文");}try{url=safeUrl(target.toString());}catch(Exception unsafe){throw new SecurityException();}continue;}
                if(page.code!=200){result.put("directStatus","http_"+page.code);throw new Exception("网页暂不可读（HTTP "+page.code+"）");}
                if(!page.mime.toLowerCase(Locale.ROOT).matches(".*(text/html|application/xhtml\\+xml).*"))throw new Exception("此链接不是可读取的网页；文件请作为附件发送");
                JSONObject meta=parse(decode(page.bytes,page.mime),url.toString());
                if(meta.optString("title").isEmpty()||challenge(meta.optString("title"))){result.put("directStatus","verification_or_missing_title");throw new Exception("网页缺少可靠标题或需要登录／验证");}
                for(String k:new String[]{"title","platform","author","contentType","description","excerpt"})result.put(k,meta.optString(k));
                result.put("finalUrl",url.toString());
                return result.put("status","metadata").put("directStatus","ok").put("readMethod","direct").put("contentStatus",result.optString("excerpt").length()>=80?"limited_text":"metadata_only").put("message","已读取网页元信息及有限文本；不保证全文，未观看视频或读取书籍全文");
            }
        }catch(SecurityException e){return result.put("status","blocked").put("message","域名或重定向目标不是允许的公开网页，已拒绝读取");}
        catch(Exception e){if(!result.has("directStatus"))result.put("directStatus",e instanceof SocketTimeoutException?"timeout":"unavailable");result.put("message",e instanceof SocketTimeoutException?"网页读取超时":e.getMessage()==null?"网页读取失败":e.getMessage());}
        result.put("title","").put("author","").put("description","").put("excerpt","").put("contentType","other");
        // An injected reader without Exa stays offline unless explicitly configured (test seam).
        JSONObject effective=searchSettings(config==null&&exa==null?new JSONObject():config);
        String provider=effective.optString("provider");result.put("searchProvider",provider);
        if(!effective.optBoolean("enabled"))return result.put("searchStatus","disabled");
        if(provider.equals("tavily")&&effective.optString("apiKey").isEmpty())return result.put("searchStatus","not_configured").put("message","网页读取失败；Tavily未配置Key，可在设置中改用免Key的Exa匿名MCP，或补充标题／截图");
        try{
            if(provider.equals("exa")&&exa!=null)try{
                JSONObject fetched=exa.fetch(requested.toString(),overall);if(!challenge(fetched.optString("title"))){JSONObject types=parse("",requested.toString());return result.put("status","metadata").put("title",fetched.getString("title")).put("excerpt",fetched.getString("excerpt")).put("description","").put("contentType",types.optString("contentType","other")).put("finalUrl",fetched.getString("url")).put("readMethod","exa_fetch").put("contentStatus","limited_text").put("textTruncated",fetched.optBoolean("textTruncated")).put("fetchStatus","ok").put("message","通过Exa读取了同链接的有限正文；不保证全文／实时，不将其当作个人笔记");}
            }catch(ExaMcp.Failure e){result.put("fetchStatus",e.status);if(e.status.matches("rate_limited|authentication_required"))throw e;}
            // Never search an anti-bot redirect carrying temporary access parameters.
            JSONObject response=provider.equals("exa")?exa.search(requested.toString(),overall):search.search(searchRequest(url.toString()),effective.getString("apiKey"));
            JSONArray results=searchResults(response);result.put("searchResults",results);
            for(int i=0;i<results.length();i++){JSONObject hit=results.getJSONObject(i);if(samePage(input,hit.getString("url"))||samePage(url.toString(),hit.getString("url"))){
                if(hit.optString("title").isEmpty()||challenge(hit.optString("title")))continue;
                return result.put("title",hit.getString("title")).put("description",hit.optString("snippet")).put("platform",platform(new URL(hit.getString("url")).getHost())).put("contentType",parse("",hit.getString("url")).optString("contentType","other")).put("status","search_match").put("readMethod","search").put("contentStatus","metadata_only").put("searchStatus","matched").put("message","来自"+(provider.equals("exa")?"Exa匿名MCP":"Tavily")+"的同一链接搜索摘要，未读取正文；请核对标题和类型");
            }}
            result.put("searchStatus","no_exact_match").put("message","网页读取失败；搜索未找到同一链接的可靠标题，请补充标题或截图");
        }catch(ExaMcp.Failure e){String msg=e.status.equals("rate_limited")?"Exa匿名搜索已限流，请两分钟后重试或手动补充标题／截图；不会自动切换到收费服务":e.status.equals("timeout")?"Exa匿名搜索超时，请检查网络或手动补充标题／截图":e.status.equals("authentication_required")?"Exa匿名入口当前要求认证；本应用不会自动登录或使用Key，请手动补充标题／截图":e.status.equals("protocol_error")?"Exa搜索协议或结果格式发生变化，请稍后重试或补充标题／截图":"Exa匿名搜索暂不可用，请检查网络或补充标题／截图";result.put("searchStatus",e.status).put("message",msg);}
        catch(Exception e){result.put("searchStatus","failed").put("message",provider.equals("exa")?"Exa匿名搜索暂不可用，请检查网络或补充标题／截图":"网页读取失败，Tavily搜索亦不可用，请检查搜索Key、额度或网络，或补充标题／截图");}
        return result;
    }
    private static boolean challenge(String s){return s.matches("(?is).*(just a moment|access denied|attention required|访问受限|安全检查|环境异常|完成验证|captcha).*")||s.matches("(?is)\\s*(登录|验证|login|sign in|用户登录|安全验证).*");}
    public static String platform(String h){h=h.toLowerCase(Locale.ROOT);String[][] names={{"mp.weixin.qq.com","微信公众号"},{"bilibili.com","B站"},{"b23.tv","B站"},{"zhihu.com","知乎"},{"youtube.com","YouTube"},{"youtu.be","YouTube"},{"douyin.com","抖音"},{"douban.com","豆瓣"},{"csdn.net","CSDN"},{"juejin.cn","掘金"},{"xiaohongshu.com","小红书"}};for(String[] n:names)if(h.equals(n[0])||h.endsWith("."+n[0]))return n[1];return h;}
    private static String clean(String s,int limit){String text=Html.fromHtml(s,Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("[\\s\\u00a0]+"," ").trim();return text.substring(0,Math.min(limit,text.length()));}
    public static JSONObject parse(String html,String url)throws Exception {
        Map<String,String> meta=new HashMap<>();Matcher tags=Pattern.compile("(?is)<meta\\b[^>]{0,8192}>").matcher(html);
        Pattern attr=Pattern.compile("(?is)([\\w:-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
        while(tags.find()){Map<String,String> a=new HashMap<>();Matcher fields=attr.matcher(tags.group());while(fields.find())a.put(fields.group(1).toLowerCase(Locale.ROOT),fields.group(2)!=null?fields.group(2):fields.group(3)!=null?fields.group(3):fields.group(4));String key=a.containsKey("property")?a.get("property"):a.get("name");if(key!=null&&a.containsKey("content"))meta.put(key.toLowerCase(Locale.ROOT),clean(a.get("content"),600));}
        String title=meta.getOrDefault("og:title",meta.getOrDefault("twitter:title",""));if(title.isEmpty()){Matcher t=Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title\\s*>").matcher(html);if(t.find())title=clean(t.group(1),300);}
        String type=meta.getOrDefault("og:type","").toLowerCase(Locale.ROOT),kind=type.startsWith("video")?"video":type.equals("book")?"book":type.equals("article")?"article":"other";
        String host=new URL(url).getHost(),path=new URL(url).getPath();if(kind.equals("other")){if((platform(host).equals("B站")&&path.startsWith("/video/"))||(platform(host).equals("YouTube")&&(path.equals("/watch")||path.startsWith("/shorts/")||host.equals("youtu.be"))))kind="video";else if((host.equals("book.douban.com")&&path.startsWith("/subject/"))||(host.equals("m.douban.com")&&path.startsWith("/book/subject/"))||(host.equals("read.douban.com")&&path.startsWith("/ebook/")))kind="book";else if(host.equals("mp.weixin.qq.com")||path.matches("/(article|articles|post|posts)/.+"))kind="article";}
        Matcher ld=Pattern.compile("(?is)<script\\b[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script\\s*>").matcher(html);
        while(ld.find())try{String json=ld.group(1).trim();Object structured=json.startsWith("[")?new JSONArray(json):new JSONObject(json);JSONObject evidence=structured instanceof JSONArray?((JSONArray)structured).optJSONObject(0):(JSONObject)structured;if(evidence==null)continue;JSONArray graph=evidence.optJSONArray("@graph");if(graph!=null){for(int i=0;i<graph.length();i++){JSONObject node=graph.optJSONObject(i);if(node!=null&&node.optString("@type").matches("Book|VideoObject|Article|NewsArticle|BlogPosting")){evidence=node;break;}}}String t=evidence.optString("@type");if(t.equals("Book"))kind="book";else if(t.equals("VideoObject"))kind="video";else if(t.matches("Article|NewsArticle|BlogPosting"))kind="article";if(title.isEmpty())title=clean(evidence.optString("headline",evidence.optString("name")),300);if(!meta.containsKey("author")){JSONObject author=evidence.optJSONObject("author");String name=author!=null?author.optString("name"):evidence.opt("author") instanceof String?evidence.optString("author"):"";meta.put("author",clean(name,300));}}catch(Exception ignored){}
        String stripped=html.replaceAll("(?is)<!--.*?-->|<(script|style|noscript|svg)\\b[^>]*>.*?</\\1\\s*>"," ").replaceAll("(?is)<(script|style|noscript|svg)\\b[^>]*>.*$","");
        return new JSONObject().put("title",title.substring(0,Math.min(300,title.length()))).put("platform",platform(host)).put("author",meta.getOrDefault("author",meta.getOrDefault("article:author",""))).put("contentType",kind).put("description",meta.getOrDefault("og:description",meta.getOrDefault("description",""))).put("excerpt",clean(stripped,5000));
    }
    public static boolean samePage(String a,String b){try{return identity(a).equals(identity(b));}catch(Exception e){return false;}}
    private static String identity(String s)throws Exception{URL u=safeUrl(s);List<String> query=new ArrayList<>();if(u.getQuery()!=null)for(String p:u.getQuery().split("&"))if(!p.matches("(?i)(utm_[^=]*|spm|from|source|fbclid|gclid)=.*"))query.add(p);Collections.sort(query);return u.getHost()+u.getPath().replaceAll("/$","")+"?"+String.join("&",query);}
    public static JSONObject searchRequest(String url)throws Exception{return new JSONObject().put("query",safeUrl(url).toString()).put("search_depth","basic").put("max_results",3).put("include_answer",false).put("include_raw_content",false).put("include_images",false);}
    public static JSONArray searchResults(JSONObject response)throws Exception{JSONArray out=new JSONArray(),rows=response.optJSONArray("results");if(rows!=null)for(int i=0;i<Math.min(3,rows.length());i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;try{String url=safeUrl(r.optString("url")).toString();out.put(new JSONObject().put("url",url).put("title",clean(r.optString("title"),300)).put("snippet",clean(r.optString("content"),1500)));}catch(Exception ignored){}}return out;}
    private static String decode(byte[] bytes,String mime){String prefix=new String(bytes,0,Math.min(bytes.length,4096),StandardCharsets.ISO_8859_1);Matcher m=Pattern.compile("(?i)charset\\s*=\\s*[\"']?([a-z0-9_-]+)").matcher(mime+" "+prefix);Charset charset=StandardCharsets.UTF_8;if(m.find())try{charset=Charset.forName(m.group(1));}catch(Exception ignored){}return new String(bytes,charset);}
    private static int remaining(long deadline)throws Exception{int ms=(int)(deadline-System.currentTimeMillis());if(ms<=0)throw new SocketTimeoutException();return Math.min(ms,6000);}
    private static Page getPage(URL url,long deadline)throws Exception {
        InetAddress[] addresses=InetAddress.getAllByName(url.getHost());if(addresses.length==0)throw new Exception("网页域名无法解析");for(InetAddress a:addresses)if(!publicAddress(a))throw new SecurityException("域名解析到非公网地址，已拒绝读取");
        // Pin the validated DNS address: TLS still verifies the original host (including SNI).
        try(Socket socket=new Socket()){
            InetAddress chosen=addresses[0];for(InetAddress a:addresses)if(a.getAddress().length==4){chosen=a;break;}socket.connect(new InetSocketAddress(chosen,443),remaining(deadline));socket.setSoTimeout(remaining(deadline));
            try(SSLSocket tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(socket,url.getHost(),443,true)){
                SSLParameters params=tls.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");tls.setSSLParameters(params);tls.startHandshake();
                String path=url.getFile().isEmpty()?"/":url.getFile();String request="GET "+path+" HTTP/1.1\r\nHost: "+url.getHost()+"\r\nUser-Agent: Zhishi/1.4.0 (public link metadata)\r\nAccept: text/html,application/xhtml+xml\r\nAccept-Encoding: gzip\r\nConnection: close\r\n\r\n";
                tls.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));tls.getOutputStream().flush();InputStream in=new BufferedInputStream(tls.getInputStream());
                String first=line(in,deadline);String[] status=first.split(" ");if(status.length<2||!status[0].startsWith("HTTP/1."))throw new Exception("网页响应格式无效");int code=Integer.parseInt(status[1]);Map<String,String> headers=new HashMap<>();int size=0;
                for(String l;(l=line(in,deadline)).length()>0;){size+=l.length();if(size>32768)throw new Exception("网页响应头过大");int colon=l.indexOf(':');if(colon>0)headers.put(l.substring(0,colon).toLowerCase(Locale.ROOT),l.substring(colon+1).trim());}
                if(code!=200)return new Page(code,headers.getOrDefault("location",""),"",new byte[0]);
                String mime=headers.getOrDefault("content-type","");if(!mime.toLowerCase(Locale.ROOT).matches(".*(text/html|application/xhtml\\+xml).*"))return new Page(200,"",mime,new byte[0]);
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];boolean chunked=headers.getOrDefault("transfer-encoding","").equalsIgnoreCase("chunked");long left=chunked?0:headers.containsKey("content-length")?Long.parseLong(headers.get("content-length")):-1;
                if(left>LIMIT)left=LIMIT;
                while(true){tls.setSoTimeout(remaining(deadline));if(chunked){left=Long.parseLong(line(in,deadline).split(";",2)[0].trim(),16);if(left==0)break;if(left<0)throw new Exception("网页分块格式无效");left=Math.min(left,LIMIT-out.size());}
                    if(left==0)break;long chunk=left;while(left!=0&&out.size()<LIMIT){int n=in.read(buf,0,(int)Math.min(LIMIT-out.size(),left<0?buf.length:Math.min(left,buf.length)));if(n<0){if(left>0)throw new EOFException("网页内容不完整");break;}out.write(buf,0,n);if(left>0)left-=n;remaining(deadline);}
                    if(!chunked||out.size()>=LIMIT)break;if(chunk>0&&!line(in,deadline).isEmpty())throw new Exception("网页分块格式无效");
                }
                byte[] bytes=out.toByteArray();String encoding=headers.getOrDefault("content-encoding","");if(encoding.equalsIgnoreCase("gzip")){try(InputStream gz=new GZIPInputStream(new ByteArrayInputStream(bytes))){ByteArrayOutputStream decoded=new ByteArrayOutputStream();int n;while(decoded.size()<LIMIT&&(n=gz.read(buf,0,Math.min(buf.length,LIMIT-decoded.size())))!=-1)decoded.write(buf,0,n);bytes=decoded.toByteArray();}}else if(!encoding.isEmpty()&&!encoding.equalsIgnoreCase("identity"))throw new Exception("网页编码暂不支持");return new Page(code,"",mime,bytes);
            }
        }
    }
    private static String line(InputStream in,long deadline)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();for(int c;(c=in.read())!=-1;){remaining(deadline);if(c=='\n')return new String(out.toByteArray(),StandardCharsets.ISO_8859_1).replaceAll("\r$","");out.write(c);if(out.size()>8192)throw new Exception("网页响应头过大");}throw new EOFException("网页响应不完整");}
    private static byte[] bounded(InputStream in,int limit)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new Exception("网页或搜索响应过大");out.write(b,0,n);}return out.toByteArray();}
    private static JSONObject searchTavily(JSONObject body,String key)throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL("https://api.tavily.com/search").openConnection();conn.setConnectTimeout(8000);conn.setReadTimeout(12000);conn.setInstanceFollowRedirects(false);conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("Authorization","Bearer "+key);
        try{try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}if(conn.getResponseCode()!=200)throw new Exception("搜索服务暂不可用");try(InputStream in=conn.getInputStream()){return new JSONObject(new String(bounded(in,256*1024),StandardCharsets.UTF_8));}}finally{conn.disconnect();}
    }
}
