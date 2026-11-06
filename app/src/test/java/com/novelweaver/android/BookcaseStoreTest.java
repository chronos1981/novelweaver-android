package com.novelweaver.android;
import org.json.*;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class BookcaseStoreTest {
    File directory;BookcaseStore store;
    @Before public void setup() throws Exception {directory=Files.createTempDirectory("bookcase-test").toFile();store=new BookcaseStore(new File(directory,"shelves"),cover->"thumbnail");}
    @After public void cleanup() throws Exception {try(java.util.stream.Stream<Path> paths=Files.walk(directory.toPath())){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException ignored){}});}}
    String project(String title) throws Exception {return new JSONObject().put("Id","project").put("Title",title).put("Books",new JSONArray().put(new JSONObject().put("Id","b").put("Title","Book").put("Author","Author").put("FrontCover",new JSONObject().put("DataUrl","original full cover"))))
        .put("Chapters",new JSONArray().put(new JSONObject().put("Id","c").put("BookId","b").put("Paragraphs",new JSONArray().put(new JSONObject().put("Id","p").put("Text","Text stays intact.").put("Speech",new JSONObject().put("Dialogue",new JSONObject().put("ProfileId","voice")))))))
        .put("Threads",new JSONArray()).put("History",new JSONArray().put(new JSONObject().put("Before","Old text"))).put("SpeechProfiles",new JSONArray().put(new JSONObject().put("Id","voice").put("Voice","installed voice"))).put("CustomFutureField",new JSONObject().put("keep",true)).toString();}
    @Test public void seriesSurvivesRestartAndListUsesThumbnails() throws Exception {
        JSONObject added=store.put("",project("Series A"),null);String id=added.getString("id");
        BookcaseStore restarted=new BookcaseStore(new File(directory,"shelves"),cover->"thumbnail");JSONObject listing=restarted.list(id);
        assertEquals(id,listing.getString("activeId"));assertEquals(1,listing.getJSONArray("shelves").length());JSONObject shelf=listing.getJSONArray("shelves").getJSONObject(0);
        assertEquals("Series A",shelf.getString("title"));assertEquals("thumbnail",shelf.getJSONArray("books").getJSONObject(0).getString("cover"));
        JSONObject restored=restarted.read(id).getJSONObject("project");assertTrue(restored.getJSONObject("CustomFutureField").getBoolean("keep"));assertEquals("original full cover",restored.getJSONArray("Books").getJSONObject(0).getJSONObject("FrontCover").getString("DataUrl"));assertEquals("voice",restored.getJSONArray("Chapters").getJSONObject(0).getJSONArray("Paragraphs").getJSONObject(0).getJSONObject("Speech").getJSONObject("Dialogue").getString("ProfileId"));
    }
    @Test public void spineArtworkSurvivesShelfRestartAndAppearsInCatalog() throws Exception {
        JSONObject p=new JSONObject(project("Spines"));p.getJSONArray("Books").getJSONObject(0).put("SpineCover",new JSONObject().put("DataUrl","narrow spine").put("Width",80).put("Height",1200)).put("BackCover",new JSONObject().put("DataUrl","full back"));
        String id=store.put("",p.toString(),null).getString("id");BookcaseStore restarted=new BookcaseStore(new File(directory,"shelves"),cover->cover.optString("DataUrl").equals("narrow spine")?"spine thumbnail":"front thumbnail");
        JSONObject book=restarted.list(id).getJSONArray("shelves").getJSONObject(0).getJSONArray("books").getJSONObject(0);assertEquals("spine thumbnail",book.getString("spine"));assertEquals("front thumbnail",book.getString("cover"));
        JSONObject original=restarted.read(id).getJSONObject("project").getJSONArray("Books").getJSONObject(0);assertEquals(80,original.getJSONObject("SpineCover").getInt("Width"));assertEquals("full back",original.getJSONObject("BackCover").getString("DataUrl"));
    }
    @Test public void identicalReimportDoesNotCreateAnotherShelf() throws Exception {
        String value=project("Series");JSONObject first=store.put("",value,null),again=store.put("",new JSONObject(value).toString(2),null);
        assertEquals(first.getString("id"),again.getString("id"));assertEquals("duplicate",again.getString("status"));assertEquals(1,store.list("").getJSONArray("shelves").length());
    }
    @Test public void changedImportCreatesCopyInsteadOfOverwritingExistingShelf() throws Exception {
        String first=store.put("",project("Original"),null).getString("id");String copy=store.put("",project("Revised"),null).getString("id");
        assertNotEquals(first,copy);assertEquals("Original",store.read(first).getJSONObject("project").getString("Title"));assertEquals(2,store.list("").getJSONArray("shelves").length());
    }
    @Test public void revisionGuardRejectsStaleAndMissingVersions() throws Exception {
        JSONObject first=store.put("",project("Original"),null);String id=first.getString("id");String revision=store.read(id).getString("revision");
        store.put(id,project("New"),revision);
        for(String expected:new String[]{revision,null,""}){try{store.put(id,project("Stale"),expected);fail("Expected revision guard");}catch(IOException e){assertTrue(e.getMessage().contains("changed"));}}
        assertEquals("New",store.read(id).getJSONObject("project").getString("Title"));
    }
    @Test public void invalidBookReferencesAndEmptyImportsAreRejected() throws Exception {
        JSONObject p=new JSONObject(project("Bad"));p.getJSONArray("Chapters").getJSONObject(0).put("BookId","missing");
        try{store.put("",p.toString(),null);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("missing book"));}
        p.put("Books",new JSONArray());try{store.put("",p.toString(),null);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("no books"));}
        assertEquals(0,store.list("").getJSONArray("shelves").length());
    }
    @Test public void invalidShelfPathsAndOutsideFilesAreNotAccepted() throws Exception {
        for(String key:new String[]{"../other","","123","00000000-0000-0000-0000-000000000000/../secret"}){try{store.read(key);fail();}catch(IOException expected){}}
        JSONObject added=store.put("",project("Series"),null);String id=added.getString("id");assertEquals(id,store.idForPath(store.file(id).getPath()));
        assertEquals("",store.idForPath(new File(directory,id+".novel.json").getPath()));assertEquals("Bookcase · Series",store.displayName(store.file(id).getPath()));
    }
    @Test public void unreadableShelfIsRetainedAndReported() throws Exception {
        String id=store.put("",project("Series"),null).getString("id");Files.write(store.file(id).toPath(),"corrupt".getBytes(StandardCharsets.UTF_8));JSONObject listing=store.list("");
        assertEquals(0,listing.getJSONArray("shelves").length());assertEquals(1,listing.getJSONArray("warnings").length());assertTrue(store.file(id).exists());
    }
    @Test public void editedShelfCanBecomeEmptyAndRemainVisible() throws Exception {
        String id=store.put("",project("Series"),null).getString("id");JSONObject empty=new JSONObject(project("Empty shelf")).put("Books",new JSONArray()).put("Chapters",new JSONArray());ProjectSession.atomic(store.file(id),empty.toString());
        JSONObject listing=store.list("");assertEquals(1,listing.getJSONArray("shelves").length());assertEquals(0,listing.getJSONArray("shelves").getJSONObject(0).getJSONArray("books").length());
    }
    @Test public void normalProjectSavingUpdatesShelfWithoutChangingImportSource() throws Exception {
        File original=new File(directory,"original.novel.json");String value=project("Imported series");Files.write(original.toPath(),value.getBytes(StandardCharsets.UTF_8));String id=store.put("",value,null).getString("id");
        ProjectSession.Documents docs=new ProjectSession.Documents(){
            public String read(String uri) throws Exception{return new String(Files.readAllBytes(Paths.get(java.net.URI.create(uri))),StandardCharsets.UTF_8);}
            public void write(String uri,String json) throws Exception{Files.write(Paths.get(java.net.URI.create(uri)),json.getBytes(StandardCharsets.UTF_8));}
            public String name(String uri){return "Bookcase series";}
            public long modified(String uri){return 0;}
        };
        ProjectSession session=new ProjectSession(new File(directory,"session"),docs);session.open(store.file(id).toURI().toString());String edited=project("Edited series");session.recover(edited);session.save(edited,null,null);
        assertEquals("Edited series",store.read(id).getJSONObject("project").getString("Title"));assertEquals(value,new String(Files.readAllBytes(original.toPath()),StandardCharsets.UTF_8));
        ProjectSession restarted=new ProjectSession(new File(directory,"session"),docs);restarted.startupInfo();assertEquals("Edited series",restarted.openLast().getJSONObject("project").getString("Title"));
    }
}
