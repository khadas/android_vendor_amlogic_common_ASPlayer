/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

import android.content.Context;
import android.media.AudioTrack;
import android.media.MediaFormat;
import android.media.tv.tuner.Tuner;
import android.os.Looper;
import android.view.Surface;

import com.amlogic.asplayer.BuildConfiguration;
import com.amlogic.asplayer.core.ASPlayerConfig;
import com.amlogic.asplayer.core.ASPlayerImpl;
import com.amlogic.asplayer.core.ASPlayerLog;
import com.amlogic.asplayer.core.BaseAppContext;
import com.amlogic.asplayer.core.Constant;

import java.util.concurrent.atomic.AtomicInteger;


public class ASPlayer implements IASPlayer {

    private static final boolean DEBUG = true;

    public static final int INFO_ERROR_RETRY = ErrorCode.ERROR_RETRY;
    public static final int INFO_BUSY = ErrorCode.ERROR_BUSY;
    public static final int INFO_INVALID_PARAMS = ErrorCode.ERROR_INVALID_PARAMS;
    public static final int INFO_INVALID_OPERATION = ErrorCode.ERROR_INVALID_OPERATION;

    // Mainly for debug
    private final int mId;
    private static final AtomicInteger sId = new AtomicInteger(0);
    private int mSyncInstanceId = Constant.INVALID_SYNC_INSTANCE_ID;

    private ASPlayerConfig mConfig;
    private ASPlayerImpl mPlayer;

    public ASPlayer(InitParams initParams, Tuner tuner, Looper looper) {
        mId = sId.getAndIncrement();

        ASPlayerConfig config = new ASPlayerConfig.Builder()
                .setPlaybackMode(initParams.getPlaybackMode())
                .setInputSourceType(initParams.getInputSourceType())
                .setEventMask(initParams.getEventMask())
                .build();
        mConfig = config;
        Context appContext = BaseAppContext.getAppContext();
        mPlayer = new ASPlayerImpl(mId, appContext, tuner, mConfig, looper);
        mPlayer.setOnGetSyncInstanceIdListener(this::onGetSyncInstanceId);

        logVersionInfo();

        ASPlayerLog.i("%s ctor playbackMode: %d, inputSourceType: %d, eventMask: %d",
                getTag(), initParams.getPlaybackMode(),initParams.getInputSourceType(), initParams.getEventMask());
    }

    private void logVersionInfo() {
        if (BuildConfiguration.HAVE_VERSION_INFO) {
            StringBuilder sb = new StringBuilder();
            sb.append("------------------------------------\n");
            sb.append(String.format("branch name:          %s\n", BuildConfiguration.BRANCH_NAME));
            sb.append(String.format("%s\n",                       BuildConfiguration.COMMIT_CHANGE_ID));
            sb.append(String.format("ID:                   %s\n", BuildConfiguration.COMMIT_PD));
            sb.append(String.format("last changed:         %s\n", BuildConfiguration.LAST_CHANGED));
            sb.append(String.format("build-time:           %s\n", BuildConfiguration.BUILD_TIME));
            sb.append(String.format("build-name:           %s\n", BuildConfiguration.BUILD_NAME));
            sb.append(String.format("uncommitted-file-num: %s\n", BuildConfiguration.GIT_UN_COMMIT_FILE_NUM));
            sb.append(String.format("version:              %s\n", BuildConfiguration.VERSION_NAME));
            sb.append("------------------------------------\n");
            ASPlayerLog.i("%s", sb.toString());
        }
    }

    protected String getName() {
        return "ASPlayer";
    }

    @Override
    public void addPlaybackListener(TsPlaybackListener listener) {
        ASPlayerLog.i("%s addPlaybackListener start", getTag());
        mPlayer.addPlaybackListener(listener);
    }

    @Override
    public void removePlaybackListener(TsPlaybackListener listener) {
        ASPlayerLog.i("%s removePlaybackListener start", getTag());
        mPlayer.removePlaybackListener(listener);
    }

    private void onGetSyncInstanceId(int syncInstanceId) {
        mSyncInstanceId = syncInstanceId;
    }

    private String getTag() {
        return String.format("[No-%d]-[%d]%s", mSyncInstanceId, mId, getName());
    }

    /**
     * Prepares the player for playback
     */
    @Override
    public int prepare() {
        ASPlayerLog.i("%s prepare start", getTag());
        return mPlayer.prepare();
    }

    /**
     * Get ASPlayer interface major version.
     */
    @Override
    public int getMajorVersion() {
        if (DEBUG) ASPlayerLog.d("%s getMajorVersion start", getTag());
//        throw new RuntimeException("Not Implementation");
        ASPlayerLog.e("%s getMajorVersion Not Implementation", getTag());
        return 0;
    }

    /**
     * Get ASPlayer interface minor version.
     * @return
     */
    @Override
    public int getMinorVersion() {
        if (DEBUG) ASPlayerLog.d("%s getMinorVersion start", getTag());
//        throw new RuntimeException("Not Implementation");
        ASPlayerLog.e("%s getMinorVersion Not Implementation", getTag());
        return 0;
    }

    /**
     * Get the instance number of specified ASPlayer
     */
    @Override
    public int getInstanceNo() {
        return mId;
    }

    /**
     * Get the sync instance number of specified ASPlayer
     */
    @Override
    public int getSyncInstanceNo() {
        if (DEBUG) ASPlayerLog.d("%s getSyncInstanceNo start", getTag());
        return mPlayer.getSyncInstanceNo();
    }

    /**
     * Release specified ASPlayer instance
     */
    @Override
    public void release() {
        ASPlayerLog.i("%s release start", getTag());
        mPlayer.release();
        mPlayer = null;
    }

    /**
     * Write Frame data to ASPlayer instance.
     * It will only work when TS input's source type is TS_MEMORY.
     *
     * @param inputFrameBuffer
     * @param timeoutMillSecond Time out limit
     */
    @Override
    public int writeFrameData(InputFrameBuffer inputFrameBuffer, long timeoutMillSecond) {
        if (DEBUG) ASPlayerLog.d("%s writeFrameData start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Write data to ASPlayer instance.
     * It will only work when TS input's source type is TS_MEMORY.
     *
     * @param inputBuffer
     * @param timeoutMillSecond
     *
     * @return the actual buffer size written successfully or an error code,
     *  {@link #INFO_ERROR_RETRY},
     *  {@link #INFO_BUSY},
     *  {@link #INFO_INVALID_PARAMS},
     *  {@link #INFO_INVALID_OPERATION}
     */
    @Override
    public int writeData(InputBuffer inputBuffer, long timeoutMillSecond) {
//        if (DEBUG) ASPlayerLog.d("%s writeData start, size: %d", getTag(), (inputBuffer != null ? inputBuffer.mBufferSize : 0));
        return mPlayer.writeData(inputBuffer, timeoutMillSecond);
    }

    public int writeData(int inputBufferType, byte[] buffer, int offset, int size, long timeoutMillSecond) {
        return mPlayer.writeData(inputBufferType, buffer, offset, size, timeoutMillSecond);
    }

    @Override
    public int flush() {
        ASPlayerLog.i("%s flush start", getTag());
        return mPlayer.flush();
    }

    @Override
    public int flushDvr() {
        ASPlayerLog.i("%s flushDvr start", getTag());
        return mPlayer.flushDvr();
    }

    /**
     * Set work mode of ASPlayer instance.
     *
     * @see WorkMode
     *
     * @param mode {@link WorkMode}
     */
    @Override
    public int setWorkMode(int mode) {
        ASPlayerLog.i("%s setWorkMode start, work mode: %d", getTag(), mode);
        if (mode != WorkMode.NORMAL && mode != WorkMode.CACHING_ONLY && mode != WorkMode.DECODE_ONLY) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        return mPlayer.setWorkMode(mode);
    }

    /**
     * Reset work mode of ASPlayer instance.
     *
     * {@note: Not really reset all work mode state, just reset for FCC}
     *
     * @return
     */
    @Override
    public int resetWorkMode() {
        ASPlayerLog.i("%s resetWorkMode start", getTag());
        return mPlayer.resetWorkMode();
    }

    /**
     * Set PIP mode to ASPlayer instance.
     *
     * @param mode {@link PIPMode}
     *
     * @return
     */
    @Override
    public int setPIPMode(int mode) {
        ASPlayerLog.i("%s setPIPMode start, pip mode: %d", getTag(), mode);
        if (mode != PIPMode.NORMAL && mode != PIPMode.PIP) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }
        return mPlayer.setPIPMode(mode);
    }

    /**
     * Get the playing time of ASPlayer instance.
     *
     * @return Playing time.
     */
    @Override
    public long getCurrentTime() {
        if (DEBUG) ASPlayerLog.d("%s getCurrentTime start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get the pts of ASPlayer instance.
     *
     * @see StreamType
     *
     * @param streamType stream type
     */
    @Override
    public long getPts(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s getPts start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set the tsync mode for ASPlayer instance.
     *
     * @see AVSyncMode
     *
     * @param mode
     */
    @Override
    public void setSyncMode(int mode) {
        if (DEBUG) ASPlayerLog.d("%s setSyncMode start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get the tsync mode for ASPlayer instance.
     */
    @Override
    public int getSyncMode() {
        if (DEBUG) ASPlayerLog.d("%s getSyncMode start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set pcr pid to ASPlayer instance.
     *
     * @param pid The pid of pcr.
     */
    @Override
    public int setPcrPid(int pid) {
        if (DEBUG) ASPlayerLog.d("%s setPcrPid start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Start Fast play for ASPlayer instance.
     *
     * @param scale Fast play speed.
     */
    @Override
    public int startFast(float scale) {
        if (DEBUG) ASPlayerLog.d("%s startFast start, scale: %.3f", getTag(), scale);
        return mPlayer.startFast(scale);
    }

    /**
     * Stop Fast play for ASPlayer instance.
     */
    @Override
    public int stopFast() {
        if (DEBUG) ASPlayerLog.d("%s stopFast start", getTag());
        return mPlayer.stopFast();
    }

    /**
     * Start trick mode for ASPlayer instance.
     *
     * @see VideoTrickMode
     *
     * @param trickMode trick mode type
     */
    @Override
    public int setTrickMode(int trickMode) {
        if (DEBUG) ASPlayerLog.d("%s setTrickMode start, trick mode: %d", getTag(), trickMode);
        if (trickMode != VideoTrickMode.NONE
            && trickMode != VideoTrickMode.TRICK_MODE_SMOOTH
            && trickMode != VideoTrickMode.TRICK_MODE_BY_SEEK
            && trickMode != VideoTrickMode.TRICK_MODE_IONLY) {
            ASPlayerLog.e("%s setTrickMode failed, invalid trickMode: %d", getTag(), trickMode);
            return ErrorCode.ERROR_INVALID_PARAMS;
        }
        return mPlayer.setTrickMode(trickMode);
    }

    /**
     * Set surface to ASPlayer Instance.
     *
     * @param surface
     */
    @Override
    public int setSurface(Surface surface) {
        ASPlayerLog.i("%s setSurface start, surface: %s", getTag(), surface);
        return mPlayer.setSurface(surface);
    }

    /**
     * Set video params need by demuxer and video decoder for ASPlayer instance.
     *
     * @param params Params need by demuxer and video decoder.
     */
    @Override
    public void setVideoParams(VideoParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException {
        if (DEBUG) ASPlayerLog.d("%s setVideoParams start, params: %s", getTag(), params);
        mPlayer.setVideoParams(params);
    }

    /**
     * Set if need keep last frame for video display for ASPlayer instance.
     *
     * @param transitionModeBefore transition mode before.
     *
     * @see com.amlogic.asplayer.api.TransitionSettings.TransitionModeBefore
     */
    @Override
    public int setTransitionModeBefore(int transitionModeBefore) {
        if (DEBUG) ASPlayerLog.d("%s setTransitionModeBefore start, mode: %d", getTag(), transitionModeBefore);
        return mPlayer.setTransitionModeBefore(transitionModeBefore);
    }

    /**
     * Get video basic info of ASPlayer instance.
     */
    @Override
    public MediaFormat getVideoInfo() {
        if (DEBUG) ASPlayerLog.d("%s getVideoInfo start", getTag());
        return mPlayer.getVideoInfo();
    }

    /**
     * Start video decoding for ASPlayer instance.
     */
    @Override
    public int startVideoDecoding() {
        ASPlayerLog.i("%s startVideoDecoding start", getTag());
        return mPlayer.startVideoDecoding();
    }

    /**
     * Pause video decoding for ASPlayer instance.
     */
    @Override
    public int pauseVideoDecoding() {
        ASPlayerLog.i("%s pauseVideoDecoding start", getTag());
        return mPlayer.pauseVideoDecoding();
    }

    /**
     * Resume video decoding for ASPlayer instance.
     */
    @Override
    public int resumeVideoDecoding() {
        ASPlayerLog.i("%s resumeVideoDecoding start", getTag());
        return mPlayer.resumeVideoDecoding();
    }

    /**
     * Stop video decoding for ASPlayer instance.
     */
    @Override
    public int stopVideoDecoding() {
        ASPlayerLog.i("%s stopVideoDecoding start", getTag());
        return mPlayer.stopVideoDecoding();
    }

    /**
     * Set audio volume to ASPlayer instance.
     *
     * @param volume Volume value
     */
    @Override
    public int setAudioVolume(int volume) {
        if (DEBUG) ASPlayerLog.d("%s setAudioVolume start, volume: %d", getTag(), volume);
        return mPlayer.setAudioVolume(volume);
    }

    /**
     * Get audio volume value from ASPlayer instance.
     */
    @Override
    public int getAudioVolume() {
        if (DEBUG) ASPlayerLog.d("%s getAudioVolume start", getTag());
        return mPlayer.getAudioVolume();
    }

    /**
     * Sets the Dual Mono mode to ASPlayer instance
     *
     * @param dualMonoMode one of {@link AudioTrack#DUAL_MONO_MODE_OFF},
     *                     {@link AudioTrack#DUAL_MONO_MODE_LR},
     *                     {@link AudioTrack#DUAL_MONO_MODE_LL},
     *                     {@link AudioTrack#DUAL_MONO_MODE_RR}
     */
    @Override
    public int setAudioDualMonoMode(int dualMonoMode) {
        ASPlayerLog.i("%s setAudioDualMonoMode start, dualMonoMode: %d", getTag(), dualMonoMode);
        if (dualMonoMode != AudioTrack.DUAL_MONO_MODE_OFF
            && dualMonoMode != AudioTrack.DUAL_MONO_MODE_LR
            && dualMonoMode != AudioTrack.DUAL_MONO_MODE_LL
            && dualMonoMode != AudioTrack.DUAL_MONO_MODE_RR) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setAudioDualMonoMode(dualMonoMode);
    }

    /**
     * Returns the Dual Mono mode of ASPlayer instance.
     */
    @Override
    public int getAudioDualMonoMode() {
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.getAudioDualMonoMode();
    }

    /**
     * Set audio output mute to ASPlayer instance.
     *
     * @param analogMute If analog mute or unmute
     * @param digitalMute If digital mute or unmute
     */
    @Override
    public int setAudioMute(boolean analogMute, boolean digitalMute) {
        if (DEBUG) ASPlayerLog.d("%s setAudioMute start, mute: %b, %b", getTag(), analogMute, digitalMute);
        return mPlayer.setAudioMute(analogMute, digitalMute);
    }

    /**
     * Get audio output mute status from ASPlayer instance
     */
    @Override
    public int getAudioAnalogMute() {
        if (DEBUG) ASPlayerLog.d("%s getAudioAnalogMute start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get audio output mute status from ASPlayer instance
     */
    @Override
    public int getAudioDigitMute() {
        if (DEBUG) ASPlayerLog.d("%s getAudioDigitMute start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set audio params need by demuxer and audio decoder to ASPlayer instance.
     *
     * @param params Params need by demuxer and audio decoder
     */
    @Override
    public void setAudioParams(AudioParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException{
        ASPlayerLog.i("%s setAudioParams start, params: %s", getTag(), params);
        mPlayer.setAudioParams(params);
    }

    /**
     * Switch audio track
     * @param params
     * @return
     */
    @Override
    public int switchAudioTrack(AudioParams params) {
        ASPlayerLog.i("%s switchAudioTrack start, params: %s", getTag(), params);
        return mPlayer.switchAudioTrack(params);
    }

    /**
     * Get audio basic info of ASPlayer instance.
     */
    @Override
    public MediaFormat getAudioInfo() {
        if (DEBUG) ASPlayerLog.d("%s getAudioInfo start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Start audio decoding for ASPlayer instance
     */
    @Override
    public int startAudioDecoding() {
        ASPlayerLog.i("%s startAudioDecoding start", getTag());
        return mPlayer.startAudioDecoding();
    }

    /**
     * Pause audio decoding for ASPlayer instance
     */
    @Override
    public int pauseAudioDecoding() {
        ASPlayerLog.i("%s pauseAudioDecoding start", getTag());
        return mPlayer.pauseAudioDecoding();
    }

    /**
     * Resume audio decoding for ASPlayer instance
     */
    @Override
    public int resumeAudioDecoding() {
        ASPlayerLog.i("%s resumeAudioDecoding start", getTag());
        return mPlayer.resumeAudioDecoding();
    }

    /**
     * Stop audio decoding for ASPlayer instance
     */
    @Override
    public int stopAudioDecoding() {
        ASPlayerLog.i("%s stopAudioDecoding start", getTag());
        return mPlayer.stopAudioDecoding();
    }

    /**
     * Set audio description params need by demuxer and audio decoder to ASPlayer instance.
     */
    @Override
    public int setADParams(AudioParams params) {
        ASPlayerLog.i("%s setADParams start, params: %s", getTag(), params);
        return mPlayer.setADParams(params);
    }

    /**
     * Set audio description volume (AD volume)
     *
     * @param adVolumeDb AD Volume in DB
     */
    @Override
    public int setADVolumeDB(float adVolumeDb) {
        if (DEBUG) ASPlayerLog.d("%s setADVolumeDB start, volume: %.3f", getTag(), adVolumeDb);
        return mPlayer.setADVolumeDB(adVolumeDb);
    }

    /**
     * Get audio description volume
     *
     * @return ad volume in db
     */
    @Override
    public float getADVolumeDB() {
        return mPlayer.getADVolumeDB();
    }

    /**
     * Enable audio description mix with master audio
     *
     * @return
     */
    @Override
    public int enableADMix() {
        ASPlayerLog.i("%s enableADMix start", getTag());
        return mPlayer.enableADMix();
    }

    /**
     * Disable audio description mix with master audio
     *
     * @return
     */
    @Override
    public int disableADMix() {
        ASPlayerLog.i("%s disableADMix start", getTag());
        return mPlayer.disableADMix();
    }

    @Override
    public int setADMixLevel(int mixLevel) {
        ASPlayerLog.i("%s setADMixLevel start, level: %d", getTag(), mixLevel);
        return mPlayer.setADMixLevel(mixLevel);
    }

    @Override
    public int getADMixLevel() {
        return mPlayer.getADMixLevel();
    }

    /**
     * Set subtitle pid for ASPlayer instance.
     *
     * @param pid
     */
    @Override
    public int setSubtitlePid(int pid) {
        if (DEBUG) ASPlayerLog.d("%s setSubtitlePid start, pid: %d", getTag(), pid);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * get State for ASPlayer instance
     */
    @Override
    public State getState() {
        if (DEBUG) ASPlayerLog.d("%s getState start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Start subtitle for ASPlayer instance
     */
    @Override
    public int startSubtitle() {
        if (DEBUG) ASPlayerLog.d("%s startSubtitle start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Stop subtitle for ASPlayer instance
     */
    @Override
    public int stopSubtitle() {
        if (DEBUG) ASPlayerLog.d("%s stopSubtitle start", getTag());
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get the first pts of ASPlayer instance.
     *
     * @see StreamType
     *
     * @param streamType stream type
     */
    @Override
    public long getFirstPts(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s getFirstPts start, streamType: %d", getTag(), streamType);
        throw new RuntimeException("Not Implementation");
    }
}
