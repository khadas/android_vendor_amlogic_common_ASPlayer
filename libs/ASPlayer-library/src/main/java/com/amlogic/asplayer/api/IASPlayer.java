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
    int prepare();

    /**
     * Get ASPlayer interface major version.
     */
    int getMajorVersion();

    /**
     * Get ASPlayer interface minor version.
     */
    int getMinorVersion();

    /**
     * Get the instance number of specified ASPlayer
     */
    int getInstancesNumber();

    /**
     * Get the sync instance number of specified ASPlayer
     */
    int getSyncInstancesNumber();

    /**
     * Release specified ASPlayer instance
     */
    void release();

    /**
     * Write Frame data to ASPlayer instance.
     * It will only work when TS input's source type is TS_MEMORY.
     *
     * @param inputFrameBuffer
     * @param timeoutMillSecond Time out limit
     */
    int writeFrameData(InputFrameBuffer inputFrameBuffer, long timeoutMillSecond);

    /**
     * Write data to ASPlayer instance.
     * It will only work when TS input's source type is TS_MEMORY.
     *
     * @param inputBuffer
     * @param timeoutMillSecond
     */
    int writeData(InputBuffer inputBuffer, long timeoutMillSecond);

    /**
     * Flush data of ASPlayer instance.
     */
    int flush();

    /**
     * Flush DvrPlayback of ASPlayer instance.
     */
    int flushDvr();

    /**
     * Set work mode of ASPlayer instance.
     *
     * @see WorkMode
     *
     * @param mode {@link WorkMode}
     */
    int setWorkMode(int mode);

    /**
     * Reset work mode of ASPlayer instance.
     *
     * {@note: Not really reset all work mode state, just reset for FCC}
     *
     * @return
     */
    int resetWorkMode();

    /**
     * Set PIP mode to ASPlayer instance.
     *
     * @param mode {@link PIPMode}
     *
     * @return
     */
    int setPIPMode(int mode);

    /**
     * Get the playing time of ASPlayer instance.
     *
     * @return Playing time.
     */
    long getCurrentTime();

    /**
     * Get the pts of ASPlayer instance.
     *
     * @see StreamType
     *
     * @param streamType stream type
     */
    long getPts(int streamType);

    /**
     * Set the tsync mode for ASPlayer instance.
     *
     * @see AVSyncMode
     *
     * @param mode
     */
    void setSyncMode(int mode);

    /**
     * Get the tsync mode for ASPlayer instance.
     *
     * @see AVSyncMode
     */
    int getSyncMode();

    /**
     * Set pcr pid to ASPlayer instance.
     *
     * @param pid The pid of pcr.
     */
    int setPcrPid(int pid);

    /**
     * Start Fast play for ASPlayer instance.
     *
     * @param scale Fast play speed.
     */
    int startFast(float scale);

    /**
     * Stop Fast play for ASPlayer instance.
     */
    int stopFast();

    /**
     * Start trick mode for ASPlayer instance.
     *
     * @see VideoTrickMode
     *
     * @param trickMode trick mode type
     */
    int setTrickMode(int trickMode);

    /**
     * Set surface to ASPlayer Instance.
     *
     * @param surface
     */
    int setSurface(Surface surface);

    /**
     * Set video params need by demuxer and video decoder for ASPlayer instance.
     *
     * @param params Params need by demuxer and video decoder.
     */
    void setVideoParams(VideoParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException;

    /**
     * Set if need keep last frame for video display for ASPlayer instance.
     *
     * @param transitionModeBefore transition mode before.
     *
     * @see com.amlogic.asplayer.api.TransitionSettings.TransitionModeBefore
     */
    int setTransitionModeBefore(int transitionModeBefore);

    /**
     * Get video basic info of ASPlayer instance.
     */
    MediaFormat getVideoInfo();

    /**
     * Start video decoding for ASPlayer instance.
     */
    int startVideoDecoding();

    /**
     * Pause video decoding for ASPlayer instance.
     */
    int pauseVideoDecoding();

    /**
     * Resume video decoding for ASPlayer instance.
     */
    int resumeVideoDecoding();

    /**
     * Stop video decoding for ASPlayer instance.
     */
    int stopVideoDecoding();

    /**
     * Set audio volume to ASPlayer instance.
     *
     * @param volume Volume value
     */
    int setAudioVolume(int volume);

    /**
     * Get audio volume value from ASPlayer instance.
     */
    int getAudioVolume();

    /**
     * Set audio stereo mode to ASPlayer instance
     *
     * @see AudioStereoMode
     *
     * @param audioStereoMode audio stereo mode
     */
    void setAudioStereoMode(int audioStereoMode);

    /**
     * Get audio stereo mode to ASPlayer instance.
     */
    int getAudioStereoMode();

    /**
     * Set audio output mute to ASPlayer instance.
     *
     * @param analogMute If analog mute or unmute
     * @param digitalMute If digital mute or unmute
     */
    int setAudioMute(boolean analogMute, boolean digitalMute);

    /**
     * Get audio output mute status from ASPlayer instance
     */
    int getAudioAnalogMute();

    /**
     * Get audio output mute status from ASPlayer instance
     */
    int getAudioDigitMute();

    /**
     * Set audio params need by demuxer and audio decoder to ASPlayer instance.
     *
     * @param params Params need by demuxer and audio decoder
     */
    void setAudioParams(AudioParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException;

    /**
     * Switch audio track
     *
     * @param params
     * @return
     */
    int switchAudioTrack(AudioParams params);

    /**
     * Get audio basic info of ASPlayer instance.
     */
    MediaFormat getAudioInfo();

    /**
     * Start audio decoding for ASPlayer instance
     */
    int startAudioDecoding();

    /**
     * Pause audio decoding for ASPlayer instance
     */
    int pauseAudioDecoding();

    /**
     * Resume audio decoding for ASPlayer instance
     */
    int resumeAudioDecoding();

    /**
     * Stop audio decoding for ASPlayer instance
     */
    int stopAudioDecoding();

    /**
     * Set audio description params need by demuxer and audio decoder to ASPlayer instance.
     */
    int setADParams(AudioParams params);

    /**
     * Set audio description volume (AD volume)
     *
     * @param adVolumeDb AD Volume in DB
     */
    int setADVolumeDB(float adVolumeDb);

    /**
     * Get audio description volume
     *
     * @return ad volume in db
     */
    float getADVolumeDB();

    /**
     * Enable audio description mix with master audio
     *
     * @return
     */
    int enableADMix();

    /**
     * Disable audio description mix with master audio
     *
     * @return
     */
    int disableADMix();

    /**
     * Set subtitle pid for ASPlayer instance.
     *
     * @param pid
     */
    int setSubtitlePid(int pid);

    /**
     * get State for ASPlayer instance
     */
    State getState();

    /**
     * Start subtitle for ASPlayer instance
     */
    int startSubtitle();

    /**
     * Stop subtitle for ASPlayer instance
     */
    int stopSubtitle();

    /**
     * Get the first pts of ASPlayer instance.
     *
     * @see StreamType
     *
     * @param streamType stream type
     */
    long getFirstPts(int streamType);
}
