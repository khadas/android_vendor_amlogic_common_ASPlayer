package com.amlogic.asplayer.core;

import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.amlogic.asplayer.api.TsPlaybackListener;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArraySet;


class EventNotifier {

    protected enum EventId {
        EVENT_VIDEO_FORMAT_CHANGED,
        EVENT_AUDIO_FORMAT_CHANGED,
        EVENT_DECODE_FIRST_VIDEO_FRAME,
        EVENT_DECODE_FIRST_AUDIO_FRAME,
    }

    private static class EventDecodeFirstFrame {

        final long mPositionMs;
        final double mSpeed;

        EventDecodeFirstFrame(long positionMs, double speed) {
            mPositionMs = positionMs;
            mSpeed = speed;
        }

        @Override
        public String toString() {
            return "EventDecodeFirstFrame{" +
                    "mPositionMs=" + mPositionMs +
                    ", mSpeed=" + mSpeed +
                    '}';
        }
    }

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
        postEvent(EventId.EVENT_VIDEO_FORMAT_CHANGED, videoFormat);
    }

    void notifyAudioFormatChange(MediaFormat audioFormat) {
        postEvent(EventId.EVENT_AUDIO_FORMAT_CHANGED, audioFormat);
    }

    void notifyDecodeFirstVideoFrame(long positionMs, double speed) {
        postEvent(EventId.EVENT_DECODE_FIRST_VIDEO_FRAME, new EventDecodeFirstFrame(positionMs, speed));
    }

    void notifyDecodeFirstAudioFrame(long positionMs, double speed) {
        postEvent(EventId.EVENT_DECODE_FIRST_AUDIO_FRAME, new EventDecodeFirstFrame(positionMs, speed));
    }

    private void postEvent(EventId eventId, Object object) {
        mEventHandler.obtainMessage(eventId.ordinal(), object).sendToTarget();
    }

    private void notifyEvent(Message msg) {
        final EventId eventId = EventId.values()[msg.what];

        switch (eventId) {
            case EVENT_VIDEO_FORMAT_CHANGED: {
                MediaFormat videoFormat = (MediaFormat) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: VideoFormatChangeEvent(%s)", mId, videoFormat);
                }
                for (TsPlaybackListener listener : mPlaybackListeners) {
                    listener.onPlaybackEvent(new TsPlaybackListener.VideoFormatChangeEvent(videoFormat));
                }
            }
                break;
            case EVENT_AUDIO_FORMAT_CHANGED: {
                MediaFormat audioFormat= (MediaFormat) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: AudioFormatChangeEvent(%s)", mId, audioFormat);
                }
                for (TsPlaybackListener listener : mPlaybackListeners) {
                    listener.onPlaybackEvent(new TsPlaybackListener.AudioFormatChangeEvent(audioFormat));
                }
            }
                break;
            case EVENT_DECODE_FIRST_VIDEO_FRAME: {
                EventDecodeFirstFrame firstFrame = (EventDecodeFirstFrame) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: VideoFirstFrameEvent(%s)", mId, firstFrame);
                }
                for (TsPlaybackListener listener : mPlaybackListeners) {
                    listener.onPlaybackEvent(new TsPlaybackListener.VideoFirstFrameEvent(
                            firstFrame.mPositionMs, firstFrame.mSpeed));
                }
            }
                break;
            case EVENT_DECODE_FIRST_AUDIO_FRAME: {
                EventDecodeFirstFrame firstFrame = (EventDecodeFirstFrame) msg.obj;
                if (LOCAL_LOG_NOTIFICATIONS) {
                    ASPlayerLog.d("EventNotifier-%d.notifyEvent: AudioFirstFrameEvent(%s)", mId, firstFrame);
                }
                for (TsPlaybackListener listener : mPlaybackListeners) {
                    listener.onPlaybackEvent(new TsPlaybackListener.AudioFirstFrameEvent(
                            firstFrame.mPositionMs, firstFrame.mSpeed));
                }
            }
                break;
            default:
                ASPlayerLog.w("EventNotifier-%d, unexpected event:%s", mId, eventId);
                break;
        }
    }
}

