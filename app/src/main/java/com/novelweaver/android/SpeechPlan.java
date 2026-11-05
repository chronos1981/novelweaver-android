package com.novelweaver.android;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Pure speech planner shared by car and phone playback. Never modifies a project. */
final class SpeechPlan {
    static final Pattern QUOTES=Pattern.compile("\"[^\"]*\"|“[^”]*”|‘(?:[^’]|’(?=\\p{L}))*’|(?<![\\p{L}\\p{N}])'(?=\\S)(?:[^']|'(?=\\p{L}))*'(?![\\p{L}\\p{N}])");
    static final class Unit {
        final String text,pid,chapterId,chapter,bookId,book,voice; final float rate,pitch;
        Unit(String text,JSONObject p,JSONObject c,String book,JSONObject s){this.text=text;pid=p.optString("Id");chapterId=c.optString("Id");chapter=c.optString("Title","Chapter");bookId=c.optString("BookId");this.book=book;voice=s.optString("Voice","");rate=number(s,"Rate",1);pitch=number(s,"Pitch",1);}
        JSONObject request(String key) throws JSONException {return new JSONObject().put("text",text).put("key",key).put("voice",voice).put("rate",rate).put("pitch",pitch);}
    }
    static float number(JSONObject s,String key,double fallback){double d=s.optDouble(key,fallback);return (float)(Double.isFinite(d)?Math.max(.5,Math.min(2,d)):fallback);}
    static JSONObject object(JSONObject p,String key){JSONObject s=p==null?null:p.optJSONObject(key);return s==null?new JSONObject():s;}
    static JSONObject merge(JSONObject a,JSONObject b) throws JSONException {JSONObject r=new JSONObject(a.toString());for(Iterator<String> i=b.keys();i.hasNext();){String k=i.next();if(k.equals("Voice")&&b.optString(k).isEmpty())continue;r.put(k,b.get(k));}return r;}
    static JSONObject resolve(JSONObject settings,JSONArray profiles) throws JSONException {
        JSONObject r=new JSONObject();String id=settings.optString("ProfileId");
        for(int i=0;i<profiles.length();i++){JSONObject p=profiles.optJSONObject(i);if(p!=null&&!id.isEmpty()&&id.equals(p.optString("Id"))){r=merge(r,p);break;}}
        return merge(r,settings);
    }
    static JSONObject settings(JSONObject project,JSONObject p,JSONObject listen,String role) throws JSONException {
        JSONArray profiles=project.optJSONArray("SpeechProfiles");if(profiles==null)profiles=new JSONArray();
        JSONObject tag=object(p,"Speech"),defaults=object(project,"SpeechDefaults"),imported=object(tag,"ImportedDefaults");String key=role.equals("dialogue")?"Dialogue":"Narration";
        JSONObject fallback=new JSONObject().put("Voice",listen.optString("voice","")).put("Rate",number(listen,"rate",1)).put("Pitch",1);
        JSONObject r=merge(fallback,resolve(imported.has(key)?object(imported,key):object(defaults,key),profiles));
        if(role.equals(tag.optString("Role","narration")))r=merge(r,resolve(tag,profiles));
        return merge(r,resolve(object(tag,key),profiles));
    }
    static String pronounce(String text,JSONArray rules){
        if(rules==null)return text;
        for(int i=0;i<Math.min(rules.length(),250);i++){JSONObject r=rules.optJSONObject(i);if(r==null)continue;String word=r.optString("word"),said=r.optString("said");if(word.isEmpty()||said.isEmpty())continue;text=Pattern.compile("(?<![\\p{L}\\p{N}])"+Pattern.quote(word)+"(?![\\p{L}\\p{N}])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE).matcher(text).replaceAll(Matcher.quoteReplacement(said));}
        return text;
    }
    static List<Unit> build(JSONObject project,String chapterId,boolean wholeBook,JSONObject listen) throws JSONException {
        List<Unit> units=new ArrayList<>();JSONArray chapters=project.optJSONArray("Chapters");if(chapters==null)return units;String bookId="";boolean found=false;
        for(int i=0;i<chapters.length();i++){JSONObject c=chapters.optJSONObject(i);if(c!=null&&chapterId.equals(c.optString("Id"))){bookId=c.optString("BookId");found=true;}}
        if(!found)return units;
        for(int i=0;i<chapters.length();i++){
            JSONObject c=chapters.optJSONObject(i);if(c==null||!(wholeBook?bookId.equals(c.optString("BookId")):chapterId.equals(c.optString("Id"))))continue;
            String book="Book";JSONArray books=project.optJSONArray("Books");if(books!=null)for(int j=0;j<books.length();j++){JSONObject b=books.optJSONObject(j);if(b!=null&&c.optString("BookId").equals(b.optString("Id")))book=b.optString("Title","Book");}
            JSONArray paragraphs=c.optJSONArray("Paragraphs");if(paragraphs==null)continue;
            for(int j=0;j<paragraphs.length();j++){
                JSONObject p=paragraphs.optJSONObject(j);if(p==null)continue;String text=p.optString("Text");Matcher m=QUOTES.matcher(text);int end=0;
                while(m.find()){add(units,text.substring(end,m.start()),project,p,c,book,listen,"narration");add(units,m.group(),project,p,c,book,listen,"dialogue");end=m.end();}
                add(units,text.substring(end),project,p,c,book,listen,"narration");
            }
        }
        return units;
    }
    private static void add(List<Unit> units,String text,JSONObject project,JSONObject p,JSONObject c,String book,JSONObject listen,String role) throws JSONException {
        text=pronounce(text,listen.optJSONArray("rules"));JSONObject options=settings(project,p,listen,role);
        // Android TTS accepts at most 4000 characters. Keep a margin and never split surrogate pairs.
        for(int start=0;start<text.length();){int end=Math.min(start+3000,text.length());if(end<text.length()){int space=text.lastIndexOf(' ',end);if(space>start+1500)end=space+1;else if(Character.isHighSurrogate(text.charAt(end-1)))end--;}
            String part=text.substring(start,end);if(!part.trim().isEmpty())units.add(new Unit(part,p,c,book,options));start=end;}
    }
    static int findParagraph(List<Unit> units,String pid){for(int i=0;i<units.size();i++)if(units.get(i).pid.equals(pid))return i;return 0;}
    static int adjacentChapter(List<Unit> units,int index,int direction){if(units.isEmpty()||index<0||index>=units.size())return -1;String current=units.get(index).chapterId;int i=index;while(i>=0&&i<units.size()&&units.get(i).chapterId.equals(current))i+=direction;if(i<0||i>=units.size())return -1;if(direction<0){String id=units.get(i).chapterId;while(i>0&&units.get(i-1).chapterId.equals(id))i--;}return i;}
}
