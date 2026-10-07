package com.insight.quantlife.tests;
import com.insight.quantlife.TrackFilter;
import java.io.*;import java.util.*;

/** Read-only stdin replay. Coordinates never appear in output or synthetic fixtures. */
public final class TrackReplay {
    public static void main(String[] args)throws Exception{
        List<TrackFilter.Point> raw=new ArrayList<>();Scanner input=new Scanner(System.in,"UTF-8");
        while(input.hasNextLine()){String[] v=input.nextLine().split("\t",-1);if(v.length!=12)throw new IllegalArgumentException("Expected twelve fields");TrackFilter.Point p=new TrackFilter.Point();p.id=Long.parseLong(v[0]);p.time=Long.parseLong(v[1]);p.lat=Double.parseDouble(v[2]);p.lon=Double.parseDouble(v[3]);p.accuracy=Double.parseDouble(v[4]);p.speed=Double.parseDouble(v[5]);p.speedAccuracy=Double.parseDouble(v[6]);p.session=v[7];p.provider=v[8];p.satellitesUsed=Integer.parseInt(v[9]);p.meanCn0=Double.parseDouble(v[10]);p.gnssAgeMs=Long.parseLong(v[11]);raw.add(p);}
        Map<Long,TrackFilter.Point> originals=new HashMap<>();for(TrackFilter.Point p:raw)originals.put(p.id,p);
        List<TrackFilter.Point> out=TrackFilter.apply(raw);double meters=0,maxShift=0;int segments=0,shifted=0;TrackFilter.Point prev=null;
        for(TrackFilter.Point p:out){boolean continuous=prev!=null&&p.session.equals(prev.session)&&p.time>prev.time&&p.time-prev.time<=TrackFilter.GAP&&!p.breakBefore;if(continuous)meters+=TrackFilter.distance(prev,p);else segments++;double shift=TrackFilter.distance(originals.get(p.id),p);if(shift>.01)shifted++;maxShift=Math.max(maxShift,shift);prev=p;}
        System.out.printf(Locale.US,"{\"rawPoints\":%d,\"shownPoints\":%d,\"segments\":%d,\"meters\":%.1f,\"shiftedPoints\":%d,\"maxShiftMeters\":%.1f}%n",raw.size(),out.size(),segments,meters,shifted,maxShift);
    }
}
