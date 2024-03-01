/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

import android.media.MediaFormat;

public interface TsPlaybackListener {

    abstract class PlaybackEvent {
        private final int mEventType;

        public PlaybackEvent(int eventType) {
            mEventType = eventType;
        }

        public final int getEventType() {
            return mEventType;
        }

        public abstract String getEventName();
    }

    /**
     * Video format changed event
     */
    public static class VideoFormatChangeEvent extends PlaybackEvent {
        private final MediaFormat mVideoFormat;

        public VideoFormatChangeEvent(MediaFormat videoFormat) {
            super(EventType.EVENT_TYPE_VIDEO_CHANGED);
            this.mVideoFormat = videoFormat;
        }

        @Override
        public String getEventName() {
            return "VideoFormatChangeEvent";
        }

        public MediaFormat getVideoFormat() {
            return mVideoFormat;
        }
    }

    /**
     * Audio format changed event
     */
    public static class AudioFormatChangeEvent extends PlaybackEvent {
        private final MediaFormat mAudioFormat;

        public AudioFormatChangeEvent(MediaFormat audioFormat) {
            super(EventType.EVENT_TYPE_AUDIO_CHANGED);
            mAudioFormat = audioFormat;
        }

        @Override
        public String getEventName() {
            return "AudioFormatChangeEvent";
        }

        public MediaFormat getAudioFormat() {
            return mAudioFormat;
        }
    }

    /**
     * The player has displayed a first video frame (or audio for audio only media)
     * the media.
     */
    public static abstract class FirstFrameEvent extends PlaybackEvent {
        protected final int mStreamType;
        protected final long mPts;
        protected final long mRenderTime;

        public FirstFrameEvent(int eventType, int streamType, long pts, long renderTime) {
            super(eventType);
            mStreamType = streamType;
            mPts = pts;
            mRenderTime = renderTime;
        }

        public int getStreamType() {
            return mStreamType;
        }

        public long getPts() {
            return mPts;
        }

        public long getRenderTime() {
            return mRenderTime;
        }
    }

    /**
     * The player has displayed a first video frame
     */
    public static class VideoFirstFrameEvent extends FirstFrameEvent {
        public VideoFirstFrameEvent(long pts, long renderTime) {
            super(EventType.EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO, StreamType.VIDEO, pts, renderTime);
        }

        @Override
        public String getEventName() {
            return "VideoFirstFrameEvent";
        }
    }

    /**
     * The player has displayed a first audio frame
     */
    public static class AudioFirstFrameEvent extends FirstFrameEvent {
        public AudioFirstFrameEvent(long pts, long renderTime) {
            super(EventType.EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO, StreamType.AUDIO, pts, renderTime);
        }

        @Override
        public String getEventName() {
            return "AudioFirstFrameEvent";
        }
    }

    /**
     * The player has decoded a first video frame
     */
    public static class DecodeFirstVideoFrameEvent extends FirstFrameEvent {
        public DecodeFirstVideoFrameEvent(long pts) {
            super(EventType.EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO, StreamType.VIDEO, pts, 0);
        }

        @Override
        public String getEventName() {
            return "DecodeFirstVideoFrameEvent";
        }
    }

    /**
     * The player has decoded a first audio frame
     */
    public static class DecodeFirstAudioFrameEvent extends FirstFrameEvent {
        public DecodeFirstAudioFrameEvent(long pts) {
            super(EventType.EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO, StreamType.AUDIO, pts, 0);
        }

        @Override
        public String getEventName() {
            return "DecodeFirstAudioFrameEvent";
        }
    }

    /**
     * pts event
     */
    public static class PtsEvent extends PlaybackEvent {
        public final int mStreamType;
        public final long mPts;
        public final long mRenderTime;

        public PtsEvent(int streamType, long pts, long renderTime) {
            super(EventType.EVENT_TYPE_PTS);
            this.mStreamType = streamType;
            this.mPts = pts;
            this.mRenderTime = renderTime;
        }

        @Override
        public String getEventName() {
            return "PtsEvent";
        }

        public int getStreamType() {
            return mStreamType;
        }

        public long getPts() {
            return mPts;
        }

        public long getRenderTime() {
            return mRenderTime;
        }
    }

    class PlaybackInfoEvent extends PlaybackEvent {
        protected final int mStreamType;

        public PlaybackInfoEvent(int eventType, int streamType) {
            super(eventType);
            this.mStreamType = streamType;
        }

        @Override
        public String getEventName() {
            return "PlaybackInfoEvent";
        }

        public int getStreamType() {
            return mStreamType;
        }
    }

    /**
     * Video decoder init completed event
     */
    class VideoDecoderInitCompletedEvent extends PlaybackInfoEvent {

        public VideoDecoderInitCompletedEvent() {
            super(EventType.EVENT_TYPE_DECODER_INIT_COMPLETED, StreamType.VIDEO);
        }

        @Override
        public String getEventName() {
            return "VideoDecoderInitCompletedEvent";
        }
    }

    /**
     * Audio decoder init completed event
     */
    class AudioDecoderInitCompletedEvent extends PlaybackInfoEvent {

        public AudioDecoderInitCompletedEvent() {
            super(EventType.EVENT_TYPE_DECODER_INIT_COMPLETED, StreamType.AUDIO);
        }

        @Override
        public String getEventName() {
            return "AudioDecoderInitCompletedEvent";
        }
    }

    /**
     * Decoder data Loss event
     */
    class DecoderDataLossEvent extends PlaybackInfoEvent {

        public DecoderDataLossEvent(int streamType) {
            super(EventType.EVENT_TYPE_DECODER_DATA_LOSS, streamType);
        }

        @Override
        public String getEventName() {
            return "DecoderDataLossEvent";
        }
    }

    /**
     * Decoder data resume event
     */
    class DecoderDataResumeEvent extends PlaybackInfoEvent {

        public DecoderDataResumeEvent(int streamType) {
            super(EventType.EVENT_TYPE_DECODER_DATA_RESUME, streamType);
        }

        @Override
        public String getEventName() {
            return "DecoderDataResumeEvent";
        }
    }

    void onPlaybackEvent(PlaybackEvent event);
}
