package com.amlogic.asplayer.jni.wrapper;

import android.util.Log;

import com.amlogic.asplayer.api.EventType;
import com.amlogic.asplayer.api.TsPlaybackListener;

public class JniTsPlaybackListenerWrapper implements TsPlaybackListener {

    private static final String TAG = "JniTsPlaybackListenerWrapper";

    public TsPlaybackListener mRealPlaybackListener;

    public JniTsPlaybackListenerWrapper(TsPlaybackListener playbackListener) {
        mRealPlaybackListener = playbackListener;
    }

    @Override
    public void onPlaybackEvent(PlaybackEvent event) {
        if (mRealPlaybackListener == null) {
            return;
        }

        if (event instanceof PlaybackInfoEvent) {
            // change playback info event to sub event
            PlaybackInfoEvent ev = null;

            switch (event.getEventType()) {
                case EventType.EVENT_TYPE_VIDEO_DECODER_INIT_COMPLETED:
                    ev = new VideoDecoderInitCompletedEvent();
                    break;
                default:
                    Log.d(TAG, "unknown PlaybackInfoEvent");
                    break;
            }

            if (ev != null) {
                mRealPlaybackListener.onPlaybackEvent(ev);
                return;
            }
        }

        mRealPlaybackListener.onPlaybackEvent(event);
    }
}
