package com.insight.quantlife.tests;
import com.insight.quantlife.TrackFilter;
import com.insight.quantlife.TrackVariants;
import java.io.*;import java.util.*;

/** Read-only stdin replay of sole D. Never outputs coordinates. */
public final class TrackReplay {
    public static void main(String[] args)throws Exception{
        List<TrackFilter.Point> raw=new ArrayList<>();Scanner input=new Scanner(System.in,"UTF-8");
        while(input.hasNextLine()){String[] v=input.nextLine().split("\\t",-1);if(v.length!=12)throw new IllegalArgumentException("Expected twelve fields");TrackFilter.Point p=new TrackFilter.Point();p.id=Long.parseLong(v[0]);p.time=Long.parseLong(v[1]);p.lat=Double.parseDouble(v[2]);p.lon=Double.parseDouble(v[3]);p.accuracy=Double.parseDouble(v[4]);p.speed=Double.parseDouble(v[5]);p.speedAccuracy=Double.parseDouble(v[6]);p.session=v[7];p.provider=v[8];p.satellitesUsed=Integer.parseInt(v[9]);p.meanCn0=Double.parseDouble(v[10]);p.gnssAgeMs=Long.parseLong(v[11]);raw.add(p);}
        Map<Long,TrackFilter.Point> originals=new HashMap<>();for(TrackFilter.Point p:raw)originals.put(p.id,p);
        List<TrackFilter.Point> view=TrackVariants.apply(raw);int shifted=0;for(TrackFilter.Point p:view)if(TrackFilter.distance(originals.get(p.id),p)>.01)shifted++;
        System.out.printf(Locale.US,"{\"filter\":\"weighted65\",\"rawPoints\":%d,\"shownPoints\":%d,\"gaps\":%d,\"referenceMeters\":%.1f,\"adjustedPoints\":%d}%n",raw.size(),view.size(),TrackVariants.gaps(view),TrackVariants.meters(view),shifted);
    }
}
