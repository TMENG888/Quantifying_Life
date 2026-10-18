package com.insight.quantlife;

import java.util.*;
import com.insight.quantlife.TrackFilter.Point;

/** Local sequence matching, NOT a location source or a mileage calculator.
 * Uses explicit OSM node topology: geometric intersections never create junctions.
 * Emission = distance to edge; transition = discrepancy to reachable network distance.
 * Ambiguous, off-network, stopped and long-gap observations are left unmatched. */
public final class RoadMatcher {
    public static final int REVISION=1,WALK=1,BIKE=2,CAR=4;
    public static final class Node {
        public final long id; public final double lat,lon;
        public Node(long id,double lat,double lon){this.id=id;this.lat=lat;this.lon=lon;}
    }
    public static final class Edge {
        public final long id; public final Node a,b; public final int forward,backward; public final double length;
        public Edge(long id,Node a,Node b,int f,int r){this.id=id;this.a=a;this.b=b;forward=f;backward=r;length=distance(a.lat,a.lon,b.lat,b.lon);}
    }
    private static final class Arc {Node node; Edge edge;Arc(Node n,Edge e){node=n;edge=e;}}
    public static final class Graph {
        final Map<Long,List<Arc>> adjacent=new HashMap<>(); final Map<String,List<Edge>> cells=new HashMap<>();
        public int edges;private int references;
        public void add(Edge e){if(e.length<.05||e.length>1500||e.forward+e.backward==0)return;
            adjacent.computeIfAbsent(e.a.id,k->new ArrayList<>()).add(new Arc(e.b,e));adjacent.computeIfAbsent(e.b.id,k->new ArrayList<>()).add(new Arc(e.a,e));
            int a=cell(e.a.lat),b=cell(e.b.lat),c=cell(e.a.lon),d=cell(e.b.lon);
            if((long)(Math.abs(a-b)+1)*(Math.abs(c-d)+1)>4096)return;
            int steps=Math.max(1,2*Math.max(Math.abs(a-b),Math.abs(c-d)));Set<String> touched=new HashSet<>();for(int i=0;i<=steps;i++){double t=(double)i/steps;touched.add(cell(e.a.lat+(e.b.lat-e.a.lat)*t)+":"+cell(e.a.lon+(e.b.lon-e.a.lon)*t));}
            if(references+touched.size()>200000)throw new IllegalArgumentException("Local road graph exceeds memory budget");for(String key:touched)cells.computeIfAbsent(key,k->new ArrayList<>()).add(e);references+=touched.size();edges++;
        }
    }
    private static int cell(double d){return (int)Math.floor(d*1000);}
    public static final class Route {
        public final long start,end; public final List<double[]> path; public final int observations,mode;
        public Route(long a,long b,List<double[]> p,int n,int m){start=a;end=b;path=p;observations=n;mode=m;}
    }
    private static final class Candidate {Edge edge;double t,lat,lon,error;}
    private static final class Path {double meters;List<Node> nodes;Path(double m,List<Node> n){meters=m;nodes=n;}}
    private static final class Visit {Node node;double cost;Visit previous;Visit(Node n,double c,Visit p){node=n;cost=c;previous=p;}}
    public static double distance(double a,double b,double c,double d){double x=Math.toRadians(c-a),y=Math.toRadians(d-b),h=Math.sin(x/2)*Math.sin(x/2)+Math.cos(Math.toRadians(a))*Math.cos(Math.toRadians(c))*Math.sin(y/2)*Math.sin(y/2);return 6371008.8*2*Math.asin(Math.sqrt(Math.min(1,h)));}
    static List<Candidate> candidates(Graph g,Point p,int mode){
        double radius=Math.min(55,Math.max(16,1.5*p.accuracy)),dy=radius/111195,dx=dy/Math.max(.09,Math.cos(Math.toRadians(p.lat)));
        Set<Edge> found=new HashSet<>();for(int x=cell(p.lat-dy);x<=cell(p.lat+dy);x++)for(int y=cell(p.lon-dx);y<=cell(p.lon+dx);y++)found.addAll(g.cells.getOrDefault(x+":"+y,Collections.emptyList()));
        List<Candidate> all=new ArrayList<>();for(Edge e:found){if(((e.forward|e.backward)&mode)==0)continue;
            double cos=Math.cos(Math.toRadians(p.lat)),x=(e.b.lon-e.a.lon)*cos,y=e.b.lat-e.a.lat,xx=(p.lon-e.a.lon)*cos,yy=p.lat-e.a.lat;
            Candidate c=new Candidate();c.edge=e;c.t=Math.max(0,Math.min(1,(xx*x+yy*y)/(x*x+y*y)));c.lat=e.a.lat+c.t*y;c.lon=e.a.lon+c.t*(e.b.lon-e.a.lon);c.error=distance(p.lat,p.lon,c.lat,c.lon);if(c.error<=radius)all.add(c);
        }
        all.sort(Comparator.comparingDouble((Candidate c)->c.error).thenComparingLong(c->c.edge.id));
        List<Candidate> out=new ArrayList<>();for(Candidate c:all){boolean same=false;for(Candidate old:out)if(old.edge.id==c.edge.id)same=true;if(!same)out.add(c);if(out.size()==4)break;}return out;
    }
    private static Path shortest(Graph graph,Node from,Node to,int mode,double max,long deadline){
        if(from.id==to.id)return new Path(0,new ArrayList<>());
        PriorityQueue<Visit> queue=new PriorityQueue<>(Comparator.comparingDouble(v->v.cost));Map<Long,Double> costs=new HashMap<>();queue.add(new Visit(from,0,null));costs.put(from.id,0.0);int visited=0;
        while(!queue.isEmpty()&&visited++<2500){if((visited&63)==0&&(System.nanoTime()>deadline||Thread.currentThread().isInterrupted()))return null;Visit v=queue.poll();if(v.cost>costs.getOrDefault(v.node.id,Double.POSITIVE_INFINITY))continue;if(v.node.id==to.id){List<Node> nodes=new ArrayList<>();for(Visit q=v;q!=null;q=q.previous)nodes.add(q.node);Collections.reverse(nodes);return new Path(v.cost,nodes);}for(Arc arc:graph.adjacent.getOrDefault(v.node.id,Collections.emptyList())){
                if(((arc.edge.a.id==v.node.id?arc.edge.forward:arc.edge.backward)&mode)==0)continue;double cost=v.cost+arc.edge.length;if(cost>max||cost>=costs.getOrDefault(arc.node.id,Double.POSITIVE_INFINITY))continue;costs.put(arc.node.id,cost);queue.add(new Visit(arc.node,cost,v));
            }}return null;
    }
    private static Path between(Graph g,Candidate a,Candidate b,int mode,double max,long deadline,Map<String,Path> cache){
        Path best=null;if(a.edge.id==b.edge.id&&((b.t>=a.t?a.edge.forward:a.edge.backward)&mode)!=0){double length=Math.abs(b.t-a.t)*a.edge.length;if(length<=max)best=new Path(length,new ArrayList<>());}
        for(int i=0;i<2;i++){Node from=i==0?a.edge.a:a.edge.b;double head=(i==0?a.t:1-a.t)*a.edge.length;if(head>.05&&((i==0?a.edge.backward:a.edge.forward)&mode)==0)continue;
            for(int j=0;j<2;j++){Node to=j==0?b.edge.a:b.edge.b;double tail=(j==0?b.t:1-b.t)*b.edge.length;if(tail>.05&&((j==0?b.edge.forward:b.edge.backward)&mode)==0)continue;
                if(head+tail>max)continue;String key=from.id+":"+to.id;Path middle;
                if(cache.containsKey(key))middle=cache.get(key);else{middle=shortest(g,from,to,mode,900,deadline);if(cache.size()<8000)cache.put(key,middle);}
                if(middle==null||head+middle.meters+tail>max)continue;double n=head+middle.meters+tail;if(best==null||n<best.meters){List<Node> nodes=new ArrayList<>();nodes.add(from);nodes.addAll(middle.nodes);nodes.add(to);best=new Path(n,nodes);}
            }}return best;
    }
    /** Entire bounded observation window is accepted or rejected, never pointwise nearest-road snapping. */
    public static Route match(Graph graph,List<Point> points,int mode,long deadline){
        int n=points.size();if(mode!=WALK&&mode!=BIKE&&mode!=CAR||n<3||n>160||points.get(n-1).time-points.get(0).time<10000)return null;
        List<List<Candidate>> cs=new ArrayList<>();int moving=0;for(int i=0;i<n;i++){Point p=points.get(i);if(p.mocked||p.time<=0||!Double.isFinite(p.accuracy)||p.accuracy<=0||p.accuracy>65||!Double.isFinite(p.lat)||!Double.isFinite(p.lon)||Math.abs(p.lat)>85||Math.abs(p.lon)>179.8||p.motionState.equals("stationary_estimate"))return null;if(p.motionState.equals("moving"))moving++;
            if(i>0){Point a=points.get(i-1);if(!a.session.equals(p.session)||p.time<=a.time||p.time-a.time>60000)return null;}List<Candidate> c=candidates(graph,p,mode);if(c.isEmpty())return null;cs.add(c);}
        if(moving<3)return null;double[][] cost=new double[n][4];double[][][] links=new double[n][4][4];for(double[][] row:links)for(double[] v:row)Arrays.fill(v,Double.POSITIVE_INFINITY);int[][] previous=new int[n][4];Map<String,Path> cache=new HashMap<>();
        for(int i=0;i<n;i++){Arrays.fill(cost[i],Double.POSITIVE_INFINITY);Arrays.fill(previous[i],-1);Point p=points.get(i);double sigma=Math.max(5,Math.min(18,p.accuracy*.5));
            for(int j=0;j<cs.get(i).size();j++){Candidate b=cs.get(i).get(j);double emission=.5*b.error*b.error/(sigma*sigma);if(i==0){cost[i][j]=emission;continue;}
                Point a=points.get(i-1);double direct=TrackFilter.distance(a,p),dt=(p.time-a.time)/1000.0,max=Math.min(850,(mode==WALK?3.2:mode==BIKE?15:45)*dt+15);max=Math.min(max,direct*3+50);
                for(int k=0;k<cs.get(i-1).size();k++){if(!Double.isFinite(cost[i-1][k]))continue;Path path=between(graph,cs.get(i-1).get(k),b,mode,max,deadline,cache);if(path==null)continue;
                    links[i][k][j]=emission+Math.abs(path.meters-direct)/Math.max(12,(a.accuracy+p.accuracy)*.5);double value=cost[i-1][k]+links[i][k][j];if(value<cost[i][j]){cost[i][j]=value;previous[i][j]=k;}
                }if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())return null;
            }}int best=-1;for(int j=0;j<cs.get(n-1).size();j++)if(Double.isFinite(cost[n-1][j])&&(best<0||cost[n-1][j]<cost[n-1][best]))best=j;if(best<0||cost[n-1][best]>n*2.5)return null;
        // Parallel roads with comparable sequence scores are not resolvable evidence.
        Candidate end=cs.get(n-1).get(best);for(int j=0;j<cs.get(n-1).size();j++){Candidate other=cs.get(n-1).get(j);if(j!=best&&cost[n-1][j]-cost[n-1][best]<1.0&&distance(end.lat,end.lon,other.lat,other.lon)>12)return null;}
        Candidate[] selected=new Candidate[n];for(int i=n-1,j=best;i>=0;i--){selected[i]=cs.get(i).get(j);j=previous[i][j];}
        // Check ambiguity across the complete sequence, including alternate roads
        // that converge again before the final observation.
        double[][] suffix=new double[n][4];for(int i=n-2;i>=0;i--){Arrays.fill(suffix[i],Double.POSITIVE_INFINITY);for(int j=0;j<cs.get(i).size();j++)for(int k=0;k<cs.get(i+1).size();k++)suffix[i][j]=Math.min(suffix[i][j],links[i+1][j][k]+suffix[i+1][k]);}
        for(int i=0;i<n;i++)for(int j=0;j<cs.get(i).size();j++){Candidate other=cs.get(i).get(j);if(cost[i][j]+suffix[i][j]-cost[n-1][best]<1.0&&distance(selected[i].lat,selected[i].lon,other.lat,other.lon)>12)return null;}
        double error=0;for(Candidate c:selected)error+=c.error;if(error/n>25)return null;
        List<double[]> path=new ArrayList<>();Point first=points.get(0),last=points.get(n-1);append(path,first.lat,first.lon);append(path,selected[0].lat,selected[0].lon);double length=0;
        for(int i=1;i<n;i++){Path part=between(graph,selected[i-1],selected[i],mode,850,deadline,cache);if(part==null)return null;length+=part.meters;for(Node node:part.nodes)append(path,node.lat,node.lon);append(path,selected[i].lat,selected[i].lon);if(path.size()>6000)return null;}
        if(length<12)return null;append(path,last.lat,last.lon);return new Route(first.time,last.time,path,n,mode);
    }
    private static void append(List<double[]> path,double lat,double lon){if(path.isEmpty()||distance(path.get(path.size()-1)[0],path.get(path.size()-1)[1],lat,lon)>.02)path.add(new double[]{lat,lon});}
    private RoadMatcher(){}
}
