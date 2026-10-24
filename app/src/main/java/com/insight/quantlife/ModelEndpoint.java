package com.insight.quantlife;
import java.net.URL;
/** HTTPS on public networks. Plain HTTP is only supported for literal local/private IPs. */
final class ModelEndpoint {
 static boolean bailian(URL u){String h=u.getHost().toLowerCase(java.util.Locale.ROOT);return h.matches("dashscope(?:-intl|-us)?\\.aliyuncs\\.com")||h.endsWith(".dashscope.aliyuncs.com")||h.endsWith(".maas.aliyuncs.com");}
 static boolean localHttp(URL u){String h=u.getHost();if(h.equals("localhost")||h.equals("[::1]")||h.equals("::1"))return true;String[] parts=h.split("\\.");if(parts.length!=4)return false;int[] n=new int[4];try{for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))return false;n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;}}catch(Exception e){return false;}return n[0]==10||n[0]==127||n[0]==192&&n[1]==168||n[0]==172&&n[1]>=16&&n[1]<=31;}
 static void validate(URL u)throws Exception{if(!u.getProtocol().matches("https|http")||u.getUserInfo()!=null||u.getQuery()!=null||u.getRef()!=null)throw new Exception("API 地址应为不含密钥、参数或片段的 HTTP/HTTPS 基础地址");if(u.getProtocol().equals("http")&&!localHttp(u))throw new Exception("公网 API 必须使用 HTTPS；HTTP 仅支持本机或私有内网 IP");}
}
