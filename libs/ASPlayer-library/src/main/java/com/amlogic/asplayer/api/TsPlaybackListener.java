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
        protected final long mPositionMs;

        public FirstFrameEvent(int eventType, long positionMs) {
            super(eventType);
            mPositionMs = positionMs;
        }

        public long getPositionMs() {
            return mPositionMs;
        }
    }

    /**
     * The player has displayed a first video frame
     */
    public static class VideoFirstFrameEvent extends FirstFrameEvent {
        public VideoFirstFrameEvent(long positionMs) {
            super(EventType.EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO, positionMs);
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
        public AudioFirstFrameEvent(long positionMs) {
            super(EventType.EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO, positionMs);
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
        public DecodeFirstVideoFrameEvent(long positionMs) {
            super(EventType.EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO, positionMs);
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
        public DecodeFirstAudioFrameEvent(long positionMs) {
            super(EventType.EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO, positionMs);
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
    }

    class PlaybackInfoEvent extends PlaybackEvent {

        public PlaybackInfoEvent(int eventType) {
            super(eventType);
        }

        @Override
        public String getEventName() {
            return "PlaybackInfoEvent";
        }
    }

    /**
     * Video decoder init completed event
     */
    public static class VideoDecoderInitCompletedEvent extends PlaybackInfoEvent {

        public VideoDecoderInitCompletedEvent() {
            super(EventType.EVENT_TYPE_VIDEO_DECODER_INIT_COMPLETED);
        }

        @Override
        public String getEventName() {
            return "VideoDecoderInitCompletedEvent";
        }
    }

    void onPlaybackEvent(PlaybackEvent event);
}
