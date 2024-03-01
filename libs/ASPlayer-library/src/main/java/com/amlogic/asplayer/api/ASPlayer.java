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
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Surface;

import com.amlogic.asplayer.api.PlaybackControl.TransitionModeBefore;
import com.amlogic.asplayer.api.PlaybackControl.TransitionModeAfter;
import com.amlogic.asplayer.api.PlaybackControl.ScreenColor;
import com.amlogic.asplayer.api.PlaybackControl.VideoMute;

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

        ASPlayerLog.i("%s ctor playbackMode: %d, inputSourceType: %d, eventMask: %d, tuner: %s",
                getTag(), initParams.getPlaybackMode(),initParams.getInputSourceType(),
                initParams.getEventMask(), tuner);
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
        if (mPlayer != null) {
            mPlayer.addPlaybackListener(listener);
        }
    }

    @Override
    public void removePlaybackListener(TsPlaybackListener listener) {
        ASPlayerLog.i("%s removePlaybackListener start", getTag());
        if (mPlayer != null) {
            mPlayer.removePlaybackListener(listener);
        }
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
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.prepare();
    }

    @Override
    public Version getVersion() {
        return null;
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
        if (mPlayer == null) {
            return -1;
        }

        return mPlayer.getSyncInstanceNo();
    }

    /**
     * Release specified ASPlayer instance
     */
    @Override
    public void release() {
        ASPlayerLog.i("%s release start", getTag());
        if (mPlayer != null) {
            mPlayer.release();
        }
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
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
        return mPlayer.writeData(inputBuffer, timeoutMillSecond);
    }

    public int writeData(int inputBufferType, byte[] buffer, int offset, int size, long timeoutMillSecond) {
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
        return mPlayer.writeData(inputBufferType, buffer, offset, size, timeoutMillSecond);
    }

    @Override
    public int flush() {
        ASPlayerLog.i("%s flush start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
        return mPlayer.flush();
    }

    @Override
    public int flushDvr() {
        ASPlayerLog.i("%s flushDvr start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
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

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
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
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

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

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setPIPMode(mode);
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
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.startFast(scale);
    }

    /**
     * Stop Fast play for ASPlayer instance.
     */
    @Override
    public int stopFast() {
        if (DEBUG) ASPlayerLog.d("%s stopFast start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

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

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
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
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setSurface(surface);
    }

    /**
     * Set video params need by demuxer and video decoder for ASPlayer instance.
     *
     * @param params Params need by demuxer and video decoder.
     */
    @Override
    public void setVideoParams(VideoParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException {
        ASPlayerLog.d("%s setVideoParams start, params: %s", getTag(), params);
        if (mPlayer == null) {
            throw new IllegalStateException("player has been released?");
        }

        mPlayer.setVideoParams(params);
    }

    /**
     * Set if need keep last frame for video display for ASPlayer instance.
     *
     * @param transitionModeBefore transition mode before. one of
     *      {@link TransitionModeBefore#BLACK } or
     *      {@link TransitionModeBefore#LAST_IMAGE}
     *
     * @return
     */
    @Override
    public int setTransitionModeBefore(int transitionModeBefore) {
        ASPlayerLog.d("%s setTransitionModeBefore start, mode: %d", getTag(), transitionModeBefore);
        if (transitionModeBefore != TransitionModeBefore.BLACK
                && transitionModeBefore != TransitionModeBefore.LAST_IMAGE) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setTransitionModeBefore(transitionModeBefore);
    }

    /**
     * Set if need show first image before sync for ASPlayer instance.
     *
     * @param transitionModeAfter transition mode after. one of
     *      {@link TransitionModeAfter#PREROLL_FROM_FIRST_IMAGE } or
     *      {@link TransitionModeAfter#WAIT_UNTIL_SYNC }
     *
     * @return
     */
    @Override
    public int setTransitionModeAfter(int transitionModeAfter) {
        ASPlayerLog.d("%s setTransitionModeAfter start, mode: %d", getTag(), transitionModeAfter);
        if (transitionModeAfter != TransitionModeAfter.PREROLL_FROM_FIRST_IMAGE
                && transitionModeAfter != TransitionModeAfter.WAIT_UNTIL_SYNC) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setTransitionModeAfter(transitionModeAfter);
    }

    /**
     * Set transition preroll rate
     *
     * @param rate
     *
     * @return
     */
    @Override
    public int setTransitionPreRollRate(float rate) {
        ASPlayerLog.d("%s setTransitionPreRollRate start, rate: %.3f", getTag(), rate);
        if (rate < 0) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setTransitionPreRollRate(rate);
    }

    /**
     *  maximum a/v time difference in ms to start preroll.
     *  This value limits the max time of preroll duration.
     *
     * @param milliSecond
     *
     * @return
     */
    @Override
    public int setTransitionPreRollAVTolerance(int milliSecond) {
        ASPlayerLog.d("%s setTransitionPreRollAVTolerance start, time: %d", getTag(), milliSecond);
        if (milliSecond < 0) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setTransitionPreRollAVTolerance(milliSecond);
    }

    /**
     * Set video mute or not
     *
     * @param mute one of {@link VideoMute#UN_MUTE } or {@link VideoMute#MUTE }
     *
     * @return
     */
    @Override
    public int setVideoMute(int mute) {
        ASPlayerLog.d("%s setVideoMute start, mute: %d", getTag(), mute);
        if (mute != VideoMute.UN_MUTE && mute != VideoMute.MUTE) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setVideoMute(mute);
    }

    /**
     * Set screen color
     *
     * @param screenColorMode screen color mode. one of
     *      {@link ScreenColor#MODE_ONCE_TRANSITION} or
     *      {@link ScreenColor#MODE_ONCE_SOLID} or
     *      {@link ScreenColor#MODE_ALWAYS} or
     *      {@link ScreenColor#MODE_ALWAYS_CANCEL}
     *
     * @param screenColor screen color. one of
     *      {@link ScreenColor#BLACK } or
     *      {@link ScreenColor#BLUE } or
     *      {@link ScreenColor#GREEN }
     *
     * @return
     */
    @Override
    public int setScreenColor(int screenColorMode, int screenColor) {
        ASPlayerLog.d("%s setScreenColor start, mode: %d, color: %d",
                getTag(), screenColorMode, screenColor);
        if (screenColorMode != ScreenColor.MODE_ONCE_TRANSITION
                && screenColorMode != ScreenColor.MODE_ONCE_SOLID
                && screenColorMode != ScreenColor.MODE_ALWAYS
                && screenColorMode != ScreenColor.MODE_ALWAYS_CANCEL) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (screenColor != ScreenColor.BLACK
                && screenColor != ScreenColor.BLUE
                && screenColor != ScreenColor.GREEN) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setScreenColor(screenColorMode, screenColor);
    }

    /**
     * Get video basic info of ASPlayer instance.
     */
    @Override
    public MediaFormat getVideoInfo() {
        if (DEBUG) ASPlayerLog.d("%s getVideoInfo start", getTag());
        if (mPlayer == null) {
            return null;
        }

        return mPlayer.getVideoInfo();
    }

    /**
     * Start video decoding for ASPlayer instance.
     */
    @Override
    public int startVideoDecoding() {
        ASPlayerLog.i("%s startVideoDecoding start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.startVideoDecoding();
    }

    /**
     * Pause video decoding for ASPlayer instance.
     */
    @Override
    public int pauseVideoDecoding() {
        ASPlayerLog.i("%s pauseVideoDecoding start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.pauseVideoDecoding();
    }

    /**
     * Resume video decoding for ASPlayer instance.
     */
    @Override
    public int resumeVideoDecoding() {
        ASPlayerLog.i("%s resumeVideoDecoding start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.resumeVideoDecoding();
    }

    /**
     * Stop video decoding for ASPlayer instance.
     */
    @Override
    public int stopVideoDecoding() {
        ASPlayerLog.i("%s stopVideoDecoding start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.stopVideoDecoding();
    }

    /**
     * Set audio volume to ASPlayer instance.
     *
     * @param volume Volume value
     */
    @Override
    public int setAudioVolume(int volume) {
        ASPlayerLog.d("%s setAudioVolume start, volume: %d", getTag(), volume);
        if (volume < 0 || volume > 100) {
            ASPlayerLog.w("%s setAudioVolume invalid parameter, volume should in[0, 100], current: %d", getTag(), volume);
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setAudioVolume(volume);
    }

    /**
     * Get audio volume value from ASPlayer instance.
     */
    @Override
    public int getAudioVolume() {
        if (DEBUG) ASPlayerLog.d("%s getAudioVolume start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

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
     * @param mute mute or not
     */
    @Override
    public int setAudioMute(boolean mute) {
        ASPlayerLog.d("%s setAudioMute start, mute: %b", getTag(), mute);
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setAudioMute(mute);
    }

    /**
     * Get audio output mute status from ASPlayer instance
     */
    @Override
    public boolean getAudioMute() {
        if (mPlayer == null) {
            return false;
        }

        return mPlayer.getAudioMute();
    }

    /**
     * Set audio params need by demuxer and audio decoder to ASPlayer instance.
     *
     * @param params Params need by demuxer and audio decoder
     */
    @Override
    public void setAudioParams(AudioParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException{
        ASPlayerLog.i("%s setAudioParams start, params: %s", getTag(), params);
        if (mPlayer == null) {
            throw new IllegalStateException("player has been released?");
        }

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
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

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
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.startAudioDecoding();
    }

    /**
     * Pause audio decoding for ASPlayer instance
     */
    @Override
    public int pauseAudioDecoding() {
        ASPlayerLog.i("%s pauseAudioDecoding start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.pauseAudioDecoding();
    }

    /**
     * Resume audio decoding for ASPlayer instance
     */
    @Override
    public int resumeAudioDecoding() {
        ASPlayerLog.i("%s resumeAudioDecoding start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.resumeAudioDecoding();
    }

    /**
     * Stop audio decoding for ASPlayer instance
     */
    @Override
    public int stopAudioDecoding() {
        ASPlayerLog.i("%s stopAudioDecoding start", getTag());
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.stopAudioDecoding();
    }

    /**
     * Set audio description params need by demuxer and audio decoder to ASPlayer instance.
     */
    @Override
    public int setADParams(AudioParams params) {
        ASPlayerLog.i("%s setADParams start, params: %s", getTag(), params);
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setADParams(params);
    }

    /**
     * Set audio description volume (AD volume)
     *
     * @param adVolumeDb AD Volume in DB
     */
    @Override
    public int setADVolumeDB(float adVolumeDb) {
        ASPlayerLog.d("%s setADVolumeDB start, volume: %.3f", getTag(), adVolumeDb);
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setADVolumeDB(adVolumeDb);
    }

    /**
     * Get audio description volume
     *
     * @return ad volume in db
     */
    @Override
    public float getADVolumeDB() {
        if (mPlayer == null) {
            return 0.f;
        }

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
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

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
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.disableADMix();
    }

    @Override
    public int setADMixLevel(int mixLevel) {
        ASPlayerLog.i("%s setADMixLevel start, level: %d", getTag(), mixLevel);
        if (mixLevel < 0 || mixLevel > 100) {
            ASPlayerLog.w("%s setADMixLevel invalid parameter, volume should in [0, 100], current: %d",
                    getTag(), mixLevel);
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setADMixLevel(mixLevel);
    }

    @Override
    public int getADMixLevel() {
        if (mPlayer == null) {
            return 0;
        }

        return mPlayer.getADMixLevel();
    }

    /**
     * Set parameter for ASPlayer instance.
     *
     * @param parameters
     * @return
     */
    @Override
    public int setParameters(Bundle parameters) {
        if (mPlayer == null) {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return mPlayer.setParameters(parameters);
    }

    /**
     * Get parameters for ASPlayer instance.
     *
     * @param keys
     *
     * @return
     */
    @Override
    public Bundle getParameters(String[] keys) {
        if (mPlayer == null) {
            return null;
        }

        return mPlayer.getParameters(keys);
    }

    /**
     * Get parameter for ASPlayer instance.
     *
     * @param key
     *
     * @return
     */
    @Override
    public Object getParameter(String key) {
        if (mPlayer == null) {
            return null;
        }

        return mPlayer.getParameter(key);
    }

    /**
     * Get parameter for ASPlayer instance if it exists and is a boolean or
     * can be coerced to a boolean, or fallback otherwise.
     *
     * @param key
     * @param fallback
     *
     * @return
     */
    @Override
    public boolean getParameterBoolean(String key, boolean fallback) {
        if (mPlayer == null) {
            return fallback;
        }

        return mPlayer.getParameterBoolean(key, fallback);
    }

    /**
     * Get parameter for ASPlayer instance if it exists and is a double or
     * can be coerced to a double, or fallback otherwise.
     *
     * @param key
     * @param fallback
     *
     * @return
     */
    @Override
    public double getParameterDouble(String key, double fallback) {
        if (mPlayer == null) {
            return fallback;
        }

        return mPlayer.getParameterDouble(key, fallback);
    }

    /**
     * Get parameter for ASPlayer instance if it exists and is an int or
     * can be coerced to an int, or fallback otherwise.
     *
     * @param key
     * @param fallback
     *
     * @return
     */
    @Override
    public int getParameterInt(String key, int fallback) {
        if (mPlayer == null) {
            return fallback;
        }

        return mPlayer.getParameterInt(key, fallback);
    }

    /**
     * Get parameter for ASPlayer instance if it exists and is a long or
     * can be coerced to a long, or fallback otherwise.
     *
     * @param key
     * @param fallback
     *
     * @return
     */
    @Override
    public long getParameterLong(String key, long fallback) {
        if (mPlayer == null) {
            return fallback;
        }

        return mPlayer.getParameterLong(key, fallback);
    }

    /**
     * Get parameter for ASPlayer instance if it exists and is a float or
     * can be coerced to a float, or fallback otherwise.
     *
     * @param key
     * @param fallback
     *
     * @return
     */
    @Override
    public float getParameterFloat(String key, float fallback) {
        if (mPlayer == null) {
            return fallback;
        }

        return mPlayer.getParameterFloat(key, fallback);
    }

    /**
     * Get parameter for ASPlayer instance if it exists and is a string,
     * coercing it if necessary, or null if not exists, or null if not a string
     *
     * @param key
     *
     * @return
     */
    @Override
    public String getParameterString(String key) {
        if (mPlayer == null) {
            return null;
        }

        return mPlayer.getParameterString(key);
    }

    /**
     * Get the first pts of ASPlayer instance.
     *
     * @see StreamType
     *
     * @param streamType stream type
     */
    @Override
    public Pts getFirstPts(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s getFirstPts start, streamType: %d", getTag(), streamType);
        throw new RuntimeException("Not Implementation");
    }
}
