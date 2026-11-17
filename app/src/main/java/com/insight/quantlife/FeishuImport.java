package com.insight.quantlife;

import android.content.Context;
import android.webkit.CookieManager;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Read-only Feishu page snapshots. Never evaluates downloaded scripts or calls a model. */
public final class FeishuImport {
 public static final int LIMIT=12*1024*1024;
 private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
 public static boolean host(String h){if(h==null)return false;h=h.toLowerCase(Locale.ROOT);return h.equals("feishu.cn")||h.endsWith(".feishu.cn")||h.equals("larksuite.com")||h.endsWith(".larksuite.com")||h.equals("larkoffice.com")||h.endsWith(".larkoffice.com");}
 public static String canonical(String input)throws Exception{
  URI u=new URI(input);if(!"https".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||!host(u.getHost())||u.getPort()!=-1&&u.getPort()!=443)throw new Exception("请填写飞书 /docx/ 或 /wiki/ 文档 HTTPS 链接");
  if(u.getRawPath()==null||!u.getRawPath().matches("/(docx|wiki)/[A-Za-z0-9]{8,100}/?"))throw new Exception("仅支持飞书新版文档及知识库文档链接，暂不支持表格和多维表格");
  return "https://"+u.getHost().toLowerCase(Locale.ROOT)+u.getPath().replaceAll("/$","");
 }
 public static String hash(String text)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 public static String contentHash(JSONObject r)throws Exception{return hash(r.optString("title")+"\n"+r.optString("note")+"\n"+(r.optJSONArray("images")==null?"[]":r.getJSONArray("images").toString()));}
 private static byte[] bytes(InputStream input,int max)throws Exception{return bytes(input,max,Long.MAX_VALUE);}
 private static byte[] bytes(InputStream input,int max,long deadline)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while(true){if(System.currentTimeMillis()>deadline||Thread.currentThread().isInterrupted())throw new Exception("飞书读取超时或已取消，请重试");if((n=in.read(b))==-1)break;if(out.size()+n>max)throw new Exception("飞书内容超过本次导入上限，请拆分文档后导入");out.write(b,0,n);}return out.toByteArray();}}
 private static void publicHost(URL u)throws Exception{InetAddress[] all=InetAddress.getAllByName(u.getHost());if(all.length==0)throw new Exception("无法解析飞书地址");for(InetAddress a:all)if(!LinkReader.publicAddress(a))throw new Exception("不访问本机或内网地址");}
 private static boolean mediaHost(String h){return host(h)||h.endsWith(".feishucdn.com")||h.endsWith(".larksuitecdn.com")||h.endsWith(".byteimg.com");}
 static byte[] fetch(String input,boolean image,boolean cookies,long deadline)throws Exception{
  java.net.CookieManager jar=new java.net.CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  URL u=new URL(input);for(int i=0;i<8;i++){
   if(!"https".equals(u.getProtocol())||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443||!(image?mediaHost(u.getHost()):host(u.getHost())))throw new Exception("飞书跳转或图片来源不在允许范围，未读取");publicHost(u);
   int remaining=(int)Math.min(20000,deadline-System.currentTimeMillis());if(remaining<1)throw new Exception("飞书读取超时，请检查网络后重试");
   HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(remaining);c.setReadTimeout(Math.min(5000,remaining));c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept",image?"image/*":"text/html");
   // CookieManager returns only cookies belonging to this validated URL. Never log or export them.
   StringBuilder header=new StringBuilder();for(List<String> values:jar.get(u.toURI(),Collections.emptyMap()).values())for(String value:values){if(header.length()>0)header.append("; ");header.append(value);}
   if(cookies){String cookie=CookieManager.getInstance().getCookie(u.toString());if(cookie!=null){if(header.length()>0)header.append("; ");header.append(cookie);}}if(header.length()>0)c.setRequestProperty("Cookie",header.toString());
   try{int code=c.getResponseCode();jar.put(u.toURI(),c.getHeaderFields());if(code>=300&&code<400){String target=c.getHeaderField("Location");if(target==null)throw new Exception("飞书未返回有效跳转");u=new URL(u,target);continue;}if(code!=200)throw new Exception(code==401||code==403?"此文档需要登录或没有阅读权限，请在飞书阅读页核对":"飞书读取失败（HTTP "+code+"），可以重试");String mime=String.valueOf(c.getContentType()).toLowerCase(Locale.ROOT);if(image?!mime.startsWith("image/"):!mime.contains("text/html"))throw new Exception("飞书返回的不是预期内容，未导入");return bytes(c.getInputStream(),image?RecordMedia.INPUT_LIMIT:LIMIT,deadline);}finally{c.disconnect();}
  }throw new Exception("飞书跳转过多，未导入");
 }
 public static JSONObject fromHtml(String html,String url)throws Exception{
  if(html.length()>LIMIT)throw new Exception("飞书页面过大，请拆分后导入");
  Matcher m=Pattern.compile("clientVars\\s*:\\s*Object\\s*\\(").matcher(html);while(m.find())try{
   Object o=new JSONTokener(html.substring(m.end())).nextValue();if(o instanceof JSONObject&&((JSONObject)o).optJSONObject("data")!=null&&((JSONObject)o).getJSONObject("data").optJSONObject("block_map")!=null)return parse((JSONObject)o,url);
  }catch(JSONException ignored){}
  throw new Exception("未读到完整飞书正文。可能需要登录、文档尚未加载或页面版本不兼容；请打开飞书阅读页后重试");
 }
 public static JSONObject parse(JSONObject payload,String input)throws Exception{
  String url=canonical(input);JSONObject data=payload.getJSONObject("data"),map=data.getJSONObject("block_map");
  if(!data.has("has_more")||data.optBoolean("has_more",true)||nonempty(data.opt("skip_blocks"))||nonempty(data.opt("next_cursors")))throw new Exception("飞书只加载了部分文档，未当作全文导入。请在阅读页加载完整文档后重试，或拆分文档");
  if(map.length()>20000)throw new Exception("文档超过 20000 个内容块，请拆分后导入");
  String id=data.getString("id");if(!id.matches("[A-Za-z0-9]{8,100}"))throw new Exception("飞书文档编号无效");
  if(url.contains("/docx/")&&!url.endsWith("/"+id))throw new Exception("返回内容与原文编号不一致，未导入");
  Renderer renderer=new Renderer(map);JSONObject page=map.getJSONObject(id).getJSONObject("data");if(!page.optString("type").equals("page"))throw new Exception("链接不是飞书文档");String title=renderer.text(page,false).trim();
  if(title.isEmpty()||title.length()>300||title.matches("(?i)登录|login|飞书|lark"))throw new Exception("未读到有效文档标题，未导入");
  String note=renderer.children(page,0).trim();if(note.isEmpty())throw new Exception("文档没有可导入的正文");if(note.length()>1000000)throw new Exception("文档超过 100 万字符，请拆分后导入");
  String canonicalDoc="https://"+new URI(url).getHost()+"/docx/"+id;
  JSONObject out=new JSONObject().put("url",canonicalDoc).put("requestedUrl",url).put("sourceId",new URI(url).getHost()+"/docx/"+id).put("documentId",id).put("title",title).put("note",note).put("remoteImages",renderer.images).put("warnings",new JSONArray(renderer.warnings)).put("blocks",renderer.visited.size()).put("fetchedAt",System.currentTimeMillis());
  // Use only this document's metadata, never dates mentioned in body text,
  // child blocks, last-edit times, fetch time or another document's metadata.
  JSONObject metaMap=data.optJSONObject("meta_map"),meta=metaMap==null?null:metaMap.optJSONObject(id);
  if(meta!=null)try{OffsetDateTime created=OffsetDateTime.parse(meta.optString("create_time"));long millis=created.toInstant().toEpochMilli();if(millis>=946684800000L&&millis<=System.currentTimeMillis()+86400000L)out.put("documentCreatedAt",millis).put("documentCreatedDate",created.toLocalDate().toString());}catch(java.time.DateTimeException ignored){}
  return out;
 }
 private static boolean nonempty(Object o){return o instanceof JSONArray?((JSONArray)o).length()>0:o instanceof JSONObject?((JSONObject)o).length()>0:o!=null&&o!=JSONObject.NULL&&!o.toString().isEmpty();}
 private static String escape(String s){return s.replace("\\","\\\\").replace("*","\\*").replace("_","\\_").replace("[","\\[").replace("]","\\]").replace("`","\\`");}
 private static final class Renderer {
  final JSONObject map;final Set<String> visited=new HashSet<>(),warnings=new LinkedHashSet<>();final JSONArray images=new JSONArray();
  Renderer(JSONObject m){map=m;}
  String text(JSONObject d,boolean rich)throws Exception{
   JSONObject t=d.optJSONObject("text");if(t==null)return "";JSONObject init=t.optJSONObject("initialAttributedTexts");if(init==null)throw new Exception("存在无法读取的飞书文本格式，未导入残缺正文；请尝试导出为 Markdown 后导入");
   JSONObject texts=init.optJSONObject("text"),attrs=init.optJSONObject("attribs"),pool=t.optJSONObject("apool");if(texts==null)throw new Exception("飞书文本内容缺失，未导入残缺正文");JSONObject attrib=new JSONObject(),source=pool==null?null:pool.optJSONObject("numToAttrib");
   if(source!=null)for(Iterator<String> it=source.keys();it.hasNext();){String key=it.next();try{attrib.put(Integer.toString(Integer.parseInt(key),36),source.get(key));}catch(NumberFormatException ignored){attrib.put(key,source.get(key));}}
   List<String> keys=new ArrayList<>();for(Iterator<String> it=texts.keys();it.hasNext();)keys.add(it.next());keys.sort((a,b)->{try{return Integer.compare(Integer.parseInt(a),Integer.parseInt(b));}catch(Exception e){return a.compareTo(b);}});
   StringBuilder out=new StringBuilder();for(String key:keys){String s=texts.getString(key);if(!rich){out.append(s);continue;}String operations=attrs==null?"":attrs.optString(key);Matcher ops=Pattern.compile("((?:\\*[0-9a-z]+)*)(?:\\|[0-9a-z]+)?\\+([0-9a-z]+)").matcher(operations);int at=0,opAt=0;
    while(ops.find()){if(ops.start()!=opAt)break;opAt=ops.end();int len;try{len=Integer.parseInt(ops.group(2),36);}catch(Exception e){break;}if(len<0||at+len>s.length())break;String part=escape(s.substring(at,at+len));String link="";boolean bold=false,italic=false,strike=false,code=false;for(String num:ops.group(1).split("\\*")){JSONArray a=attrib==null?null:attrib.optJSONArray(num);if(a==null||a.length()<2)continue;String n=a.optString(0),v=a.optString(1);boolean on=v.equals("true")||v.equals("1");if(n.equals("bold"))bold=on;if(n.equals("italic"))italic=on;if(n.equals("strikethrough"))strike=on;if(n.equals("inlineCode"))code=on;if(n.equals("link"))try{String raw=URLDecoder.decode(v,"UTF-8");URI u=new URI(raw);if(("https".equals(u.getScheme())||"http".equals(u.getScheme()))&&u.getRawUserInfo()==null)link=raw.replace(")","%29").replace("(","%28");}catch(Exception ignored){}}
     if(!part.trim().isEmpty()){if(code)part="`"+s.substring(at,at+len).replace("`","\\`")+"`";if(bold)part="**"+part+"**";if(italic)part="*"+part+"*";if(strike)part="~~"+part+"~~";if(!link.isEmpty())part="["+part+"]("+link+")";}out.append(part);at+=len;
    }if(at<s.length())out.append(escape(s.substring(at)));
   }return out.toString();
  }
  String children(JSONObject d,int depth)throws Exception{JSONArray a=d.optJSONArray("children");if(a==null)return "";StringBuilder b=new StringBuilder();String previous="";for(int i=0;i<a.length();i++){String id=a.getString(i),v=block(id,depth+1);if(v.isEmpty())continue;String type=map.getJSONObject(id).getJSONObject("data").optString("type");if(b.length()>0)b.append(type.equals(previous)&&type.matches("ordered|bullet|todo")?"\n":"\n\n");b.append(v);previous=type;}return b.toString();}
  String block(String id,int depth)throws Exception{
   if(depth>100||!visited.add(id))throw new Exception("文档结构循环或层级过深，未导入");JSONObject wrapper=map.optJSONObject(id);if(wrapper==null)throw new Exception("正文内容块缺失，未导入部分文档，请加载完整后重试");JSONObject d=wrapper.getJSONObject("data");if(d.optBoolean("hidden"))return "";String type=d.optString("type"),s=text(d,true),nested=children(d,depth);
   if(type.startsWith("heading")&&type.matches("heading[1-9]"))s=String.join("",Collections.nCopies(Math.min(6,Integer.parseInt(type.substring(7))),"#"))+" "+s;
   else if(type.equals("bullet")||type.equals("ordered")||type.equals("todo")){if(s.trim().isEmpty()&&nested.isEmpty())return "";s=(type.equals("ordered")?"1. ":type.equals("todo")?"- ["+(d.optBoolean("done")?"x":" ")+"] ":"- ")+s;}
   else if(type.equals("quote"))s="> "+s.replace("\n","\n> ");
   else if(type.equals("quote_container")){s="> "+(s+(s.isEmpty()?"":"\n\n")+nested).replace("\n","\n> ");nested="";}
   else if(type.equals("code")){s=text(d,false);int size=3;Matcher runs=Pattern.compile("`{3,}").matcher(s);while(runs.find())size=Math.max(size,runs.group().length()+1);String fence=String.join("",Collections.nCopies(size,"`")),language=d.optString("language","");if(!language.matches("[A-Za-z0-9_+#.\\-]{1,64}"))language="";s=fence+language+"\n"+s+"\n"+fence;}
   else if(type.equals("divider"))s="---";
   else if(type.equals("image")){if(images.length()>=100)throw new Exception("每次最多导入 100 张图片，请拆分文档");String token=d.optString("token"),src=d.optString("url");images.put(new JSONObject().put("token",token).put("url",src));s="\nZHISHIFEISHUIMAGE"+(images.length()-1)+"PLACEHOLDER\n";}
   else if(type.equals("grid")||type.equals("grid_column"))warnings.add("飞书分栏已按原文顺序转为单列，便于手机阅读");
   else if(!type.matches("text|callout|page|column|grid_column")&&!type.startsWith("heading")){warnings.add("包含暂未完整支持的内容块："+type+"，请核对预览");if(s.isEmpty()&&nested.isEmpty())s="[未导入的飞书内容："+type+"]";}
   if(!nested.isEmpty())s+=(s.isEmpty()?"":"\n\n")+nested;return s;
  }
 }
 public static JSONObject read(Context c,String input)throws Exception{String url=canonical(input);try{JSONObject snapshot=fromHtml(new String(fetch(url,false,false,System.currentTimeMillis()+40000),StandardCharsets.UTF_8),url);return stage(c,snapshot,false);}catch(IOException ex){throw new Exception("飞书网络读取失败，请检查手机网络后重试，或在飞书阅读页打开文档",ex);}}
 public static synchronized JSONObject stage(Context c,JSONObject s,boolean authenticated)throws Exception{
  JSONObject out=new JSONObject(s.toString());JSONArray images=new JSONArray(),refs=out.getJSONArray("remoteImages"),warnings=out.getJSONArray("warnings");String note=out.getString("note");long deadline=System.currentTimeMillis()+90000,total=0;Set<String> ids=new HashSet<>();
  for(int i=0;i<refs.length();i++){JSONObject ref=refs.getJSONObject(i);String placeholder="ZHISHIFEISHUIMAGE"+i+"PLACEHOLDER";try{
   String url=ref.optString("url"),token=ref.optString("token");if(url.isEmpty()&&token.matches("[A-Za-z0-9]{8,150}"))url="https://"+new URI(out.getString("url")).getHost()+"/space/api/box/stream/download/all/"+token+"/";
   if(url.isEmpty())throw new Exception("图片地址缺失");byte[] b=fetch(url,true,authenticated,deadline);total+=b.length;if(total>64L*1024*1024)throw new Exception("图片总大小超过 64MB");JSONObject image=RecordMedia.fromBytes(c,b,"飞书插图 "+(i+1));if(ids.add(image.getString("id")))images.put(image);note=note.replace(placeholder,"![飞书插图 "+(i+1)+"](zhishi-media:"+image.getString("id")+")");
  }catch(Exception e){note=note.replace(placeholder,"[图片未导入 "+(i+1)+"]");warnings.put("第 "+(i+1)+" 张图片未导入，请在原文核对或稍后重试");}}
  out.remove("remoteImages");out.put("note",note).put("images",images).put("ticket",UUID.randomUUID().toString());
  File file=new File(c.getFilesDir(),"feishu-import.json"),temp=new File(c.getFilesDir(),"feishu-import.tmp");try(FileOutputStream stream=new FileOutputStream(temp)){stream.write(out.toString().getBytes(StandardCharsets.UTF_8));stream.getFD().sync();}if(!temp.renameTo(file))throw new Exception("飞书预览暂存失败，请重试");return out;
 }
 public static synchronized JSONObject pending(Context c)throws Exception{File f=new File(c.getFilesDir(),"feishu-import.json");return f.isFile()?new JSONObject(new String(bytes(new FileInputStream(f),LIMIT),StandardCharsets.UTF_8)):null;}
 public static synchronized void clear(Context c,String ticket)throws Exception{JSONObject p=pending(c);if(p!=null&&p.optString("ticket").equals(ticket)&&!new File(c.getFilesDir(),"feishu-import.json").delete())throw new Exception("无法清除导入预览");}
 private FeishuImport(){}
}
