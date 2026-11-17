package com.insight.quantlife;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.JsonWriter;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-only, full-history analysis snapshot. No model requests, secrets, images or chats. */
public final class LearningAnalysisExport {
    private LearningAnalysisExport(){}
    public interface Progress { void update(long completed,long total); }
    private static final String TYPES="'learning','work','work_note','idea','experience'";
    private static final String[] RECORD_FIELDS={"id","type","date","title","note","tags","platform","contentType","url","durationMinutes","workStart","workEnd","occurredAt","createdAt","updatedAt","source","titleOrigin","masteryLevel","evidenceType","evidenceNote","wikiTopics","wikiTopicVersion","wikiDomains","wikiDomainVersion","wikiClassificationVersion","workLogMigrationSource","workNoteId"};
    private static final String[] PAGE_FIELDS={"id","topic","summary","aliases","sourceIds","indexSourceIds","sourceVersions","fingerprints","searchKeywords","updatedAt","compilerVersion","readChunks","completedBatches","partial","notesTruncated","compiledFrom"};
    private static final String[] REPORT_FIELDS={"id","taskKey","signature","pageIds","pageVersions","sourceVersions","analyzerVersion","updatedAt"};
    private static JSONObject pick(JSONObject raw,String...fields)throws Exception{
        JSONObject out=new JSONObject();for(String key:fields)if(raw.has(key))out.put(key,raw.get(key));return out;
    }
    private static JSONArray objects(JSONArray values,String...fields)throws Exception{
        JSONArray result=new JSONArray();if(values!=null)for(int i=0;i<values.length();i++)result.put(pick(values.getJSONObject(i),fields));return result;
    }
    private static void child(JSONObject out,JSONObject raw,String name,String...fields)throws Exception{
        JSONObject value=raw.optJSONObject(name);if(value!=null)out.put(name,pick(value,fields));
    }
    private static JSONObject record(JSONObject r)throws Exception{
        JSONObject out=pick(r,RECORD_FIELDS);
        child(out,r,"quantLabels","schemaVersion","model","sourceVersion","sourceHash","complete","labeledAt","chunks","primary","topics","domains","evidenceLimit");
        if(out.has("quantLabels"))out.getJSONObject("quantLabels").put("evidence",objects(r.getJSONObject("quantLabels").optJSONArray("evidence"),"kind","quote","claim","actor","status","start","end"));
        child(out,r,"informationUnits","schemaVersion","model","sourceVersion","sourceHash","complete","analyzedAt","characters","count","density","countingRule","paragraphs","reusedBlocks","modelCalls","blockSize","previousCount","previousSourceVersion");
        if(out.has("informationUnits")){
            JSONArray source=r.getJSONObject("informationUnits").optJSONArray("units"),clean=new JSONArray();
            if(source!=null)for(int i=0;i<source.length();i++){JSONObject u=source.getJSONObject(i),v=pick(u,"id","text");v.put("sources",objects(u.optJSONArray("sources"),"start","end","quote"));clean.put(v);}
            out.getJSONObject("informationUnits").put("units",clean);
        }
        if(r.has("breaks"))out.put("breaks",objects(r.optJSONArray("breaks"),"start","end"));
        child(out,r,"studyTiming","id","startedAt","endedAt","durationMs","recoveredAfterReboot","limitReached");
        child(out,r,"feishuSource","documentId","url","importedAt","documentCreatedDate","documentCreatedAt");
        child(out,r,"feishuCopy","documentId","url","importedAt","documentCreatedDate","documentCreatedAt");
        child(out,r,"linkEvidence","url","title","status","readMethod","contentStatus","message");
        JSONArray images=r.optJSONArray("images");out.put("imageCount",images==null?0:images.length());
        // Never interpret zero/missing time, note length or a Wiki connection as cognitive gain.
        JSONObject quality=new JSONObject().put("noteCodePoints",r.optString("note").codePointCount(0,r.optString("note").length()))
            .put("hasRecordedPositiveLearningMinutes",r.optString("type").equals("learning")&&r.optDouble("durationMinutes",0)>0)
            .put("hasUserTags",r.optJSONArray("tags")!=null&&r.optJSONArray("tags").length()>0)
            .put("domainCurrent",r.optInt("wikiClassificationVersion")==WikiClassificationService.currentVersion()&&r.optLong("wikiDomainVersion",-1)==r.optLong("updatedAt")&&r.optJSONArray("wikiDomains")!=null&&r.optJSONArray("wikiDomains").length()>0)
            .put("topicCurrent",r.optInt("wikiClassificationVersion")==WikiClassificationService.currentVersion()&&r.optLong("wikiTopicVersion",-1)==r.optLong("updatedAt")&&r.optJSONArray("wikiTopics")!=null&&r.optJSONArray("wikiTopics").length()>0);
        return out.put("analysisFields",quality);
    }
    private static JSONObject page(JSONObject raw)throws Exception{
        JSONObject out=pick(raw,PAGE_FIELDS);out.put("concepts",objects(raw.optJSONArray("concepts"),"title","description","sourceIds"));return out;
    }
    private static JSONObject report(JSONObject raw)throws Exception{
        JSONObject out=pick(raw,REPORT_FIELDS);JSONArray findings=new JSONArray(),source=raw.optJSONArray("findings");
        if(source!=null)for(int i=0;i<source.length();i++){JSONObject f=source.getJSONObject(i),item=pick(f,"id","kind","title","reason","question","nextStep");JSONArray sides=new JSONArray(),rawSides=f.optJSONArray("sides");if(rawSides!=null)for(int j=0;j<rawSides.length();j++){JSONObject side=rawSides.getJSONObject(j),clean=pick(side,"pageId","claim");clean.put("evidence",objects(side.optJSONArray("evidence"),"recordId","quote"));sides.put(clean);}findings.put(item.put("sides",sides));}
        return out.put("findings",findings);
    }
    private static void write(JsonWriter writer,Object value)throws Exception{
        if(value==null||value==JSONObject.NULL)writer.nullValue();
        else if(value instanceof JSONObject){writer.beginObject();Iterator<String> keys=((JSONObject)value).keys();while(keys.hasNext()){String key=keys.next();writer.name(key);write(writer,((JSONObject)value).get(key));}writer.endObject();}
        else if(value instanceof JSONArray){writer.beginArray();JSONArray array=(JSONArray)value;for(int i=0;i<array.length();i++)write(writer,array.get(i));writer.endArray();}
        else if(value instanceof Boolean)writer.value((Boolean)value);
        else if(value instanceof Number)writer.value((Number)value);
        else writer.value(value.toString());
    }
    private static boolean table(SQLiteDatabase db,String name){try(Cursor c=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{name})){return c.moveToFirst();}}
    private static String[] dateBounds(SQLiteDatabase db){try(Cursor c=db.rawQuery("SELECT MIN(date),MAX(date),COUNT(*) FROM records WHERE type IN ("+TYPES+")",null)){c.moveToFirst();return new String[]{c.getString(0),c.getString(1),c.getString(2)};}}
    private static JSONObject dictionary()throws Exception{
        return new JSONObject()
            .put("scope","所有日期的已保存学习、工作班次、工作日志、经验和灵感；不含未提交草稿。Wiki是模型整理结果，必须回到记录原文核查。")
            .put("records.note","完整保存的笔记原文（Markdown或导入内容），不截断；图片只保留原有占位符，不能由此分析图片内的信息。来源可能是摘录、飞书导入、AI或个人撰写，不能全部视为个人理解。")
            .put("records.date","记录归属日期；飞书记录可能采用文档创建日期，不等于实际学习时刻。createdAt、updatedAt表示本机写入与最后修改，不是学习时间。")
            .put("records.durationMinutes","原始分钟数。缺失和0都不能自动视为实际没有学习；只把明确正时长用于投入比较。工作日志与随笔不记录工时。")
            .put("records.studyTiming","开始、结束为Unix毫秒；durationMs为扣除暂停后的有效计时。起止跨度包含暂停，不能替代有效时长。")
            .put("records.workStart/workEnd/breaks","本机时区的上班、下班及休息时间。净工时需按班次扣除休息，跨日切分；未下班、未结束休息仍进行中，统计只能截至本次exportedAt。不能将班次时长分摊到每篇工作日志。")
            .put("records.analysisFields","noteCodePoints是原文Unicode码点数，包含Markdown和图片占位文本，不是字数、信息量或认知分数。topicCurrent/domainCurrent只检查版本与字段，不能保证语义正确。")
            .put("records.quantLabels","qwen3.7-flash的版本化派生标签；完整结果须与sourceVersion/sourceHash核对。primary为内容类型，evidence中的start/end是原文UTF-16偏移、quote为原文。actor=self仅表示本人记录，measured仍为记录自述，不等于独立核验；计划和目标不可算已实现结果。")
            .put("records.informationUnits","DeepSeek提取并在单篇内部去重的信息单元，属于模型估计。核对sourceVersion/sourceHash；sources为UTF-16原文定位。characters排除格式、空白、URL与图片占位，density=count/characters*1000。跨篇重复仍计数；只有有效学习分钟与已分析记录匹配时计算每小时单元数，不能直接解释为认知进步。")
            .put("records.wikiTopics/wikiDomains","Wiki派生主题与宽领域。需核对版本；旧分类可能过期，不能将待分类算作其他主题。")
            .put("records.masteryLevel/evidenceType/evidenceNote","历史自评或实践/测验证据字段。缺失、unspecified、none不等于能力为零；内容须人工核对，不能由阅读篇数推定熟练。")
            .put("wikiPages/wikiInsights","摘要、概念、跨主题联系、冲突和知识缺口，均为模型派生内容。sourceIds/sourceVersions和原文引句用于核查；历史结果可能失效，不代表新增认知。")
            .put("sampling","全部历史、无抽样。记录缺失的日期不等于当天投入为零；同内容跨日复读可有多条记录，需分析时区分记录数和唯一内容数。")
            .put("filePurpose","用于协助选择学习/工作指标与图表；不是备份恢复文件，不做AI评分，不上传。")
            .put("excluded",new JSONArray(Arrays.asList("模型配置及密钥存储","聊天消息","定位与照片位置","图片文件及媒体内容","未提交草稿","功能使用数据","禅定记录")));
    }
    /** Streaming consistent snapshot. This method never closes caller's database or output. */
    public static JSONObject write(SQLiteDatabase db,OutputStream output,String version,Progress progress)throws Exception{
        if(db.inTransaction())throw new IllegalStateException("导出需要独立的数据快照，请稍后重试");
        db.beginTransactionNonExclusive();
        try{
            String[] bounds=dateBounds(db);long total=Long.parseLong(bounds[2]),done=0,exportedAt=System.currentTimeMillis();
            JSONObject summary=new JSONObject().put("recordCount",total).put("dateStart",bounds[0]==null?JSONObject.NULL:bounds[0]).put("dateEnd",bounds[1]==null?JSONObject.NULL:bounds[1]);
            JSONObject counts=new JSONObject();long learning=0,positive=0,missing=0,zero=0,noteEmpty=0,tagsEmpty=0,current=0;
            JsonWriter writer=new JsonWriter(new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8),65536));writer.setIndent("  ");
            writer.beginObject();writer.name("format").value("zhishi-analysis");writer.name("schemaVersion").value(1);writer.name("appVersion").value(version);writer.name("classificationVersion").value(WikiClassificationService.currentVersion());
            writer.name("exportedAt").value(exportedAt);writer.name("timeZone").value(TimeZone.getDefault().getID());writer.name("utcOffsetMinutes").value(TimeZone.getDefault().getOffset(exportedAt)/60000);
            writer.name("scope").value("all-history");writer.name("fieldGuide");write(writer,dictionary());writer.name("records");writer.beginArray();progress.update(0,total);
            try(Cursor c=db.rawQuery("SELECT payload FROM records WHERE type IN ("+TYPES+") ORDER BY date,id",null)){
                while(c.moveToNext()){
                    JSONObject raw=new JSONObject(c.getString(0)),item=record(raw);write(writer,item);done++;String type=raw.optString("type");counts.put(type,counts.optInt(type)+1);
                    if(type.equals("learning")){learning++;if(!raw.has("durationMinutes")||raw.isNull("durationMinutes"))missing++;else if(raw.optDouble("durationMinutes",0)>0)positive++;else zero++;}
                    if(raw.optString("note").trim().isEmpty())noteEmpty++;if(!item.getJSONObject("analysisFields").getBoolean("hasUserTags"))tagsEmpty++;
                    if(item.getJSONObject("analysisFields").getBoolean("domainCurrent"))current++;
                    if(done%100==0||done==total)progress.update(done,total);
                }
            }writer.endArray();
            summary.put("byType",counts).put("learningRecords",learning).put("learningWithPositiveMinutes",positive).put("learningWithMissingMinutes",missing).put("learningWithZeroMinutes",zero).put("emptyNotes",noteEmpty).put("withoutUserTags",tagsEmpty).put("currentDomainClassifications",current);
            long pages=0,reports=0,reviews=0;
            writer.name("wikiPages");writer.beginArray();if(table(db,"wiki_pages"))try(Cursor c=db.rawQuery("SELECT payload FROM wiki_pages ORDER BY id",null)){while(c.moveToNext()){write(writer,page(new JSONObject(c.getString(0))));pages++;}}writer.endArray();
            writer.name("wikiInsights");writer.beginObject();
            writer.name("reports");writer.beginArray();if(table(db,"wiki_insights"))try(Cursor c=db.rawQuery("SELECT payload FROM wiki_insights ORDER BY id",null)){while(c.moveToNext()){write(writer,report(new JSONObject(c.getString(0))));reports++;}}writer.endArray();
            writer.name("reviews");writer.beginArray();if(table(db,"wiki_insight_reviews"))try(Cursor c=db.rawQuery("SELECT payload FROM wiki_insight_reviews ORDER BY id",null)){while(c.moveToNext()){write(writer,pick(new JSONObject(c.getString(0)),"id","reportId","findingId","signature","state","note","updatedAt"));reviews++;}}writer.endArray();writer.endObject();
            summary.put("wikiPages",pages).put("wikiReports",reports).put("wikiReviews",reviews);writer.name("summary");write(writer,summary);writer.endObject();writer.flush();
            db.setTransactionSuccessful();return summary;
        }finally{db.endTransaction();}
    }
}
