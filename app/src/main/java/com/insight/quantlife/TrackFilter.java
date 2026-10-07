package com.insight.quantlife;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One deterministic filter for screen, distance and GPX. No road snapping or mode inference. */
public final class TrackFilter {
    public static final long GAP=60000;
    public static final double MAX_SPEED=100,MAX_ACCURACY=35;
    public static final class Point {
        public long id,time; public double lat,lon,accuracy,reportedAccuracy=Double.NaN,reportedSpeed=Double.NaN,speed=Double.NaN,speedAccuracy=Double.NaN;
        public String session="",provider="gps"; public boolean mocked,filtered,breakBefore;
        public int satellitesUsed=-1;public double meanCn0=Double.NaN;public long gnssAgeMs=-1;
        public Point copy(){Point p=new Point();p.id=id;p.time=time;p.lat=lat;p.lon=lon;p.accuracy=accuracy;p.reportedAccuracy=reportedAccuracy;p.reportedSpeed=reportedSpeed;p.speed=speed;p.speedAccuracy=speedAccuracy;p.session=session;p.provider=provider;p.mocked=mocked;p.filtered=filtered;p.breakBefore=breakBefore;p.satellitesUsed=satellitesUsed;p.meanCn0=meanCn0;p.gnssAgeMs=gnssAgeMs;return p;}
        public boolean reliableSpeed(){return "gps".equals(provider)&&Double.isFinite(speed)&&speed>=0&&speed<=MAX_SPEED&&Double.isFinite(speedAccuracy)&&speedAccuracy>=0&&speedAccuracy<=0.6;}
    }
    public static boolean valid(Point p){return p!=null&&Double.isFinite(p.lat)&&Double.isFinite(p.lon)&&Math.abs(p.lat)<=90&&Math.abs(p.lon)<=180&&p.time>0&&p.accuracy>0&&p.accuracy<=MAX_ACCURACY&&Double.isFinite(p.accuracy)&&p.session!=null&&!p.session.isEmpty()&&"gps".equals(p.provider)&&!(p.gnssAgeMs>=0&&p.gnssAgeMs<=15000&&(p.satellitesUsed>=0&&p.satellitesUsed<4||Double.isFinite(p.meanCn0)&&p.meanCn0<20));}
    public static double distance(Point a,Point b){double x=Math.toRadians(b.lat-a.lat),y=Math.toRadians(b.lon-a.lon),h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a.lat))*Math.cos(Math.toRadians(b.lat))*Math.sin(y/2)*Math.sin(y/2);return 6371008.8*2*Math.asin(Math.sqrt(Math.min(1,h)));}
    private static boolean continuity(Point a,Point b){return !b.breakBefore&&a.session.equals(b.session)&&b.time>a.time&&b.time-a.time<=GAP;}
    private static double uncertainty(Point a,Point b){return Math.max(12,2*(a.accuracy+b.accuracy));}
    private static Point pinned(Point sample,Point anchor){Point p=sample.copy();p.lat=anchor.lat;p.lon=anchor.lon;p.accuracy=anchor.accuracy;p.filtered=distance(sample,anchor)>0.01;p.speed=0;return p;}
    public static List<Point> apply(List<Point> source){
        List<Point> ordered=new ArrayList<>(),good=new ArrayList<>(),out=new ArrayList<>();for(Point p:source)if(p!=null)ordered.add(p);ordered.sort(Comparator.comparingLong(p->p.time));int missing=0;for(Point p:ordered){if(valid(p)){Point q=p.copy();q.reportedAccuracy=p.accuracy;q.reportedSpeed=p.speed;if(missing>=2)q.breakBefore=true;good.add(q);missing=0;}else missing++;}
        List<Point> cleaned=new ArrayList<>();
        for(int i=0;i<good.size();i++){
            Point p=good.get(i),prev=cleaned.isEmpty()?null:cleaned.get(cleaned.size()-1),next=i+1<good.size()?good.get(i+1):null;
            if(prev!=null&&continuity(prev,p)){
                double d=distance(prev,p),dt=(p.time-prev.time)/1000.0;
                if(d/dt>MAX_SPEED)continue;
                if(prev.reliableSpeed()&&p.reliableSpeed()&&d>(prev.speed+p.speed)*0.5*dt+3*(prev.accuracy+p.accuracy)+10)continue;
                // A single teleport and return is not a journey. Positive Doppler speed preserves real U-turns.
                if(next!=null&&continuity(p,next)&&p.time-prev.time<=120000&&next.time-p.time<=120000&&(!p.reliableSpeed()||p.speed<0.6)){
                    double onward=distance(p,next),direct=distance(prev,next);
                    if(d>uncertainty(prev,p)&&onward>uncertainty(p,next)&&direct<0.35*Math.min(d,onward))continue;
                }
            }else if(prev!=null&&p.time<=prev.time)continue;
            cleaned.add(p);
        }
        Point anchor=null,previous=null;boolean moving=false;double motionRate=0;List<Point> pending=new ArrayList<>();
        for(Point p:cleaned){
            if(previous==null||!continuity(previous,p)){
                flushTail(pending,anchor,out,moving,motionRate);pending.clear();anchor=p;moving=false;motionRate=0;out.add(p);previous=p;continue;
            }
            boolean quiet=p.reliableSpeed()&&p.speed<=0.35,travel=p.reliableSpeed()&&p.speed>=0.6;
            if(quiet){
                flushTail(pending,anchor,out,moving,motionRate);pending.clear();
                // First stopped fix becomes the anchor, but an implausible displacement at zero speed does not.
                if(moving&&distance(previous,p)<=Math.max(uncertainty(previous,p),((p.time-previous.time)/1000.0)*(previous.reliableSpeed()?previous.speed:2)+p.accuracy))anchor=p;
                moving=false;motionRate=0;out.add(pinned(p,anchor));
            }else if(travel){
                // Validate against the drawn position, not only the drifting raw predecessor.
                // Otherwise a locked stationary anchor can suddenly become a several-hundred-meter line.
                if(!pending.isEmpty()){flushTail(pending,anchor,out,moving,motionRate);pending.clear();}
                if(!out.isEmpty()){Point drawn=out.get(out.size()-1);double dt=(p.time-drawn.time)/1000.0,allowed=p.speed*Math.max(1,dt)+3*(p.accuracy+drawn.accuracy)+10;if(continuity(drawn,p)&&distance(drawn,p)>allowed)p.breakBefore=true;}
                out.add(p);anchor=p;moving=true;motionRate=p.speed;
            }else{
                // No trustworthy speed (old history / some chipsets): wait for coherent displacement, not pairwise jitter.
                pending.add(p);double net=distance(anchor,p),path=0;Point last=anchor;int beyond=0;
                for(Point q:pending){path+=distance(last,q);last=q;if(distance(anchor,q)>uncertainty(anchor,q))beyond++;}
                double dt=(p.time-anchor.time)/1000.0;
                // Count consecutive fixes, not a hard-coded 5-second sampling window.
                // Every hop has already passed continuity; a 31-second history needs ~93 seconds for three fixes.
                boolean continued=moving&&pending.size()>=3&&p.time-anchor.time<=3*GAP&&net>=Math.max(6,0.6*(anchor.accuracy+p.accuracy))&&path>0&&net/path>=0.55&&net/Math.max(1,dt)>=0.35;
                if(continued||beyond>=3&&path>0&&net/path>=0.7&&net/Math.max(1,dt)>=0.45){
                    motionRate=Math.min(MAX_SPEED,path/Math.max(1,dt));for(Point q:pending)out.add(q);pending.clear();anchor=p;moving=true;
                }else if(pending.size()>=4){
                    // A turn or loop may have little net displacement from the old anchor.
                    // Reacquire from the most recent three hops; never let that old anchor trap hours of movement.
                    int start=pending.size()-3;Point local=pending.get(start-1),lastLocal=local;double localPath=0;int localBeyond=0;
                    for(int k=start;k<pending.size();k++){Point q=pending.get(k);localPath+=distance(lastLocal,q);lastLocal=q;if(distance(local,q)>uncertainty(local,q))localBeyond++;}
                    double localNet=distance(local,p),localDt=(p.time-local.time)/1000.0;
                    if(localDt>0&&localDt<=3*GAP&&localPath>0&&localNet/localPath>=0.7&&localNet/localDt>=0.45&&localBeyond>=3){
                        for(int k=0;k<start;k++)out.add(pinned(pending.get(k),anchor));
                        motionRate=Math.min(MAX_SPEED,localPath/localDt);Point first=pending.get(start),drawn=out.get(out.size()-1);double bridgeDt=(first.time-drawn.time)/1000.0;
                        if(distance(drawn,first)>motionRate*Math.max(0,bridgeDt)+3*(drawn.accuracy+first.accuracy)+10)first.breakBefore=true;
                        for(int k=start;k<pending.size();k++)out.add(pending.get(k));pending.clear();anchor=p;moving=true;
                    }else if(pending.size()>12){Point q=pending.remove(0);out.add(pinned(q,anchor));}
                }
            }
            previous=p;
        }
        flushTail(pending,anchor,out,moving,motionRate);
        List<Point> smooth=new ArrayList<>();
        for(int i=0;i<out.size();i++){
            Point p=out.get(i).copy();
            if(i>0&&i+1<out.size()&&p.speed!=0){
                Point a=out.get(i-1),b=out.get(i+1);double path=distance(a,p)+distance(p,b),direct=distance(a,b);
                // Short, almost straight windows only. Do not cut corners, gaps or stationary anchors.
                if(continuity(a,p)&&continuity(p,b)&&b.time-a.time<=30000&&path>0&&direct/path>=0.96&&a.speed!=0&&b.speed!=0){
                    double ratio=(p.time-a.time)/(double)(b.time-a.time),blend=Math.min(0.45,Math.max(0.1,p.accuracy/60));
                    p.lat=p.lat*(1-blend)+(a.lat+(b.lat-a.lat)*ratio)*blend;p.lon=p.lon*(1-blend)+(a.lon+(b.lon-a.lon)*ratio)*blend;p.filtered=true;
                }
            }
            smooth.add(p);
        }
        return smooth;
    }
    private static void flushTail(List<Point> pending,Point anchor,List<Point> out,boolean moving,double motionRate){
        if(anchor==null)return;Point previous=anchor;int preserved=0;
        for(Point q:pending){
            // Preserve at most two consecutive observed tail fixes at the confirmed movement rate.
            // Never cross a missing >60s hop, or resume preservation after an implausible fix.
            double dt=(q.time-previous.time)/1000.0,allowed=Math.max(25,motionRate*Math.max(0,dt)+3*(previous.accuracy+q.accuracy));
            if(moving&&preserved<2&&continuity(previous,q)&&q.time-anchor.time<=2*GAP&&distance(previous,q)<=allowed){out.add(q);previous=q;preserved++;}
            else{moving=false;out.add(pinned(q,anchor));}
        }
    }
    private TrackFilter(){}
}
