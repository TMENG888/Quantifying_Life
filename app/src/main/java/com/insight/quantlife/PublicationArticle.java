package com.insight.quantlife;

import android.content.Context;
import org.json.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public final class PublicationArticle {
    public static JSONObject record(Context context,PublicationLibrary library,JSONObject source,String id)throws Exception{
        JSONObject common=source.getJSONObject("common");JSONArray originals=common.getJSONArray("illustrations"),images=new JSONArray();JSONObject imageMap=new JSONObject();
        if(originals.length()>100)throw new Exception("每篇创作记录最多100张配图");
        Map<Integer,List<String>> positions=new TreeMap<>();Map<String,String> replacements=new HashMap<>();Set<String> stored=new HashSet<>();
        for(int i=0;i<originals.length();i++){
            JSONObject original=originals.getJSONObject(i),image=RecordMedia.fromBytes(context,Files.readAllBytes(library.assetFile(source.getString("id"),original.getString("sha256")).toPath()),original.optString("title","正文配图"));
            imageMap.put(image.getString("id"),original.optString("sourceImageId").isEmpty()?"legacy-image:"+(i+1):original.optString("sourceImageId"));
            if(stored.add(image.getString("id")))images.put(image);
            String token="!["+image.optString("name").replaceAll("[\\[\\]\\r\\n]","")+"](zhishi-media:"+image.getString("id")+")";
            positions.computeIfAbsent(original.getInt("afterParagraph"),key->new ArrayList<>()).add(token);
            if(!original.optString("sourceImageId").isEmpty())replacements.put(original.getString("sourceImageId"),token);
        }
        String body;
        if(source.has("articleMarkdown")){
            body=source.getString("articleMarkdown");
            java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("!\\[[^\\]\\n]*\\]\\(([^)]+)\\)").matcher(body);StringBuffer result=new StringBuffer();
            while(matcher.find()){String replacement=replacements.get(matcher.group(1));if(replacement==null)throw new Exception("原始图文中的配图没有匹配资料清单");matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(replacement));}
            matcher.appendTail(result);body=result.toString();
        }else{
            String[] paragraphs=common.getString("body").trim().split("\\n[\\t \\u3000]*\\n+");
            for(Integer position:positions.keySet())if(position<0||position>paragraphs.length)throw new Exception("配图位置超出文章段落");
            List<String> blocks=new ArrayList<>();if(positions.containsKey(0))blocks.addAll(positions.get(0));
            for(int i=0;i<paragraphs.length;i++){blocks.add(paragraphs[i]);if(positions.containsKey(i+1))blocks.addAll(positions.get(i+1));}
            body=String.join("\n\n",blocks);
        }
        long time=source.getLong("importedAt");String date=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(time));
        return new JSONObject().put("id",id).put("type","creation").put("title",source.getString("title")).put("note",body).put("images",images)
            .put("date",date).put("createdAt",time).put("durationMinutes",0).put("tags",new JSONArray()).put("creatorPlatform","").put("creationStatus","draft").put("publishedUrl","")
            .put("knowflowImageMap",imageMap).put("knowflowRevision",source.optJSONObject("revision")).put("source","knowflow-zip").put("publicationPackageId",source.getString("id")).put("knowflowCreationId",source.optString("sourceId"));
    }
    private PublicationArticle(){}
}
