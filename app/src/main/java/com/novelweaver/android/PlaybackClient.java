package com.novelweaver.android;

import android.app.Activity;
import android.content.*;
import android.os.IBinder;
import org.json.*;
import java.util.*;
import java.util.function.Consumer;

/** A bound editor client; disconnecting the editor never destroys active narration. */
final class PlaybackClient {
    interface Command {void run(ChapterPlaybackService service) throws Exception;}
    private final Context context;
    private final ChapterPlaybackService.Listener listener;
    private final List<Runnable> pending=new ArrayList<>();
    private ChapterPlaybackService service;
    private boolean closed,bound;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(closed)return;service=((ChapterPlaybackService.LocalBinder)binder).service();
            if(context instanceof Activity)((Activity)context).setMediaController(service.mediaController());
            service.addListener(listener);
            List<Runnable> work=new ArrayList<>(pending);pending.clear();for(Runnable task:work)task.run();
        }
        @Override public void onServiceDisconnected(ComponentName name){if(context instanceof Activity)((Activity)context).setMediaController(null);service=null;}
    };
    PlaybackClient(Context context,ChapterPlaybackService.Listener listener){this.context=context;this.listener=listener;bound=context.bindService(new Intent(context,ChapterPlaybackService.class).setAction(ChapterPlaybackService.LOCAL),connection,Context.BIND_AUTO_CREATE);}
    JSONArray voices(){return service==null?new JSONArray():service.voices();}
    void run(Command command,Consumer<Exception> error){
        if(closed||!bound){error.accept(new IllegalStateException("Narration service is unavailable. Reopen NovelWeaver."));return;}
        Runnable action=()->{try{command.run(service);}catch(Exception e){error.accept(e);}};
        if(service==null)pending.add(action);else action.run();
    }
    void project(JSONObject value,String uri){run(s->s.setProject(value,uri),e->{});}
    void command(String action,JSONObject payload,Runnable done,Consumer<Exception> error){run(s->{s.command(action,payload);done.run();},error);}
    void destroy(){closed=true;pending.clear();if(context instanceof Activity)((Activity)context).setMediaController(null);if(service!=null)service.removeListener(listener);if(bound)context.unbindService(connection);service=null;}
}
