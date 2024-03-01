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
import static com.amlogic.asplayer.api.TsPlaybackListener.AudioDecoderInitCompletedEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.VideoDecoderInitCompletedEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.DecoderDataLossEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.DecoderDataResumeEvent;
import static com.amlogic.asplayer.api.TsPlaybackListener.PlaybackInfoEvent;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArraySet;


class EventNotifier {

    // Should be careful enabling this since may produce many traces!
    private static final boolean LOG_PTS_NOTIFICATIONS = true;

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
    private int mSyncInstanceId = Constant.INVALID_SYNC_INSTANCE_ID;

    EventNotifier(int id, Looper looper) {
        mId = id;
        mPlaybackListeners = new CopyOnWriteArraySet<>();
        mEventHandler = new WeakHandler(this, looper);
    }

    void setSyncInstanceId(int syncInstanceId) {
        mSyncInstanceId = syncInstanceId;
    }

    private String getTag() {
        return String.format("[No-%d]-[%d]EventNotifier", mSyncInstanceId, mId);
    }

    void release() {
        mEventHandler.removeCallbacksAndMessages(null);
        mPlaybackListeners.clear();
    }

    void notifyVideoFormatChange(MediaFormat videoFormat) {
        postEvent(EventType.EVENT_TYPE_VIDEO_CHANGED, new VideoFormatChangeEvent(videoFormat));
    }

    void notifyAudioFormatChange(MediaFormat audioFormat) {
        postEvent(EventType.EVENT_TYPE_AUDIO_CHANGED, new AudioFormatChangeEvent(audioFormat));
    }

    void notifyRenderFirstVideoFrame(long pts, long renderTime) {
        postEvent(EventType.EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO,
                new VideoFirstFrameEvent(pts, renderTime));
    }

    void notifyRenderFirstAudioFrame(long pts, long renderTime) {
        postEvent(EventType.EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO,
                new AudioFirstFrameEvent(pts, renderTime));
    }

    void notifyDecodeFirstVideoFrame(long positionMs) {
        postEvent(EventType.EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO,
                new DecodeFirstVideoFrameEvent(positionMs));
    }

    void notifyDecodeFirstAudioFrame(long positionMs) {
        postEvent(EventType.EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO,
                new DecodeFirstAudioFrameEvent(positionMs));
    }

    private void notifyFrameRendered(int streamType, long presentationTimeUs, long renderTime) {
         postEvent(EventType.EVENT_TYPE_PTS, new PtsEvent(streamType, presentationTimeUs, renderTime));
    }

    void notifyVideoFrameRendered(long presentationTimeUs, long renderTime) {
        notifyFrameRendered(StreamType.VIDEO, presentationTimeUs, renderTime);
    }

    void notifyAudioFrameRendered(long presentationTimeUs, long renderTime) {
        notifyFrameRendered(StreamType.AUDIO, presentationTimeUs, renderTime);
    }

    void notifyVideoDecoderInitCompleted() {
        postEvent(EventType.EVENT_TYPE_DECODER_INIT_COMPLETED, new VideoDecoderInitCompletedEvent());
    }

    void notifyAudioDecoderInitCompleted() {
        postEvent(EventType.EVENT_TYPE_DECODER_INIT_COMPLETED, new AudioDecoderInitCompletedEvent());
    }

    void notifyDecoderDataLoss(int streamType) {
        postEvent(EventType.EVENT_TYPE_DECODER_DATA_LOSS, new DecoderDataLossEvent(streamType));
    }

    void notifyDecoderDataResume(int streamType) {
        postEvent(EventType.EVENT_TYPE_DECODER_DATA_RESUME, new DecoderDataResumeEvent(streamType));
    }

    private void postEvent(int eventType, Object object) {
        mEventHandler.obtainMessage(eventType, object).sendToTarget();
    }

    private void notifyEvent(Message msg) {
        final int eventType = msg.what;

        switch (eventType) {
            case EventType.EVENT_TYPE_PTS: {
                PtsEvent event = (PtsEvent) msg.obj;
                if (LOG_PTS_NOTIFICATIONS) {
                    logPlaybackEvent(event, String.format("%s pts: %d, renderTime: %d",
                            StreamType.toString(event.getStreamType()), event.getPts(), event.getRenderTime()));
                }
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_VIDEO_CHANGED: {
                VideoFormatChangeEvent event = (VideoFormatChangeEvent) msg.obj;
                logPlaybackEvent(event, event.getVideoFormat());
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_AUDIO_CHANGED: {
                AudioFormatChangeEvent event = (AudioFormatChangeEvent) msg.obj;
                logPlaybackEvent(event, event.getAudioFormat());
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO: {
                DecodeFirstVideoFrameEvent event = (DecodeFirstVideoFrameEvent) msg.obj;
                logPlaybackEvent(event, String.format("pts: %d, renderTime: %d",
                        event.getPts(), event.getRenderTime()));
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO: {
                DecodeFirstAudioFrameEvent event = (DecodeFirstAudioFrameEvent) msg.obj;
                logPlaybackEvent(event, String.format("pts: %d, renderTime: %d",
                        event.getPts(), event.getRenderTime()));
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO: {
                VideoFirstFrameEvent event = (VideoFirstFrameEvent) msg.obj;
                logPlaybackEvent(event, String.format("pts: %d, renderTime: %d",
                        event.getPts(), event.getRenderTime()));
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO: {
                AudioFirstFrameEvent event = (AudioFirstFrameEvent) msg.obj;
                logPlaybackEvent(event, String.format("pts: %d, renderTime: %d",
                        event.getPts(), event.getRenderTime()));
                notifyPlaybackEvent(event);
            }
                break;
            case EventType.EVENT_TYPE_DECODER_INIT_COMPLETED:
            case EventType.EVENT_TYPE_DECODER_DATA_LOSS:
            case EventType.EVENT_TYPE_DECODER_DATA_RESUME: {
                PlaybackInfoEvent event = (PlaybackInfoEvent) msg.obj;
                logPlaybackEvent(event, null);
                notifyPlaybackEvent(event);
            }
                break;
            default:
                ASPlayerLog.w("%s notifyEvent, unexpected event:%d, %s",
                        getTag(), eventType, getEventTypeName(eventType));
                break;
        }
    }

    private void logPlaybackEvent(TsPlaybackListener.PlaybackEvent event) {
        logPlaybackEvent(event, null);
    }

    private void logPlaybackEvent(TsPlaybackListener.PlaybackEvent event, Object param) {
        if (param != null) {
            ASPlayerLog.d("%s: %s(%s)", getTag(), event.getEventName(), param);
        } else {
            ASPlayerLog.d("%s: %s", getTag(), event.getEventName());
        }
    }

    private void logPlaybackEvent(TsPlaybackListener.PlaybackInfoEvent event, Object param) {
        if (param != null) {
            ASPlayerLog.d("%s: %s[%s](%s)", getTag(), event.getEventName(),
                    StreamType.toString(event.getStreamType()), param);
        } else {
            ASPlayerLog.d("%s: %s[%s]", getTag(), event.getEventName(),
                    StreamType.toString(event.getStreamType()));
        }
    }

    private static String getEventTypeName(int eventType) {
        String eventName;

        switch (eventType) {
            case EventType.EVENT_TYPE_PTS:
                eventName = "EVENT_TYPE_PTS";
                break;
            case EventType.EVENT_TYPE_USERDATA_AFD:
                eventName = "EVENT_TYPE_USERDATA_AFD";
                break;
            case EventType.EVENT_TYPE_USERDATA_CC:
                eventName = "EVENT_TYPE_USERDATA_CC";
                break;
            case EventType.EVENT_TYPE_VIDEO_CHANGED:
                eventName = "EVENT_TYPE_VIDEO_CHANGED";
                break;
            case EventType.EVENT_TYPE_AUDIO_CHANGED:
                eventName = "EVENT_TYPE_AUDIO_CHANGED";
                break;
            case EventType.EVENT_TYPE_DATA_LOSS:
                eventName = "EVENT_TYPE_DATA_LOSS";
                break;
            case EventType.EVENT_TYPE_DATA_RESUME:
                eventName = "EVENT_TYPE_DATA_RESUME";
                break;
            case EventType.EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO:
                eventName = "EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO";
                break;
            case EventType.EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO:
                eventName = "EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO";
                break;
            case EventType.EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO:
                eventName = "EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO";
                break;
            case EventType.EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO:
                eventName = "EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO";
                break;
            case EventType.EVENT_TYPE_AV_SYNC_DONE:
                eventName = "EVENT_TYPE_AV_SYNC_DONE";
                break;
            case EventType.EVENT_TYPE_DECODER_INIT_COMPLETED:
                eventName = "EVENT_TYPE_DECODER_INIT_COMPLETED";
                break;
            case EventType.EVENT_TYPE_DECODER_DATA_LOSS:
                eventName = "EVENT_TYPE_DECODER_DATA_LOSS";
                break;
            case EventType.EVENT_TYPE_DECODER_DATA_RESUME:
                eventName = "EVENT_TYPE_DECODER_DATA_RESUME";
                break;
            default:
                eventName = "UNKNOWN_EVENT_TYPE";
                break;
        }

        return eventName;
    }

    private void notifyPlaybackEvent(TsPlaybackListener.PlaybackEvent event) {
        for (TsPlaybackListener listener : mPlaybackListeners) {
            listener.onPlaybackEvent(event);
        }
    }
}