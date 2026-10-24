package com.insight.quantlife.tests;
import com.insight.quantlife.*;
import com.insight.quantlife.TrackFilter.Point;
import java.util.*;

public final class TrackDwellRegression {
    static int passed;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);passed++;System.out.println("PASS "+message);}
    static Point point(int sec,double meters){Point p=TrackFilterRegression.p(sec,meters,0);p.motionRms=.02;p.motionAgeMs=20;p.accuracy=20;return p;}
    static List<Point> cloud(){List<Point> a=new ArrayList<>();for(int i=0;i<=60;i++)a.add(point(i*2,(i%20<=10?i%20:20-i%20)*2));return a;}
    static int stops(List<Point> a){int n=0;for(Point p:a)if(p.motionState.equals("stationary_estimate"))n++;return n;}
    static void unchanged(List<Point> raw,String label){List<Point> weighted=TrackVariants.apply(raw);check(stops(weighted)==0,label);}
    public static void main(String[] args){
        List<Point> raw=cloud();String before=TrackVariantsRegression.fingerprint(raw);List<Point> view=TrackVariants.apply(raw);
        check(view.size()==raw.size()&&stops(view)==raw.size(),"Confirmed dual-evidence stop preserves all timestamps, estimates one anchor");
        check(TrackVariants.meters(view)<.001,"Quiet GPS jitter does not accumulate mileage");
        check(TrackVariantsRegression.fingerprint(raw).equals(before),"Dwell does not mutate raw observations");
        check(view.get(20).reportedSpeed==0&&view.get(20).reportedAccuracy==20&&view.get(20).motionState.equals("stationary_estimate"),"Observed speed/accuracy remain distinct from estimated stationary position");
        List<Point> moving=cloud();for(Point p:moving){p.motionRms=.3;p.speed=1.4;}unchanged(moving,"Actual walking evidence vetoes stop lock");
        List<Point> slow=cloud();for(Point p:slow)p.motionRms=.2;unchanged(slow,"Active sensor preserves very slow walking even with zero reported speed");
        List<Point> vehicle=cloud();for(Point p:vehicle)p.speed=12;unchanged(vehicle,"Quiet accelerometer cannot immobilize a moving vehicle");
        List<Point> missing=cloud();for(Point p:missing)p.motionRms=Double.NaN;unchanged(missing,"Legacy history without sensors retains D geometry");
        List<Point> stale=cloud();for(Point p:stale)p.motionAgeMs=3001;unchanged(stale,"Stale sensor cannot confirm a stop");
        List<Point> noSpeed=cloud();for(Point p:noSpeed)p.speedAccuracy=Double.NaN;check(stops(TrackVariants.apply(noSpeed))>30,"Fresh quiet sensors plus a bounded non-progressing cluster can estimate a stop without trustworthy Doppler");
        List<Point> shortRun=cloud().subList(0,10);unchanged(shortRun,"Short quiet pause does not freeze an unconfirmed track");
        List<Point> sparse=new ArrayList<>();for(int i=0;i<8;i++)sparse.add(point(i*20,i));unchanged(sparse,"Sparse samples do not become a fabricated continuous dwell");
        List<Point> relocating=new ArrayList<>();for(int i=0;i<=60;i++)relocating.add(point(i*2,i*2));unchanged(relocating,"Conflicting sustained relocation is not pinned despite quiet sensors");
        List<Point> departure=cloud();Point walked=point(124,40);walked.motionRms=.3;walked.speed=1.4;departure.add(walked);List<Point> out=TrackVariants.apply(departure);
        check(!out.get(out.size()-1).motionState.equals("stationary_estimate")&&Math.abs(out.get(out.size()-1).lat-walked.lat)<1e-12,"First positive movement evidence releases the stop immediately");
        check(out.get(out.size()-1).motionState.equals("uncertain"),"One-frame departure remains visible but does not falsely claim a confirmed journey");
        departure.set(departure.size()-1,point(124,180));departure.get(departure.size()-1).motionRms=.3;departure.get(departure.size()-1).speed=1.4;
        check(TrackVariants.apply(departure).get(departure.size()-1).breakBefore,"Remote stop-anchor correction is inferred, not fake measured mileage");
        List<Point> breaks=cloud();for(int i=0;i<breaks.size();i++)if(i%5==0)breaks.get(i).breakBefore=true;unchanged(breaks,"Cannot infer dwell across explicit missing-data breaks");
        List<Point> session=cloud();for(int i=0;i<session.size();i++)session.get(i).session="s"+(i/5);unchanged(session,"Different sessions cannot establish a dwell together");
        System.out.println("PASS: "+passed+" conservative D dwell checks.");
    }
}
