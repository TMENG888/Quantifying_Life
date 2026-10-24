package com.insight.quantlife.tests;
import android.app.*;import android.os.*;import org.json.*;import java.io.*;import java.net.*;import java.util.concurrent.*;import com.insight.quantlife.*;
/** Shipping adapter with synthetic HTTPS only. Exercises disconnects without billable calls. */
public final class IllustrationRecoveryTest extends DocumentEditorTest {
 int posts,code=200;String mode="ok";volatile boolean disconnected;CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
 class Fake extends HttpURLConnection {
  Fake(URL u){super(u);}public void connect(){}public void disconnect(){disconnected=true;release.countDown();}public boolean usingProxy(){return false;}
  public OutputStream getOutputStream()throws IOException{if(mode.equals("offline"))throw new UnknownHostException("synthetic offline");if(mode.equals("stall")){entered.countDown();try{release.await(55,TimeUnit.SECONDS);}catch(InterruptedException ex){throw new IOException(ex);}throw new SocketTimeoutException("synthetic stalled upload");}return new ByteArrayOutputStream();}
  public int getResponseCode()throws IOException{posts++;if(mode.equals("timeout"))throw new SocketTimeoutException("synthetic reply lost");if(mode.equals("late-connect"))throw new ConnectException("synthetic failure after write");return code;}
  public InputStream getInputStream()throws IOException{return new ByteArrayInputStream((mode.equals("missing")?"{}":"{\"output\":{\"task_id\":\"synthetic-task\",\"task_status\":\"PENDING\"}}").getBytes("UTF-8"));}
 }
 JSONObject params(String id)throws Exception{return new JSONObject().put("id",id).put("recordId",id).put("prompt","合成测试图，不访问真实服务").put("size","1024*1024");}
 void cleanFixtures()throws Exception{android.content.SharedPreferences p=getTargetContext().getSharedPreferences("illustration-jobs",0);android.content.SharedPreferences.Editor edit=p.edit();for(java.util.Map.Entry<String,?> entry:p.getAll().entrySet())if(entry.getKey().startsWith("recovery-")&&entry.getValue() instanceof String&&new JSONObject((String)entry.getValue()).optString("prompt").equals("合成测试图，不访问真实服务"))edit.remove(entry.getKey());edit.commit();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  cleanFixtures();URL.setURLStreamHandlerFactory(p->p.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Fake(u);}}:null);
  JSONObject config=ImageGeneration.configure(null,new JSONObject().put("apiKey","synthetic-only"));String prefix="recovery-"+System.currentTimeMillis();
  for(String failure:new String[]{"offline","timeout","late-connect","missing","http401","http429","http400","http500","http408"}){
   mode=failure;code=failure.startsWith("http")?Integer.parseInt(failure.substring(4)):200;JSONObject p=params(prefix+failure),j=ImageGeneration.create(getTargetContext(),config,p);boolean definite=failure.equals("offline")||failure.matches("http(401|429|400)");
   check(j.getString("status").equals(definite?"FAILED":"UNCERTAIN"),"Correct certainty for "+failure);int sent=posts;
   ImageGeneration.create(getTargetContext(),config,p);check(posts==sent,"Same ID never re-POSTs after "+failure);
   if(!definite){boolean blocked=false;try{ImageGeneration.acknowledge(getTargetContext(),p.getString("id"));}catch(Exception ex){blocked=true;}check(blocked,"Uncertain job requires explicit confirmation: "+failure);JSONObject next=new JSONObject(p.toString()).put("id",p.getString("id")+"-next");try{ImageGeneration.create(getTargetContext(),config,next);throw new AssertionError("duplicate accepted");}catch(Exception ex){check(posts==sent,"Old uncertain task blocks duplicate before POST: "+failure);}ImageGeneration.acknowledge(getTargetContext(),p.getString("id"),true);}
   mode="ok";code=200;JSONObject retry=new JSONObject(p.toString()).put("id",p.getString("id")+"-retry");check(ImageGeneration.create(getTargetContext(),config,retry).optString("status").equals("PENDING"),"Manual retry works after resolved "+failure);
  }
  // Process-death legacy job: no worker survives; it must offer recovery immediately.
  JSONObject orphan=params(prefix+"-orphan").put("status","SUBMITTING").put("createdAt",System.currentTimeMillis());getTargetContext().getSharedPreferences("illustration-jobs",0).edit().putString(orphan.getString("id"),orphan.toString()).commit();
  JSONArray jobs=ImageGeneration.jobs(getTargetContext());boolean recovered=false;for(int i=0;i<jobs.length();i++)if(jobs.getJSONObject(i).optString("id").equals(orphan.getString("id")))recovered=jobs.getJSONObject(i).optString("status").equals("UNCERTAIN");check(recovered,"Orphan SUBMITTING becomes recoverable without waiting 90 seconds");
  // A blocked upload must end on the overall deadline, and cannot be released while live.
  mode="stall";disconnected=false;release=new CountDownLatch(1);JSONObject stalled=params(prefix+"-stall");final JSONObject[] answer={null};final Throwable[] error={null};long began=System.nanoTime();Thread worker=new Thread(()->{try{answer[0]=ImageGeneration.create(getTargetContext(),config,stalled);}catch(Throwable ex){error[0]=ex;}});worker.start();check(entered.await(5,TimeUnit.SECONDS),"Synthetic upload entered");ImageGeneration.jobs(getTargetContext());boolean protectedLive=false;try{ImageGeneration.acknowledge(getTargetContext(),stalled.getString("id"),true);}catch(Exception ex){protectedLive=true;}check(protectedLive,"Live upload cannot be released or mistaken for orphan");worker.join(52000);long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began);check(!worker.isAlive()&&error[0]==null&&disconnected&&answer[0].optString("status").equals("UNCERTAIN")&&elapsed>=44000&&elapsed<52000,"Overall deadline terminates stalled upload, preserves uncertainty");
  cleanFixtures();result.putString("stream","PASS IllustrationRecoveryTest: "+passed+" checks\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable ex){result.putString("stream","FAIL IllustrationRecoveryTest after "+passed+": "+ex+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
