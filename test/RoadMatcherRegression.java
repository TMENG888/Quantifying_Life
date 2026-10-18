package com.insight.quantlife.tests;
import java.util.*;
import com.insight.quantlife.*;
import com.insight.quantlife.TrackFilter.Point;
public final class RoadMatcherRegression {
    static int passed;static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);passed++;}
    static RoadMatcher.Node node(long id,double x,double y){return new RoadMatcher.Node(id,y/111195,x/111195);}
    static Point p(int i,double x,double y){Point p=new Point();p.time=1700000000000L+i*15000L;p.lat=y/111195;p.lon=x/111195;p.accuracy=6;p.session="synthetic";p.motionState="moving";return p;}
    static RoadMatcher.Route match(RoadMatcher.Graph g,List<Point> p,int mode){return RoadMatcher.match(g,p,mode,System.nanoTime()+5000000000L);}
    static List<Point> l(){return new ArrayList<>(Arrays.asList(p(0,20,3),p(1,40,4),p(2,65,3),p(3,92,0),p(4,103,20),p(5,102,40),p(6,101,65)));}
    public static void main(String[] args){
        RoadMatcher.Node a=node(1,0,0),b=node(2,100,0),c=node(3,100,100);RoadMatcher.Graph g=new RoadMatcher.Graph();g.add(new RoadMatcher.Edge(1,a,b,7,7));g.add(new RoadMatcher.Edge(2,b,c,7,7));List<Point> input=l();double before=input.get(1).lat;
        RoadMatcher.Route route=match(g,input,1);check(route!=null,"Noisy L shape matches full connected road sequence");check(route.path.stream().anyMatch(v->RoadMatcher.distance(v[0],v[1],b.lat,b.lon)<.1),"L follows real corner, no diagonal through building");check(input.get(1).lat==before,"Matcher never mutates observations");check(route.start==input.get(0).time&&route.end==input.get(input.size()-1).time,"Inference retains observation time range");
        List<Point> stop=l();for(Point p:stop)p.motionState="stationary_estimate";check(match(g,stop,1)==null,"Stationary positions are not pulled to a road");
        List<Point> missing=l();for(int i=3;i<missing.size();i++)missing.get(i).time+=600000;check(match(g,missing,1)==null,"Long outages are not fabricated as a route");
        List<Point> session=l();session.get(2).session="other";check(match(g,session,1)==null,"Paused/new session not bridged by road matching");
        List<Point> mock=l();mock.get(2).mocked=true;check(match(g,mock,1)==null,"Mock location not a road source");
        List<Point> off=l();for(Point p:off){p.lat+=.01;p.lon+=.01;}check(match(g,off,1)==null,"Outside coverage leaves original route");
        RoadMatcher.Graph stairs=new RoadMatcher.Graph();stairs.add(new RoadMatcher.Edge(1,a,b,1,1));stairs.add(new RoadMatcher.Edge(2,b,c,1,1));check(match(stairs,l(),4)==null,"Cars cannot be matched to foot-only edges");check(match(stairs,l(),2)==null,"Bikes cannot be matched to steps");
        RoadMatcher.Graph reverse=new RoadMatcher.Graph();reverse.add(new RoadMatcher.Edge(1,a,b,0,4));reverse.add(new RoadMatcher.Edge(2,b,c,0,4));check(match(reverse,l(),4)==null,"Reverse one-way route is rejected");
        RoadMatcher.Graph ambiguous=new RoadMatcher.Graph();RoadMatcher.Node aa=node(4,0,30),bb=node(5,200,30);ambiguous.add(new RoadMatcher.Edge(1,node(6,0,-30),node(7,200,-30),1,1));ambiguous.add(new RoadMatcher.Edge(2,aa,bb,1,1));List<Point> mid=new ArrayList<>();for(int i=0;i<7;i++){Point p=p(i,20+i*20,0);p.accuracy=25;mid.add(p);}check(match(ambiguous,mid,1)==null,"Equally plausible parallel roads stay ambiguous");
        RoadMatcher.Graph crossing=new RoadMatcher.Graph();crossing.add(new RoadMatcher.Edge(1,node(10,-100,0),node(11,100,0),7,7));crossing.add(new RoadMatcher.Edge(2,node(12,0,-100),node(13,0,100),7,7));List<Point> turn=Arrays.asList(p(0,-80,0),p(1,-50,0),p(2,-15,0),p(3,0,15),p(4,0,50),p(5,0,80));check(match(crossing,turn,1)==null,"Geometric crossing without common OSM node is not a junction");
        check(RoadMatcher.match(g,l(),1,System.nanoTime()-1)==null,"Time budget aborts safely");
        RoadMatcher.Graph converging=new RoadMatcher.Graph();RoadMatcher.Node split=node(50,0,0),merge=node(51,220,0),end=node(52,320,0);long edgeId=1;for(int side:new int[]{-1,1}){RoadMatcher.Node x=node(60+side,20,side*20),y=node(70+side,200,side*20);converging.add(new RoadMatcher.Edge(edgeId++,split,x,1,1));converging.add(new RoadMatcher.Edge(edgeId++,x,y,1,1));converging.add(new RoadMatcher.Edge(edgeId++,y,merge,1,1));}converging.add(new RoadMatcher.Edge(edgeId++,merge,end,1,1));List<Point> between=new ArrayList<>();for(int i=0;i<13;i++){Point q=p(i,20+i*20,0);q.accuracy=25;between.add(q);}check(match(converging,between,1)==null,"Ambiguous roads that reconverge before endpoint still rejected by full-sequence check");
        List<Point> single=new ArrayList<>();single.add(p(0,20,0));single.add(p(1,30,0));check(match(g,single,1)==null,"Two points cannot establish a sequence");
        List<Point> bad=l();bad.get(3).accuracy=100;check(match(g,bad,1)==null,"Poor accuracy does not force road fitting");
        System.out.println("PASS "+passed+" offline road matcher checks (synthetic data only)");
    }
}
