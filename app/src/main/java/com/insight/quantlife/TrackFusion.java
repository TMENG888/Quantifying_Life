package com.insight.quantlife;
import java.util.*;
import com.insight.quantlife.TrackFilter.Point;

/** Offline stop/move/unknown classifier. Raw observations are never mutated.
 * Acceleration is evidence, not displacement. No inertial or road reconstruction. */
public final class TrackFusion {
    public static final String ID="motion65",LABEL="停留与移动识别",COLOR="#347b64";
    public static final int REVISION=10;
    private static final class State {Point anchor,lastObservation;}
    private static double wrap(double x){return ((x+540)%360)-180;}
    private static boolean fresh(Point p){return p.motionAgeMs>=0&&p.motionAgeMs<=3000&&Double.isFinite(p.motionRms);}
    private static boolean travel(Point p){return p.reliableSpeed()&&p.speed>=.6;}
    private static double median(List<Double> a){Collections.sort(a);return a.get(a.size()/2);}
    private static Point center(List<Point> a,int lo,int hi,Point reference){
        List<Double> lat=new ArrayList<>(),lon=new ArrayList<>();
        for(int i=lo;i<=hi;i++){lat.add(a.get(i).lat);lon.add(wrap(a.get(i).lon-reference.lon));}
        Point p=reference.copy();p.lat=median(lat);p.lon=wrap(reference.lon+median(lon));return p;
    }
    /** Short, locally coherent arcs can turn back inside a long dwell window.
     * Requires actual recent sensor observations, not future-only activity. */
    private static boolean sensorArc(List<Point> raw,List<Point> smooth,int i){
        Point p=smooth.get(i);if(p.accuracy>25)return false;
        int lo=i,hi=i,recent=0;while(lo>0&&p.time-smooth.get(lo-1).time<=12000)lo--;
        while(hi+1<smooth.size()&&smooth.get(hi+1).time-p.time<=12000)hi++;
        if(hi-lo<5)return false;
        for(int k=lo;k<=i;k++){Point r=raw.get(k);if(fresh(r)&&r.motionRms>=.15)recent++;}if(recent<2)return false;
        int third=Math.max(1,(hi-lo+1)/3),ai=lo+third/2,bi=hi-third/2;
        Point a=center(smooth,lo,lo+third-1,smooth.get(ai)),b=center(smooth,hi-third+1,hi,smooth.get(bi));
        double dt=(b.time-a.time)/1000.0,net=TrackFilter.distance(a,b),path=0,max=0;Point step=a;
        for(int k=ai;k<=bi;k++)if(smooth.get(k).time-step.time>=4000){Point q=smooth.get(k);double d=TrackFilter.distance(step,q);path+=d;max=Math.max(max,d);step=q;}
        double tail=TrackFilter.distance(step,b);path+=tail;max=Math.max(max,tail);
        return dt>=8&&net>=Math.max(10,1.25*(a.accuracy+b.accuracy)/2)&&net/dt>=.4&&net/Math.max(.01,path)>=.75&&max<=.7*path;
    }
    public static List<Point> apply(List<Point> raw){
        List<Point> good=new ArrayList<>(),out=new ArrayList<>();List<Long> gpsTimes=new ArrayList<>();
        for(Point p:raw)if(p!=null&&!p.mocked&&TrackFilter.valid(p,65,2))gpsTimes.add(p.time);
        Collections.sort(gpsTimes);
        for(Point p:raw){if(p==null||!p.eligible||p.mocked)continue;
            boolean gps="gps".equals(p.provider),fallback="fused".equals(p.provider)&&p.gpsAgeMs>=15000&&p.accuracy<=35;
            // Replay can see both sides of an outage. Do not alternate GPS and correlated
            // system-fused callbacks just because the previous GPS was temporarily poor.
            if(fallback){int k=Collections.binarySearch(gpsTimes,p.time);if(k<0)k=-k-1;
                if(k<gpsTimes.size()&&gpsTimes.get(k)-p.time<=15000||k>0&&p.time-gpsTimes.get(k-1)<=15000)fallback=false;}
            if(!gps&&!fallback)continue;Point q=p.copy();q.provider="gps";
            if(!TrackFilter.valid(q,65,2))continue;
            q.provider=p.provider;q.sourceProvider=p.provider;q.reportedAccuracy=p.accuracy;q.reportedSpeed=p.speed;
            if(fallback)q.speedAccuracy=Double.NaN;good.add(q);
        }
        good.sort(Comparator.comparingLong(p->p.time));List<Point> run=new ArrayList<>();Point last=null;State state=new State();
        for(int index=0;index<good.size();index++){Point p=good.get(index);if(last!=null&&p.time<=last.time)continue;
            Point next=index+1<good.size()?good.get(index+1):null;
            if(last!=null&&next!=null&&last.session.equals(p.session)&&p.session.equals(next.session)&&last.provider.equals(p.provider)&&p.provider.equals(next.provider)&&p.time-last.time<=TrackFilter.GAP&&next.time>p.time&&next.time-p.time<=TrackFilter.GAP){
                double d=TrackFilter.distance(last,p),on=TrackFilter.distance(p,next),direct=TrackFilter.distance(last,next);
                double v=p.reliableSpeed()?p.speed:Double.NaN;
                double before=Double.isFinite(v)?v*(p.time-last.time)/1000.0+Math.max(15,2*p.accuracy):Math.max(40,2*(last.accuracy+p.accuracy));
                double after=Double.isFinite(v)?v*(next.time-p.time)/1000.0+Math.max(15,2*p.accuracy):Math.max(40,2*(next.accuracy+p.accuracy));
                if(d>before&&on>after&&direct<.3*Math.min(d,on))continue;
            }
            // Rejected callbacks do not manufacture gaps.
            if(last!=null&&(!last.session.equals(p.session)||p.time-last.time>TrackFilter.GAP)){classify(run,out,state);run.clear();}
            if(last!=null&&!run.isEmpty()&&TrackFilter.distance(last,p)/((p.time-last.time)/1000.0)>100)continue;
            run.add(p);last=p;
        }
        classify(run,out,state);confirmMovement(out);return out;
    }
    /** One-frame geometry/sensor positives are not a confirmed departure. Keep
     * their times as uncertain evidence; never discard a genuine measured tail. */
    private static void confirmMovement(List<Point> out){
        Set<Point> withdrawn=Collections.newSetFromMap(new IdentityHashMap<Point,Boolean>());
        for(int start=0;start<out.size();){Point first=out.get(start);if(!first.motionState.equals("moving")){start++;continue;}int end=start,speeds=travel(first)?1:0;
            while(end+1<out.size()){Point a=out.get(end),b=out.get(end+1);if(!b.motionState.equals("moving")||!a.session.equals(b.session)||!a.provider.equals(b.provider)||b.breakBefore||b.time-a.time>TrackFilter.GAP)break;end++;if(travel(b))speeds++;}
            if(speeds<2&&(end-start<2||out.get(end).time-first.time<4000)){for(int k=start;k<=end;k++){Point q=out.get(k);q.motionState="uncertain";q.breakBefore=true;withdrawn.add(q);}if(end+1<out.size())out.get(end+1).breakBefore=true;}
            start=end+1;
        }
        Point anchor=null,previous=null;
        for(Point q:out){if(previous!=null&&(!previous.session.equals(q.session)||q.time-previous.time>TrackFilter.GAP))anchor=null;
            if(q.motionState.equals("moving"))anchor=null;
            // Classification owns the stop estimate. Do not snap arbitrary unknown
            // fixes back and forth according to each fix's changing error radius.
            // Only roll back a withdrawn departure to its still-confirmed stop.
            else if(withdrawn.contains(q)&&anchor!=null){q.filtered|=TrackFilter.distance(anchor,q)>.01;q.lat=anchor.lat;q.lon=anchor.lon;q.speed=0;}
            else if(q.motionState.equals("stationary_estimate"))anchor=q.copy();
            if(q.motionState.equals("uncertain")||previous!=null&&previous.motionState.equals("uncertain"))q.breakBefore=true;
            previous=q;
        }
    }
    private static void classify(List<Point> raw,List<Point> out,State state){
        if(raw.isEmpty())return;int n=raw.size();List<Point> smooth=new ArrayList<>();int lo=0,hi=0;
        for(int i=0;i<n;i++){Point p=raw.get(i);while(lo<i&&p.time-raw.get(lo).time>5000)lo++;while(hi+1<n&&raw.get(hi+1).time-p.time<=5000)hi++;
            // Doppler-confirmed travel retains corners; otherwise a bounded median rejects spikes.
            smooth.add(travel(p)||i==0||i==n-1?p.copy():center(raw,lo,hi,p));
        }
        // Legacy/weak reception can deliver a point every 20-50s. A fixed +/-30s
        // window then sees only 1-2 fixes and used to discard virtually the whole route.
        List<Double> intervals=new ArrayList<>();for(int i=1;i<n;i++)intervals.add((double)(raw.get(i).time-raw.get(i-1).time));
        long window=intervals.isEmpty()?30000:(long)Math.max(30000,Math.min(90000,2.5*median(intervals)));
        Point anchor=state.anchor,previous=null;boolean wasMoving=false;lo=0;hi=0;int dwellLo=0,dwellHi=0;
        Point observationBefore=state.lastObservation,firstObservation=raw.get(0);
        // A timeout alone is not displacement evidence. If reception resumes in
        // the same remote noise cloud, retain the existing uncertain stop instead
        // of drawing a journey that occurred only in our anchor bookkeeping.
        boolean reacquire=observationBefore!=null&&firstObservation.time-observationBefore.time>300000
            &&TrackFilter.distance(observationBefore,firstObservation)>Math.max(35,2*(observationBefore.accuracy+firstObservation.accuracy));
        long departureStart=0;int departureCount=0,departureSpeeds=0;
        for(int i=0;i<n;i++){Point p=smooth.get(i);while(lo<i&&p.time-smooth.get(lo).time>window)lo++;while(hi+1<n&&smooth.get(hi+1).time-p.time<=window)hi++;
            int count=hi-lo+1,third=Math.max(1,count/3),active=0,available=0,quiet=0,moving=0;Point motionReference=null;
            for(int k=lo;k<=hi;k++){Point r=raw.get(k);if(fresh(r)){available++;if(r.motionRms>=.15){active++;if(r.time<=p.time&&p.time-r.time<=30000)motionReference=r;}}if(r.reliableSpeed()&&r.speed<=.25)quiet++;if(travel(r))moving++;}
            int ai=lo+third/2,bi=hi-third/2;
            Point a=center(smooth,lo,Math.min(hi,lo+third-1),smooth.get(ai)),b=center(smooth,Math.max(lo,hi-third+1),hi,smooth.get(bi));
            double dt=(b.time-a.time)/1000.0,net=TrackFilter.distance(a,b),path=0,maxStep=0;Point step=a;
            // Temporal hops, not per-callback jitter, determine directional consistency.
            for(int k=ai;k<=bi;k++){Point q=smooth.get(k);if(q.time-step.time>=8000){double d=TrackFilter.distance(step,q);path+=d;maxStep=Math.max(maxStep,d);step=q;}}
            double finalStep=TrackFilter.distance(step,b);path+=finalStep;maxStep=Math.max(maxStep,finalStep);double coherence=net/Math.max(.01,path),acc=(a.accuracy+b.accuracy)/2;
            boolean supported=count>=3&&dt>=10;
            boolean distributed=maxStep<=.7*path;
            boolean sensorTrend=active>=Math.max(2,(available+1)/2)&&net/Math.max(1,dt)>=.3&&distributed
                &&(net>=Math.max(10,1.25*acc)&&coherence>=.65||net>=Math.max(10,.45*acc)&&coherence>=.9);
            boolean sensorMove=motionReference!=null&&(sensorTrend||sensorArc(raw,smooth,i));
            boolean geometryMove=acc<=25&&net>=Math.max(12,(acc<=12?2:3)*acc)&&net/Math.max(1,dt)>=.45&&coherence>=.72&&distributed;
            Point speedReference=null;for(int k=lo;k<=hi;k++)if(travel(raw.get(k))&&(speedReference==null||Math.abs(raw.get(k).time-p.time)<Math.abs(speedReference.time-p.time)))speedReference=raw.get(k);
            boolean speedSupported=moving>=2&&speedReference!=null&&TrackFilter.distance(p,speedReference)<=speedReference.speed*Math.abs(p.time-speedReference.time)/1000.0+Math.max(15,2*(p.accuracy+speedReference.accuracy));
            boolean isMoving=speedSupported||supported&&(sensorMove||geometryMove);
            if(quiet>count/2&&active<count/4&&moving<2)isMoving=false;
            List<Double> radii=new ArrayList<>();Point c=center(smooth,lo,hi,p);
            for(int k=lo;k<=hi;k++)radii.add(TrackFilter.distance(c,smooth.get(k)));Collections.sort(radii);
            boolean cluster=supported&&dt>=25&&net<=Math.max(10,.8*acc)&&radii.get((radii.size()-1)*4/5)<=Math.max(12,1.25*acc);
            boolean stop=!isMoving&&(cluster||supported&&quiet>=count/2&&active<count/4);
            // A broader robust dwell window stabilizes a stationary cloud even when
            // short windows wander. It cannot veto measured travel or a coherent walk.
            while(dwellLo<i&&p.time-smooth.get(dwellLo).time>150000)dwellLo++;
            while(dwellHi+1<n&&smooth.get(dwellHi+1).time-p.time<=150000)dwellHi++;
            Point dwell=center(smooth,dwellLo,dwellHi,p);int travelCount=0;List<Double> spread=new ArrayList<>(),errors=new ArrayList<>();
            for(int k=dwellLo;k<=dwellHi;k++){Point r=raw.get(k);if(travel(r))travelCount++;spread.add(TrackFilter.distance(dwell,smooth.get(k)));errors.add(r.accuracy);}
            Collections.sort(spread);double dwellError=median(errors);int dwellCount=dwellHi-dwellLo+1;
            boolean dwelling=dwellCount>=6&&smooth.get(dwellHi).time-smooth.get(dwellLo).time>=90000
                &&travelCount<Math.max(2,dwellCount/8)&&spread.get((spread.size()-1)*4/5)<=Math.max(18,1.6*dwellError);
            // Leaving an established dwell needs independent movement evidence or
            // sustained geometry, not a few reflected fixes sliding to another cluster.
            if(anchor!=null&&departureCount==0&&moving<2&&!sensorMove){
                int thirdLong=Math.max(1,dwellCount/3);
                Point da=center(smooth,dwellLo,dwellLo+thirdLong-1,smooth.get(dwellLo+thirdLong/2));
                Point db=center(smooth,dwellHi-thirdLong+1,dwellHi,smooth.get(dwellHi-thirdLong/2));
                double longDt=(db.time-da.time)/1000.0,longNet=TrackFilter.distance(da,db),longPath=0,maxHop=0;Point hop=da;
                for(int k=dwellLo+thirdLong/2;k<=dwellHi-thirdLong/2;k++){Point r=smooth.get(k);if(r.time-hop.time>=10000){double d=TrackFilter.distance(hop,r);longPath+=d;maxHop=Math.max(maxHop,d);hop=r;}}
                double tail=TrackFilter.distance(hop,db);longPath+=tail;maxHop=Math.max(maxHop,tail);
                boolean sustained=longDt>=60&&longNet>=Math.max(35,3*dwellError)&&longNet/Math.max(1,longDt)>=.45&&longNet/Math.max(.01,longPath)>=.75&&maxHop<=longPath*.45;
                if(!sustained)isMoving=false;
                else if(!isMoving){anchor=null;} // Coarse but sustained relocation: dashed, never pinned to old stop.
            }
            if(!isMoving&&dwelling){stop=true;c=dwell;c.accuracy=dwellError;}
            // Keep genuinely ambiguous observations as dashed-only evidence, not
            // isolated accepted islands or invented stationary positions.
            Point q=p.copy();q.motionState=isMoving?"moving":stop?"stationary_estimate":"uncertain";
            if(isMoving){
                if(departureCount==0||previous!=null&&!previous.provider.equals(q.provider)){departureStart=q.time;departureCount=0;departureSpeeds=0;}
                departureCount++;if(travel(q))departureSpeeds++;
            }else{departureCount=0;departureSpeeds=0;}
            boolean confirmedDeparture=isMoving&&(departureSpeeds>=2||departureCount>=3&&q.time-departureStart>=4000);
            if(stop){
                boolean relocated=anchor!=null&&TrackFilter.distance(anchor,c)>Math.max(35,2*(anchor.accuracy+c.accuracy));
                // A remote cloud reached without a confirmed departure is not a
                // second measured stop. Hold the last estimate, flagged uncertain.
                boolean hold=relocated&&!wasMoving&&!reacquire;
                if(hold){stop=false;q.motionState="uncertain";}
                if(anchor==null||wasMoving||relocated&&!hold){anchor=c.copy();
                    if(!out.isEmpty()){Point old=out.get(out.size()-1);if(old.motionState.equals("stationary_estimate")&&TrackFilter.distance(old,anchor)<=Math.max(25,2*(old.accuracy+anchor.accuracy))){anchor.lat=old.lat;anchor.lon=old.lon;}}
                }q.lat=anchor.lat;q.lon=anchor.lon;q.speed=0;
            }else if(isMoving){if(confirmedDeparture)anchor=null;}
            else if(anchor!=null&&TrackFilter.distance(anchor,p)<=Math.max(25,anchor.accuracy+p.accuracy)){
                // Existing dwell + overlapping uncertainty: retain its estimate;
                // absence of a sensor alone never establishes a new stop.
                q.lat=anchor.lat;q.lon=anchor.lon;q.speed=0;q.motionState="stationary_estimate";stop=true;
            }
            else if(anchor!=null&&!reacquire){q.lat=anchor.lat;q.lon=anchor.lon;q.speed=0;}
            q.filtered=TrackFilter.distance(q,raw.get(i))>.01;
            if(previous==null||q.time-previous.time>TrackFilter.GAP)q.breakBefore=!out.isEmpty();
            else if(!previous.motionState.equals("moving")&&isMoving&&TrackFilter.distance(previous,q)>Math.max(15,2*acc))q.breakBefore=true;
            else q.breakBefore=false;
            if(q.motionState.equals("uncertain")||previous!=null&&previous.motionState.equals("uncertain")||previous!=null&&!previous.provider.equals(q.provider))q.breakBefore=true;
            if(stop&&previous!=null&&TrackFilter.distance(previous,q)>.01)q.breakBefore=true;
            out.add(q);previous=q;wasMoving=confirmedDeparture;
            if(stop||isMoving)reacquire=false;
        }
        state.anchor=anchor;state.lastObservation=raw.get(n-1).copy();
    }
    private TrackFusion(){}
}
