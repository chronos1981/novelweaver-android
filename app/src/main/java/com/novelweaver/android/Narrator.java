package com.novelweaver.android;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.*;
import android.speech.tts.*;
import org.json.*;
import java.io.File;
import java.util.Locale;

/** TTS produces one private audio clip; the app owns its actual media playback.
 * Direct TTS playback belongs to the engine UID and can leave Bluetooth buttons
 * routed to another app's last-used MediaSession. */
final class Narrator {
    interface Events {void post(JSONObject message);}
    private final TextToSpeech tts;
    private final Events events;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final File clips;
    private volatile boolean ready;
    private boolean paused,prepared,destroyed;
    private String key,token;
    private File clip;
    private MediaPlayer player;
    private int sequence;
    Narrator(Context context,Events events){
        this.events=events;this.context=context.getApplicationContext();
        clips=new File(context.getCacheDir(),"narration-audio");clips.mkdirs();
        File[] old=clips.listFiles();if(old!=null)for(File f:old)if(f.getName().startsWith("speech-")&&f.getName().endsWith(".wav"))f.delete();
        tts=new TextToSpeech(context,status->{ready=status==TextToSpeech.SUCCESS;main.post(this::sendVoices);});
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}
            public void onDone(String id){main.post(()->synthesized(id));}
            public void onError(String id){main.post(()->complete(id,"Speech synthesis failed."));}
            public void onError(String id,int code){main.post(()->complete(id,"Speech synthesis failed ("+code+")."));}
        });
    }
    JSONArray voices(){
        JSONArray list=new JSONArray();if(!ready)return list;
        try{for(Voice v:tts.getVoices())list.put(new JSONObject().put("id",v.getName()).put("name",v.getName()).put("local",!v.isNetworkConnectionRequired()).put("lang",v.getLocale().toLanguageTag()));}catch(Exception ignored){}
        return list;
    }
    boolean isReady(){return ready;}
    boolean canResume(){return paused&&key!=null;}
    private void sendVoices(){if(destroyed)return;try{events.post(new JSONObject().put("type","voices").put("voices",voices()));}catch(Exception ignored){}}
    void speak(JSONObject p) throws Exception {
        if(!ready)throw new Exception("Speech engine is unavailable or still starting. Check Android text-to-speech settings.");
        stop();String voice=p.optString("voice","");
        if(voice.isEmpty()){Voice fallback=tts.getDefaultVoice();if(fallback!=null)tts.setVoice(fallback);else tts.setLanguage(Locale.getDefault());}
        else {boolean found=false;for(Voice v:tts.getVoices())if(v.getName().equals(voice)){tts.setVoice(v);found=true;break;}if(!found)throw new Exception("The selected speech voice is no longer installed.");}
        tts.setSpeechRate(setting(p,"rate"));tts.setPitch(setting(p,"pitch"));
        key=p.getString("key");token=key+":"+(++sequence);paused=false;
        clip=File.createTempFile("speech-",".wav",clips);
        if(tts.synthesizeToFile(p.getString("text"),new Bundle(),clip,token)==TextToSpeech.ERROR){stop();throw new Exception("Android could not generate speech audio.");}
    }
    private boolean current(String id){return !destroyed&&id!=null&&id.equals(token)&&key!=null;}
    private void synthesized(String id){
        if(!current(id)||player!=null)return;
        try{
            MediaPlayer audio=new MediaPlayer();player=audio;prepared=false;
            audio.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            audio.setWakeMode(context,PowerManager.PARTIAL_WAKE_LOCK);
            audio.setOnPreparedListener(p->{if(current(id)&&p==player){prepared=true;if(!paused)startAudio(id);}});
            audio.setOnCompletionListener(p->{if(p==player)complete(id,null);});
            audio.setOnErrorListener((p,what,extra)->{if(p==player)complete(id,"Speech audio playback failed ("+what+").");return true;});
            audio.setDataSource(clip.getAbsolutePath());audio.prepareAsync();
        }catch(Exception e){complete(id,"Could not play generated speech audio.");}
    }
    private void startAudio(String id){
        if(!current(id)||!prepared||paused)return;
        try{player.start();events.post(new JSONObject().put("type","speechStarted").put("key",key));}
        catch(Exception e){complete(id,"Could not start speech audio.");}
    }
    void pause(){if(key==null)return;paused=true;if(player!=null&&prepared)player.pause();}
    static float setting(JSONObject p,String name){double value=p.optDouble(name,1);return Double.isNaN(value)||Double.isInfinite(value)?1:(float)Math.max(.5,Math.min(2,value));}
    void resume(){if(!canResume())return;paused=false;if(prepared)startAudio(token);}
    void stop(){key=null;token=null;paused=false;tts.stop();releaseAudio();}
    private void releaseAudio(){prepared=false;if(player!=null){player.release();player=null;}if(clip!=null){clip.delete();clip=null;}}
    private void complete(String id,String error){
        if(!current(id))return;String completed=key;key=null;token=null;paused=false;releaseAudio();
        try{events.post(new JSONObject().put("type","speechComplete").put("key",completed).put("error",error==null?JSONObject.NULL:error));}catch(Exception ignored){}
    }
    void destroy(){destroyed=true;stop();tts.shutdown();main.removeCallbacksAndMessages(null);}
}
