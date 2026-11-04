package com.novelweaver.android;

import org.junit.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class ProjectSessionTest {
    @Test public void voicePresetOnlyWorkspaceHasRecovery() throws Exception{
        String blank=new JSONObject().put("Title","Untitled Series").put("Books",new JSONArray()).put("Chapters",new JSONArray()).put("SpeechProfiles",new JSONArray().put(new JSONObject().put("Id","male").put("Name","Narrator").put("Gender","male").put("Voice","installed-voice"))).toString();
        session.startNew(blank);
        ProjectSession launched=new ProjectSession(directory,docs);JSONObject info=launched.startupInfo();
        assertEquals(1,info.getJSONArray("recoveries").length());
        JSONObject restored=launched.openRecovery(info.getJSONArray("recoveries").getJSONObject(0).getString("id")).getJSONObject("project");
        assertEquals("male",restored.getJSONArray("SpeechProfiles").getJSONObject(0).getString("Gender"));
    }
    @Test public void speechSettingsClampAndReset() throws Exception{
        assertEquals(1f,Narrator.setting(new JSONObject(),"pitch"),0f);
        assertEquals(.5f,Narrator.setting(new JSONObject().put("pitch",.1),"pitch"),0f);
        assertEquals(2f,Narrator.setting(new JSONObject().put("rate",9),"rate"),0f);
        assertEquals(.8f,Narrator.setting(new JSONObject().put("pitch",.8),"pitch"),.001f);
    }
    private File directory;
    private final Map<String,String> files=new HashMap<>();
    private ProjectSession session;
    private final ProjectSession.Documents docs=new ProjectSession.Documents(){
        public String read(String uri) throws Exception{if(!files.containsKey(uri))throw new FileNotFoundException("Missing document");return files.get(uri);}
        public void write(String uri,String value){files.put(uri,value);}
        public String name(String uri){return uri;}
        public long modified(String uri){return 1000;}
    };
    @Before public void setup() throws Exception{directory=Files.createTempDirectory("nw-android-test").toFile();session=new ProjectSession(directory,docs);}
    @After public void cleanup() throws Exception{try(java.util.stream.Stream<Path> paths=Files.walk(directory.toPath())){paths.sorted(Comparator.reverseOrder()).forEach(p->p.toFile().delete());}}
    private String project(String text) throws Exception{return new JSONObject().put("Id","series").put("Title",text).put("ModifiedAtUtc","2026-11-04T00:00:00Z")
        .put("Books",new JSONArray().put(new JSONObject().put("Id","b").put("Author","Writer").put("FrontCover",new JSONObject().put("DataUrl","data:image/png;base64,AAAA").put("Width",1).put("Height",1))))
        .put("Chapters",new JSONArray().put(new JSONObject().put("Id","c").put("BookId","b").put("Paragraphs",new JSONArray().put(new JSONObject().put("Id","p").put("Text",text).put("ThreadIds",new JSONArray().put("t"))))))
        .put("History",new JSONArray().put(new JSONObject().put("Id","h").put("ParagraphId","p").put("Before","Old").put("After",text)))
        .put("Threads",new JSONArray()).put("Reviews",new JSONArray()).put("FutureField","keep me").toString();}
    @Test public void recoveryAndFirstSave() throws Exception{
        assertEquals("empty",session.restore().getString("status"));String p=project("One");
        assertEquals("recovery",session.recover(p).getString("status"));
        assertEquals("saved",session.save(p,"drive:book",null).getString("status"));assertEquals(p,files.get("drive:book"));
        ProjectSession restored=new ProjectSession(directory,docs);assertEquals("restored",restored.restore().getString("status"));assertEquals(p,restored.currentJson());
    }
    @Test public void autosaveAndBackup() throws Exception{
        String one=project("One"),two=project("Two");session.recover(one);session.save(one,"drive:book",null);
        assertEquals("pending",session.recover(two).getString("status"));session.save(two,null,null);
        assertEquals(one,ProjectSession.readFile(session.previousFile()));assertEquals(two,files.get("drive:book"));
        session.save(two,null,null);assertEquals(one,ProjectSession.readFile(session.previousFile()));
    }
    @Test public void conflictAndStaleChoice() throws Exception{
        String p=project("Local"),remote=project("Remote");session.recover(p);session.save(p,"drive:book",null);files.put("drive:book",remote);
        JSONObject conflict=session.save(p,null,null);assertEquals("conflict",conflict.getString("status"));assertTrue(session.paused());assertEquals(remote,files.get("drive:book"));
        String newer=project("Newer");files.put("drive:book",newer);
        assertEquals("conflict",session.save(p,null,conflict.getString("revision")).getString("status"));
        JSONObject current=session.getConflict();assertEquals("saved",session.save(p,null,current.getString("revision")).getString("status"));assertFalse(session.paused());assertEquals(newer,ProjectSession.readFile(session.previousFile()));
    }
    @Test public void privateRecoveryContinuesDuringConflict() throws Exception{
        String p=project("Local");session.recover(p);session.save(p,"drive:book",null);files.put("drive:book",project("Remote"));session.save(p,null,null);
        String latest=project("Further local typing");assertEquals("paused",session.recover(latest).getString("status"));
        ProjectSession restored=new ProjectSession(directory,docs);assertEquals("conflict",restored.restore().getString("status"));assertEquals(latest,restored.currentJson());
    }
    @Test public void missingFileIsNotRecreated() throws Exception{
        String p=project("Local");session.recover(p);session.save(p,"drive:book",null);files.clear();
        JSONObject r=session.save(p,null,null);assertEquals("missing",r.getString("status"));assertFalse(r.has("project"));assertTrue(files.isEmpty());
    }
    @Test public void openKeepsCoversHistoryAndFutureFields() throws Exception{
        files.put("drive:book",project("Opened"));JSONObject result=session.open("drive:book"),p=result.getJSONObject("project");
        assertEquals("keep me",p.getString("FutureField"));assertEquals(1,p.getJSONArray("History").length());assertEquals(1,p.getJSONArray("Books").getJSONObject(0).getJSONObject("FrontCover").getInt("Width"));
    }
    @Test public void newProjectDetachesFile() throws Exception{
        String old=project("Old");session.recover(old);session.save(old,"drive:book",null);session.startNew(project("New"));session.recover(project("Typing"));
        assertNull(session.currentUri());assertEquals(old,files.get("drive:book"));
    }
    @Test public void failedOpenDoesNotReplaceWorkspace() throws Exception{
        String p=project("Safe");session.startNew(p);files.put("bad","{}");
        try{session.open("bad");fail("Bad project accepted");}catch(IOException expected){}
        assertEquals(p,session.currentJson());assertNull(session.currentUri());
    }
    @Test public void networkPolicy() throws Exception{
        assertEquals("https",QuillNetwork.validate("https://my-server.ts.net/api/models").getProtocol());
        QuillNetwork.validate("http://192.168.0.101:8080/api/chat/completions");QuillNetwork.validate("http://100.100.10.1:8080/api/models");
        for(String address:new String[]{"http://public.example/api/models","file:///api/models","https://u:pw@host/api/models","https://host/api/models?key=secret","https://host/api/delete"})
            try{QuillNetwork.validate(address);fail("Unsafe URL accepted: "+address);}catch(IOException|java.net.URISyntaxException expected){}
    }
    @Test public void typingDuringSlowFileSaveRemainsPending() throws Exception{
        ProjectSession[] holder=new ProjectSession[1];String first=project("First"),later=project("Typed during upload");
        ProjectSession.Documents slow=new ProjectSession.Documents(){
            public String read(String uri){return files.get(uri);}
            public void write(String uri,String value) throws Exception{files.put(uri,value);holder[0].recover(later);}
            public String name(String uri){return uri;}
            public long modified(String uri){return 1000;}
        };
        holder[0]=new ProjectSession(directory,slow);holder[0].recover(first);
        assertEquals("pending",holder[0].save(first,"drive:book",null).getString("status"));
        assertEquals(later,holder[0].currentJson());assertEquals(first,files.get("drive:book"));
        ProjectSession restored=new ProjectSession(directory,docs);assertEquals("restored",restored.restore().getString("status"));assertEquals(later,restored.currentJson());
    }
    @Test public void interruptedProviderWriteKeepsRecoveryAndPreviousFile() throws Exception{
        String first=project("Before"),latest=project("Latest recovery");files.put("drive:book",first);
        ProjectSession.Documents broken=new ProjectSession.Documents(){
            public String read(String uri){return files.get(uri);}
            public void write(String uri,String value) throws Exception{files.put(uri,"partial");throw new IOException("Provider disconnected");}
            public String name(String uri){return uri;}
            public long modified(String uri){return 1000;}
        };
        ProjectSession s=new ProjectSession(directory,broken);s.open("drive:book");s.recover(latest);
        try{s.save(latest,null,null);fail("Broken provider write reported success");}catch(IOException expected){}
        assertEquals(first,ProjectSession.readFile(s.previousFile()));
        ProjectSession recovered=new ProjectSession(directory,docs);assertEquals("missing",recovered.restore().getString("status"));assertEquals(latest,recovered.currentJson());
    }
    @Test public void startupWaitsForChoiceAndOpenLastReadsFreshFile() throws Exception{
        String old=project("Old saved"),cached=project("Cached unsaved"),fresh=project("Latest file");
        session.recover(old);session.save(old,"drive:book",null);session.recover(cached);files.put("drive:book",fresh);
        ProjectSession launched=new ProjectSession(directory,docs);JSONObject info=launched.startupInfo();
        assertTrue(info.getBoolean("hasLast"));assertNull(launched.currentJson());assertNull(launched.currentUri());assertEquals(fresh,files.get("drive:book"));
        assertEquals(fresh,launched.openLast().getJSONObject("project").toString());assertEquals(fresh,launched.currentJson());assertEquals(fresh,files.get("drive:book"));
        assertTrue(info.getJSONArray("recoveries").length()>0);
    }
    @Test public void blankStartupKeepsLastFileAndUnsavedRecoverySeparate() throws Exception{
        String saved=project("Saved"),unsaved=project("Unsaved"),blank=project("Blank");session.recover(saved);session.save(saved,"drive:book",null);session.recover(unsaved);
        ProjectSession launched=new ProjectSession(directory,docs);JSONObject info=launched.startupInfo();String id=info.getJSONArray("recoveries").getJSONObject(0).getString("id");
        launched.startNew(blank);assertNull(launched.currentUri());assertEquals(saved,files.get("drive:book"));
        ProjectSession again=new ProjectSession(directory,docs);assertTrue(again.startupInfo().getBoolean("hasLast"));assertNull(again.currentJson());
        JSONObject recovered=again.openRecovery(id);assertEquals("Unsaved",recovered.getJSONObject("project").getString("Title"));assertNull(again.currentUri());assertEquals(saved,files.get("drive:book"));
    }
    @Test public void missingLastFileDoesNotRestoreCachedWriting() throws Exception{
        String p=project("Cached");session.recover(p);session.save(p,"drive:book",null);files.clear();
        ProjectSession launched=new ProjectSession(directory,docs);launched.startupInfo();
        try{launched.openLast();fail("Missing file accepted");}catch(FileNotFoundException expected){}
        assertNull(launched.currentJson());assertNull(launched.currentUri());
    }
    @Test public void savedWorkspaceDoesNotCreateAnUnsavedRecoveryOffer() throws Exception{
        String p=project("Saved");session.recover(p);session.save(p,"drive:book",null);
        assertEquals(0,new ProjectSession(directory,docs).startupInfo().getJSONArray("recoveries").length());
    }
    @Test public void legacyLastFileMetadataMigratesWithoutLoadingCachedProject() throws Exception{
        String p=project("Legacy cached"),fresh=project("Current file");
        ProjectSession.atomic(new File(directory,"workspace-recovery.json"),new JSONObject().put("project",new JSONObject(p)).put("uri","drive:book").put("baseHash",ProjectSession.hash(p)).toString());files.put("drive:book",fresh);
        ProjectSession launched=new ProjectSession(directory,docs);assertTrue(launched.startupInfo().getBoolean("hasLast"));assertNull(launched.currentJson());assertEquals(fresh,launched.openLast().getJSONObject("project").toString());
    }
}
