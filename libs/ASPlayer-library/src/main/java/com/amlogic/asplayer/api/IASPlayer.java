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
import android.view.Surface;


public interface IASPlayer {

    public interface OnPtsListener {
        /**
         * ASPLAYER_EVENT_TYPE_PTS
         * @param pts
         */
        void onPts(Pts pts);
    }

    public interface OnDtvSubtitleListener {
        /**
         * ASPLAYER_EVENT_TYPE_DTV_SUBTITLE
         */
        void onDtvSubtitle();
    }

    public interface OnInfoListener {

        /**
         * @param what info type
         *      ASPLAYER_EVENT_TYPE_DATA_LOSS
         *      ASPLAYER_EVENT_TYPE_DATA_RESUME
         *      ASPLAYER_EVENT_TYPE_SCRAMBLING
         *      ASPLAYER_EVENT_TYPE_FIRST_FRAME
         *      ASPLAYER_EVENT_TYPE_STREAM_MODE_EOF
         *      ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO
         *      ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO
         *      ASPLAYER_EVENT_TYPE_AV_SYNC_DONE
         *      ASPLAYER_EVENT_TYPE_INPUT_VIDEO_BUFFER_DONE
         *      ASPLAYER_EVENT_TYPE_INPUT_AUDIO_BUFFER_DONE
         *      ASPLAYER_EVENT_TYPE_DECODE_FRAME_ERROR_COUNT
         */
        void onInfo(IASPlayer player, int what, int extra);
    }

    public interface OnVideoChangedListener {

        /**
         * ASPLAYER_EVENT_TYPE_VIDEO_CHANGED callback
         */
        void onVideoChanged(IASPlayer player, VideoFormat videoFormat);
    }

    public interface OnAudioChangedListener {

        /**
         * ASPLAYER_EVENT_TYPE_AUDIO_CHANGED callback
         */
        void onAudioChanged(IASPlayer player, AudioFormat audioFormat);
    }

    public interface OnMpegUserDataListener {

        /**
         * ASPLAYER_EVENT_TYPE_USERDATA_AFD
         * ASPLAYER_EVENT_TYPE_USERDATA_CC
         */
        void onMpegUserData(int type, UserData userData);
    }

    public static class AudioVolume {
        public int masterVolume;
        public int slaveVolume;
    }

    public interface OnErrorListener {
        /**
         *      ASPLAYER_EVENT_TYPE_VIDEO_OVERFLOW
         *      ASPLAYER_EVENT_TYPE_VIDEO_UNDERFLOW
         *      ASPLAYER_EVENT_TYPE_AUDIO_OVERFLOW
         *      ASPLAYER_EVENT_TYPE_AUDIO_UNDERFLOW
         *      ASPLAYER_EVENT_TYPE_VIDEO_INVALID_TIMESTAMP
         *      ASPLAYER_EVENT_TYPE_VIDEO_INVALID_DATA
         *      ASPLAYER_EVENT_TYPE_AUDIO_INVALID_TIMESTAMP
         *      ASPLAYER_EVENT_TYPE_AUDIO_INVALID_DATA
         *      ASPLAYER_EVENT_TYPE_DECODE_VIDEO_UNSUPPORT
         *      ASPLAYER_EVENT_TYPE_PREEMPTED
         */
        void onError(int what, int extra);
    }

    void addPlaybackListener(TsPlaybackListener listener);

    void removePlaybackListener(TsPlaybackListener listener);

    /**
     * Prepares the player for playback
     */
    public int prepare();

    /**
     * Get ASPlayer interface major version.
     */
    public int getMajorVersion();

    // Get ASPlayer interface minor version.
    public int getMinorVersion();

    /**
     * Get the instance number of specified ASPlayer
     */
    public int getInstancesNumber();

    /**
     * Get the sync instance number of specified ASPlayer
     */
    public int getSyncInstancesNumber();

    /**
     * Release specified ASPlayer instance
     */
    public void release();

    /**
     * Write Frame data to ASPlayer instance.
     * It will only work when TS input's source type is TS_MEMORY.
     *
     * @param inputFrameBuffer
     * @param timeoutMillSecond Time out limit
     */
    public int writeFrameData(InputFrameBuffer inputFrameBuffer, long timeoutMillSecond);

    /**
     * Write data to ASPlayer instance.
     * It will only work when TS input's source type is TS_MEMORY.
     *
     * @param inputBuffer
     * @param timeoutMillSecond
     */
    public int writeData(InputBuffer inputBuffer, long timeoutMillSecond);

    /**
     * Flush data of ASPlayer instance.
     */
    public void flush();

    /**
     * Flush DvrPlayback of ASPlayer instance.
     */
    public void flushDvr();

    /**
     * Set work mode to ASPlayer instance.
     *
     * @see WorkMode
     *
     * @param mode
     */
    public int setWorkMode(int mode);

    /**
     * Get the playing time of ASPlayer instance.
     *
     * @return Playing time.
     */
    public long getCurrentTime();

    /**
     * Get the pts of ASPlayer instance.
     *
     * @see StreamType
     *
     * @param streamType stream type
     */
    public long getPts(int streamType);

    /**
     * Set the tsync mode for ASPlayer instance.
     *
     * @see AVSyncMode
     *
     * @param mode
     */
    public void setSyncMode(int mode);

    /**
     * Get the tsync mode for ASPlayer instance.
     *
     * @see AVSyncMode
     */
    public int getSyncMode();

    /**
     * Set pcr pid to ASPlayer instance.
     *
     * @param pid The pid of pcr.
     */
    public int setPcrPid(int pid);

    /**
     * Get the dealy time for ASPlayer instance.
     */
    public long getDelayTime();

    /**
     * Start Fast play for ASPlayer instance.
     *
     * @param scale Fast play speed.
     */
    public int startFast(float scale);

    /**
     * Stop Fast play for ASPlayer instance.
     */
    public int stopFast();

    /**
     * Start trick mode for ASPlayer instance.
     *
     * @see VideoTrickMode
     *
     * @param trickMode trick mode type
     */
    public int setTrickMode(int trickMode);

    /**
     * Get Buffer Stat for ASPlayer instance.
     *
     * @see StreamType
     *
     * @param streamType The stream type we want to check.
     */
    public BufferStat getBufferStat(int streamType);

    /**
     * Set the video display rect size for ASPlayer instance.
     *
     * @param x The display rect x.
     * @param y The display rect y.
     * @param width The display rect width.
     * @param height The display rect height.
     */
    public int setVideoWindow(int x, int y, int width, int height);

    /**
     * Set the video crop rect size for ASPlayer instance.
     *
     * @param left The video crop rect left.
     * @param top The video crop rect top.
     * @param right The video crop rect right.
     * @param bottom The video crop rect bottom.
     */
    public int setVideoCrop(int left, int top, int right, int bottom);

    /**
     * Set surface to ASPlayer Instance.
     *
     * @param surface
     */
    public int setSurface(Surface surface);

    /**
     * Set video display match mode for ASPlayer instance.
     *
     * @see VideoMatchMode
     *
     * @param videoMatchMode video display match mode
     */
    public void setVideoMatchMode(int videoMatchMode);

    /**
     * Set video params need by demuxer and video decoder for ASPlayer instance.
     *
     * @param params Params need by demuxer and video decoder.
     */
    public void setVideoParams(VideoParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException;

    /**
     * Set if need keep last frame for video display for ASPlayer instance.
     *
     * @param blackout If blackout for last frame.
     */
    public void setVideoBlackOut(boolean blackout);

    /**
     * Get video basic info of ASPlayer instance.
     */
    public MediaFormat getVideoInfo();

    /**
     * Get video decoder real time info of ASPlayer instance.
     */
    public VideoDecoderStat getVideoStat();

    /**
     * Start video decoding for ASPlayer instance.
     */
    public int startVideoDecoding();

    /**
     * Pause video decoding for ASPlayer instance.
     */
    public int pauseVideoDecoding();

    /**
     * Resume video decoding for ASPlayer instance.
     */
    public int resumeVideoDecoding();

    /**
     * Stop video decoding for ASPlayer instance.
     */
    public int stopVideoDecoding();

    /**
     * Set audio volume to ASPlayer instance.
     *
     * @param volume Volume value
     */
    public void setAudioVolume(int volume);

    /**
     * Get audio volume value from ASPlayer instance.
     */
    public int getAudioVolume();

    /**
     * Set audio stereo mode to ASPlayer instance
     *
     * @see AudioStereoMode
     *
     * @param audioStereoMode audio stereo mode
     */
    public void setAudioStereoMode(int audioStereoMode);

    /**
     * Get audio stereo mode to ASPlayer instance.
     */
    public int getAudioStereoMode();

    /**
     * Set audio output mute to ASPlayer instance.
     *
     * @param analogMute If analog mute or unmute
     * @param digitalMute If digital mute or unmute
     */
    public int setAudioMute(boolean analogMute, boolean digitalMute);

    /**
     * Get audio output mute status from ASPlayer instance
     */
    public int getAudioAnalogMute();

    /**
     * Get audio output mute status from ASPlayer instance
     */
    public int getAudioDigitMute();

    /**
     * Set audio params need by demuxer and audio decoder to ASPlayer instance.
     *
     * @param params Params need by demuxer and audio decoder
     */
    public void setAudioParams(AudioParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException;

    /**
     * Set audio output mode to ASPlayer instance.
     *
     * @see AudioOutputMode
     *
     * @param audioOutputMode audio output mode
     */
    public void setAudioOutMode(int audioOutputMode);

    /**
     * Get audio basic info of ASPlayer instance.
     */
    public MediaFormat getAudioInfo();

    /**
     * Get audio decoder real time info of ASPlayer instance.
     */
    public AudioDecoderStat getAudioStat();

    /**
     * Start audio decoding for ASPlayer instance
     */
    public int startAudioDecoding();

    /**
     * Pause audio decoding for ASPlayer instance
     */
    public int pauseAudioDecoding();

    /**
     * Resume audio decoding for ASPlayer instance
     */
    public int resumeAudioDecoding();

    /**
     * Stop audio decoding for ASPlayer instance
     */
    public int stopAudioDecoding();

    /**
     * Set audio description params need by demuxer and audio decoder to ASPlayer instance.
     */
    public int setAudioDescriptionParams(AudioParams params);

    /**
     * Set audio description mix level (master vol and ad vol)
     *
     * @param masterVolume Master volume value
     * @param slaveVolume Slave volume value
     */
    public int setAudioDescriptionMixLevel(int masterVolume, int slaveVolume);

    /**
     * Get audio description mix level (master vol and ad vol)
     */
    public AudioVolume getAudioDescriptionMixLevel();

    /**
     * Enable audio description mix with master audio
     */
    public int enableAudioDescriptionMix();

    /**
     * Disable audio description mix with master audio
     */
    public int disableAudioDescriptionMix();

    /**
     * Get audio description basic info of Player instance.
     */
    public MediaFormat getAudioDescriptionInfo();

    /**
     * Get audio description decoder real time info of ASPlayer instance.
     */
    public AudioDecoderStat getAudioDescriptionStat();

    /**
     * Set subtitle pid for ASPlayer instance.
     *
     * @param pid
     */
    public int setSubtitlePid(int pid);

    /**
     * get State for ASPlayer instance
     */
    public State getState();

    /**
     * Start subtitle for ASPlayer instance
     */
    public int startSubtitle();

    /**
     * Stop subtitle for ASPlayer instance
     */
    public int stopSubtitle();

    /**
     * Get the first pts of ASPlayer instance.
     *
     * @see StreamType
     *
     * @param streamType stream type
     */
    public long getFirstPts(int streamType);
}
