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
import android.media.MediaFormat;
import android.media.tv.tuner.Tuner;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import com.amlogic.asplayer.BuildConfiguration;
import com.amlogic.asplayer.core.BaseAppContext;
import com.amlogic.asplayer.core.ASPlayerConfig;
import com.amlogic.asplayer.core.ASPlayerImpl;
import com.amlogic.asplayer.core.ASPlayerLog;


public class ASPlayer implements IASPlayer {

    private static final boolean DEBUG = true;
    private static final String TAG = "ASPlayer";

    public static final int INFO_ERROR_RETRY = -1;
    public static final int INFO_BUSY = -2;
    public static final int INFO_UNKNOWN_ERROR = -3;

    // Mainly for debug
    private int mId;
    private static int sId = 0;

    private ASPlayerConfig mConfig;
    private ASPlayerImpl mPlayer;

    public ASPlayer(InitParams initParams, Tuner tuner, Looper looper) {
        mId = sId++;

        ASPlayerConfig config = new ASPlayerConfig.Builder()
                .setPlaybackMode(initParams.getPlaybackMode())
                .setInputSourceType(initParams.getInputSourceType())
                .build();
        mConfig = config;
        Context appContext = BaseAppContext.getAppContext();
        if (looper == null) {
            looper = Looper.getMainLooper();
        }
        mPlayer = new ASPlayerImpl(mId, appContext, tuner, mConfig, looper);

        logVersionInfo();
    }

    private void logVersionInfo() {
        if (BuildConfiguration.HAVE_VERSION_INFO) {
            StringBuilder sb = new StringBuilder();
            sb.append("------------------------------------\n");
            sb.append(String.format("branch name:          %s\n", BuildConfiguration.BRANCH_NAME));
            sb.append(String.format("%s\n", BuildConfiguration.COMMIT_CHANGE_ID));
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

    @Override
    public void addPlaybackListener(TsPlaybackListener listener) {
        ASPlayerLog.i("%s-%d addPlaybackListener start", TAG, mId);
        mPlayer.addPlaybackListener(listener);
    }

    @Override
    public void removePlaybackListener(TsPlaybackListener listener) {
        ASPlayerLog.i("%s-%d removePlaybackListener start", TAG, mId);
        mPlayer.removePlaybackListener(listener);
    }

    /**
     * Prepares the player for playback
     */
    @Override
    public int prepare() {
        ASPlayerLog.i("%s-%d prepare start", TAG, mId);
        return mPlayer.prepare();
    }

    /**
     * Get ASPlayer interface major version.
     */
    @Override
    public int getMajorVersion() {
        if (DEBUG) ASPlayerLog.d("%s-%d getMajorVersion start", TAG, mId);
//        throw new RuntimeException("Not Implementation");
        Log.e(TAG, "ASPlayer-" + mId + " getMajorVersion Not Implementation");
        return 0;
    }

    /**
     * Get ASPlayer interface minor version.
     * @return
     */
    @Override
    public int getMinorVersion() {
        if (DEBUG) ASPlayerLog.d("%s-%d getMinorVersion start", TAG, mId);
//        throw new RuntimeException("Not Implementation");
        Log.e(TAG, "ASPlayer-" + mId + " getMinorVersion Not Implementation");
        return 0;
    }

    /**
     * Get the instance number of specified ASPlayer
     */
    @Override
    public int getInstancesNumber() {
        if (DEBUG) ASPlayerLog.d("%s-%d getInstancesNumber start", TAG, mId);
        Log.e(TAG, "ASPlayer-" + mId + " getInstancesNumber Not Implementation");
//        throw new RuntimeException("Not Implementation");
        return 0;
    }

    /**
     * Get the sync instance number of specified ASPlayer
     */
    @Override
    public int getSyncInstancesNumber() {
        if (DEBUG) ASPlayerLog.d("%s-%d getSyncInstancesNumber start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Release specified ASPlayer instance
     */
    @Override
    public void release() {
        ASPlayerLog.i("%s-%d release start", TAG, mId);
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
        if (DEBUG) ASPlayerLog.d("%s-%d writeFrameData start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Write data to ASPlayer instance.
     * It will only work when TS input's source type is TS_MEMORY.
     *
     * @param inputBuffer
     * @param timeoutMillSecond
     */
    @Override
    public int writeData(InputBuffer inputBuffer, long timeoutMillSecond) {
//        if (DEBUG) ASPlayerLog.d("%s-%d writeData start, size: %d", TAG, mId, (inputBuffer != null ? inputBuffer.mBufferSize : 0));
        return mPlayer.writeData(inputBuffer, timeoutMillSecond);
    }

    public long writeData(int inputBufferType, byte[] buffer, long offset, long size, long timeoutMillSecond) {
        return mPlayer.writeData(buffer, offset, size);
    }

    @Override
    public void flush() {
        ASPlayerLog.i("%s-%d flush start", TAG, mId);
        if (mPlayer != null) {
            mPlayer.flush();
        }
    }

    /**
     * Set work mode to ASPlayer instance.
     *
     * @see WorkMode
     *
     * @param mode
     */
    @Override
    public int setWorkMode(int mode) {
        ASPlayerLog.i("%s-%d setWorkMode start", TAG, mId);
        if (mode != WorkMode.NORMAL && mode != WorkMode.CACHING_ONLY && mode != WorkMode.DECODE_ONLY) {
            return -1;
        }

        return mPlayer.setWorkMode(mode);
    }

    /**
     * Get the playing time of ASPlayer instance.
     *
     * @return Playing time.
     */
    @Override
    public long getCurrentTime() {
        if (DEBUG) ASPlayerLog.d("%s-%d getCurrentTime start", TAG, mId);
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
        if (DEBUG) ASPlayerLog.d("%s-%d getPts start", TAG, mId);
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
        if (DEBUG) ASPlayerLog.d("%s-%d setSyncMode start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get the tsync mode for ASPlayer instance.
     */
    @Override
    public int getSyncMode() {
        if (DEBUG) ASPlayerLog.d("%s-%d getSyncMode start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set pcr pid to ASPlayer instance.
     *
     * @param pid The pid of pcr.
     */
    @Override
    public int setPcrPid(int pid) {
        if (DEBUG) ASPlayerLog.d("%s-%d setPcrPid start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get the dealy time for ASPlayer instance.
     */
    @Override
    public long getDelayTime() {
        if (DEBUG) ASPlayerLog.d("%s-%d getDelayTime start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Start Fast play for ASPlayer instance.
     *
     * @param scale Fast play speed.
     */
    @Override
    public int startFast(float scale) {
        if (DEBUG) ASPlayerLog.d("%s-%d startFast start", TAG, mId);
        return mPlayer.startFast(scale);
    }

    /**
     * Stop Fast play for ASPlayer instance.
     */
    @Override
    public int stopFast() {
        if (DEBUG) ASPlayerLog.d("%s-%d stopFast start", TAG, mId);
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
        if (DEBUG) ASPlayerLog.d("%s-%d setTrickMode start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get Buffer Stat for ASPlayer instance.
     *
     * @see StreamType
     *
     * @param streamType The stream type we want to check.
     */
    @Override
    public BufferStat getBufferStat(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s-%d getBufferStat start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set the video display rect size for ASPlayer instance.
     *
     * @param x The display rect x.
     * @param y The display rect y.
     * @param width The display rect width.
     * @param height The display rect height.
     */
    @Override
    public int setVideoWindow(int x, int y, int width, int height) {
        if (DEBUG) ASPlayerLog.d("%s-%d setVideoWindow start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set the video crop rect size for ASPlayer instance.
     *
     * @param left The video crop rect left.
     * @param top The video crop rect top.
     * @param right The video crop rect right.
     * @param bottom The video crop rect bottom.
     */
    @Override
    public int setVideoCrop(int left, int top, int right, int bottom) {
        if (DEBUG) ASPlayerLog.d("%s-%d setVideoCrop start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set surface to ASPlayer Instance.
     *
     * @param surface
     */
    @Override
    public int setSurface(Surface surface) {
        ASPlayerLog.i("%s-%d setSurface start, surface: %s", TAG, mId, surface);
        return mPlayer.setSurface(surface);
    }

    @Override
    public int setFccDummySurface(Surface surface) {
        ASPlayerLog.i("%s-%d setFccDummySurface start surface: %s", TAG, mId, surface);
        return 0;
    }

    /**
     * Set video display match mode for ASPlayer instance.
     *
     * @see VideoMatchMode
     *
     * @param videoMatchMode video display match mode
     */
    @Override
    public void setVideoMatchMode(int videoMatchMode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setVideoMatchMode start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set video params need by demuxer and video decoder for ASPlayer instance.
     *
     * @param params Params need by demuxer and video decoder.
     */
    @Override
    public int setVideoParams(VideoParams params) {
        if (DEBUG) ASPlayerLog.d("%s-%d setVideoParams start, params: %s", TAG, mId, params);
        return mPlayer.setVideoParams(params);
    }

    /**
     * Set if need keep last frame for video display for ASPlayer instance.
     *
     * @param blackout If blackout for last frame.
     */
    @Override
    public void setVideoBlackOut(boolean blackout) {
        if (DEBUG) ASPlayerLog.d("%s-%d setVideoBlackOut start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get video basic info of ASPlayer instance.
     */
    @Override
    public MediaFormat getVideoInfo() {
        if (DEBUG) ASPlayerLog.d("%s-%d getVideoInfo start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get video decoder real time info of ASPlayer instance.
     */
    @Override
    public VideoDecoderStat getVideoStat() {
        if (DEBUG) ASPlayerLog.d("%s-%d getVideoStat start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Start video decoding for ASPlayer instance.
     */
    @Override
    public int startVideoDecoding() {
        ASPlayerLog.i("%s-%d startVideoDecoding start", TAG, mId);
        return mPlayer.startVideoDecoding();
    }

    /**
     * Pause video decoding for ASPlayer instance.
     */
    @Override
    public int pauseVideoDecoding() {
        ASPlayerLog.i("%s-%d pauseVideoDecoding start", TAG, mId);
        return mPlayer.pauseVideoDecoding();
    }

    /**
     * Resume video decoding for ASPlayer instance.
     */
    @Override
    public int resumeVideoDecoding() {
        ASPlayerLog.i("%s-%d resumeVideoDecoding start", TAG, mId);
        return mPlayer.resumeVideoDecoding();
    }

    /**
     * Stop video decoding for ASPlayer instance.
     */
    @Override
    public int stopVideoDecoding() {
        ASPlayerLog.i("%s-%d stopVideoDecoding start", TAG, mId);
        return mPlayer.stopVideoDecoding();
    }

    /**
     * Set audio volume to ASPlayer instance.
     *
     * @param volume Volume value
     */
    @Override
    public void setAudioVolume(int volume) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioVolume start, volume: %d", TAG, mId, volume);
        mPlayer.setAudioVolume(volume);
    }

    /**
     * Get audio volume value from ASPlayer instance.
     */
    @Override
    public int getAudioVolume() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioVolume start", TAG, mId);
        return mPlayer.getAudioVolume();
    }

    /**
     * Set audio stereo mode to ASPlayer instance
     *
     * @see AudioStereoMode
     *
     * @param audioStereoMode audio stereo mode
     */
    @Override
    public void setAudioStereoMode(int audioStereoMode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioStereoMode start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get audio stereo mode to ASPlayer instance.
     */
    @Override
    public int getAudioStereoMode() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioStereoMode start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set audio output mute to ASPlayer instance.
     *
     * @param analogMute If analog mute or unmute
     * @param digitalMute If digital mute or unmute
     */
    @Override
    public int setAudioMute(boolean analogMute, boolean digitalMute) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioMute start", TAG, mId);
        mPlayer.setAudioMute(analogMute, digitalMute);
        return 0;
    }

    /**
     * Get audio output mute status from ASPlayer instance
     */
    @Override
    public int getAudioAnalogMute() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioAnalogMute start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get audio output mute status from ASPlayer instance
     */
    @Override
    public int getAudioDigitMute() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioDigitMute start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set audio params need by demuxer and audio decoder to ASPlayer instance.
     *
     * @param params Params need by demuxer and audio decoder
     */
    @Override
    public int setAudioParams(AudioParams params) {
        ASPlayerLog.i("%s-%d setAudioParams start, params: %s", TAG, mId, params);
        return mPlayer.setAudioParams(params);
    }

    /**
     * Set audio output mode to ASPlayer instance.
     *
     * @see AudioOutputMode
     *
     * @param audioOutputMode audio output mode
     */
    @Override
    public void setAudioOutMode(int audioOutputMode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioOutMode start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get audio basic info of ASPlayer instance.
     */
    @Override
    public MediaFormat getAudioInfo() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioInfo start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get audio decoder real time info of ASPlayer instance.
     */
    @Override
    public AudioDecoderStat getAudioStat() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioStat start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Start audio decoding for ASPlayer instance
     */
    @Override
    public int startAudioDecoding() {
        ASPlayerLog.i("%s-%d startAudioDecoding start", TAG, mId);
        return mPlayer.startAudioDecoding();
    }

    /**
     * Pause audio decoding for ASPlayer instance
     */
    @Override
    public int pauseAudioDecoding() {
        ASPlayerLog.i("%s-%d pauseAudioDecoding start", TAG, mId);
        return mPlayer.pauseAudioDecoding();
    }

    /**
     * Resume audio decoding for ASPlayer instance
     */
    @Override
    public int resumeAudioDecoding() {
        ASPlayerLog.i("%s-%d resumeAudioDecoding start", TAG, mId);
        return mPlayer.resumeAudioDecoding();
    }

    /**
     * Stop audio decoding for ASPlayer instance
     */
    @Override
    public int stopAudioDecoding() {
        ASPlayerLog.i("%s-%d stopAudioDecoding start", TAG, mId);
        return mPlayer.stopAudioDecoding();
    }

    /**
     * Set audio description params need by demuxer and audio decoder to ASPlayer instance.
     */
    @Override
    public int setAudioDescriptionParams(AudioParams params) {
        ASPlayerLog.i("%s-%d setAudioDescriptionParams start, params: %s", TAG, mId, params);
        return mPlayer.setAudioDescriptionParams(params);
    }

    /**
     * Set audio description mix level (master vol and ad vol)
     *
     * @param masterVolume Master volume value
     * @param slaveVolume Slave volume value
     */
    @Override
    public int setAudioDescriptionMixLevel(int masterVolume, int slaveVolume) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioDescriptionMixLevel start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get audio description mix level (master vol and ad vol)
     */
    @Override
    public AudioVolume getAudioDescriptionMixLevel() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioDescriptionMixLevel start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Enable audio description mix with master audio
     */
    @Override
    public int enableAudioDescriptionMix() {
        if (DEBUG) ASPlayerLog.d("%s-%d enableAudioDescriptionMix start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Disable audio description mix with master audio
     */
    @Override
    public int disableAudioDescriptionMix() {
        if (DEBUG) ASPlayerLog.d("%s-%d disableAudioDescriptionMix start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get audio description basic info of Player instance.
     */
    @Override
    public MediaFormat getAudioDescriptionInfo() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioDescriptionInfo start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Get audio description decoder real time info of ASPlayer instance.
     */
    @Override
    public AudioDecoderStat getAudioDescriptionStat() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioDescriptionStat start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Set subtitle pid for ASPlayer instance.
     *
     * @param pid
     */
    @Override
    public int setSubtitlePid(int pid) {
        if (DEBUG) ASPlayerLog.d("%s-%d setSubtitlePid start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * get State for ASPlayer instance
     */
    @Override
    public State getState() {
        if (DEBUG) ASPlayerLog.d("%s-%d getState start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Start subtitle for ASPlayer instance
     */
    @Override
    public int startSubtitle() {
        if (DEBUG) ASPlayerLog.d("%s-%d startSubtitle start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }

    /**
     * Stop subtitle for ASPlayer instance
     */
    @Override
    public int stopSubtitle() {
        if (DEBUG) ASPlayerLog.d("%s-%d stopSubtitle start", TAG, mId);
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
        if (DEBUG) ASPlayerLog.d("%s-%d getFirstPts start", TAG, mId);
        throw new RuntimeException("Not Implementation");
    }
}
