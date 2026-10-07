package com.insight.quantlife.tests;

import android.app.*;
import android.os.Bundle;
import android.view.WindowManager;
import org.json.*;

/** Run only on a disposable emulator. Seed on 1.4.2, verify after installing 1.4.3 with -r. */
public class HelpScreenshotTest extends Instrumentation {
    int passed; String phase;
    void check(boolean value, String message) throws Exception {
        if (!value) throw new Exception(message);
        passed++; Bundle event = new Bundle(); event.putString("stream", "PASS " + message + "\n"); sendStatus(0, event);
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); phase = args == null ? "verify" : args.getString("phase", "verify"); start(); }
    @Override public void onStart() {
        Bundle out = new Bundle();
        try {
            MainHost h = new MainHost(this); h.open();
            if (phase.equals("seed")) {
                check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 0).versionName.equals("1.4.2"), "Seed starts on previous signed version");
                JSONObject record = new JSONObject().put("id", "qa-help-upgrade-persistent").put("type", "learning").put("date", "2026-10-07").put("title", "Synthetic upgrade note").put("platform", "QA only").put("contentType", "article").put("durationMinutes", 25).put("note", "Synthetic fixture, no personal data").put("tags", new JSONArray()).put("source", "manual");
                h.call("saveRecord", new JSONObject().put("record", record).put("allowDuplicate", true));
                h.call("saveSettings", new JSONObject().put("settings", new JSONObject().put("dailyLearningGoal", 37).put("profile", new JSONObject().put("id", "custom").put("label", "Local test only").put("baseUrl", "http://127.0.0.1:8790/v1").put("model", "dummy-model").put("apiKey", "local-test-only-not-a-real-key").put("protocol", "chat").put("allowHttp", true))));
                out.putString("stream", "PASS seed for signed upgrade\n"); finish(Activity.RESULT_OK, out); return;
            }
            check(getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 0).versionName.equals("1.4.3"), "New version installed");
            JSONObject init = (JSONObject) h.call("init", new JSONObject());
            boolean retained = false; JSONArray records = init.getJSONArray("records");
            for (int i = 0; i < records.length(); i++) { JSONObject r = records.getJSONObject(i); if (r.getString("id").equals("qa-help-upgrade-persistent") && r.getInt("durationMinutes") == 25) retained = true; }
            check(retained, "Existing learning record survives upgrade");
            check(init.getJSONObject("settings").getInt("dailyLearningGoal") == 37, "Learning goal survives upgrade");
            check(init.getJSONObject("settings").getJSONObject("profiles").getJSONObject("custom").getBoolean("keyConfigured"), "Encrypted synthetic credential remains configured");
            final boolean[] allowed = { false };
            runOnMainSync(() -> allowed[0] = (h.activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) == 0);
            check(allowed[0], "Window allows system screenshots");
            h.js("document.querySelector('[data-page=settings]').click()"); check(h.waitText("使用说明 · 新手入门与常见问题"), "Settings shows friendly guide entry");
            check(h.js("document.querySelector('#api-key').type==='password' && document.querySelector('#api-key').value===''").equals("true"), "Key stays masked and is not echoed");
            h.js("window.__guideFetch=[];window.__guideOldFetch=window.fetch;window.fetch=function(url,...args){window.__guideFetch.push(String(url));return window.__guideOldFetch.call(window,url,...args)};document.querySelector('[data-action=user-guide]').click()");
            check(h.waitText("第一次使用：三分钟开始记录"), "Offline guide opens in actual WebView");
            check(h.js("document.querySelector('#editor').open && document.querySelectorAll('.user-guide details').length===10").equals("true"), "All ten help sections render in dialog");
            check(h.js("window.__guideFetch.length===1 && window.__guideFetch[0]==='user-guide.html'").equals("true"), "Guide loads only bundled asset, no network resource");
            h.js("(()=>{const section=document.querySelectorAll('.user-guide details')[9],dialog=document.querySelector('#editor');section.open=true;dialog.scrollTop+=section.getBoundingClientRect().top-dialog.getBoundingClientRect().top-80;})()");
            check(h.js("document.querySelectorAll('.user-guide details')[9].open").equals("true"), "Screenshot and privacy section expands and scrolls");
            check(h.js("(()=>{const r=document.querySelectorAll('.user-guide details')[9].getBoundingClientRect();return r.top>=0 && r.top<innerHeight && document.querySelector('#editor').open;})()").equals("true"), "Scrolled help stays visible on screen");
            // Allow the headless software renderer to present the updated WebView frame.
            Thread.sleep(2000);
            h.screenshot("help-privacy-1.4.3.png"); check(true, "System screenshot saved for visual inspection");
            h.js("window.appBack()"); check(h.js("!document.querySelector('#editor').open && !!document.querySelector('[data-action=user-guide]')").equals("true"), "Back closes help and preserves settings page");
            h.js("document.querySelector('[data-action=user-guide]').click()"); check(h.waitText("第一次使用：三分钟开始记录"), "Guide reopens after closing");
            h.js("document.querySelector('#editor').scrollTop=0");
            h.screenshot("help-start-1.4.3.png");
            check(h.js("document.querySelector('.user-guide').scrollWidth<=document.querySelector('.user-guide').clientWidth+1").equals("true"), "Guide has no horizontal text overflow");
            h.js("document.querySelector('#close-editor').click()"); check(h.js("!document.querySelector('#editor').open").equals("true"), "Close button dismisses help");
            check(((JSONObject) h.call("init", new JSONObject())).getJSONArray("records").length() == records.length(), "Reading help does not alter records");
            out.putString("stream", "PASS: " + passed + " guide/screenshot/upgrade checks\n"); finish(Activity.RESULT_OK, out);
        } catch (Throwable e) { out.putString("stream", "FAIL after " + passed + ": " + e + "\n"); finish(Activity.RESULT_CANCELED, out); }
    }
}
