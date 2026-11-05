package com.novelweaver.android;

import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import android.speech.tts.*;
import android.view.KeyEvent;
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
    Intent button(int action,int code,int repeat){return new Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT,new KeyEvent(0,0,action,code,repeat));}
    void press(int code){service.onStartCommand(button(KeyEvent.ACTION_DOWN,code,0),0,1);service.onStartCommand(button(KeyEvent.ACTION_UP,code,0),0,2);shadowOf(Looper.getMainLooper()).idle();}
    @Test public void headsetTogglePausesAndResumesExactlyOncePerPress() throws Exception {
        play();int calls=engine.calls;press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);assertTrue(service.snapshot().getBoolean("paused"));assertEquals(calls,engine.calls);
        press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);assertTrue(service.snapshot().getBoolean("playing"));assertEquals(calls+1,engine.calls);
        press(KeyEvent.KEYCODE_HEADSETHOOK);assertTrue(service.snapshot().getBoolean("paused"));
        press(KeyEvent.KEYCODE_HEADSETHOOK);assertTrue(service.snapshot().getBoolean("playing"));
    }
    @Test public void headsetDedicatedKeysAndRepeatEventsDoNotToggleTwice() throws Exception {
        play();press(KeyEvent.KEYCODE_MEDIA_PAUSE);assertTrue(service.snapshot().getBoolean("paused"));
        service.handleMediaButton(button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,1));assertTrue(service.snapshot().getBoolean("paused"));
        press(KeyEvent.KEYCODE_MEDIA_PLAY);assertTrue(service.snapshot().getBoolean("playing"));int calls=engine.calls;
        press(KeyEvent.KEYCODE_MEDIA_PLAY);assertEquals(calls,engine.calls);
        press(KeyEvent.KEYCODE_MEDIA_STOP);assertFalse(service.snapshot().getBoolean("playing"));assertFalse(service.snapshot().getBoolean("paused"));
    }
    @Test public void headsetNextAndPreviousSelectChapters() throws Exception {
        play();press(KeyEvent.KEYCODE_MEDIA_NEXT);assertEquals("c2",service.snapshot().getString("chapterId"));
        press(KeyEvent.KEYCODE_MEDIA_PREVIOUS);assertEquals("c1",service.snapshot().getString("chapterId"));
    }
    @Test public void unrelatedOrMalformedKeysDoNotChangePlayback() throws Exception {
        play();assertFalse(service.handleMediaButton(new Intent(Intent.ACTION_MEDIA_BUTTON)));
        assertFalse(service.handleMediaButton(button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP,0)));
        assertFalse(service.handleMediaButton(new Intent("unexpected").putExtra(Intent.EXTRA_KEY_EVENT,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PAUSE))));
        assertTrue(service.snapshot().getBoolean("playing"));
    }
    @Test public void restartReceiverForwardsOnlyOneSupportedDownEvent() {
        Context context=RuntimeEnvironment.getApplication();ShadowApplication app=shadowOf(RuntimeEnvironment.getApplication());while(app.getNextStartedService()!=null){}
        HeadsetButtonReceiver receiver=new HeadsetButtonReceiver();
        receiver.onReceive(context,button(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0));
        receiver.onReceive(context,button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,1));
        receiver.onReceive(context,button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP,0));
        receiver.onReceive(context,new Intent(Intent.ACTION_MEDIA_BUTTON));assertNull(app.getNextStartedService());
        receiver.onReceive(context,button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0));
        Intent forwarded=app.getNextStartedService();assertNotNull(forwarded);assertEquals(new ComponentName(context,ChapterPlaybackService.class),forwarded.getComponent());
        assertEquals(Intent.ACTION_MEDIA_BUTTON,forwarded.getAction());assertEquals(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,((KeyEvent)forwarded.getParcelableExtra(Intent.EXTRA_KEY_EVENT)).getKeyCode());assertNull(app.getNextStartedService());
    }
    @Test public void foregroundEditorUsesNarrationMediaController() {
        android.app.Activity activity=Robolectric.buildActivity(android.app.Activity.class).setup().get();
        PlaybackClient client=new PlaybackClient(activity,message->{});
        ServiceConnection connection=ReflectionHelpers.getField(client,"connection");
        connection.onServiceConnected(new ComponentName(activity,ChapterPlaybackService.class),service.onBind(new Intent(ChapterPlaybackService.LOCAL)));
        assertNotNull(activity.getMediaController());assertSame(service.mediaController(),activity.getMediaController());
        client.destroy();assertNull(activity.getMediaController());activity.finish();
    }
    @Test public void mediaSessionAdvertisesToggleAndRestartReceiver() throws Exception {
        MediaSession session=ReflectionHelpers.getField(service,"media");
        PlaybackState state=ReflectionHelpers.getField(session,"mPlaybackState");assertTrue((state.getActions()&PlaybackState.ACTION_PLAY_PAUSE)!=0);
        // Robolectric doesn't populate PlaybackInfo for platform MediaSession;
        // audio routing still requires a physical-device check.
        assertTrue(service.getPackageManager().getReceiverInfo(new ComponentName(service,HeadsetButtonReceiver.class),0).exported);
    }
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
    @Test public void headsetCanRestoreSavedProjectAndResumeWithoutActivity() throws Exception {
        play();service.command("speechPause",new JSONObject());controller.destroy();
        Context context=RuntimeEnvironment.getApplication();File file=new File(context.getFilesDir(),"headset.novel.json");
        ProjectSession.atomic(file,project().toString());String uri=android.net.Uri.fromFile(file).toString();
        ProjectSession.atomic(new File(context.getFilesDir(),"last-project.json"),new JSONObject().put("uri",uri).toString());
        context.getSharedPreferences("car-playback",0).edit().putString("source",uri).commit();
        ShadowApplication app=shadowOf(RuntimeEnvironment.getApplication());while(app.getNextStartedService()!=null){}
        new HeadsetButtonReceiver().onReceive(context,button(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0));
        Intent command=app.getNextStartedService();assertNotNull(command);
        controller=Robolectric.buildService(ChapterPlaybackService.class).create();service=controller.get();
        engine=(ControlledSpeech)shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());engine.getOnInitListener().onInit(TextToSpeech.SUCCESS);
        service.onStartCommand(command,0,1);pump(()->engine.calls>0&&service.snapshot().optBoolean("playing"));
        assertEquals("c1",service.snapshot().getString("chapterId"));assertEquals("He said, ",engine.spoken);
        assertNotNull(shadowOf(service.getSystemService(android.app.NotificationManager.class)).getNotification(42));
    }
}
