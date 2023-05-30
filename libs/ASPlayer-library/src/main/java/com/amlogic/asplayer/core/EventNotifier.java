package com.amlogic.asplayer.core;

import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.amlogic.asplayer.api.EventType;
import com.amlogic.asplayer.api.StreamType;
import com.amlogic.asplayer.api.TsPlaybackListener;

import static com.amlogic.asplayer.api.TsPlaybackListener.AudioFirstFrameEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.AudioFormatChangeEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.DecodeFirstAudioFrameEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.DecodeFirstVideoFrameEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.VideoFirstFrameEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.VideoFormatChangeEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.PtsEvent;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArraySet;


class EventNotifier {

    // Should be careful enabling this since may produce many traces!
    private static final boolean LOCAL_LOG_NOTIFICATIONS = true;

    private final Handler mEventHandler;

    final CopyOnWriteArraySet<TsPlaybackListener> mPlaybackListeners;

    private static class WeakHandler extends Handler {
        private final WeakReference<EventNotifier> mOwner;

        WeakHandler(EventNotifier owner, Looper looper) {
            super(looper);
            mOwner = new WeakReference<>(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            EventNotifier owner = mOwner.get();
            if (owner != null) {
                owner.notifyEvent(msg);
            }
        }
    }

    private int mId;

    EventNotifier(int id, Looper looper) {
        mId = id;
        mPlaybackListeners = new CopyOnWriteArraySet<>();
        mEventHandler = new WeakHandler(this, looper);
    }

    void release() {
        mEventHandler.removeCallbacksAndMessages(null);
    }

    void start() {
    }

    void stop() {
    }

    void notifyVideoFormatChange(MediaFormat videoFormat) {
        postEvent(EventType.EVENT_TYPE_VIDEO_CHANGED, videoFormat);
    }

    void notifyAudioFormatChange(MediaFormat audioFormat) {
        postEvent(EventType.EVENT_TYPE_AUDIO_CHANGED, audioFormat);
    }

    void notifyRenderFirstVideoFrame(long positionMs) {
        postEvent(EventType.EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO,
                new VideoFirstFrameEvent(positionMs));
    }

    void notifyRenderFirstAudioFrame(long positionMs) {
        postEvent(EventType.EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO,
                new AudioFirstFrameEvent(positionMs));
    }

    void notifyDecodeFirstVideoFrame(long positionMs) {
        postEvent(EventType.EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO,
                new DecodeFirstVideoFrameEvent(positionMs));
    }

    void notifyDecodeFirstAudioFrame(long positionMs) {
        postEvent(EventType.EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO,
                new DecodeFirstAudioFrameEvent(positionMs));
    }

    void notifyFrameRendered(int type, long presentationTimeUs, long renderTime) {
         postEvent(EventType.EVENT_TYPE_PTS, new PtsEvent(type, presentationTimeUs, renderTime));
    }

    void notifyVideoFrameRendered(long presentationTimeUs, long renderTime) {
        notifyFrameRendered(StreamType.VIDEO, presentationTimeUs, renderTime);
    }

    void notifyAudioFrameRendered(long presentationTimeUs, long renderTime) {
        notifyFrameRendered(StreamType.AUDIO, presentationTimeUs, renderTime);
    }

    private void postEvent(int eventType, Object object) {
        mEventHandler.obtainMessage(eventType, object).sendToTarget();
    }

    private void notifyEvent(Message msg) {
        final int eventType = msg.what;

        switch (eventType) {
            case EventType.EVENT_TYPE_VIDEO_CHANGED: {
                MediaFormat videoFormat = (MediaFormat) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: VideoFormatChangeEvent(%s)", mId, videoFormat);
                }
                for (TsPlaybackListener listener : mPlaybackListeners) {
                    listener.onPlaybackEvent(new VideoFormatChangeEvent(videoFormat));
                }
            }
                break;
            case EventType.EVENT_TYPE_AUDIO_CHANGED: {
                MediaFormat audioFormat= (MediaFormat) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: AudioFormatChangeEvent(%s)", mId, audioFormat);
                }
                for (TsPlaybackListener listener : mPlaybackListeners) {
                    listener.onPlaybackEvent(new AudioFormatChangeEvent(audioFormat));
                }
            }
                break;
            case EventType.EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO: {
                VideoFirstFrameEvent event = (VideoFirstFrameEvent) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: VideoFirstFrameEvent(%s)", mId, event);
                }
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO: {
                AudioFirstFrameEvent event = (AudioFirstFrameEvent) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: AudioFirstFrameEvent(%s)", mId, event);
                }
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO: {
                DecodeFirstVideoFrameEvent event = (DecodeFirstVideoFrameEvent) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: DecodeFirstVideoFrameEvent(%s)", mId, event);
                }
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO: {
                DecodeFirstAudioFrameEvent event = (DecodeFirstAudioFrameEvent) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: DecodeFirstAudioFrameEvent(%s)", mId, event);
                }
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_PTS:
                notifyPlaybackEvent((PtsEvent) msg.obj);
                break;
            default:
                ASPlayerLog.w("EventNotifier-%d, unexpected event:%d", mId, eventType);
                break;
        }
    }

    private void notifyPlaybackEvent(TsPlaybackListener.PlaybackEvent event) {
        for (TsPlaybackListener listener : mPlaybackListeners) {
            listener.onPlaybackEvent(event);
        }
    }
}