package com.insight.quantlife.tests;

import android.app.*;
import android.os.Bundle;
import org.json.*;
import com.insight.quantlife.*;
import java.io.*;

/** Disposable emulator only: seed on 1.4.3, then install 1.4.4 with -r and verify. No personal locations. */
public class TimelineBridgeTest extends Instrumentation {
    String phase; int passed;
    @Override public void onCreate(Bundle args) { super.onCreate(args); phase = args == null ? "verify" : args.getString("phase", "verify"); start(); }
    void check(boolean value, String label) throws Exception { if (!value) throw new Exception(label); passed++; Bundle event = new Bundle(); event.putString("stream", "PASS " + label + "\n"); sendStatus(0, event); }
    @Override public void onStart() {
        Bundle out = new Bundle();
        try (TrackStore db = new TrackStore(getTargetContext())) {
            MainHost h = new MainHost(this); h.open(); String date = "2026-10-18";
            if (phase.equals("seed")) {
                check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 0).versionName.equals("1.4.3"), "Seed starts on original signed 1.4.3");
                db.deleteDay(date); long base = TrackStore.dayStart(date) + 12 * 3600000;
                int[] seconds = {0,30,120,150,368,398,500,530}, meters = {0,30,120,150,425,455,550,580};
                for (int i = 0; i < seconds.length; i++) db.add(BeforeGapFixTest.p(base + seconds[i] * 1000, meters[i], 1.4f), i < 6 ? "timeline-fixture" : "timeline-resumed");
                h.js("localStorage.setItem('zhishi-track-inferred-bridges','off')");
                Thread.sleep(1200); h.js("location.reload()"); Thread.sleep(1200);
                check(h.js("localStorage.getItem('zhishi-track-inferred-bridges')").equals("\"off\""), "Old off preference flushed and survives reload");
                JSONObject init = (JSONObject) h.call("init", new JSONObject());
                JSONObject snapshot = new JSONObject().put("init", init).put("raw", db.diagnostics(date).getJSONArray("rawPoints")).put("gpx", db.gpx(date)).put("day", db.day(date));
                try (FileOutputStream f = getTargetContext().openFileOutput("timeline-upgrade-fixture.json", 0)) { f.write(snapshot.toString().getBytes("UTF-8")); }
                out.putString("stream", "PASS synthetic seed with short gap, long gap, pause/resume and old display preference off\n"); finish(Activity.RESULT_OK, out); return;
            }
            check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 0).versionName.equals("1.4.4"), "Timeline upgrade installed");
            StringBuilder text = new StringBuilder(); try (BufferedReader reader = new BufferedReader(new InputStreamReader(getTargetContext().openFileInput("timeline-upgrade-fixture.json"), "UTF-8"))) { String line; while ((line = reader.readLine()) != null) text.append(line); }
            JSONObject snapshot = new JSONObject(text.toString()), init = (JSONObject) h.call("init", new JSONObject()), previous = snapshot.getJSONObject("init");
            check(init.getJSONArray("records").toString().equals(previous.getJSONArray("records").toString()), "Learning and work records retained");
            check(init.getJSONArray("wikiPages").toString().equals(previous.getJSONArray("wikiPages").toString()), "Wiki retained");
            check(init.getJSONObject("settings").toString().equals(previous.getJSONObject("settings").toString()), "Model settings and encrypted key state retained");
            check(db.getReadableDatabase().getVersion() == 3 && db.day(date).getInt("filterVersion") == 4, "No track schema or filter change");
            check(db.diagnostics(date).getJSONArray("rawPoints").toString().equals(snapshot.getJSONArray("raw").toString()), "Raw points unchanged");
            check(db.gpx(date).equals(snapshot.getString("gpx")), "GPX remains byte-for-byte identical");
            JSONArray points = db.day(date).getJSONArray("points");
            check(h.js("LifeTracks.bridges(" + points + ").length===LifeTracks.segments(" + points + ").length-1").equals("true"), "Every time-adjacent segment has a connector");
            check(h.js("LifeTracks.bridges(" + points + ").length").equals("3"), "Short, long and session gaps all connect");
            check(h.js("LifeTracks.summary(" + points + ").meters").equals("120"), "Only 120m recorded legs count; no virtual mileage");
            check(h.js("localStorage.getItem('zhishi-track-inferred-bridges')").equals("\"off\""), "Old off preference present before opening map");
            h.js("window.__timelineStrokes=[];window.__timelineOldStroke=CanvasRenderingContext2D.prototype.stroke;CanvasRenderingContext2D.prototype.stroke=function(){if(this.canvas.id==='track-map')window.__timelineStrokes.push({style:this.strokeStyle,dash:this.getLineDash()});return window.__timelineOldStroke.apply(this,arguments)};document.querySelector('[data-page=tracks]').click()");
            check(h.waitText("每天走过的地方"), "Actual trajectory page opens");
            h.js("document.querySelector('#track-date').value='2026-10-18';document.querySelector('#track-date').dispatchEvent(new Event('change'))");
            check(h.waitText("已连接 3 处采样缺口"), "All chronological connections visible despite previous off preference");
            check(h.js("document.querySelector('#track-show-bridges')===null").equals("true"), "No obsolete toggle can hide connectors");
            check(h.js("window.__timelineStrokes.filter(s=>s.style==='#8754a1'&&s.dash.join(',')==='7,5').length>=3").equals("true"), "Canvas draws all purple dashed connectors");
            check(h.js("window.__timelineStrokes.some(s=>s.style==='#216a54'&&s.dash.length===0)").equals("true"), "Recorded lines remain solid green");
            check(h.waitText("0.12"), "Displayed mileage remains 0.12km");
            h.js("document.querySelector('#track-gap-list').open=true;document.querySelector('[data-track-gap=\"2\"]').click()");
            check(h.js("document.querySelector('#track-selected-point').innerText").contains("按时间补连") && h.js("document.querySelector('#track-selected-point').innerText").contains("未计入里程"), "Pause gap inspection discloses inference and excluded mileage");
            h.js("document.querySelector('#track-fit').click();document.querySelector('#track-map').scrollIntoView({block:'center'})"); Thread.sleep(2200); h.screenshot("timeline-bridges-1.4.4.png");
            h.js("document.querySelector('#track-refresh').click()"); Thread.sleep(500);
            check(h.waitText("已连接 3 处采样缺口"), "Refresh retains all connections");
            h.js("document.querySelector('[data-page=today]').click();document.querySelector('[data-page=tracks]').click()");
            check(h.waitText("已连接 3 处采样缺口"), "Page revisit cannot restore old disabled preference");
            check(db.gpx(date).equals(snapshot.getString("gpx")) && db.diagnostics(date).getJSONArray("rawPoints").toString().equals(snapshot.getJSONArray("raw").toString()), "Painting, refresh and focus do not change GPX or raw data");
            check(!init.has("points"), "Locations still excluded from AI initialization");
            h.js("document.querySelector('[data-page=settings]').click();document.querySelector('[data-action=user-guide]').click()");
            check(h.waitText("第一次使用：三分钟开始记录"), "Offline guide opens after timeline changes");
            h.js("document.querySelectorAll('.user-guide details')[7].open=true");
            check(h.waitText("相邻轨迹段的终点与起点始终按时间"), "Offline help describes the new rule");
            out.putString("stream", "PASS: " + passed + " chronological connector / cover-upgrade checks\n"); finish(Activity.RESULT_OK, out);
        } catch (Throwable e) { out.putString("stream", "FAIL after " + passed + ": " + e + "\n"); finish(Activity.RESULT_CANCELED, out); }
    }
}
