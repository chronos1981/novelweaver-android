package com.novelweaver.android;

import org.json.*;
import java.io.*;
import java.util.*;

/** Each shelf is a complete private project file, so normal saving and native
 * narration work on it without retaining access to the imported source file. */
public final class BookcaseStore {
    public interface Artwork {String thumbnail(JSONObject cover);}
    private final File directory;
    private final Artwork artwork;
    private final Map<String,String> names=new HashMap<>();
    private static final String KEY="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    public BookcaseStore(File directory,Artwork artwork) throws IOException {
        this.directory=directory;this.artwork=artwork;
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("The bookcase could not be created.");
    }
    File file(String id) throws IOException {
        if(id==null||!id.matches(KEY))throw new IOException("Invalid bookcase shelf.");
        return new File(directory,id+".novel.json");
    }
    String idForPath(String path){
        try{if(path==null)return "";File f=new File(path).getCanonicalFile();if(!directory.getCanonicalFile().equals(f.getParentFile()))return "";
            String name=f.getName();return name.matches(KEY+"\\.novel\\.json")?name.substring(0,36):"";
        }catch(IOException e){return "";}
    }
    String displayName(String path){String id=idForPath(path);return id.isEmpty()?null:"Bookcase · "+names.getOrDefault(id,"Saved series");}
    private JSONObject validate(String value,boolean requireBooks) throws Exception {
        JSONObject project=ProjectSession.validate(value);JSONArray books=project.optJSONArray("Books");
        if(books==null||(requireBooks&&books.length()==0))throw new IOException("This series contains no books.");
        Set<String> ids=new HashSet<>();
        for(int i=0;i<books.length();i++){JSONObject book=books.optJSONObject(i);String id=book==null?"":book.optString("Id");if(id.isEmpty()||!ids.add(id))throw new IOException("The series contains missing or duplicate book IDs.");}
        JSONArray chapters=project.getJSONArray("Chapters");
        for(int i=0;i<chapters.length();i++){JSONObject chapter=chapters.optJSONObject(i);if(chapter==null||!ids.contains(chapter.optString("BookId")))throw new IOException("A chapter refers to a missing book.");}
        return project;
    }
    public synchronized JSONObject read(String id) throws Exception {
        File file=file(id);if(!file.isFile())throw new IOException("This series is no longer on the bookcase.");
        String text=ProjectSession.readFile(file);JSONObject project=validate(text,false);names.put(id,project.optString("Title","Untitled series"));
        return new JSONObject().put("id",id).put("revision",ProjectSession.hash(text)).put("project",project);
    }
    public synchronized JSONObject put(String id,String value,String expectedRevision) throws Exception {
        JSONObject project=validate(value,true);String text=project.toString(),status="updated";
        if(id==null||id.isEmpty()){
            // Identical reimports reuse a shelf; changed imports become a new
            // copy rather than silently overwriting edits to an existing shelf.
            File[] files=seriesFiles();String hash=ProjectSession.hash(text);
            for(File candidate:files){try{String raw=ProjectSession.readFile(candidate);if(hash.equals(ProjectSession.hash(new JSONObject(raw).toString()))){String existing=candidate.getName().substring(0,36);names.put(existing,project.optString("Title","Untitled series"));return new JSONObject().put("id",existing).put("revision",ProjectSession.hash(raw)).put("status","duplicate");}}catch(Exception ignored){}}
            id=UUID.randomUUID().toString();status="imported";
        }else{
            String actual=read(id).getString("revision");
            if(expectedRevision==null||!expectedRevision.equals(actual))throw new IOException("This shelf changed while books were being imported. Reopen the import and try again.");
        }
        ProjectSession.atomic(file(id),text);names.put(id,project.optString("Title","Untitled series"));
        return new JSONObject().put("id",id).put("revision",ProjectSession.hash(text)).put("status",status);
    }
    private File[] seriesFiles(){File[] files=directory.listFiles((dir,name)->name.matches(KEY+"\\.novel\\.json"));if(files==null)return new File[0];Arrays.sort(files,Comparator.comparing(File::getName));return files;}
    public synchronized JSONObject list(String activeId) throws Exception {
        JSONArray shelves=new JSONArray(),warnings=new JSONArray();
        for(File file:seriesFiles()){
            String id=file.getName().substring(0,36);
            try{
                JSONObject stored=read(id),project=stored.getJSONObject("project");JSONArray source=project.getJSONArray("Books"),books=new JSONArray();
                for(int i=0;i<source.length();i++){JSONObject b=source.getJSONObject(i);String cover="";if(artwork!=null&&b.optJSONObject("FrontCover")!=null){try{cover=artwork.thumbnail(b.getJSONObject("FrontCover"));}catch(Exception ignored){}}
                    books.put(new JSONObject().put("id",b.getString("Id")).put("title",b.optString("Title","Untitled book")).put("author",b.optString("Author","")).put("cover",cover));}
                shelves.put(new JSONObject().put("id",id).put("title",project.optString("Title","Untitled series")).put("books",books).put("modified",file.lastModified()));
            }catch(Exception e){warnings.put("A saved series could not be read ("+id+"). Its file has been retained.");}
        }
        return new JSONObject().put("shelves",shelves).put("activeId",activeId==null?"":activeId).put("warnings",warnings);
    }
}
