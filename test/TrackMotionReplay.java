package com.insight.quantlife.tests;
import com.insight.quantlife.*;import com.insight.quantlife.TrackFilter.Point;
import java.util.*;
/** Private stdin only; aggregate stdout only. Never packages user locations. */
public final class TrackMotionReplay {
 public static void main(String[] args){List<Point> raw=new ArrayList<>();Scanner s=new Scanner(System.in,"UTF-8");
  while(s.hasNextLine()){String[] v=s.nextLine().split("\\t",-1);Point p=new Point();p.id=Long.parseLong(v[0]);p.time=Long.parseLong(v[1]);p.lat=Double.parseDouble(v[2]);p.lon=Double.parseDouble(v[3]);p.accuracy=Double.parseDouble(v[4]);p.speed=Double.parseDouble(v[5]);p.speedAccuracy=Double.parseDouble(v[6]);p.session=v[7];p.provider=v[8];p.eligible=Boolean.parseBoolean(v[9]);p.motionRms=Double.parseDouble(v[10]);p.motionAgeMs=Long.parseLong(v[11]);p.gpsAgeMs=Long.parseLong(v[12]);raw.add(p);}
  for(String mode:new String[]{"D","stopMove"}){long start=System.nanoTime();List<Point> a=mode.equals("D")?TrackVariants.apply(raw):TrackFusion.apply(raw);int moving=0,stopped=0;for(Point p:a){if(p.motionState.equals("moving"))moving++;if(p.motionState.equals("stationary_estimate"))stopped++;}
   System.out.printf(Locale.US,"%s raw=%d processed=%d moving=%d stopEstimate=%d gaps=%d meters=%.1f timeMs=%d%n",mode,raw.size(),a.size(),moving,stopped,TrackVariants.gaps(a),TrackVariants.meters(a),(System.nanoTime()-start)/1000000);
   for(int hour=0;hour<24;hour++){List<Point> h=new ArrayList<>();for(Point p:a)if((p.time/3600000+8)%24==hour)h.add(p);if(!h.isEmpty())System.out.printf(Locale.US,"hour=%02d points=%d meters=%.1f gaps=%d%n",hour,h.size(),TrackVariants.meters(h),TrackVariants.gaps(h));}
  }
 }
}
