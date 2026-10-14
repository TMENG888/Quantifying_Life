package com.insight.quantlife.tests;
import com.insight.quantlife.*;
import com.insight.quantlife.TrackFilter.Point;
import java.util.*;

/** Same sole production D pipeline used for map, diagnostics and GPX. */
public final class TrackVariantsRegression {
    static int passed;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    static Point p(int seconds,double meters){Point p=TrackFilterRegression.p(seconds,meters,1.4);p.id=seconds+1;return p;}
    static List<Point> points(){return new ArrayList<>(Arrays.asList(p(0,0),p(5,7),p(10,14),p(15,21)));}
    static String fingerprint(List<Point> points){StringBuilder b=new StringBuilder();for(Point p:points)b.append(p.id).append(':').append(p.time).append(':').append(p.lat).append(':').append(p.lon).append(':').append(p.accuracy).append(':').append(p.speed).append(':').append(p.satellitesUsed).append(':').append(p.filtered).append(':').append(p.breakBefore).append(';');return b.toString();}
    public static void main(String[] args){
        check(TrackVariants.ID.equals("weighted65")&&TrackVariants.MAX_ACCURACY==65,"Only former D is the default production policy");
        List<Point> raw=points();String before=fingerprint(raw);List<Point> out=TrackVariants.apply(raw);
        check(fingerprint(raw).equals(before),"Original objects never mutated");
        check(out.size()==4&&out.get(0)!=raw.get(0),"Output is a distinct display/export copy");
        check(Math.abs(TrackVariants.meters(out)-21)<.01,"Small walking steps count without another noise gate");
        List<Point> loose=points();for(Point q:loose){q.accuracy=65;q.satellitesUsed=0;q.gnssAgeMs=0;}
        check(TrackVariants.apply(loose).size()==4,"65m inclusive and zero satellites remain usable");
        for(Point q:loose)q.accuracy=65.01;check(TrackVariants.apply(loose).isEmpty(),"Above 65m rejected");
        for(Point q:loose){q.accuracy=45;q.satellitesUsed=3;q.meanCn0=10;}
        check(TrackVariants.apply(loose).size()==4,"Satellite metadata remains advisory exactly as former D");
        for(double accuracy:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}){Point q=p(0,0);q.accuracy=accuracy;check(TrackVariants.apply(Arrays.asList(q)).isEmpty(),"Invalid accuracy rejected: "+accuracy);}
        Point invalid=p(0,0);invalid.lat=Double.NaN;check(TrackVariants.apply(Arrays.asList(invalid)).isEmpty(),"Invalid coordinate rejected");
        Point network=p(0,0);network.provider="network";check(TrackVariants.apply(Arrays.asList(network)).isEmpty(),"Network fix excluded");
        check(TrackVariants.apply(Collections.emptyList()).isEmpty()&&TrackVariants.apply(Arrays.asList((Point)null)).isEmpty(),"Empty/null point input supported");
        List<Point> noise=new ArrayList<>(Arrays.asList(p(0,0),p(5,12),p(10,20)));
        noise.get(0).accuracy=10;noise.get(1).accuracy=20;noise.get(2).accuracy=10;
        List<Point> smooth=TrackVariants.apply(noise);
        double expected=(.01*noise.get(0).lat+.005*noise.get(1).lat+.01*noise.get(2).lat)/.025;
        check(Math.abs(smooth.get(1).lat-expected)<1e-12&&smooth.get(1).filtered,"Exact former D three-point inverse-error weighting");
        check(smooth.get(0).lat==noise.get(0).lat&&smooth.get(2).lat==noise.get(2).lat,"Endpoints never shifted");
        check(smooth.get(1).time==noise.get(1).time&&smooth.get(1).reportedAccuracy==20,"Timestamps and reported uncertainty preserved");
        List<Point> corner=new ArrayList<>(Arrays.asList(p(0,0),p(5,10),p(10,10)));corner.get(2).lon+=10/(111195*Math.cos(Math.toRadians(corner.get(2).lat)));
        check(TrackFilter.distance(corner.get(1),TrackVariants.apply(corner).get(1))<.01,"Right-angle corner not cut");
        List<Point> dateLine=new ArrayList<>(Arrays.asList(p(0,0),p(5,0),p(10,0)));dateLine.get(0).lon=179.999;dateLine.get(1).lon=179.9995;dateLine.get(2).lon=-179.999;
        check(Math.abs(TrackVariants.apply(dateLine).get(1).lon)>179,"Date-line longitude never averaged through zero");
        List<Point> gap=new ArrayList<>(Arrays.asList(p(0,0),p(5,7),p(100,200),p(105,207)));
        check(TrackVariants.gaps(TrackVariants.apply(gap))==1&&Math.abs(TrackVariants.meters(TrackVariants.apply(gap))-14)<.01,"Gap excluded from smoothing and mileage");
        List<Point> paused=points();paused.get(2).session="new";paused.get(3).session="new";
        check(TrackVariants.gaps(TrackVariants.apply(paused))==1,"Pause session remains separate");
        List<Point> marked=points();marked.get(2).breakBefore=true;check(TrackVariants.gaps(TrackVariants.apply(marked))==1,"Explicit break honoured");
        List<Point> badRun=new ArrayList<>(Arrays.asList(p(0,0),p(5,7),p(10,14),p(15,21)));badRun.get(1).accuracy=90;badRun.get(2).accuracy=90;
        check(TrackVariants.apply(badRun).get(1).breakBefore,"Two invalid points produce break before next usable point");
        List<Point> spike=new ArrayList<>(Arrays.asList(p(0,0),p(5,150),p(10,14)));spike.get(1).speed=0;
        check(TrackVariants.apply(spike).size()==2,"Isolated return jump rejected");
        List<Point> impossible=new ArrayList<>(Arrays.asList(p(0,0),p(5,1000),p(10,14)));
        check(TrackVariants.apply(impossible).size()==2,"Impossible speed jump rejected");
        List<Point> unordered=points();Collections.reverse(unordered);check(TrackVariants.apply(unordered).get(0).time==raw.get(0).time,"Time ordering retained");
        List<Point> large=new ArrayList<>();for(int i=0;i<20000;i++)large.add(p(i*5,i*7));long start=System.currentTimeMillis();
        List<Point> largeView=TrackVariants.apply(large);check(largeView.size()==20000,"20000-point input supported");
        check(System.currentTimeMillis()-start<15000,"Bounded production pipeline runs within JVM budget");
        System.out.println("PASS: "+passed+" sole-D production checks.");
    }
}
