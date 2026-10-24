package com.insight.quantlife;

import java.util.*;
import com.insight.quantlife.TrackFilter.Point;

/** GPS-only, sensor-evidenced overlay for D. Reuses the tested state classifier,
 * not its Fused fallback or its geometry-only decisions on legacy history.
 * Accepted observations and raw coordinates are never deleted or rewritten. */
public final class TrackDwell {
    private static boolean fresh(Point p){return p.motionAgeMs>=0&&p.motionAgeMs<=3000&&Double.isFinite(p.motionRms)&&p.motionRms>=0;}
    public static List<Point> apply(List<Point> points){
        List<Point> out=new ArrayList<>();boolean available=false;
        for(Point p:points){out.add(p.copy());if(fresh(p))available=true;}
        if(!available)return out;
        boolean[] supported=new boolean[points.size()];
        for(int start=0;start<points.size();){int end=start+1,count=fresh(points.get(start))?1:0,quietSpeed=points.get(start).reliableSpeed()&&points.get(start).speed<=.25?1:0;double path=0;
            while(end<points.size()&&TrackVariants.connected(points.get(end-1),points.get(end))&&points.get(end).time-points.get(end-1).time<=15000){path+=TrackFilter.distance(points.get(end-1),points.get(end));if(fresh(points.get(end)))count++;if(points.get(end).reliableSpeed()&&points.get(end).speed<=.25)quietSpeed++;end++;}
            Point a=points.get(start),b=points.get(end-1);double seconds=(b.time-a.time)/1000.,net=TrackFilter.distance(a,b);
            boolean relocation=quietSpeed*10>=(end-start)*9&&net>=Math.max(25,2*(a.accuracy+b.accuracy))&&net/Math.max(1,seconds)>=.45&&net/Math.max(.01,path)>=.7;
            if(end-start>=6&&seconds>=30&&count*10>=(end-start)*7&&!relocation)Arrays.fill(supported,start,end,true);
            start=end;
        }
        Map<Long,Point> estimates=new HashMap<>();for(Point p:TrackFusion.apply(points))estimates.put(p.time,p);
        for(int i=0;i<points.size();i++){
            Point p=points.get(i),q=out.get(i),e=estimates.get(p.time);
            // Missing/expired sensors alone cannot establish a stop or suppress
            // historical walking. This is deliberately not a default-mode flip.
            if(!fresh(p))continue;
            if(e==null){q.motionState="uncertain";q.breakBefore=true;continue;}
            if(e.motionState.equals("moving")){
                // Keep D's observed corners and endpoints for measured movement.
                q.motionState="moving";q.breakBefore|=e.breakBefore;continue;
            }
            if(!supported[i]){q.motionState="uncertain";q.breakBefore=true;continue;}
            if(p.motionRms>=.15){
                // Active motion without corroborated travel may be handling the
                // phone or a coarse short walk: retain its original position as
                // uncertain evidence rather than erase it into a stationary pin.
                q.motionState="uncertain";q.breakBefore=true;continue;
            }
            q.lat=e.lat;q.lon=e.lon;q.speed=e.speed;q.motionState=e.motionState;
            q.filtered|=TrackFilter.distance(q,p)>.01;q.breakBefore|=e.breakBefore;
        }
        for(int i=1;i<out.size();i++){
            Point a=out.get(i-1),b=out.get(i),rawA=points.get(i-1),rawB=points.get(i);
            if(a.motionState.equals("uncertain")||b.motionState.equals("uncertain")){b.breakBefore=true;continue;}
            if(!a.motionState.equals(b.motionState)){
                double rawStep=TrackFilter.distance(rawA,rawB),drawnStep=TrackFilter.distance(a,b),seconds=(b.time-a.time)/1000.;
                double allowed=(rawB.reliableSpeed()?rawB.speed*Math.max(0,seconds):0)+Math.max(25,2*(a.accuracy+b.accuracy));
                if(drawnStep>rawStep+Math.max(15,a.accuracy+b.accuracy)||a.motionState.equals("stationary_estimate")&&drawnStep>allowed)b.breakBefore=true;
            }
        }
        return out;
    }
    private TrackDwell(){}
}
