package com.novelweaver.android;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;

/** JSON stays intact across editions; the old typed model must not strip fields. */
public final class ProjectSession {
    public static final int MAX_BYTES = 60_000_000;
    public interface Documents {
        String read(String uri) throws Exception;
        void write(String uri, String json) throws Exception;
        String name(String uri);
        long modified(String uri);
    }
    private final File recovery, previous;
    private final File lastFile, recoveryCopies;
    private final Documents documents;
    private String uri, baseHash, json, baseProjectHash;
    private boolean paused;
    private JSONObject conflict;
    private String lastUri,lastName;

    public ProjectSession(File directory, Documents documents) {
        directory.mkdirs(); this.documents=documents;
        recovery=new File(directory,"workspace-recovery.json");
        previous=new File(directory,"previous-file.novel.json");
        lastFile=new File(directory,"last-project.json");
        recoveryCopies=new File(directory,"recovery-copies");
    }
    private static String canonical(Object value) throws Exception {
        if(value instanceof JSONObject){JSONObject p=(JSONObject)value;TreeSet<String> keys=new TreeSet<>();Iterator<String> i=p.keys();while(i.hasNext())keys.add(i.next());StringJoiner s=new StringJoiner(",","{","}");for(String key:keys)s.add(JSONObject.quote(key)+":"+canonical(p.get(key)));return s.toString();}
        if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringJoiner s=new StringJoiner(",","[","]");for(int i=0;i<a.length();i++)s.add(canonical(a.get(i)));return s.toString();}
        return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);
    }
    private static boolean hasWriting(JSONObject project){
        if(!"Untitled Series".equals(project.optString("Title","Untitled Series")))return true;
        for(String key:new String[]{"History","Threads","Reviews"})if(project.optJSONArray(key)!=null&&project.optJSONArray(key).length()>0)return true;
        JSONArray books=project.optJSONArray("Books"),chapters=project.optJSONArray("Chapters");
        if(books!=null){if(books.length()>1)return true;for(int i=0;i<books.length();i++){JSONObject b=books.optJSONObject(i);if(b!=null&&(!"Book One".equals(b.optString("Title","Book One"))||!b.optString("Author","").isEmpty()||!b.isNull("FrontCover")||!b.isNull("BackCover")))return true;}}
        if(chapters!=null){if(chapters.length()>1)return true;for(int i=0;i<chapters.length();i++){JSONObject c=chapters.optJSONObject(i);if(c==null)continue;if(!"Chapter 1".equals(c.optString("Title","Chapter 1")))return true;JSONArray paragraphs=c.optJSONArray("Paragraphs");if(paragraphs!=null)for(int j=0;j<paragraphs.length();j++){JSONObject p=paragraphs.optJSONObject(j);if(p!=null&&!p.optString("Text","").trim().isEmpty())return true;}}}
        return false;
    }
    private synchronized void archiveRecovery(String replacingWith) throws Exception {
        if(!recovery.exists())return;
        String raw=readFile(recovery);JSONObject old,snapshot;
        try{snapshot=new JSONObject(raw);old=snapshot.getJSONObject("project");}catch(Exception e){recoveryCopies.mkdirs();File unreadable=new File(recoveryCopies,hash(raw)+".unreadable.json");if(!unreadable.exists())atomic(unreadable,raw);return;}
        if(!hasWriting(old))return;
        String normalized=canonical(old);
        if(hash(normalized).equals(snapshot.optString("baseProjectHash","")))return;
        if(replacingWith!=null&&normalized.equals(canonical(validate(replacingWith))))return;
        recoveryCopies.mkdirs();File copy=new File(recoveryCopies,hash(normalized)+".novel.json");
        if(!copy.exists())atomic(copy,old.toString());
    }
    private synchronized void rememberLast(String selectedUri,String name) throws Exception {
        atomic(lastFile,new JSONObject().put("uri",selectedUri).put("name",name).toString());lastUri=selectedUri;lastName=name;
    }
    /** Startup inspects only private metadata. It never restores writing or reads
     * the provider until the user explicitly selects Open last project. */
    public synchronized JSONObject startupInfo() throws Exception {
        if(lastFile.exists()){
            JSONObject link=new JSONObject(readFile(lastFile));lastUri=link.optString("uri",null);lastName=link.optString("name","Last project");
        }else if(recovery.exists()){
            try{JSONObject legacy=new JSONObject(readFile(recovery));if(!legacy.isNull("uri"))rememberLast(legacy.getString("uri"),"Last saved project");}catch(Exception ignored){}
        }
        String recoveryError="";
        try{archiveRecovery(null);}catch(Exception e){recoveryError="An older recovery copy could not be read. It has been retained on this device.";}
        JSONArray copies=new JSONArray();File[] files=recoveryCopies.listFiles((dir,name)->name.matches("[a-f0-9]{64}\\.novel\\.json"));
        if(files!=null){Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(File file:files){try{JSONObject p=validate(readFile(file));copies.put(new JSONObject().put("id",file.getName().substring(0,64)).put("title",p.optString("Title","Recovery copy")).put("modified",p.optString("ModifiedAtUtc","")).put("savedAt",file.lastModified()));}catch(Exception ignored){}}}
        return new JSONObject().put("hasLast",lastUri!=null).put("lastPath",lastName==null?JSONObject.NULL:lastName).put("recoveries",copies).put("recoveryError",recoveryError);
    }
    public JSONObject openLast() throws Exception {
        final String selected; synchronized(this){selected=lastUri;}
        if(selected==null)throw new IOException("No last project file is available. Choose Open another project.");
        return open(selected);
    }
    public JSONObject openRecovery(String id) throws Exception {
        if(!id.matches("[a-f0-9]{64}"))throw new IOException("Invalid recovery copy.");
        String value=readFile(new File(recoveryCopies,id+".novel.json"));validate(value);
        JSONObject result=startNew(value);return result.put("project",validate(value));
    }
    public static JSONObject validate(String text) throws Exception {
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES) throw new IOException("Project exceeds 60 MB.");
        JSONObject p;
        try{p=new JSONObject(text);}catch(org.json.JSONException e){throw new IOException("This is not a valid NovelWeaver project. Choose a .novel.json file.");}
        if(p.optJSONArray("Chapters")==null) throw new IOException("Choose a NovelWeaver .novel.json project.");
        return p;
    }
    public static String hash(String text) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder(); for(byte value:digest)b.append(String.format("%02x",value&255)); return b.toString();
    }
    public static void atomic(File file,String value) throws Exception {
        File temp=new File(file.getParentFile(),file.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(temp)) {out.write(value.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        try { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch(AtomicMoveNotSupportedException e) { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING); }
    }
    public static String readFile(File file) throws Exception {
        if(file.length()>MAX_BYTES+2_000_000)throw new IOException("Recovery file is too large.");
        return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
    }
    private synchronized void checkpoint() throws Exception {
        if(json==null)return;
        JSONObject data=new JSONObject().put("project",validate(json)).put("uri",uri==null?JSONObject.NULL:uri).put("baseHash",baseHash==null?JSONObject.NULL:baseHash).put("baseProjectHash",baseProjectHash==null?JSONObject.NULL:baseProjectHash);
        atomic(recovery,data.toString());
    }
    public synchronized JSONObject recover(String value) throws Exception {
        validate(value); json=value; checkpoint();
        return result(paused?"paused":uri==null?"recovery":"pending");
    }
    public synchronized String currentUri(){return uri;}
    public synchronized String currentJson(){return json;}
    public synchronized boolean paused(){return paused;}
    public synchronized JSONObject result(String status) throws Exception {
        return new JSONObject().put("status",status).put("path",uri==null?JSONObject.NULL:documents.name(uri));
    }
    public JSONObject restore() throws Exception {
        if(!recovery.exists())return result("empty");
        synchronized(this) {
            JSONObject p=new JSONObject(readFile(recovery));
            json=p.getJSONObject("project").toString();validate(json);
            uri=p.isNull("uri")?null:p.getString("uri");baseHash=p.isNull("baseHash")?null:p.getString("baseHash");baseProjectHash=p.optString("baseProjectHash",null);
        }
        if(uri!=null) {
            try {
                String disk=documents.read(uri);validate(disk);
                String revision=hash(disk);
                if(!revision.equals(baseHash)&&!revision.equals(hash(json)))return block(disk,revision);
                if(revision.equals(hash(json)))synchronized(this){baseHash=revision;checkpoint();}
            } catch(Exception e) {return unavailable(e);}
        }
        return result("restored");
    }
    private synchronized JSONObject unavailable(Exception e) throws Exception {
        paused=true;
        conflict=result("missing").put("message","The chosen file is unavailable: "+e.getMessage());
        return conflict;
    }
    private synchronized JSONObject block(String disk,String revision) throws Exception {
        paused=true;
        conflict=result("conflict").put("project",validate(disk)).put("revision",revision).put("fileTime",documents.modified(uri));
        return conflict;
    }
    // All provider operations are serialized by the Activity's document executor.
    // Recovery writes use another executor and never wait for cloud networking.
    public JSONObject open(String selectedUri) throws Exception {
        String value=documents.read(selectedUri);validate(value);
        synchronized(this){archiveRecovery(value);rememberLast(selectedUri,documents.name(selectedUri));uri=selectedUri;baseHash=hash(value);baseProjectHash=hash(canonical(validate(value)));json=value;paused=false;conflict=null;checkpoint();}
        return result("opened").put("project",validate(value));
    }
    public synchronized JSONObject startNew(String value) throws Exception {
        validate(value);archiveRecovery(value);uri=null;baseHash=null;baseProjectHash=null;json=value;paused=false;conflict=null;checkpoint();return result("recovery");
    }
    public JSONObject save(String value,String saveAs,String acceptedRevision) throws Exception {
        validate(value);
        final String target,expected;
        synchronized(this){target=saveAs==null?uri:saveAs;expected=baseHash;}
        if(target==null)return recover(value);
        String disk=null;
        if(saveAs==null) {
            try { disk=documents.read(target);validate(disk); }
            catch(Exception e){return unavailable(e);}
            String revision=hash(disk);
            if(!revision.equals(expected)&&!revision.equals(hash(value))&&!revision.equals(acceptedRevision))return block(disk,revision);
            if(revision.equals(hash(value))){synchronized(this){baseHash=revision;baseProjectHash=hash(canonical(validate(value)));paused=false;conflict=null;checkpoint();return result(value.equals(json)?"saved":"pending");}}
            // Keep the last document version in app-private storage before a
            // provider write: SAF cannot promise atomic file replacement.
            atomic(previous,disk);
        }
        documents.write(target,value);
        synchronized(this){rememberLast(target,documents.name(target));uri=target;baseHash=hash(value);baseProjectHash=hash(canonical(validate(value)));paused=false;conflict=null;checkpoint();return result(value.equals(json)?"saved":"pending");}
    }
    public synchronized JSONObject getConflict(){return conflict;}
    public File previousFile(){return previous;}
}
