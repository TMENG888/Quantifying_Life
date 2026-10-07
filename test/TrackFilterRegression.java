package com.insight.quantlife.tests;
import com.insight.quantlife.TrackFilter;
import java.util.*;

/** Runs the production Java filter on the host JVM, not a reimplementation. */
public final class TrackFilterRegression {
    static int passed;
    static TrackFilter.Point p(int seconds,double meters,double speed){TrackFilter.Point p=new TrackFilter.Point();p.time=1700000000000L+seconds*1000L;p.lat=31.23+meters/111195;p.lon=121.47;p.accuracy=8;p.session="a";p.speed=speed;p.speedAccuracy=Double.isFinite(speed)?0.15:Double.NaN;return p;}
    static double distance(List<TrackFilter.Point> points){double n=0;for(int i=1;i<points.size();i++){TrackFilter.Point a=points.get(i-1),b=points.get(i);if(a.session.equals(b.session)&&b.time-a.time<=TrackFilter.GAP)n+=TrackFilter.distance(a,b);}return n;}
    static void check(boolean value,String title){if(!value)throw new AssertionError(title);passed++;System.out.println("PASS "+title);}
    static List<TrackFilter.Point> route(double speed,int count){List<TrackFilter.Point> out=new ArrayList<>();for(int i=0;i<count;i++)out.add(p(i*5,i*5*speed,speed));return out;}
    public static void main(String[] args){
        List<TrackFilter.Point> staticDrift=new ArrayList<>();for(int i=0;i<720;i++)staticDrift.add(p(i*5,i==0?0:Math.sin(i*0.7)*150,0));
        check(distance(TrackFilter.apply(staticDrift))<0.01,"one hour of zero-speed GPS drift does not accumulate distance");
        List<TrackFilter.Point> bounce=new ArrayList<>();for(int i=0;i<120;i++)bounce.add(p(i*30,i==0?0:(i%2==0?65:-65),Double.NaN));
        check(distance(TrackFilter.apply(bounce))<0.01,"legacy alternating drift without speed is not coherent movement");
        List<TrackFilter.Point> spike=route(1.2,30);spike.set(10,p(50,800,0));check(Math.abs(distance(TrackFilter.apply(spike))-174)<2,"isolated jump is removed without counting outward and return legs");
        for(double speed:new double[]{0.7,1.4,5,20,85}){List<TrackFilter.Point> input=route(speed,121);double expected=600*speed;check(Math.abs(distance(TrackFilter.apply(input))-expected)<Math.max(1,expected*0.001),"5-second trajectory retained at "+speed+" m/s");}
        List<TrackFilter.Point> geometric=route(1.0,121);for(TrackFilter.Point p:geometric){p.speed=Double.NaN;p.speedAccuracy=Double.NaN;}check(Math.abs(distance(TrackFilter.apply(geometric))-600)<15,"coherent walking retained when speed metadata is absent");
        List<TrackFilter.Point> badSpeed=route(1.2,121);for(TrackFilter.Point p:badSpeed){p.speed=0;p.speedAccuracy=4;}check(distance(TrackFilter.apply(badSpeed))>700,"unreliable zero-speed estimate cannot suppress a real journey");
        List<TrackFilter.Point> turn=route(2,11);for(int i=1;i<=10;i++){TrackFilter.Point q=p((10+i)*5,100,2);q.lon+=i*10/(111195*Math.cos(Math.toRadians(q.lat)));turn.add(q);}List<TrackFilter.Point> turns=TrackFilter.apply(turn);check(Math.abs(distance(turns)-200)<2&&TrackFilter.distance(turns.get(10),turn.get(10))<0.01,"right-angle corner is not cut by smoothing");
        List<TrackFilter.Point> noise=route(1.4,121);for(int i=1;i<120;i++)noise.get(i).lon+=(i%2==0?1:-1)*1/95000.0;double raw=distance(noise),filtered=distance(TrackFilter.apply(noise));check(filtered<raw&&Math.abs(filtered-840)<35,"small straight-road coordinate noise is smoothed");
        List<TrackFilter.Point> stopped=route(1.2,41);for(int i=0;i<40;i++)stopped.add(p(205+i*5,240+(i%2==0?15:-15),0));for(int i=0;i<41;i++)stopped.add(p(405+i*5,240+i*6,1.2));check(Math.abs(distance(TrackFilter.apply(stopped))-480)<40,"walking-stop-walking does not accumulate stationary jitter");
        List<TrackFilter.Point> gaps=new ArrayList<>();gaps.add(p(0,0,1));gaps.add(p(600,10000,1));check(distance(TrackFilter.apply(gaps))==0,"missing ten minutes is not interpolated");
        List<TrackFilter.Point> sessions=route(1,3);TrackFilter.Point newSession=p(20,5000,1);newSession.session="b";sessions.add(newSession);check(distance(TrackFilter.apply(sessions))<11,"pause and resume are separate routes");
        TrackFilter.Point poor=p(0,0,0);poor.accuracy=60;check(TrackFilter.apply(Arrays.asList(poor)).isEmpty(),"low-accuracy historical points are excluded");
        TrackFilter.Point network=p(0,0,0);network.provider="network";check(TrackFilter.apply(Arrays.asList(network)).isEmpty(),"cell and Wi-Fi fixes do not shift GPS tracks");
        List<TrackFilter.Point> ordered=route(2,20);Collections.reverse(ordered);check(Math.abs(distance(TrackFilter.apply(ordered))-190)<1,"timestamps sorted deterministically");
        List<TrackFilter.Point> duplicate=route(2,20);duplicate.add(duplicate.get(10));check(TrackFilter.apply(duplicate).size()==20,"duplicate timestamp does not add distance");
        List<TrackFilter.Point> large=route(1.2,17280);check(TrackFilter.apply(large).size()==17280,"24-hour five-second data set fits without losing valid samples");
        check(TrackFilter.apply(Collections.emptyList()).isEmpty(),"empty history remains empty");
        List<TrackFilter.Point> tail=route(1.4,61);for(TrackFilter.Point p:tail){p.speed=Double.NaN;p.speedAccuracy=Double.NaN;}List<TrackFilter.Point> tailOut=TrackFilter.apply(tail);
        check(TrackFilter.distance(tailOut.get(tailOut.size()-1),tail.get(tail.size()-1))<1,"confirmed walking without speed retains its last short leg and endpoint");
        List<TrackFilter.Point> square=new ArrayList<>();for(int i=0;i<=24;i++){double north=i<=6?i*5:i<=12?30:i<=18?30-(i-12)*5:0,east=i<=6?0:i<=12?(i-6)*5:i<=18?30:30-(i-18)*5;TrackFilter.Point q=p(i*5,north,Double.NaN);q.lon+=east/(111195*Math.cos(Math.toRadians(q.lat)));square.add(q);}
        List<TrackFilter.Point> squareOut=TrackFilter.apply(square);check(distance(squareOut)>110&&TrackFilter.distance(squareOut.get(squareOut.size()-1),square.get(24))<1,"missing speed metadata does not erase a turning walk or pin its destination to an old corner");
        List<TrackFilter.Point> indoorGap=Arrays.asList(p(0,0,0),p(218,275,1.4));check(distance(TrackFilter.apply(indoorGap))==0,"218-second indoor signal gap is not a 275-meter drawn journey");
        List<TrackFilter.Point> driftTransition=new ArrayList<>();for(int i=0;i<=60;i++)driftTransition.add(p(i*5,i*4,0));driftTransition.add(p(305,247,1.4));List<TrackFilter.Point> transitionOut=TrackFilter.apply(driftTransition);check(transitionOut.get(transitionOut.size()-1).breakBefore,"stationary anchor cannot teleport to a gradually drifted raw point when speed becomes positive");
        List<TrackFilter.Point> weak=route(1.4,10);weak.get(4).satellitesUsed=2;weak.get(4).gnssAgeMs=1000;weak.get(5).meanCn0=16;weak.get(5).gnssAgeMs=1000;List<TrackFilter.Point> weakOut=TrackFilter.apply(weak);check(weakOut.size()==8&&weakOut.get(4).breakBefore,"weak satellite evidence overrides optimistic position accuracy and leaves an explicit break");
        TrackFilter.Point unknown=p(0,0,1);unknown.satellitesUsed=0;unknown.gnssAgeMs=20000;check(TrackFilter.valid(unknown),"stale satellite status is unknown, not treated as a current denial");
        List<TrackFilter.Point> sparse=new ArrayList<>();for(int i=0;i<=10;i++)sparse.add(p(i*31,i*40,Double.NaN));List<TrackFilter.Point> sparseOut=TrackFilter.apply(sparse);
        check(Math.abs(distance(sparseOut)-400)<1&&TrackFilter.distance(sparseOut.get(10),sparse.get(10))<1,"31-second legacy walk retains the final observed point instead of pinning it to an old anchor");
        List<TrackFilter.Point> sparseTurn=new ArrayList<>();for(int i=0;i<=24;i++){double north=i<=6?i*40:i<=12?240:i<=18?240-(i-12)*40:0,east=i<=6?0:i<=12?(i-6)*40:i<=18?240:240-(i-18)*40;TrackFilter.Point q=p(i*31,north,Double.NaN);q.lon+=east/(111195*Math.cos(Math.toRadians(q.lat)));sparseTurn.add(q);}List<TrackFilter.Point> sparseTurnOut=TrackFilter.apply(sparseTurn);
        check(Math.abs(distance(sparseTurnOut)-960)<2,"31-second legacy right-angle walk keeps all four sides without cutting turns");
        List<TrackFilter.Point> sparseRide=new ArrayList<>();for(int i=0;i<=11;i++)sparseRide.add(p(i*31,i*220,Double.NaN));List<TrackFilter.Point> rideOut=TrackFilter.apply(sparseRide);
        check(Math.abs(distance(rideOut)-2420)<2,"31-second vehicle samples retain both final observed fixes without speed metadata");
        List<TrackFilter.Point> sparseStop=new ArrayList<>(sparse);sparseStop.add(p(341,650,Double.NaN));sparseStop.add(p(372,400,Double.NaN));check(distance(TrackFilter.apply(sparseStop))<401,"sparse stopped teleport-return does not inflate the preceding walk");
        List<TrackFilter.Point> winding=new ArrayList<>();for(int i=0;i<=3;i++)winding.add(p(i*31,i*40,Double.NaN));for(int i=1;i<=8;i++){int side=i%4;TrackFilter.Point q=p((i+3)*31,120+(side==2||side==3?40:0),Double.NaN);q.lon+=(side==1||side==2?40:0)/(111195*Math.cos(Math.toRadians(q.lat)));winding.add(q);}for(int i=1;i<=60;i++){TrackFilter.Point q=p((i+11)*31,120+i*9+90*Math.sin(i*.6),Double.NaN);q.lon+=90*(1-Math.cos(i*.6))/(111195*Math.cos(Math.toRadians(q.lat)));winding.add(q);}List<TrackFilter.Point> windingOut=TrackFilter.apply(winding);
        check(TrackFilter.distance(windingOut.get(windingOut.size()-1),winding.get(winding.size()-1))<1,"local motion can recover from an old anchor on a long winding route with small overall displacement");
        TrackFilter.Point low=p(5,30,0);low.accuracy=30;List<TrackFilter.Point> pinnedAccuracy=TrackFilter.apply(Arrays.asList(p(0,0,0),low));check(pinnedAccuracy.get(1).reportedAccuracy==30&&low.lat==31.23+30.0/111195&&Double.isNaN(low.reportedAccuracy),"pinned sample keeps its original reported uncertainty and source coordinates remain untouched");
        List<TrackFilter.Point> speedSource=TrackFilter.apply(Arrays.asList(p(0,0,0),p(5,1,.46)));check(speedSource.get(1).speed==0&&speedSource.get(1).reportedSpeed==.46&&speedSource.get(1).speedAccuracy==.15,"auxiliary bridge checks use original GPS speed and uncertainty, not post-filter pinned zero speed");
        System.out.println("PASS: "+passed+" production-filter regression checks.");
    }
}
