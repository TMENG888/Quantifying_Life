package com.insight.quantlife;

import java.util.*;
import com.insight.quantlife.TrackFilter.Point;

/** Historical D baseline for comparison. Never changes stored coordinates. */
public final class TrackVariants {
    public static final String ID="weighted65", LABEL="65米＋误差加权平滑", COLOR="#b47719";
    public static final double MAX_ACCURACY=65;
    public static boolean connected(Point a,Point b){return a.session.equals(b.session)&&!b.breakBefore&&b.time>a.time&&b.time-a.time<=TrackFilter.GAP&&TrackFilter.distance(a,b)/((b.time-a.time)/1000.0)<=TrackFilter.MAX_SPEED;}
    public static List<Point> apply(List<Point> raw){return weighted(light(raw));}
    static List<Point> light(List<Point> raw){
        List<Point> ordered=new ArrayList<>();for(Point p:raw)if(p!=null)ordered.add(p);ordered.sort(Comparator.comparingLong(p->p.time));
        List<Point> valid=new ArrayList<>();int missing=0;
        for(Point p:ordered){if(!TrackFilter.valid(p,MAX_ACCURACY,2)){missing++;continue;}Point q=p.copy();q.reportedAccuracy=p.accuracy;q.reportedSpeed=p.speed;if(missing>=2)q.breakBefore=true;valid.add(q);missing=0;}
        List<Point> out=new ArrayList<>();
        for(int i=0;i<valid.size();i++){
            Point p=valid.get(i),a=out.isEmpty()?null:out.get(out.size()-1),b=i+1<valid.size()?valid.get(i+1):null;
            if(a!=null&&p.session.equals(a.session)&&!p.breakBefore&&p.time>a.time&&p.time-a.time<=TrackFilter.GAP){
                double d=TrackFilter.distance(a,p),dt=(p.time-a.time)/1000.0;
                if(d/dt>TrackFilter.MAX_SPEED)continue;
                if(b!=null&&connected(p,b)&&(!p.reliableSpeed()||p.speed<.6)){
                    double onward=TrackFilter.distance(p,b),direct=TrackFilter.distance(a,b),noise=Math.max(40,2*(a.accuracy+p.accuracy));
                    if(d>noise&&onward>Math.max(40,2*(p.accuracy+b.accuracy))&&direct<.25*Math.min(d,onward))continue;
                }
            }
            out.add(p);
        }
        return out;
    }
    private static List<Point> weighted(List<Point> points){
        List<Point> out=new ArrayList<>();
        for(int i=0;i<points.size();i++){
            Point p=points.get(i),q=p.copy();
            if(i>0&&i+1<points.size()){
                Point a=points.get(i-1),b=points.get(i+1);double path=TrackFilter.distance(a,p)+TrackFilter.distance(p,b),direct=TrackFilter.distance(a,b);
                if(connected(a,p)&&connected(p,b)&&b.time-a.time<=30000&&path>0&&direct/path>=.92){
                    double wa=1/(a.accuracy*a.accuracy),wp=2/(p.accuracy*p.accuracy),wb=1/(b.accuracy*b.accuracy),sum=wa+wp+wb;
                    // Wrapped longitudes avoid averaging +180 and -180 into zero.
                    q.lat=(wa*a.lat+wp*p.lat+wb*b.lat)/sum;q.lon=wrap(p.lon+(wa*delta(a.lon-p.lon)+wb*delta(b.lon-p.lon))/sum);
                    q.filtered=TrackFilter.distance(p,q)>.01;
                }
            }
            out.add(q);
        }
        return out;
    }
    private static double delta(double lon){return ((lon+540)%360)-180;}
    private static double wrap(double lon){return delta(lon);}
    public static double meters(List<Point> points){double sum=0;for(int i=1;i<points.size();i++)if(connected(points.get(i-1),points.get(i)))sum+=TrackFilter.distance(points.get(i-1),points.get(i));return sum;}
    private static boolean uncertainEdge(Point a,Point b){return (a.motionState.equals("uncertain")||b.motionState.equals("uncertain"))&&a.session.equals(b.session)&&b.time>a.time&&b.time-a.time<=TrackFilter.GAP;}
    public static int gaps(List<Point> points){int n=0;boolean priorUncertain=false;for(int i=1;i<points.size();i++){Point a=points.get(i-1),b=points.get(i);boolean uncertain=uncertainEdge(a,b);if(!connected(a,b)&&!(priorUncertain&&uncertain))n++;priorUncertain=uncertain;}return n;}
    private TrackVariants(){}
}
