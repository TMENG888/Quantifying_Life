package com.insight.quantlife;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.webkit.*;
import android.widget.*;
import android.view.ViewGroup;
import org.json.*;
import java.util.concurrent.*;

/** Isolated remote reader: deliberately has no JavaScript/native bridge. */
public final class FeishuReaderActivity extends Activity {
 private WebView browser;private TextView status;private Button read;private String source;private boolean busy;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 @Override public void onCreate(Bundle state){super.onCreate(state);
  try{source=FeishuImport.canonical(getIntent().getStringExtra("url"));}catch(Exception e){finish();return;}
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(248,248,242));
  float density=getResources().getDisplayMetrics().density;int height=Math.round(56*density),side=Math.round(16*density),gap=Math.round(8*density);
  LinearLayout bar=new LinearLayout(this);Button close=new Button(this);close.setText("返回");close.setOnClickListener(v->finish());read=new Button(this);read.setText("读取此文档");read.setOnClickListener(v->capture());bar.addView(close,new LinearLayout.LayoutParams(0,height,1));bar.addView(read,new LinearLayout.LayoutParams(0,height,2));root.addView(bar);
  status=new TextView(this);status.setPadding(side,gap,side,gap);status.setText("仅在飞书站点登录并阅读；加载完整后点击读取。登录信息不发送给模型。");root.addView(status);
  browser=new WebView(this);WebSettings s=browser.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setSupportMultipleWindows(false);
  browser.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){android.net.Uri u=r.getUrl();boolean allow="https".equals(u.getScheme())&&FeishuImport.host(u.getHost())&&(u.getPort()==-1||u.getPort()==443)&&u.getUserInfo()==null;if(!allow)status.setText("已阻止离开飞书站点。请在此完成登录，或返回重试公开文档读取。");return !allow;}});
  root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});root.addView(browser,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));setContentView(root);browser.loadUrl(source);
 }
 private void capture(){if(busy)return;try{if(!FeishuImport.canonical(browser.getUrl()).equals(source))throw new Exception("请返回原文档页面再读取；登录页不能导入");}catch(Exception e){status.setText(e.getMessage());return;}
  busy=true;read.setEnabled(false);status.setText("正在核对正文完整性并保存图片…");
  browser.evaluateJavascript("(()=>{try{return JSON.stringify(window.DATA&&window.DATA.clientVars||null)}catch(e){return null}})()",encoded->{if(isDestroyed()||worker.isShutdown())return;try{worker.execute(()->{try{
   Object raw=new JSONTokener(encoded).nextValue();if(!(raw instanceof String))throw new Exception("页面尚未提供完整正文，请等待加载后重试");JSONObject snapshot=FeishuImport.parse(new JSONObject((String)raw),source);FeishuImport.stage(this,snapshot,true);
   runOnUiThread(()->{if(!isDestroyed()){setResult(RESULT_OK);finish();}});
  }catch(Exception ex){runOnUiThread(()->{if(!isDestroyed()){status.setText("未导入："+ex.getMessage());busy=false;read.setEnabled(true);}});}});}catch(RejectedExecutionException ignored){}});
 }
 @Override public void onBackPressed(){if(busy){status.setText("正在读取，请稍候；返回不会改变已有记录。");return;}super.onBackPressed();}
 @Override protected void onDestroy(){if(browser!=null){browser.stopLoading();browser.destroy();}worker.shutdownNow();super.onDestroy();}
}
