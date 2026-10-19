package com.insight.quantlife.tests;
import com.insight.quantlife.*;import com.insight.quantlife.TrackFilter.Point;import java.util.*;
public final class TrackFusionRegression {
 static int passed;static void check(boolean b,String s){if(!b)throw new AssertionError(s);passed++;System.out.println("PASS "+s);}
 static Point p(int seconds,double meters,double speed){Point p=TrackFilterRegression.p(seconds,meters,speed);p.motionRms=.03;p.motionAgeMs=500;return p;}
 static List<Point> line(int n,double step,double speed){List<Point> a=new ArrayList<>();for(int i=0;i<n;i++)a.add(p(i*2,i*step,speed));return a;}
 public static void main(String[] args){
  check(TrackFusion.apply(Collections.emptyList()).isEmpty(),"Empty source safe");
  List<Point> raw=line(40,0,0);for(int i=0;i<raw.size();i++)raw.get(i).lat+=(i%2==0?1:-1)*3/111195.0;
  String before=TrackVariantsRegression.fingerprint(raw);List<Point> out=TrackFusion.apply(raw);
  check(before.equals(TrackVariantsRegression.fingerprint(raw)),"No raw coordinate/time/flag mutation");
  check(out.size()==raw.size(),"Stationary samples retained with original timestamps");
  check(TrackFilter.distance(out.get(20),out.get(39))<.01,"Sustained quiet motion and Doppler evidence locks stationary drift");
  check(TrackVariants.meters(out)<TrackVariants.meters(TrackVariants.apply(raw)),"Stationary reference distance reduced in synthetic fixture");
  List<Point> walking=line(80,2.8,1.4);List<Point> walk=TrackFusion.apply(walking);check(walk.size()==80,"Slow walking samples preserved");check(Math.abs(TrackVariants.meters(walk)-221.2)<1,"Walking distance not suppressed");
  List<Point> car=line(40,40,20);List<Point> travel=TrackFusion.apply(car);check(TrackVariants.meters(travel)>1558,"Low acceleration at steady vehicle speed does not imply stationary");
  List<Point> bike=line(40,10,5);check(TrackVariants.meters(TrackFusion.apply(bike))>389,"Cycling mileage preserved");
  List<Point> active=line(40,2.5,0);for(Point q:active)q.motionRms=.5;check(TrackVariants.meters(TrackFusion.apply(active))>90,"Motion evidence releases zero-speed lock");
  List<Point> unknown=line(40,2,0);for(Point q:unknown){q.motionRms=Double.NaN;q.motionAgeMs=-1;q.speed=Double.NaN;q.speedAccuracy=Double.NaN;}check(TrackVariants.meters(TrackFusion.apply(unknown))>75,"Missing sensor does not pin motion: meters="+TrackVariants.meters(TrackFusion.apply(unknown))+" points="+TrackFusion.apply(unknown).size());
  for(Point q:unknown){q.motionRms=.01;q.motionAgeMs=5000;}check(TrackVariants.meters(TrackFusion.apply(unknown))>75,"Expired sensor evidence ignored");
  List<Point> gap=Arrays.asList(p(0,0,1),p(2,2,1),p(100,100,1),p(102,102,1));check(TrackVariants.gaps(TrackFusion.apply(gap))==1,"No fabricated points across GPS gap");check(TrackVariants.meters(TrackFusion.apply(gap))<5,"Gap excluded from mileage");
  Point bad=p(4,10,1);bad.eligible=false;out=TrackFusion.apply(Arrays.asList(p(0,0,1),bad));check(out.size()==1&&out.get(0).motionState.equals("uncertain")&&out.get(0).breakBefore&&TrackVariants.meters(out)==0,"Isolated valid fix remains a dashed-only reference; invalid callback excluded");
  Point fused=p(20,20,1);fused.provider="fused";fused.gpsAgeMs=16000;
  List<Point> fallback=line(30,2,Double.NaN);for(Point q:fallback){q.provider="fused";q.gpsAgeMs=20000;q.motionRms=.3;}fallback.add(0,p(-2,-2,1));fallback.add(0,p(-4,-4,1));out=TrackFusion.apply(fallback);check(out.stream().anyMatch(q->q.provider.equals("fused")&&q.breakBefore),"Confirmed precise system fallback source preserved and not joined as GPS mileage");
  fused.gpsAgeMs=3000;check(TrackFusion.apply(Arrays.asList(fused)).isEmpty(),"System fused does not compete with fresh GPS");fused.gpsAgeMs=20000;fused.accuracy=36;check(TrackFusion.apply(Arrays.asList(fused)).isEmpty(),"Coarse fused backup not used");
  Point network=p(2,2,1);network.provider="network";check(TrackFusion.apply(Arrays.asList(network)).isEmpty(),"Cell/network locations excluded");
  List<Point> corner=Arrays.asList(p(0,0,1),p(2,3,1),p(4,3,1));corner.get(2).lon+=3/111195.0;out=TrackFusion.apply(corner);check(TrackFilter.distance(out.get(1),corner.get(1))<.2,"Turn retained within conservative smoothing bound");
  List<Point> optimistic=line(60,2.8,1.4);optimistic.get(25).lon+=80/111195.0;
  check(TrackVariants.meters(TrackFusion.apply(optimistic))<170,"A nonzero-speed single out-and-back GPS spike does not inflate mileage");
  List<Point> drift=line(1800,0,Double.NaN);for(int i=0;i<drift.size();i++){Point q=drift.get(i);q.motionRms=Double.NaN;q.motionAgeMs=-1;q.accuracy=60;q.lat+=Math.sin(i*.8)*40/111195.0;q.lon+=Math.cos(i*.7)*30/111195.0;}
  before=TrackVariantsRegression.fingerprint(drift);out=TrackFusion.apply(drift);
  check(TrackVariants.meters(out)<.01,"One hour of coarse stationary jitter without sensors adds no mileage");
  check(!out.isEmpty()&&out.stream().allMatch(q->q.motionState.equals("stationary_estimate")),"Geometry-only stops are explicitly estimates, not sensor certainty");
  check(before.equals(TrackVariantsRegression.fingerprint(drift)),"Stop classification preserves every raw coordinate");
  List<Point> flaky=new ArrayList<>();for(int i=0;i<240;i++){Point q=p(i*2,Math.sin(i)*5,Double.NaN);q.motionAgeMs=-1;q.accuracy=i%10<2?90:20;flaky.add(q);}
  out=TrackFusion.apply(flaky);check(TrackVariants.gaps(out)==0,"Two poor callbacks do not split a stable stop into repeated gaps");
  List<Point> small=line(30,.9,Double.NaN);for(Point q:small)q.motionRms=.4;
  check(TrackVariants.meters(TrackFusion.apply(small))>20,"A sensor-supported short walk is not removed as coarse GPS jitter");
  List<Point> coarse=line(50,2,Double.NaN);for(Point q:coarse){q.accuracy=60;q.motionRms=Double.NaN;q.motionAgeMs=-1;}
  out=TrackFusion.apply(coarse);check(out.isEmpty()||TrackVariants.meters(out)<1,"Coarse ambiguous displacement is not claimed as confirmed movement");
  List<Point> stopWalk=new ArrayList<>();for(int i=0;i<180;i++){double m=i<60?0:i<120?(i-60)*2:120;Point q=p(i*2,m,i>=60&&i<120?1:0);q.motionRms=i>=60&&i<120?.4:.02;stopWalk.add(q);}
  out=TrackFusion.apply(stopWalk);check(TrackVariants.meters(out)>110&&TrackVariants.meters(out)<130,"Stop-walk-stop retains a short walk without idle mileage");
  List<Point> differentStops=new ArrayList<>();for(int i=0;i<180;i++){Point q=p(i*2,i<60?0:i<120?(i-60)*5:300,Double.NaN);q.accuracy=60;q.motionRms=Double.NaN;q.motionAgeMs=-1;differentStops.add(q);}
  out=TrackFusion.apply(differentStops);check(!out.isEmpty()&&TrackFilter.distance(out.get(0),out.get(out.size()-1))>250,"Uncertain relocation does not pin a later confirmed stop to yesterday's anchor");
  check(TrackVariants.meters(out)<1&&TrackVariants.gaps(out)>0,"Unknown relocation is a gap, not fabricated solid mileage");
  List<Point> allDay=line(43201,2.8,1.4);allDay.get(22000).session="restart";String fingerprint=TrackVariantsRegression.fingerprint(allDay);List<Point> complete=TrackFusion.apply(allDay),shown=TrackDisplay.simplify(complete);
  check(complete.size()==43201&&complete.get(complete.size()-1).time==allDay.get(allDay.size()-1).time,"24 hours at 2-second interval keeps last point and uncertain restart fix");check(shown.size()<20000&&shown.get(shown.size()-1).time==complete.get(complete.size()-1).time,"Display reduction preserves end of day");check(TrackVariants.gaps(shown)==TrackVariants.gaps(complete),"Display reduction creates no new missing segments");
  check(fingerprint.equals(TrackVariantsRegression.fingerprint(allDay)),"Full-day reduction does not mutate original source");
  List<Point> sparse=new ArrayList<>();for(int i=0;i<20;i++){Point q=p(i*31,i*40,Double.NaN);q.motionRms=Double.NaN;q.motionAgeMs=-1;sparse.add(q);}
  out=TrackFusion.apply(sparse);check(out.size()==20&&TrackVariants.meters(out)>750,"Adaptive window preserves sparse 31-second walking history instead of dropping it");
  List<Point> clouds=new ArrayList<>();for(int i=0;i<900;i++){Point q=p(i*2,i<180?Math.sin(i)*4:240+Math.sin(i)*4,Double.NaN);q.accuracy=25;q.motionRms=Double.NaN;q.motionAgeMs=300000;clouds.add(q);}
  before=TrackVariantsRegression.fingerprint(clouds);out=TrackFusion.apply(clouds);double extent=0;for(Point q:out)extent=Math.max(extent,TrackFilter.distance(out.get(0),q));
  check(extent<2&&TrackVariants.meters(out)<.01,"Stationary cloud teleport without departure evidence cannot relocate the drawn anchor");
  check(out.stream().anyMatch(q->q.motionState.equals("uncertain")),"Held remote cloud is explicitly uncertain, not measured current position");
  check(TrackVariants.gaps(out)<=2,"A continuous uncertain interval is one diagnostic gap, not hundreds of isolated badges");
  check(before.equals(TrackVariantsRegression.fingerprint(clouds)),"Remote-cloud holding never rewrites raw evidence");
  List<Point> sparseUnknown=Arrays.asList(p(0,0,Double.NaN),p(25,20,Double.NaN));out=TrackFusion.apply(sparseUnknown);
  check(out.size()==2&&TrackVariants.meters(out)==0&&out.stream().allMatch(q->q.motionState.equals("uncertain")),"Two ambiguous fixes stay visible as dashed-only evidence and add no mileage");
  List<Point> reacquired=new ArrayList<>();for(int i=0;i<80;i++)reacquired.add(p(i*2,0,0));for(int i=0;i<80;i++)reacquired.add(p(1000+i*2,1000,0));out=TrackFusion.apply(reacquired);
  check(TrackFilter.distance(out.get(0),out.get(out.size()-1))>990&&TrackVariants.meters(out)<1,"A true long outage permits a new stable destination, never a solid fabricated journey");
  List<Point> recovery=new ArrayList<>(clouds);for(int i=0;i<80;i++){Point q=p(1800+i*2,i*2.8,1.4);q.motionRms=.4;recovery.add(q);}out=TrackFusion.apply(recovery);
  check(TrackVariants.meters(out)>200&&TrackVariants.meters(out)<230&&TrackFilter.distance(out.get(out.size()-1),recovery.get(recovery.size()-1))<1,"Independent walking evidence releases a held stop without counting reflected-cloud return as travel");
  for(int i=900;i<recovery.size();i++)recovery.get(i).accuracy=25;out=TrackFusion.apply(recovery);extent=0;for(Point q:out)if(q.time<recovery.get(900).time)extent=Math.max(extent,TrackFilter.distance(out.get(0),q));
  check(extent<2&&TrackVariants.meters(out)>200&&TrackVariants.meters(out)<230,"Future Doppler evidence must be position-consistent before it can release a coarse stationary cloud");
  List<Point> burstWalk=line(100,2.8,Double.NaN);for(int i=0;i<burstWalk.size();i++){Point q=burstWalk.get(i);q.accuracy=35;q.motionRms=i%15<2?.5:Double.NaN;q.motionAgeMs=i%15<2?500:10000;}
  out=TrackFusion.apply(burstWalk);check(TrackVariants.meters(out)>240,"Intermittent genuine motion bursts plus coherent displacement preserve walking without Doppler");
  List<Point> shortLoop=new ArrayList<>();for(int t=0;t<360;t+=2){double angle=Math.max(0,Math.min(60,t-120))*2*Math.PI/60;Point q=p(t,0,Double.NaN);q.lon+=10*Math.sin(angle)/(111195*Math.cos(Math.toRadians(q.lat)));q.lat+=10*(1-Math.cos(angle))/111195;q.motionRms=t>=120&&t<=180?.5:.01;shortLoop.add(q);}
  out=TrackFusion.apply(shortLoop);check(TrackVariants.meters(out)>55&&TrackVariants.meters(out)<70,"Sensor-supported 63m short loop survives the long dwell window without Doppler metadata");
  check(out.get(out.size()-1).time==shortLoop.get(shortLoop.size()-1).time,"Short loop and return stop keep their final timestamp");
  List<Point> activeDesk=new ArrayList<>();Random rng=new Random(1017);for(int t=0;t<7200;t+=2){Point q=p(t,rng.nextGaussian()*5,Double.NaN);q.lon+=rng.nextGaussian()*5/(111195*Math.cos(Math.toRadians(q.lat)));q.accuracy=12;q.motionRms=.6;activeDesk.add(q);}
  check(TrackVariants.meters(TrackFusion.apply(activeDesk))<1,"Continuous phone handling plus stationary noisy GPS is not a walk (meters="+TrackVariants.meters(TrackFusion.apply(activeDesk))+")");
  List<Point> resumedCloud=new ArrayList<>();for(int i=0;i<210;i++)resumedCloud.add(p(i*2,i<90?0:200,0));for(int i=0;i<90;i++)resumedCloud.add(p(788+i*2,200,0));
  before=TrackVariantsRegression.fingerprint(resumedCloud);out=TrackFusion.apply(resumedCloud);extent=0;for(Point q:out)extent=Math.max(extent,TrackFilter.distance(out.get(0),q));
  check(extent<1&&TrackVariants.meters(out)<.01,"A long outage within the same unconfirmed remote cloud cannot reset the anchor and invent a 200m journey");
  check(before.equals(TrackVariantsRegression.fingerprint(resumedCloud)),"Outage continuity correction leaves raw observations intact");
  try{
   java.lang.reflect.Method confirm=TrackFusion.class.getDeclaredMethod("confirmMovement",List.class);confirm.setAccessible(true);
   List<Point> candidate=new ArrayList<>();Point home=p(0,0,0);home.motionState="stationary_estimate";candidate.add(home);Point one=p(2,60,Double.NaN);one.motionState="moving";candidate.add(one);Point back=p(4,0,0);back.motionState="stationary_estimate";candidate.add(back);confirm.invoke(null,candidate);
   check(one.motionState.equals("uncertain")&&one.breakBefore&&TrackFilter.distance(home,one)<.01,"Withdrawn one-frame departure rolls back to the last confirmed stop regardless of its error radius");
   List<Point> unknowns=new ArrayList<>();home=p(0,0,0);home.motionState="stationary_estimate";unknowns.add(home);for(int i=1;i<8;i++){Point q=p(i*2,100,Double.NaN);q.accuracy=i%2==0?60:8;q.motionState="uncertain";unknowns.add(q);}confirm.invoke(null,unknowns);
   double wobble=0;for(int i=2;i<unknowns.size();i++)wobble+=TrackFilter.distance(unknowns.get(i-1),unknowns.get(i));
   check(wobble<.01,"Final confirmation never alternates an unknown sequence between old stop and raw cloud as reported accuracy changes");
  }catch(ReflectiveOperationException e){throw new AssertionError(e);}
  System.out.println("PASS: "+passed+" motion and full-day pipeline checks.");
 }
}
