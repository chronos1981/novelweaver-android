package com.novelweaver.android;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.*;
import org.json.*;
import java.util.Locale;

final class Narrator {
    interface Events {void post(JSONObject message);}
    private final TextToSpeech tts;
    private final Events events;
    private volatile boolean ready,paused;
    private volatile String key,token;
    private String text="",voice="";
    private volatile int offset;
    private int base,sequence;
    private float rate=1,pitch=1;
    Narrator(Context context,Events events){
        this.events=events;
        tts=new TextToSpeech(context,status->{ready=status==TextToSpeech.SUCCESS;sendVoices();});
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){}
            public void onDone(String id){complete(id,null);}
            public void onError(String id){complete(id,"Speech playback failed.");}
            public void onRangeStart(String id,int start,int end,int frame){if(id.equals(token))offset=base+start;}
        });
    }
    JSONArray voices(){
        JSONArray list=new JSONArray();if(!ready)return list;
        try{for(Voice v:tts.getVoices())list.put(new JSONObject().put("id",v.getName()).put("name",v.getName()+(v.isNetworkConnectionRequired()?" (online)":"")).put("lang",v.getLocale().toLanguageTag()));}catch(Exception ignored){}
        return list;
    }
    private void sendVoices(){try{events.post(new JSONObject().put("type","voices").put("voices",voices()));}catch(Exception ignored){}}
    void speak(JSONObject p) throws Exception {
        if(!ready)throw new Exception("Speech engine is unavailable or still starting. Check Android text-to-speech settings.");
        stop();text=p.getString("text");voice=p.optString("voice","");rate=setting(p,"rate");pitch=setting(p,"pitch");key=p.getString("key");offset=0;play();
    }
    private void play() throws Exception {
        if(voice.isEmpty())tts.setLanguage(Locale.getDefault());
        else {boolean found=false;for(Voice v:tts.getVoices())if(v.getName().equals(voice)){tts.setVoice(v);found=true;break;}if(!found)throw new Exception("The selected speech voice is no longer installed.");}
        tts.setSpeechRate(rate);tts.setPitch(pitch);paused=false;base=Math.min(offset,text.length());token=key+":"+(++sequence);
        if(tts.speak(text.substring(base),TextToSpeech.QUEUE_FLUSH,new Bundle(),token)==TextToSpeech.ERROR)throw new Exception("Android could not start speech playback.");
    }
    void pause(){paused=true;token=null;tts.stop();}
    static float setting(JSONObject p,String name){double value=p.optDouble(name,1);return Double.isNaN(value)||Double.isInfinite(value)?1:(float)Math.max(.5,Math.min(2,value));}
    void resume() throws Exception {if(paused&&key!=null)play();}
    void stop(){key=null;token=null;paused=false;tts.stop();}
    private void complete(String id,String error){
        if(id==null||!id.equals(token)||paused||key==null)return;
        String completed=key;key=null;token=null;
        try{events.post(new JSONObject().put("type","speechComplete").put("key",completed).put("error",error==null?JSONObject.NULL:error));}catch(Exception ignored){}
    }
    void destroy(){stop();tts.shutdown();}
}
