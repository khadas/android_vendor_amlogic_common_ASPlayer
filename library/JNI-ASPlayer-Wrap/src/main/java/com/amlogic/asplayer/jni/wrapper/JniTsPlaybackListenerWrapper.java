package com.amlogic.asplayer.jni.wrapper;

import android.util.Log;

import com.amlogic.asplayer.api.EventType;
import com.amlogic.asplayer.api.StreamType;
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
            PlaybackInfoEvent ev = (PlaybackInfoEvent) event;
            int streamType = ev.getStreamType();

            switch (event.getEventType()) {
                case EventType.EVENT_TYPE_DECODER_INIT_COMPLETED:
                    if (streamType == StreamType.VIDEO) {
                        ev = new VideoDecoderInitCompletedEvent();
                    } else {
                        ev = (PlaybackInfoEvent) event;
                    }
                    break;
                case EventType.EVENT_TYPE_DECODER_DATA_LOSS:
                    ev = new DecoderDataLossEvent(ev.getStreamType());
                    break;
                case EventType.EVENT_TYPE_DECODER_DATA_RESUME:
                    ev = new DecoderDataResumeEvent(ev.getStreamType());
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
