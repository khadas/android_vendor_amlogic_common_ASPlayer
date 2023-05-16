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

    public interface OnInfoListener {

        /**
         * @param what info type
         *      EVENT_TYPE_DATA_LOSS
         *      EVENT_TYPE_DATA_RESUME
         *      EVENT_TYPE_FIRST_FRAME
         *      EVENT_TYPE_STREAM_MODE_EOF
         *      EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO
         *      EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO
         *      EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO
         *      EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO
         *      EVENT_TYPE_AV_SYNC_DONE
         */
        void onInfo(IASPlayer player, int what, int extra);
    }

    /**
     * Add playback listener
     * @param listener
     */
    void addPlaybackListener(TsPlaybackListener listener);

    /**
     * Remove playback listener
     * @param listener
     */
    void removePlaybackListener(TsPlaybackListener listener);

    /**
     * Prepares the player for playback
     */
    public int prepare();

    /**
     * Get ASPlayer interface major version.
     */
    public int getMajorVersion();

    /**
     * Get ASPlayer interface minor version.
     */
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
     * Set surface to ASPlayer Instance.
     *
     * @param surface
     */
    public int setSurface(Surface surface);

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
     * Get audio basic info of ASPlayer instance.
     */
    public MediaFormat getAudioInfo();

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
     * Get audio description basic info of Player instance.
     */
    public MediaFormat getAudioDescriptionInfo();

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
