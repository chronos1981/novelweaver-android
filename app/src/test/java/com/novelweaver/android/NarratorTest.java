package com.novelweaver.android;

import android.media.*;
import android.os.Looper;
import android.speech.tts.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35},shadows=ChapterPlaybackServiceTest.ControlledSpeech.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class NarratorTest {
    Narrator narrator;ChapterPlaybackServiceTest.ControlledSpeech engine;
    List<JSONObject> events=new ArrayList<>();
    @Before public void setup(){
        ShadowMediaPlayer.setMediaInfoProvider(source->new ShadowMediaPlayer.MediaInfo(60000,0));
        narrator=new Narrator(RuntimeEnvironment.getApplication(),events::add);
        engine=(ChapterPlaybackServiceTest.ControlledSpeech)shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());
        ShadowTextToSpeech.addVoice(new Voice("voice",Locale.US,400,200,false,new HashSet<>()));
        engine.getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();events.clear();
    }
    @After public void cleanup(){narrator.destroy();}
    void idle(){shadowOf(Looper.getMainLooper()).idle();}
    void speak(String key) throws Exception {narrator.speak(new JSONObject().put("key",key).put("text","A spoken test.").put("voice","voice").put("rate",1.2).put("pitch",.9));}
    MediaPlayer player(){return ReflectionHelpers.getField(narrator,"player");}
    File clip(){return ReflectionHelpers.getField(narrator,"clip");}
    long count(String type){return events.stream().filter(e->type.equals(e.optString("type"))).count();}
    @Test public void synthesisFeedsAppPlayerAndOnlyAudioCompletionFinishesSpeech() throws Exception {
        engine.autoSynthesize=false;speak("one");File file=clip();assertTrue(file.exists());assertNull(player());assertEquals(0,count("speechComplete"));
        engine.getUtteranceProgressListener().onDone(engine.key);idle();assertNotNull(player());assertTrue(player().isPlaying());
        assertEquals(AudioAttributes.USAGE_MEDIA,shadowOf(player()).getAudioAttributes().getUsage());
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH,shadowOf(player()).getAudioAttributes().getContentType());
        assertEquals(1,count("speechStarted"));assertEquals(0,count("speechComplete"));
        shadowOf(player()).invokeCompletionListener();assertEquals(1,count("speechComplete"));assertFalse(file.exists());assertNull(player());
    }
    @Test public void pauseResumeKeepsExactPlayerPositionAndDoesNotResynthesize() throws Exception {
        speak("one");idle();MediaPlayer audio=player();shadowOf(audio).setCurrentPosition(4321);
        narrator.pause();assertFalse(audio.isPlaying());assertTrue(narrator.canResume());
        narrator.resume();assertSame(audio,player());assertTrue(audio.isPlaying());assertEquals(4321,audio.getCurrentPosition());assertEquals(1,engine.calls);
    }
    @Test public void pausingDuringSynthesisPreventsAudioUntilResumed() throws Exception {
        engine.autoSynthesize=false;speak("one");narrator.pause();
        engine.getUtteranceProgressListener().onDone(engine.key);idle();assertNotNull(player());assertFalse(player().isPlaying());assertEquals(0,count("speechStarted"));
        narrator.resume();assertTrue(player().isPlaying());assertEquals(1,engine.calls);
    }
    @Test public void replacedAudioAndLateSynthesisCannotFinishNewUtterance() throws Exception {
        speak("old");idle();String old=engine.key;MediaPlayer previous=player();File oldFile=clip();
        speak("new");idle();assertFalse(oldFile.exists());MediaPlayer current=player();assertTrue(current.isPlaying());
        shadowOf(previous).invokeCompletionListener();engine.getUtteranceProgressListener().onDone(old);engine.getUtteranceProgressListener().onError(old);idle();
        assertSame(current,player());assertTrue(current.isPlaying());assertEquals(0,count("speechComplete"));
        shadowOf(current).invokeCompletionListener();assertEquals("new",events.get(events.size()-1).optString("key"));
    }
    @Test public void synthesisStartFailureRemovesTemporaryClip() throws Exception {
        engine.synthResult=TextToSpeech.ERROR;
        try{speak("bad");fail("Expected synthesis rejection");}catch(Exception expected){assertTrue(expected.getMessage().contains("generate speech"));}
        assertNull(clip());assertNull(player());assertFalse(narrator.canResume());
        File dir=new File(RuntimeEnvironment.getApplication().getCacheDir(),"narration-audio");assertEquals(0,Objects.requireNonNull(dir.list()).length);
    }
    @Test public void playbackErrorIsReportedAndAudioFileIsRemoved() throws Exception {
        speak("bad");idle();File file=clip();shadowOf(player()).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN,0);
        assertFalse(file.exists());assertNull(player());assertEquals(1,count("speechComplete"));assertFalse(events.get(events.size()-1).isNull("error"));
    }
    @Test public void destructionReleasesPlayerAndIgnoresLateEngineCallbacks() throws Exception {
        speak("one");idle();File file=clip();String old=engine.key;narrator.destroy();engine.getUtteranceProgressListener().onDone(old);idle();
        assertFalse(file.exists());assertNull(player());assertEquals(0,count("speechComplete"));
    }
}
