package com.novelweaver.android;

import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.speech.tts.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.util.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={28,35},shadows=ChapterPlaybackServiceTest.ControlledSpeech.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ChapterPlaybackServiceTest {
    @Implements(TextToSpeech.class)
    public static class ControlledSpeech extends ShadowTextToSpeech {
        String spoken,key;int calls;
        @Implementation protected int speak(CharSequence text,int mode,Bundle params,String id){spoken=text.toString();key=id;calls++;return TextToSpeech.SUCCESS;}
    }
    ServiceController<ChapterPlaybackService> controller;ChapterPlaybackService service;ControlledSpeech engine;
    JSONObject project() throws Exception {return new JSONObject("{\"Id\":\"project\",\"Title\":\"Series\",\"Books\":[{\"Id\":\"b\",\"Title\":\"Book\"}],\"SpeechDefaults\":{\"Narration\":{\"Voice\":\"narrator\"},\"Dialogue\":{\"Voice\":\"dialogue\"}},\"Chapters\":[{\"Id\":\"c1\",\"Title\":\"First\",\"BookId\":\"b\",\"Paragraphs\":[{\"Id\":\"p1\",\"Text\":\"He said, “Hello.” Then left.\"},{\"Id\":\"p2\",\"Text\":\"Next paragraph.\"}]},{\"Id\":\"c2\",\"Title\":\"Second\",\"BookId\":\"b\",\"Paragraphs\":[{\"Id\":\"p3\",\"Text\":\"Next chapter.\"}]}]}");}
    @Before public void setup() throws Exception {
        Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("car-playback",0).edit().clear().commit();new File(c.getFilesDir(),"last-project.json").delete();
        controller=Robolectric.buildService(ChapterPlaybackService.class).create();service=controller.get();
        engine=(ControlledSpeech)shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());
        for(String name:new String[]{"narrator","dialogue"})ShadowTextToSpeech.addVoice(new Voice(name,Locale.US,400,200,false,new HashSet<>()));
        engine.getOnInitListener().onInit(TextToSpeech.SUCCESS);service.setProject(project(),"content://project");shadowOf(Looper.getMainLooper()).idle();
    }
    @After public void cleanup(){controller.destroy();}
    void pump(BooleanSupplier condition) throws Exception {long end=System.currentTimeMillis()+4000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<end){shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5);}shadowOf(Looper.getMainLooper()).idle();assertTrue("Playback did not reach expected state",condition.getAsBoolean());}
    void play() throws Exception {service.command("mediaPlay",new JSONObject("{\"chapterId\":\"c1\",\"wholeBook\":true}"));pump(()->service.snapshot().optBoolean("playing")&&engine.calls>0);}
    void done(){engine.getUtteranceProgressListener().onDone(engine.key);shadowOf(Looper.getMainLooper()).idle();}
    @Test public void backgroundQueueSwitchesVoicesWithoutAnActivity() throws Exception {
        play();assertEquals("He said, ",engine.spoken);assertEquals("narrator",engine.getCurrentVoice().getName());done();assertEquals("“Hello.”",engine.spoken);assertEquals("dialogue",engine.getCurrentVoice().getName());done();assertEquals(" Then left.",engine.spoken);assertEquals("narrator",engine.getCurrentVoice().getName());done();assertEquals("Next paragraph.",engine.spoken);done();assertEquals("c2",service.snapshot().optString("chapterId"));assertEquals("Next chapter.",engine.spoken);done();assertFalse(service.snapshot().optBoolean("playing"));
    }
    @Test public void pauseResumeAndStaleCompletionCannotAdvance() throws Exception {
        play();String old=engine.key;service.command("speechPause",new JSONObject());assertTrue(service.snapshot().getBoolean("paused"));engine.getUtteranceProgressListener().onDone(old);shadowOf(Looper.getMainLooper()).idle();assertEquals(1,engine.calls);
        service.command("speechResume",new JSONObject());assertTrue(service.snapshot().getBoolean("playing"));assertEquals(2,engine.calls);assertEquals("He said, ",engine.spoken);
        service.command("speechStop",new JSONObject());engine.getUtteranceProgressListener().onDone(engine.key);shadowOf(Looper.getMainLooper()).idle();assertFalse(service.snapshot().getBoolean("playing"));assertEquals(2,engine.calls);
    }
    @Test public void notificationNextPreviousAndAudioInterruption() throws Exception {
        play();service.onStartCommand(new Intent().setAction("next"),0,1);assertEquals("c2",service.snapshot().optString("chapterId"));service.onStartCommand(new Intent().setAction("previous"),0,2);assertEquals("c1",service.snapshot().optString("chapterId"));
        AudioManager.OnAudioFocusChangeListener listener=ReflectionHelpers.getField(service,"focusChange");listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);assertTrue(service.snapshot().getBoolean("paused"));listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);assertTrue(service.snapshot().getBoolean("playing"));
        service.sendBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));shadowOf(Looper.getMainLooper()).idle();assertTrue(service.snapshot().getBoolean("paused"));
    }
    @Test public void phoneListenerCanDisconnectAndNarrationContinues() throws Exception {
        List<JSONObject> messages=new ArrayList<>();ChapterPlaybackService.Listener listener=messages::add;service.addListener(listener);play();service.removeListener(listener);int seen=messages.size();done();assertEquals("“Hello.”",engine.spoken);assertTrue(service.snapshot().getBoolean("playing"));assertEquals(seen,messages.size());
        MediaSession session=ReflectionHelpers.getField(service,"media");assertEquals("First",shadowOf(service.getSystemService(android.app.NotificationManager.class)).getNotification(42).extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString());assertEquals(PlaybackState.STATE_PLAYING,((PlaybackState)ReflectionHelpers.getField(session,"mPlaybackState")).getState());
    }
    @Test public void browserRejectsUntrustedClientsAndDoesNotAutoplay() {
        int uid=service.getApplicationInfo().uid;shadowOf(service.getPackageManager()).setPackagesForUid(uid,service.getPackageName());assertNotNull(service.onGetRoot(service.getPackageName(),uid,null));assertNull(service.onGetRoot("untrusted.app",12345,null));assertFalse(service.snapshot().optBoolean("playing"));assertEquals(0,engine.calls);
    }
    @Test public void browserContainsBooksAndPlayableChapters() {
        List<android.media.browse.MediaBrowser.MediaItem> books=ReflectionHelpers.callInstanceMethod(service,"children",ReflectionHelpers.ClassParameter.from(String.class,"library"));
        assertEquals(1,books.size());assertTrue(books.get(0).isBrowsable());assertEquals("Book",books.get(0).getDescription().getTitle().toString());
        List<android.media.browse.MediaBrowser.MediaItem> chapters=ReflectionHelpers.callInstanceMethod(service,"children",ReflectionHelpers.ClassParameter.from(String.class,"book:b"));
        assertEquals(2,chapters.size());assertTrue(chapters.get(0).isPlayable());assertEquals("chapter:c1",chapters.get(0).getMediaId());
    }
    @Test public void coldStartReadsActualLastFileWithoutActivity() throws Exception {
        controller.destroy();Context c=RuntimeEnvironment.getApplication();File file=new File(c.getFilesDir(),"actual.novel.json");JSONObject disk=project();disk.getJSONArray("Chapters").getJSONObject(0).getJSONArray("Paragraphs").getJSONObject(0).put("Text","Updated on disk.");ProjectSession.atomic(file,disk.toString());ProjectSession.atomic(new File(c.getFilesDir(),"last-project.json"),new JSONObject().put("uri",android.net.Uri.fromFile(file).toString()).toString());
        controller=Robolectric.buildService(ChapterPlaybackService.class).create();service=controller.get();engine=(ControlledSpeech)shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());engine.getOnInitListener().onInit(TextToSpeech.SUCCESS);
        service.onStartCommand(new Intent().setAction("play"),0,1);pump(()->engine.calls>0);assertEquals("Updated on disk.",engine.spoken);
    }
}
