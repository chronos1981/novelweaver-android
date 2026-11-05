package com.novelweaver.android;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.media.*;
import android.media.browse.MediaBrowser;
import android.media.session.*;
import android.net.Uri;
import android.os.*;
import android.service.media.MediaBrowserService;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Owns the entire speech queue. Neither WebView timers nor an Activity are needed to play. */
public final class ChapterPlaybackService extends MediaBrowserService {
    static final String LOCAL="com.novelweaver.android.LOCAL_PLAYBACK";
    private static final String ROOT="library",CHANNEL="chapter-playback";
    private static final int NOTIFICATION=42;
    interface Listener {void post(JSONObject event);}
    public final class LocalBinder extends Binder {ChapterPlaybackService service(){return ChapterPlaybackService.this;}}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final List<Runnable> waiting=new ArrayList<>();
    private final Set<Listener> listeners=new HashSet<>();
    private MediaSession media;
    private Narrator narrator;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wake;
    private JSONObject project,options=new JSONObject();
    private String source="",error="",previewKey;
    private List<SpeechPlan.Unit> queue=Collections.emptyList();
    private int index,state=PlaybackState.STATE_STOPPED,generation,loadGeneration,planGeneration;
    private boolean loading=true,foreground,hasFocus,resumeAfterFocus,destroyed;
    private final Runnable idle=()->{stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf();};
    private final BroadcastReceiver noisy=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){pause(false);}};
    private final AudioManager.OnAudioFocusChangeListener focusChange=change->{
        if(change==AudioManager.AUDIOFOCUS_GAIN){hasFocus=true;if(resumeAfterFocus){resumeAfterFocus=false;resume();}}
        else if(change<0){boolean restart=change!=AudioManager.AUDIOFOCUS_LOSS&&(state==PlaybackState.STATE_PLAYING||state==PlaybackState.STATE_BUFFERING);pause(true);resumeAfterFocus=restart;if(change==AudioManager.AUDIOFOCUS_LOSS)abandonFocus();}
    };
    @Override public void onCreate(){
        super.onCreate();audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener(focusChange,main).build();
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"NovelWeaver:chapter");wake.setReferenceCounted(false);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Chapter narration",NotificationManager.IMPORTANCE_LOW));
        media=new MediaSession(this,"NovelWeaver chapters");media.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        media.setSessionActivity(openApp());media.setCallback(new MediaSession.Callback(){
            @Override public void onPlay(){whenLoaded(()->resume());}
            @Override public void onPause(){pause(false);}
            @Override public void onStop(){stop();}
            @Override public void onPlayFromMediaId(String id,Bundle extras){whenLoaded(()->playChapter(id.startsWith("chapter:")?id.substring(8):id,"",true));}
            @Override public void onPlayFromSearch(String query,Bundle extras){whenLoaded(()->search(query));}
            @Override public void onSkipToNext(){skipChapter(1);}
            @Override public void onSkipToPrevious(){skipChapter(-1);}
            @Override public void onSkipToQueueItem(long id){if(id>=0&&id<queue.size())jump((int)id);}
            @Override public void onPrepare(){publish();}
        },main);setSessionToken(media.getSessionToken());media.setActive(true);
        narrator=new Narrator(getApplicationContext(),message->main.post(()->speechEvent(message)));
        IntentFilter filter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(noisy,filter);
        try{options=new JSONObject(getSharedPreferences("car-playback",MODE_PRIVATE).getString("options","{}"));}catch(JSONException ignored){}
        publish();loadLastProject();
    }
    @Override public IBinder onBind(Intent intent){if(LOCAL.equals(intent.getAction()))return new LocalBinder();return super.onBind(intent);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){String a=intent.getAction();if("pause".equals(a))pause(false);else if("play".equals(a))whenLoaded(()->resume());else if("next".equals(a))skipChapter(1);else if("previous".equals(a))skipChapter(-1);else if("stop".equals(a))stop();else if("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(a))whenLoaded(()->search(intent.getStringExtra(android.app.SearchManager.QUERY)));else if(Intent.ACTION_MEDIA_BUTTON.equals(a)){android.view.KeyEvent key=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);if(key!=null&&key.getAction()==android.view.KeyEvent.ACTION_DOWN)media.getController().dispatchMediaButtonEvent(key);}}
        return START_NOT_STICKY;
    }
    @Override public BrowserRoot onGetRoot(String pkg,int uid,Bundle hints){
        // Check the package actually belongs to the binder caller before exposing manuscript titles.
        String[] owned=getPackageManager().getPackagesForUid(uid);boolean owns=owned!=null&&Arrays.asList(owned).contains(pkg);
        boolean allowed=uid==getApplicationInfo().uid||uid==android.os.Process.SYSTEM_UID||"com.google.android.projection.gearhead".equals(pkg)||"com.google.android.googlequicksearchbox".equals(pkg)||"com.android.bluetooth".equals(pkg);
        return owns&&allowed?new BrowserRoot(ROOT,null):null;
    }
    @Override public void onLoadChildren(String parent,Result<List<MediaBrowser.MediaItem>> result){
        result.detach();whenLoaded(()->result.sendResult(children(parent)));
    }
    private void whenLoaded(Runnable action){if(loading)waiting.add(action);else action.run();}
    private void finishLoading(){loading=false;List<Runnable> copy=new ArrayList<>(waiting);waiting.clear();for(Runnable r:copy)r.run();notifyChildrenChanged(ROOT);}
    private void loadLastProject(){
        final int load=++loadGeneration;
        main.postDelayed(()->{if(!destroyed&&loading&&load==loadGeneration){++loadGeneration;error="The project file is taking too long to open. Open it on your phone while parked.";finishLoading();publish();}},15000);
        io.execute(()->{JSONObject value=null;String uri="",failure="";
            try{File last=new File(getFilesDir(),"last-project.json");if(last.exists()){JSONObject link=new JSONObject(ProjectSession.readFile(last));uri=link.getString("uri");InputStream stream=getContentResolver().openInputStream(Uri.parse(uri));if(stream==null)throw new IOException("File unavailable");value=ProjectSession.validate(new String(QuillNetwork.readLimited(stream,ProjectSession.MAX_BYTES,null),StandardCharsets.UTF_8));}}
            catch(Exception e){failure="Open your project on the phone to restore file access.";}
            final JSONObject loaded=value;final String path=uri,message=failure;
            main.post(()->{if(destroyed||load!=loadGeneration)return;project=loaded;source=path;error=message;finishLoading();publish();});
        });
    }
    void setProject(JSONObject value,String uri){
        ++loadGeneration;String next=uri==null?"":uri;
        if(project!=null&&(!project.optString("Id").equals(value.optString("Id"))||(!source.equals(next)&&!source.isEmpty())))stop();
        project=value;source=next;error="";artBook=null;art=null;finishLoading();
        JSONArray books=project.optJSONArray("Books");if(books!=null)for(int i=0;i<books.length();i++){JSONObject b=books.optJSONObject(i);if(b!=null)notifyChildrenChanged("book:"+b.optString("Id"));}
    }
    void addListener(Listener listener){listeners.add(listener);emitVoices();listener.post(snapshot());}
    void removeListener(Listener listener){listeners.remove(listener);}
    JSONArray voices(){return narrator.voices();}
    void configure(JSONObject value){options=value;getSharedPreferences("car-playback",MODE_PRIVATE).edit().putString("options",value.toString()).apply();}
    void command(String action,JSONObject p) throws Exception {
        try{switch(action){
            case "mediaPlay":configure(SpeechPlan.object(p,"options"));playChapter(p.getString("chapterId"),p.optString("pid"),p.optBoolean("wholeBook"));break;
            case "mediaState":emit(snapshot());break;
            case "mediaParagraph":skipParagraph(p.optInt("delta",1));break;
            case "mediaSettings":configure(p);break;
            case "speak":reset(false);previewKey=p.getString("key");beginForeground();if(!takeFocus())throw new IOException("Audio is in use by another app.");narrator.speak(p);break;
            case "speechStop":stop();break;
            case "speechPause":pause(false);break;
            case "speechResume":resume();break;
            default:throw new IOException("Unknown playback command.");
        }}catch(Exception e){fail(e.getMessage());throw e;}
    }
    private List<MediaBrowser.MediaItem> children(String parent){
        List<MediaBrowser.MediaItem> items=new ArrayList<>();
        if(project==null){if(ROOT.equals(parent))items.add(new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId("unavailable").setTitle("Open a project on your phone").setSubtitle(error.isEmpty()?"Choose a .novel.json file in NovelWeaver":error).build(),0));return items;}
        JSONArray books=project.optJSONArray("Books"),chapters=project.optJSONArray("Chapters");
        if(ROOT.equals(parent)&&books!=null){for(int i=0;i<books.length();i++){JSONObject b=books.optJSONObject(i);if(b==null)continue;items.add(new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId("book:"+b.optString("Id")).setTitle(b.optString("Title","Book")).setSubtitle(project.optString("Title")).build(),MediaBrowser.MediaItem.FLAG_BROWSABLE));}}
        else if(parent.startsWith("book:")&&chapters!=null){String id=parent.substring(5);for(int i=0;i<chapters.length();i++){JSONObject c=chapters.optJSONObject(i);if(c==null||!id.equals(c.optString("BookId")))continue;JSONArray paragraphs=c.optJSONArray("Paragraphs");boolean hasText=false;if(paragraphs!=null)for(int j=0;j<paragraphs.length();j++){JSONObject p=paragraphs.optJSONObject(j);if(p!=null&&!p.optString("Text").trim().isEmpty()){hasText=true;break;}}if(hasText)items.add(new MediaBrowser.MediaItem(new MediaDescription.Builder().setMediaId("chapter:"+c.optString("Id")).setTitle(c.optString("Title","Chapter")).setSubtitle("Read aloud").build(),MediaBrowser.MediaItem.FLAG_PLAYABLE));}}
        return items;
    }
    private void search(String query){
        if(project==null){fail("Open a project on your phone first.");return;}String term=query==null?"":query.toLowerCase(Locale.ROOT);JSONArray chapters=project.optJSONArray("Chapters");
        if(chapters!=null)for(int i=0;i<chapters.length();i++){JSONObject c=chapters.optJSONObject(i);if(c!=null&&(term.isEmpty()||c.optString("Title").toLowerCase(Locale.ROOT).contains(term))){playChapter(c.optString("Id"),"",true);return;}}
        fail("No matching chapter in the open project.");
    }
    private void playChapter(String chapterId,String pid,boolean wholeBook){
        if(project==null){fail("Open a project on your phone first.");return;}
        reset(false);error="";final int build=++planGeneration;final JSONObject snapshot=project,settings=options;
        state=PlaybackState.STATE_BUFFERING;
        try{beginForeground();}catch(Exception e){fail(e.getMessage());return;}publish();
        io.execute(()->{try{List<SpeechPlan.Unit> prepared=SpeechPlan.build(snapshot,chapterId,wholeBook,settings);main.post(()->{
            if(destroyed||build!=planGeneration)return;queue=prepared;if(queue.isEmpty()){fail("This chapter has no text to read.");return;}
            index=pid.isEmpty()?firstChapter(prepared,chapterId):SpeechPlan.findParagraph(prepared,pid);startUnit();
        });}catch(Exception e){main.post(()->{if(build==planGeneration)fail("Could not prepare chapter narration.");});}});
    }
    private int firstChapter(List<SpeechPlan.Unit> units,String id){for(int i=0;i<units.size();i++)if(units.get(i).chapterId.equals(id))return i;return 0;}
    private void startUnit(){
        if(queue.isEmpty()||index>=queue.size()){stop();return;}
        state=PlaybackState.STATE_BUFFERING;publish();final int token=++generation;waitForEngine(token,0);
    }
    private void waitForEngine(int token,int attempt){
        if(destroyed||token!=generation||state!=PlaybackState.STATE_BUFFERING)return;
        if(!narrator.isReady()){if(attempt>=100){fail("Speech engine unavailable. Check text-to-speech settings on your phone.");return;}main.postDelayed(()->waitForEngine(token,attempt+1),100);return;}
        try{
            beginForeground();if(!takeFocus()){fail("Audio is in use by another app. Press Play to retry.");return;}
            SpeechPlan.Unit unit=queue.get(index);JSONObject request=unit.request("chapter:"+token);JSONArray voices=narrator.voices();boolean found=unit.voice.isEmpty();for(int i=0;i<voices.length();i++)if(unit.voice.equals(voices.optJSONObject(i).optString("id")))found=true;
            if(!found){String fallback=options.optString("voice","");boolean installed=false;for(int i=0;i<voices.length();i++)if(fallback.equals(voices.optJSONObject(i).optString("id")))installed=true;request.put("voice",installed?fallback:"");}
            wake.acquire(10*60*1000L);narrator.speak(request);state=PlaybackState.STATE_PLAYING;remember();publish();
        }catch(Exception e){fail(e.getMessage());}
    }
    private void speechEvent(JSONObject message){
        if(destroyed)return;
        if("voices".equals(message.optString("type"))){emit(message);return;}
        if(!"speechComplete".equals(message.optString("type")))return;
        if(previewKey!=null&&previewKey.equals(message.optString("key"))){previewKey=null;emit(message);abandonFocus();stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf();return;}
        if(!("chapter:"+generation).equals(message.optString("key"))||state!=PlaybackState.STATE_PLAYING)return;
        if(!message.isNull("error")){fail(message.optString("error","Speech playback failed."));return;}
        if(index+1>=queue.size()){stop();return;}index++;startUnit();
    }
    private void resume(){
        if(state==PlaybackState.STATE_PLAYING||state==PlaybackState.STATE_BUFFERING)return;
        if(queue.isEmpty()){
            String id=getSharedPreferences("car-playback",MODE_PRIVATE).getString("chapter","");String pid=getSharedPreferences("car-playback",MODE_PRIVATE).getString("pid","");String previous=getSharedPreferences("car-playback",MODE_PRIVATE).getString("source","");
            if(!source.equals(previous)){id="";pid="";}
            if(id.isEmpty()&&project!=null){JSONArray chapters=project.optJSONArray("Chapters");if(chapters!=null&&chapters.length()>0)id=chapters.optJSONObject(0).optString("Id");}
            playChapter(id,pid,true);return;
        }
        if(state==PlaybackState.STATE_PAUSED&&narrator.isReady()&&narrator.canResume()){
            try{beginForeground();if(!takeFocus()){fail("Audio is in use by another app.");return;}wake.acquire(10*60*1000L);narrator.resume();state=PlaybackState.STATE_PLAYING;publish();}catch(Exception e){fail(e.getMessage());}
        }else startUnit();
    }
    private void pause(boolean focusLoss){
        if(state!=PlaybackState.STATE_PLAYING&&state!=PlaybackState.STATE_BUFFERING&&previewKey==null)return;
        if(previewKey!=null){String key=previewKey;stop();try{emit(new JSONObject().put("type","speechComplete").put("key",key).put("error","Voice preview interrupted."));}catch(JSONException ignored){}return;}
        ++planGeneration; // Cancel a pending chapter build as well as pending engine retries.
        if(state==PlaybackState.STATE_BUFFERING){++generation;narrator.stop();state=PlaybackState.STATE_STOPPED;}else {narrator.pause();state=PlaybackState.STATE_PAUSED;}
        if(!focusLoss){resumeAfterFocus=false;abandonFocus();}releaseWake();remember();publish();main.removeCallbacks(idle);main.postDelayed(idle,5*60*1000L);
    }
    private void stop(){reset(true);}
    private void reset(boolean endService){
        ++generation;++planGeneration;resumeAfterFocus=false;previewKey=null;narrator.stop();state=PlaybackState.STATE_STOPPED;queue=Collections.emptyList();index=0;abandonFocus();releaseWake();main.removeCallbacks(idle);if(endService){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;stopSelf();}publish();
    }
    private static boolean hasText(JSONObject chapter){JSONArray ps=chapter.optJSONArray("Paragraphs");if(ps!=null)for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);if(p!=null&&!p.optString("Text").trim().isEmpty())return true;}return false;}
    private void skipChapter(int direction){
        if(queue.isEmpty()){whenLoaded(()->resume());return;}int next=SpeechPlan.adjacentChapter(queue,index,direction);if(next>=0){jump(next);return;}
        // Phone chapter-only playback can still move to an adjacent chapter from car controls.
        SpeechPlan.Unit current=queue.get(index);JSONArray chapters=project==null?null:project.optJSONArray("Chapters");List<String> ids=new ArrayList<>();if(chapters!=null)for(int i=0;i<chapters.length();i++){JSONObject c=chapters.optJSONObject(i);if(c!=null&&current.bookId.equals(c.optString("BookId"))&&hasText(c))ids.add(c.optString("Id"));}int at=ids.indexOf(current.chapterId)+direction;if(at>=0&&at<ids.size())playChapter(ids.get(at),"",true);
    }
    private void skipParagraph(int delta){if(queue.isEmpty())return;int target=index;String pid=queue.get(index).pid;do{target+=delta<0?-1:1;}while(target>=0&&target<queue.size()&&pid.equals(queue.get(target).pid));if(target<0||target>=queue.size())return;if(delta<0){String p=queue.get(target).pid;while(target>0&&queue.get(target-1).pid.equals(p))target--;}jump(target);}
    private void jump(int target){++generation;narrator.stop();index=target;startUnit();}
    private void remember(){if(queue.isEmpty()||index>=queue.size())return;SpeechPlan.Unit u=queue.get(index);getSharedPreferences("car-playback",MODE_PRIVATE).edit().putString("chapter",u.chapterId).putString("pid",u.pid).putString("source",source).apply();}
    private void fail(String message){stop();error=message==null?"Playback failed.":message;state=PlaybackState.STATE_ERROR;publish();}
    private boolean takeFocus(){if(hasFocus)return true;hasFocus=audio.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;return hasFocus;}
    private void abandonFocus(){audio.abandonAudioFocusRequest(focus);hasFocus=false;}
    private void releaseWake(){if(wake.isHeld())wake.release();}
    private void beginForeground(){
        main.removeCallbacks(idle);if(foreground)return;
        startForegroundService(new Intent(this,ChapterPlaybackService.class));
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(NOTIFICATION,notification());foreground=true;
    }
    private PendingIntent openApp(){return PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    private PendingIntent action(String name){return PendingIntent.getService(this,name.hashCode(),new Intent(this,ChapterPlaybackService.class).setAction(name),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);}
    private Notification notification(){
        SpeechPlan.Unit u=queue.isEmpty()?null:queue.get(Math.min(index,queue.size()-1));boolean playing=state==PlaybackState.STATE_PLAYING||state==PlaybackState.STATE_BUFFERING;
        return new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_audio).setContentTitle(u==null?"NovelWeaver narration":u.chapter).setContentText(u==null?"Preparing speech…":u.book).setContentIntent(openApp()).setVisibility(Notification.VISIBILITY_PUBLIC).setOnlyAlertOnce(true).setOngoing(playing).setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous,"Previous chapter",action("previous")).build())
            .addAction(new Notification.Action.Builder(playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"Pause":"Play",action(playing?"pause":"play")).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next,"Next chapter",action("next")).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Stop",action("stop")).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(media.getSessionToken()).setShowActionsInCompactView(0,1,2)).build();
    }
    private Bitmap cover(String bookId){
        try{JSONArray books=project==null?null:project.optJSONArray("Books");if(books==null)return null;for(int i=0;i<books.length();i++){JSONObject b=books.optJSONObject(i);if(b==null||!bookId.equals(b.optString("Id")))continue;String data=SpeechPlan.object(b,"FrontCover").optString("DataUrl");if(data.length()>2_000_000||!data.startsWith("data:image/"))return null;int comma=data.indexOf(',');if(comma<0)return null;byte[] bytes=Base64.decode(data.substring(comma+1),Base64.DEFAULT);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);bounds.inJustDecodeBounds=false;bounds.inSampleSize=1;while(bounds.outWidth/bounds.inSampleSize>256||bounds.outHeight/bounds.inSampleSize>256)bounds.inSampleSize*=2;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);}}catch(Exception ignored){}return null;
    }
    private String artBook;private Bitmap art;
    // MediaStyle notifications with a MediaSession are exempt from POST_NOTIFICATIONS.
    @android.annotation.SuppressLint("NotificationPermission")
    private void publish(){
        if(media==null)return;long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_FROM_MEDIA_ID|PlaybackState.ACTION_PLAY_FROM_SEARCH|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_STOP|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        PlaybackState.Builder playback=new PlaybackState.Builder().setActions(actions).setState(state,PlaybackState.PLAYBACK_POSITION_UNKNOWN,state==PlaybackState.STATE_PLAYING?1:0);
        if(state==PlaybackState.STATE_ERROR)playback.setErrorMessage(error);media.setPlaybackState(playback.build());
        if(!queue.isEmpty()){
            SpeechPlan.Unit u=queue.get(index);MediaMetadata.Builder meta=new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"chapter:"+u.chapterId).putString(MediaMetadata.METADATA_KEY_TITLE,u.chapter).putString(MediaMetadata.METADATA_KEY_ARTIST,u.book).putString(MediaMetadata.METADATA_KEY_ALBUM,project==null?"NovelWeaver":project.optString("Title"));
            if(!u.bookId.equals(artBook)){artBook=u.bookId;art=cover(u.bookId);}if(art!=null)meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,art);media.setMetadata(meta.build());
        }
        if(foreground)getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());emit(snapshot());
    }
    JSONObject snapshot(){JSONObject r=new JSONObject();try{r.put("type","mediaState").put("playing",state==PlaybackState.STATE_PLAYING||state==PlaybackState.STATE_BUFFERING).put("paused",state==PlaybackState.STATE_PAUSED).put("error",state==PlaybackState.STATE_ERROR?error:"");if(!queue.isEmpty()){SpeechPlan.Unit u=queue.get(index);r.put("pid",u.pid).put("chapterId",u.chapterId).put("bookId",u.bookId);}}catch(JSONException ignored){}return r;}
    private void emit(JSONObject message){for(Listener listener:new ArrayList<>(listeners))listener.post(message);}
    private void emitVoices(){try{emit(new JSONObject().put("type","voices").put("voices",voices()));}catch(JSONException ignored){}}
    @Override public void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);narrator.destroy();abandonFocus();releaseWake();unregisterReceiver(noisy);listeners.clear();waiting.clear();io.shutdownNow();media.release();super.onDestroy();}
}
