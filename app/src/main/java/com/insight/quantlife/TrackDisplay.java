package com.insight.quantlife;
import java.util.*;
import com.insight.quantlife.TrackFilter.Point;
/** Display-only simplification in <=15 second windows. Mileage/GPX use the full processed list. */
public final class TrackDisplay {
    public static List<Point> simplify(List<Point> points){if(points.size()<=20000)return points;List<Point> out=new ArrayList<>();int start=0;
        while(start<points.size()){int end=start;while(end+1<points.size()&&TrackVariants.connected(points.get(end),points.get(end+1))&&points.get(end+1).time-points.get(start).time<=15000&&end-start<100)end++;
            boolean[] keep=new boolean[end-start+1];keep[0]=keep[keep.length-1]=true;reduce(points,start,end,start,keep);for(int i=start;i<=end;i++)if(keep[i-start])out.add(points.get(i));start=end+1;
        }return out;}
    private static void reduce(List<Point> p,int a,int b,int offset,boolean[] keep){if(b-a<=1)return;Point x=p.get(a),y=p.get(b);double lon=Math.cos(Math.toRadians(x.lat)),dx=(y.lon-x.lon)*lon,dy=y.lat-x.lat,d2=dx*dx+dy*dy,max=0;int index=-1;
        for(int i=a+1;i<b;i++){Point z=p.get(i);double zx=(z.lon-x.lon)*lon,zy=z.lat-x.lat,t=d2==0?0:Math.max(0,Math.min(1,(zx*dx+zy*dy)/d2));double d=Math.hypot(zx-t*dx,zy-t*dy)*111195;if(d>max){max=d;index=i;}}
        if(max>1&&index>a){keep[index-offset]=true;reduce(p,a,index,offset,keep);reduce(p,index,b,offset,keep);}
    }
    private TrackDisplay(){}
}
