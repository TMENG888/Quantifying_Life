package com.insight.quantlife;

import org.json.*;
import java.text.*;
import java.util.*;
import com.insight.quantlife.TrackFilter.Point;

/** Read-only time views of the full processed day, before display simplification. */
public final class TrackTime {
    private static long time(String date,String text,boolean end)throws Exception{
        if(end&&"00:00".equals(text))return TrackStore.dayEnd(date);
        if(text==null||!text.matches("\\d{2}:\\d{2}(:\\d{2})?"))throw new Exception("请选择有效时间");
        SimpleDateFormat f=new SimpleDateFormat(text.length()==5?"yyyy-MM-dd HH:mm":"yyyy-MM-dd HH:mm:ss",Locale.US);
        f.setLenient(false);String value=date+" "+text;ParsePosition pos=new ParsePosition(0);Date parsed=f.parse(value,pos);
        if(parsed==null||pos.getIndex()!=value.length())throw new Exception("时间无效");
        long t=parsed.getTime();if(t<TrackStore.dayStart(date)||t>=TrackStore.dayEnd(date))throw new Exception("时间不在所选日期内");return t;
    }
    public static JSONObject query(JSONObject day,JSONObject request)throws Exception{
        String date=day.getString("date"),mode=request.getString("mode");
        JSONArray all=day.getJSONArray("points"),shown=new JSONArray();
        JSONObject out=new JSONObject().put("date",date).put("mode",mode).put("filter",day.getJSONObject("filter")).put("truncated",day.optBoolean("truncated"));
        if(mode.equals("range")){
            long start=time(date,request.getString("start"),false),end=time(date,request.getString("end"),true);
            if(end<=start)throw new Exception("结束时间需晚于开始时间；00:00 表示次日零点");
            List<Point> points=new ArrayList<>();IdentityHashMap<Point,JSONObject> originals=new IdentityHashMap<>();
            for(int i=0;i<all.length();i++){JSONObject o=all.getJSONObject(i);long t=o.getLong("time");if(t<start||t>=end)continue;
                Point p=new Point();p.time=t;p.lat=o.getDouble("lat");p.lon=o.getDouble("lon");p.accuracy=o.getDouble("accuracy");p.session=o.getString("session");p.breakBefore=o.optBoolean("breakBefore");p.motionState=o.optString("motionState","");points.add(p);originals.put(p,o);
            }
            List<Point> display=TrackDisplay.simplify(points);for(Point p:display)shown.put(originals.get(p));
            out.put("start",start).put("end",end).put("processedPoints",points.size()).put("estimatedMeters",Math.round(TrackVariants.meters(points))).put("processedGaps",TrackVariants.gaps(points)).put("displaySimplified",display.size()<points.size());
        }else if(mode.equals("moment")){
            long target=time(date,request.getString("at"),false);JSONObject before=null,after=null,nearest=null;
            // Uncertain held coordinates must not become a confident historical location.
            for(int i=0;i<all.length();i++){JSONObject p=all.getJSONObject(i);if("uncertain".equals(p.optString("motionState")))continue;long t=p.getLong("time");
                if(t<=target&&(before==null||t>before.getLong("time")))before=p;
                if(t>=target&&(after==null||t<after.getLong("time")))after=p;
                if(nearest==null||Math.abs(t-target)<Math.abs(nearest.getLong("time")-target))nearest=p;
            }
            boolean found=nearest!=null&&Math.abs(nearest.getLong("time")-target)<=60000;
            if(found)shown.put(nearest);
            out.put("target",target).put("match",found?nearest:JSONObject.NULL).put("before",before==null?JSONObject.NULL:before).put("after",after==null?JSONObject.NULL:after).put("toleranceSeconds",60).put("processedPoints",shown.length()).put("estimatedMeters",0);
        }else throw new Exception("时间查看方式无效");
        return out.put("points",shown);
    }
    private TrackTime(){}
}
