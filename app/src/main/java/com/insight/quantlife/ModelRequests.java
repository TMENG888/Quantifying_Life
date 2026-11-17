package com.insight.quantlife;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.view.ActionMode;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;


/** Shared encrypted settings and bounded model transport for foreground and background work. */
public final class ModelRequests {
    private static synchronized SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("quantlife-secrets")){KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder("quantlife-secrets",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
        return (SecretKey)ks.getKey("quantlife-secrets",null);
    }
    public static JSONObject settings(android.content.Context context) throws Exception {
        SharedPreferences sp=context.getSharedPreferences("secure",android.content.Context.MODE_PRIVATE);String encrypted=sp.getString("settings",null);if(encrypted==null)return new JSONObject();
        String[] parts=encrypted.split(":");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new JSONObject(new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    public static JSONObject ask(android.content.Context context,JSONObject p) throws Exception {
        JSONObject settings=settings(context),profiles=settings.optJSONObject("profiles");String active=settings.optString("activeProfile");
        JSONObject profile=profiles==null?null:profiles.optJSONObject(active);if(profile==null||profile.optString("apiKey").isEmpty())throw new Exception("请先在历史侧栏的「模型配置」中填写 API Key");
        String endpoint=profile.getString("baseUrl"),protocol=profile.optString("protocol","chat");
        URL url=new URL(endpoint+(protocol.equals("responses")?"/responses":"/chat/completions"));
        ModelEndpoint.validate(url);
        JSONArray chat=p.getJSONArray("messages");JSONObject body=new JSONObject();body.put("model",profile.getString("model"));body.put("stream",false);
        JSONObject tool=p.optJSONObject("tool");boolean layout=tool!=null&&"plan_record_layout".equals(tool.optString("name"));boolean rewriting="selection-rewrite".equals(p.optString("purpose"));
        String reference=p.optString("illustrationReferenceId");
        if(!reference.isEmpty()){
            if(tool==null||!"prepare_record_illustration".equals(tool.optString("name")))throw new Exception("参考图片只用于笔记配图策划");
            byte[] image=readBytes(new java.io.FileInputStream(RecordMedia.file(context,reference)),4*1024*1024);
            JSONObject user=chat.getJSONObject(chat.length()-1);if(!"user".equals(user.optString("role")))throw new Exception("配图请求格式无效");
            user.put("content",new JSONArray().put(new JSONObject().put("type","text").put("text",user.getString("content"))).put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","data:image/jpeg;base64,"+Base64.encodeToString(image,Base64.NO_WRAP)))));
        }
        String effort=profile.optString("effort","off");
        if(protocol.equals("responses")) {
            JSONArray input=new JSONArray();String instruction="";
            for(int i=0;i<chat.length();i++){JSONObject m=chat.getJSONObject(i);if(m.getString("role").equals("system")){instruction+=m.getString("content");continue;}
                JSONObject out=new JSONObject();out.put("role",m.getString("role"));Object content=m.get("content");
                if(content instanceof JSONArray){JSONArray items=new JSONArray();JSONArray parts=(JSONArray)content;for(int j=0;j<parts.length();j++){JSONObject part=parts.getJSONObject(j),item=new JSONObject();if(part.getString("type").equals("text")){item.put("type","input_text");item.put("text",part.getString("text"));}else{item.put("type","input_image");item.put("image_url",part.getJSONObject("image_url").getString("url"));}items.put(item);}out.put("content",items);}else out.put("content",content);input.put(out);
            }body.put("instructions",instruction);body.put("input",input);body.put("store",false);body.put("max_output_tokens",rewriting?12000:layout?8192:4096);
            if(!effort.equals("off")){JSONObject reasoning=new JSONObject();reasoning.put("effort",effort);body.put("reasoning",reasoning);}
            if(tool!=null){JSONObject t=new JSONObject(tool.toString());t.put("type","function");body.put("tools",new JSONArray().put(t));body.put("tool_choice",(layout||profile.optString("id").equals("deepseek"))&&effort.equals("off")?new JSONObject().put("type","function").put("name",tool.getString("name")):"auto");}
        } else {
            body.put("messages",chat);body.put("max_tokens",rewriting?12000:layout?8192:4096);
            if(tool!=null){JSONObject t=new JSONObject();t.put("type","function");t.put("function",tool);body.put("tools",new JSONArray().put(t));body.put("tool_choice",(layout||profile.optString("id").equals("deepseek"))&&effort.equals("off")?new JSONObject().put("type","function").put("function",new JSONObject().put("name",tool.getString("name"))):"auto");}
            if(ModelEndpoint.bailian(url)){body.put("enable_thinking",!effort.equals("off"));}
            else if(profile.optString("id").equals("glm")){JSONObject thinking=new JSONObject();thinking.put("type",effort.equals("off")?"disabled":"enabled");body.put("thinking",thinking);}
            else if(profile.optString("id").equals("deepseek")){body.put("thinking",new JSONObject().put("type",effort.equals("off")?"disabled":"enabled"));if(!effort.equals("off"))body.put("reasoning_effort",effort);}
            else if(!effort.equals("off"))body.put("reasoning_effort",effort);
        }
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(20000);conn.setReadTimeout(120000);conn.setInstanceFollowRedirects(false);conn.setRequestMethod("POST");conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.setRequestProperty("Authorization","Bearer "+profile.getString("apiKey"));
        try {
            try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=conn.getResponseCode();if(code<200||code>=300){
                if(code==401||code==403)throw new Exception("模型服务拒绝认证（"+code+"），请检查 API Key 和模型权限");
                if(code==429)throw new Exception("模型服务限流或额度不足，请稍后重试并检查余额");
                if(code==404)throw new Exception("接口或模型不存在（404），请检查 API 地址、协议和模型名称");
                throw new Exception("模型请求失败（HTTP "+code+"）；请检查模型是否支持图片、输入长度与服务状态");
            }
            JSONObject response=new JSONObject(new String(readBytes(conn.getInputStream(),8*1024*1024),StandardCharsets.UTF_8));StringBuilder answer=new StringBuilder();
            JSONArray choicesForStatus=response.optJSONArray("choices");if("incomplete".equals(response.optString("status"))||choicesForStatus!=null&&choicesForStatus.length()>0&&"length".equals(choicesForStatus.getJSONObject(0).optString("finish_reason")))throw new Exception("模型输出达到上限，结果不完整，请重试");
            if(protocol.equals("responses")){JSONArray outputs=response.optJSONArray("output");if(outputs!=null)for(int i=0;i<outputs.length();i++){JSONObject output=outputs.getJSONObject(i);if("function_call".equals(output.optString("type"))&&(tool==null?"prepare_life_records":tool.optString("name")).equals(output.optString("name"))){answer.setLength(0);answer.append(output.getString("arguments"));break;}JSONArray parts=output.optJSONArray("content");if(parts!=null)for(int j=0;j<parts.length();j++)if("output_text".equals(parts.getJSONObject(j).optString("type")))answer.append(parts.getJSONObject(j).optString("text"));}}
            else{JSONArray choices=response.optJSONArray("choices");if(choices!=null&&choices.length()>0){JSONObject message=choices.getJSONObject(0).getJSONObject("message");JSONArray calls=message.optJSONArray("tool_calls");if(calls!=null){for(int i=0;i<calls.length();i++){JSONObject function=calls.getJSONObject(i).getJSONObject("function");if((tool==null?"prepare_life_records":tool.optString("name")).equals(function.optString("name"))){answer.append(function.getString("arguments"));break;}}}if(answer.length()==0)answer.append(message.optString("content", ""));}}
            if(answer.toString().trim().isEmpty()||"null".equals(answer.toString().trim()))throw new Exception("模型未返回文本，请稍后重试或检查模型配置");
            JSONObject result=new JSONObject();result.put("text",answer.toString());result.put("model",profile.getString("model"));if(response.has("usage"))result.put("usage",response.get("usage"));return result;
        }finally{conn.disconnect();}
    }
    private static byte[] readBytes(InputStream input,int max)throws Exception{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new Exception("文件超过大小限制");out.write(buffer,0,n);}return out.toByteArray();}}
}
