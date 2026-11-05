package com.novelweaver.android;

import android.content.*;
import android.os.Build;
import android.view.KeyEvent;

/** Android's explicit restart target for the last-used narration session. */
public final class HeadsetButtonReceiver extends BroadcastReceiver {
    static boolean isMediaKey(Intent intent){
        if(intent==null||!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction()))return false;
        KeyEvent event=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if(event==null)return false;
        switch(event.getKeyCode()){
            case KeyEvent.KEYCODE_HEADSETHOOK:case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:return true;
            default:return false;
        }
    }
    @Override public void onReceive(Context context,Intent intent){
        if(!isMediaKey(intent))return;
        KeyEvent event=intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if(event.getAction()!=KeyEvent.ACTION_DOWN||event.getRepeatCount()!=0)return;
        Intent command=new Intent(context,ChapterPlaybackService.class)
            .setAction(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT,event);
        try{
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(command);else context.startService(command);
        }catch(IllegalStateException ignored){
            // Android can reject a restart after an explicit force-stop; the user
            // can reopen the app. Do not crash the receiver in that case.
        }
    }
}
