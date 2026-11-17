package com.insight.quantlife;

import java.util.*;
import com.insight.quantlife.TrackFilter.Point;

/** Read-only view of saved callbacks. No signal, speed, source or motion filtering. */
public final class TrackRaw {
    public static final String ID="raw",LABEL="原始轨迹",COLOR="#087F43";
    public static List<Point> apply(List<Point> saved){
        List<Point> out=new ArrayList<>(saved.size());
        for(Point original:saved){Point p=original.copy();p.reportedAccuracy=original.accuracy;p.reportedSpeed=original.speed;p.filtered=false;p.breakBefore=false;p.motionState="raw";out.add(p);}
        out.sort(Comparator.comparingLong((Point p)->p.time).thenComparingLong(p->p.id));
        return out;
    }
    public static boolean connected(Point a,Point b){return a.session.equals(b.session)&&b.time>a.time&&b.time-a.time<=TrackFilter.GAP;}
    public static double meters(List<Point> points){double sum=0;for(int i=1;i<points.size();i++)if(connected(points.get(i-1),points.get(i)))sum+=TrackFilter.distance(points.get(i-1),points.get(i));return sum;}
    public static int gaps(List<Point> points){int n=0;for(int i=1;i<points.size();i++)if(!connected(points.get(i-1),points.get(i)))n++;return n;}
    private TrackRaw(){}
}
