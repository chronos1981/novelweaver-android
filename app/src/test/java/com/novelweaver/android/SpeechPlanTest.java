package com.novelweaver.android;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SpeechPlanTest {
    private JSONObject project() throws Exception {
        return new JSONObject("{\"Id\":\"project\",\"Title\":\"Series\",\"Books\":[{\"Id\":\"b\",\"Title\":\"Book\"},{\"Id\":\"other\",\"Title\":\"Other book\"}],\"SpeechDefaults\":{\"Narration\":{\"Voice\":\"narrator\",\"Rate\":0.9,\"Pitch\":1.1},\"Dialogue\":{\"Voice\":\"dialogue\"}},\"SpeechProfiles\":[{\"Id\":\"male\",\"Voice\":\"Niels\",\"Rate\":1.2,\"Pitch\":0.8}],\"Chapters\":[{\"Id\":\"c1\",\"Title\":\"First\",\"BookId\":\"b\",\"Paragraphs\":[{\"Id\":\"p1\",\"Text\":\"He said, “Hello.” Then left.\",\"Speech\":{\"Dialogue\":{\"ProfileId\":\"male\"}}},{\"Id\":\"p2\",\"Text\":\"Alt’erra.\"}]},{\"Id\":\"c2\",\"Title\":\"Second\",\"BookId\":\"b\",\"Paragraphs\":[{\"Id\":\"p3\",\"Text\":\"Next chapter.\"}]},{\"Id\":\"c3\",\"Title\":\"Unrelated\",\"BookId\":\"other\",\"Paragraphs\":[{\"Id\":\"p4\",\"Text\":\"Other book.\"}]}]}");
    }
    @Test public void preservesNarratorAndDialogueAndProject() throws Exception {
        JSONObject p=project();String before=p.toString();List<SpeechPlan.Unit> u=SpeechPlan.build(p,"c1",false,new JSONObject());
        assertEquals(4,u.size());assertEquals("He said, ",u.get(0).text);assertEquals("narrator",u.get(0).voice);assertEquals(.9,u.get(0).rate,.001);
        assertEquals("“Hello.”",u.get(1).text);assertEquals("Niels",u.get(1).voice);assertEquals(.8,u.get(1).pitch,.001);assertEquals("narrator",u.get(2).voice);assertEquals(before,p.toString());
    }
    @Test public void bookQueueNeverIncludesAnotherBook() throws Exception {
        List<SpeechPlan.Unit> u=SpeechPlan.build(project(),"c1",true,new JSONObject());assertEquals(5,u.size());for(SpeechPlan.Unit item:u)assertEquals("b",item.bookId);
        assertEquals(4,SpeechPlan.adjacentChapter(u,0,1));assertEquals(0,SpeechPlan.adjacentChapter(u,4,-1));assertEquals(-1,SpeechPlan.adjacentChapter(u,0,-1));assertEquals(-1,SpeechPlan.adjacentChapter(u,4,1));
        assertEquals(3,SpeechPlan.findParagraph(u,"p2"));assertEquals(0,SpeechPlan.findParagraph(u,"deleted"));
    }
    @Test public void straightAndCurlyQuotesAndApostrophes() throws Exception {
        for(String text:new String[]{"She said 'I can't.' Then left.","She said, ‘I don’t know.’ Then left.","He said, \"Hello.\" Then left."}){
            JSONObject p=project();p.getJSONArray("Chapters").getJSONObject(0).getJSONArray("Paragraphs").getJSONObject(0).put("Text",text);List<SpeechPlan.Unit> u=SpeechPlan.build(p,"c1",false,new JSONObject());assertEquals("Niels",u.get(1).voice);assertEquals(text,u.get(0).text+u.get(1).text+u.get(2).text);
        }
        for(String text:new String[]{"It's his brother's book.","An unmatched “quote.","An unmatched \"quote."}){JSONObject p=project();p.getJSONArray("Chapters").getJSONObject(0).getJSONArray("Paragraphs").getJSONObject(0).put("Text",text);assertEquals("narrator",SpeechPlan.build(p,"c1",false,new JSONObject()).get(0).voice);}
    }
    @Test public void importedDefaultsAndOverrides() throws Exception {
        JSONObject p=project(),paragraph=p.getJSONArray("Chapters").getJSONObject(0).getJSONArray("Paragraphs").getJSONObject(0);
        paragraph.put("Speech",new JSONObject("{\"Role\":\"dialogue\",\"ProfileId\":\"male\",\"Rate\":1.5,\"ImportedDefaults\":{\"Narration\":{\"Voice\":\"imported\"}},\"Dialogue\":{\"Pitch\":1.3}}"));
        List<SpeechPlan.Unit> u=SpeechPlan.build(p,"c1",false,new JSONObject());assertEquals("imported",u.get(0).voice);assertEquals("Niels",u.get(1).voice);assertEquals(1.5,u.get(1).rate,.001);assertEquals(1.3,u.get(1).pitch,.001);
    }
    @Test public void pronunciationDoesNotChangeQuoteRoles() throws Exception {
        JSONObject settings=new JSONObject("{\"rules\":[{\"word\":\"Alt’erra\",\"said\":\"Al terra\"},{\"word\":\"Hello\",\"said\":\"He said \\\"hi\\\"\"}]}");List<SpeechPlan.Unit> u=SpeechPlan.build(project(),"c1",false,settings);assertEquals("Al terra.",u.get(3).text);assertEquals("Niels",u.get(1).voice);assertTrue(u.get(1).text.contains("He said"));
    }
    @Test public void longParagraphsFitTtsLimitWithoutBreakingUnicode() throws Exception {
        JSONObject p=project();String text="abc😀".repeat(2000);p.getJSONArray("Chapters").getJSONObject(0).getJSONArray("Paragraphs").getJSONObject(0).put("Text",text);List<SpeechPlan.Unit> u=SpeechPlan.build(p,"c1",false,new JSONObject());StringBuilder joined=new StringBuilder();for(SpeechPlan.Unit part:u){if(!part.pid.equals("p1"))continue;assertTrue(part.text.length()<=3000);assertFalse(Character.isHighSurrogate(part.text.charAt(part.text.length()-1)));joined.append(part.text);}assertEquals(text,joined.toString());
    }
    @Test public void missingChapterAndEmptyTextProduceNoSpeech() throws Exception {
        assertTrue(SpeechPlan.build(project(),"missing",false,new JSONObject()).isEmpty());assertTrue(SpeechPlan.build(project(),"missing",true,new JSONObject()).isEmpty());
        JSONObject p=project();p.getJSONArray("Chapters").getJSONObject(0).put("Paragraphs",new JSONArray("[{\"Text\":\"   \"}]"));assertTrue(SpeechPlan.build(p,"c1",false,new JSONObject()).isEmpty());
    }
    @Test public void fallbacksAndInvalidSettingsAreBounded() throws Exception {
        JSONObject p=project();p.remove("SpeechDefaults");p.remove("SpeechProfiles");JSONObject listen=new JSONObject("{\"voice\":\"global\",\"rate\":1.6}");List<SpeechPlan.Unit> u=SpeechPlan.build(p,"c1",false,listen);assertEquals("global",u.get(0).voice);assertEquals(1.6,u.get(0).rate,.001);assertEquals(1,u.get(0).pitch,.001);assertEquals(2,SpeechPlan.number(new JSONObject("{\"Rate\":99}"),"Rate",1),.001);
    }
}
