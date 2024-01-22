/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaFormat;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrSettings;
import android.media.tv.tuner.filter.Filter;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceControl;

import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.ErrorCode;
import com.amlogic.asplayer.api.InputBuffer;
import com.amlogic.asplayer.api.InputFrameBuffer;
import com.amlogic.asplayer.api.InputSourceType;
import com.amlogic.asplayer.api.Parameters;
import com.amlogic.asplayer.api.Pts;
import com.amlogic.asplayer.api.TsPlaybackListener;
import com.amlogic.asplayer.api.Version;
import com.amlogic.asplayer.api.VideoFormat;
import com.amlogic.asplayer.api.VideoParams;
import com.amlogic.asplayer.api.IASPlayer;
import com.amlogic.asplayer.api.WorkMode;
import com.amlogic.asplayer.api.audio.SpdifProtectionMode;
import com.amlogic.asplayer.core.utils.Utils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.amlogic.asplayer.api.ASPlayer.INFO_BUSY;
import static com.amlogic.asplayer.api.ASPlayer.INFO_ERROR_RETRY;
import static com.amlogic.asplayer.api.ASPlayer.INFO_INVALID_OPERATION;
import static com.amlogic.asplayer.api.ASPlayer.INFO_INVALID_PARAMS;
import static com.amlogic.asplayer.core.Constant.INVALID_AV_SYNC_ID;
import static com.amlogic.asplayer.core.Constant.INVALID_SYNC_INSTANCE_ID;
import static com.amlogic.asplayer.core.Constant.UNKNOWN_AUDIO_LANGUAGE;
import static com.amlogic.asplayer.core.Constant.UNKNOWN_AUDIO_PRESENTATION_ID;
import static com.amlogic.asplayer.core.Constant.UNKNOWN_AUDIO_PROGRAM_ID;
import static com.amlogic.asplayer.core.TsPlaybackConfig.PLAYBACK_BUFFER_SIZE;
import static com.amlogic.asplayer.core.TsPlaybackConfig.TS_PACKET_SIZE;

public class ASPlayerImpl implements IASPlayer, AudioOutputPathBase.AudioFormatListener {

    private static final boolean DEBUG = false;

    private Tuner mTuner;
    private Context mContext;

    private ASPlayerConfig mConfig;

    private HandlerThread mPlayerThread;
    private Handler mPlayerHandler;

    private VideoOutputPath mVideoOutputPath;
    private AudioOutputPathBase mAudioOutputPath;

    private VideoFormatListenerAdapter mVideoFormatListener;

    private RendererScheduler mRendererScheduler;
    private TsPlayback mTsPlayback;

    private SurfaceControl mFccDummySurfaceControl;
    private Surface mFccDummySurface;

    private EventNotifier mEventNotifier;
    private Looper mEventLooper;
    private HandlerThread mEventThread;
    private CopyOnWriteArraySet<TsPlaybackListener> mPendingPlaybackListeners;

    private VideoFormatState mVideoFormatState;

    private final int mId;
    private int mSyncInstanceId = INVALID_SYNC_INSTANCE_ID;
    private int mAvSyncHwId = INVALID_AV_SYNC_ID;

    public interface OnGetSyncInstanceIdListener {
        void onGetSyncInstanceId(int syncInstanceId);
    }

    private OnGetSyncInstanceIdListener mOnGetSyncInstanceIdListener;

    public ASPlayerImpl(int id, Context context, Tuner tuner, ASPlayerConfig config, Looper looper) {
        mId = id;
        mContext = context;
        mTuner = tuner;
        mConfig = config;
        mEventLooper = looper;

        mVideoOutputPath = new VideoOutputPathV3(mId, ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH);
        mAudioOutputPath = new AudioOutputPathV3(mId);

        mPendingPlaybackListeners = new CopyOnWriteArraySet<>();

        mVideoFormatListener = new VideoFormatListenerAdapter();
    }

    @Override
    public void addPlaybackListener(TsPlaybackListener listener) {
        if (mEventNotifier != null) {
            mEventNotifier.mPlaybackListeners.add(listener);
        } else {
            mPendingPlaybackListeners.add(listener);
        }
    }

    @Override
    public void removePlaybackListener(TsPlaybackListener listener) {
        if (mEventNotifier != null) {
            mEventNotifier.mPlaybackListeners.remove(listener);
        }
        mPendingPlaybackListeners.remove(listener);
    }

    public void setOnGetSyncInstanceIdListener(OnGetSyncInstanceIdListener listener) {
        mOnGetSyncInstanceIdListener = listener;
    }

    @Override
    public int prepare() {
        if (mPlayerThread != null) {
            ASPlayerLog.w("%s already prepared", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        mPlayerThread = new HandlerThread(String.format(Locale.US, "AsPlayer:%d", mId),
                Process.THREAD_PRIORITY_AUDIO);
        mPlayerThread.start();
        mPlayerHandler = new Handler(mPlayerThread.getLooper());

        if (mEventLooper != null) {
            mEventNotifier = new EventNotifier(mId, mEventLooper);
        } else {
            // no event looper, notify events on sub thread
            mEventThread = new HandlerThread(String.format(Locale.US, "AsPlayer-ev:%d", mId));
            mEventThread.start();
            mEventNotifier = new EventNotifier(mId, mEventThread.getLooper());
            mEventLooper = mEventThread.getLooper();
        }

        mEventNotifier.mPlaybackListeners.addAll(mPendingPlaybackListeners);
        mPendingPlaybackListeners.clear();

        mRendererScheduler = new RendererScheduler(mId, mContext, this, mConfig,
                mVideoOutputPath, mAudioOutputPath, mEventNotifier);

        mVideoFormatState = new VideoFormatState(mEventNotifier);

        if (needTsPlayback()) {
            if (mTuner == null) {
                ASPlayerLog.e("%s prepare failed, Dvr playback but tuner is null", getTag());
                return ErrorCode.ERROR_INVALID_PARAMS;
            }

            prepareTsPlayback();
        }

        mPlayerHandler.post(this::handlePrepare);

        return ErrorCode.SUCCESS;
    }

    private void handlePrepare() {
        mRendererScheduler.setVideoFormatListener(mVideoFormatListener);
        mRendererScheduler.setAudioFormatListener(this);
        mRendererScheduler.prepare(mPlayerHandler);
    }

    private boolean isAlive() {
        return mPlayerHandler != null && mPlayerThread != null && mPlayerThread.isAlive();
    }

    @Override
    public void onAudioFormat(AudioFormat audioFormat) {
        if (audioFormat == null) {
            return;
        }

        MediaFormat mediaFormat = new MediaFormat();
        mediaFormat.setInteger(
                com.amlogic.asplayer.api.AudioFormat.KEY_SAMPLE_RATE, audioFormat.getSampleRate());
        mediaFormat.setInteger(
                com.amlogic.asplayer.api.AudioFormat.KEY_CHANNEL_COUNT, audioFormat.getChannelCount());
        mediaFormat.setInteger(
                com.amlogic.asplayer.api.AudioFormat.KEY_CHANNEL_MASK, audioFormat.getChannelMask());

        mEventNotifier.notifyAudioFormatChange(mediaFormat);
    }

    @Override
    public Version getVersion() {
        return null;
    }

    @Override
    public int getInstanceNo() {
        return mId;
    }

    @Override
    public int getSyncInstanceNo() {
        return mAvSyncHwId;
    }

    @Override
    public void release() {
        if (DEBUG) ASPlayerLog.d("%s release start", getTag());
        if (mPlayerHandler == null) {
            ASPlayerLog.i("%s already released", getTag());
            return;
        }

        final ConditionVariable lock = new ConditionVariable();
        mPlayerHandler.post(new Runnable() {
            @Override
            public void run() {
                handleRelease();
                lock.open();
            }
        });
        lock.block();

        mAvSyncHwId = INVALID_AV_SYNC_ID;
        mSyncInstanceId = INVALID_SYNC_INSTANCE_ID;

        mPendingPlaybackListeners.clear();

        if (mVideoFormatState != null) {
            mVideoFormatState.release();
            mVideoFormatState = null;
        }

        mVideoFormatListener = null;

        if (mEventNotifier != null) {
            mEventNotifier.release();
            mEventNotifier = null;
        }

        if (mEventThread != null) {
            mEventThread.quitSafely();
            mEventThread = null;
        }

        mEventLooper = null;

        mPlayerHandler.removeCallbacksAndMessages(null);

        mPlayerThread.quitSafely();
        mPlayerThread = null;

        mPlayerHandler = null;

        mVideoOutputPath = null;
        mAudioOutputPath = null;

        mRendererScheduler = null;

        mTuner = null;
        mFccDummySurfaceControl = null;
        mFccDummySurface = null;
    }

    private void handleRelease() {
        handleStop();

        releaseTsPlayback();

        mRendererScheduler.setVideoFormatListener(null);
        mRendererScheduler.setAudioFormatListener(null);
        mRendererScheduler.release();

        mVideoOutputPath.release();
        mAudioOutputPath.release();
    }

    private void releaseTsPlayback() {
        if (mTsPlayback != null) {
            mTsPlayback.release();
            mTsPlayback = null;
        }
    }

    @Override
    public int writeFrameData(InputFrameBuffer inputFrameBuffer, long timeoutMillSecond) {
        if (DEBUG) ASPlayerLog.d("%s writeFrameData start", getTag());
        return 0;
    }

    @Override
    public int writeData(InputBuffer inputBuffer, long timeoutMillSecond) {
//        if (DEBUG) ASPlayerLog.d("%s-%d writeData start, buffer size: %d", TAG, mId, (inputBuffer != null ? inputBuffer.mBufferSize : 0));
        if (inputBuffer == null) {
            ASPlayerLog.i("%s writeData failed, invalid param, inputBuffer is null", getTag());
            return INFO_INVALID_PARAMS;
        } else if (inputBuffer.mBuffer == null) {
            ASPlayerLog.i("%s writeData failed, invalid param, inputBuffer.mBuffer is null", getTag());
            return INFO_INVALID_PARAMS;
        } else if (inputBuffer.mOffset < 0 || inputBuffer.mBufferSize < 0) {
            ASPlayerLog.i("%s writeData failed, invalid param, offset: %d, bufferSize: %d",
                    getTag(), inputBuffer.mOffset, inputBuffer.mBufferSize);
            return INFO_INVALID_PARAMS;
        } else if (inputBuffer.mBufferSize == 0) {
            ASPlayerLog.i("%s writeData buffer is empty", getTag());
            return INFO_ERROR_RETRY;
        }

        if (mTsPlayback != null) {
            return writeToTsPlayback(inputBuffer.mBuffer, inputBuffer.mOffset, inputBuffer.mBufferSize);
        } else {
            ASPlayerLog.w("%s writeData failed", getTag());
            return INFO_INVALID_OPERATION;
        }
    }

    public int writeData(int inputBufferType, byte[] buffer, int offset, int size, long timeoutMillSecond) {
        if (buffer == null) {
            ASPlayerLog.i("%s writeData failed, invalid param, buffer is null", getTag());
            return INFO_INVALID_PARAMS;
        } else if (offset < 0 || size < 0) {
            ASPlayerLog.i("%s writeData failed, invalid param, offset: %d, size: %d",
                    getTag(), offset, size);
            return INFO_INVALID_PARAMS;
        } else if (size == 0) {
            ASPlayerLog.i("%s writeData buffer is empty", getTag());
            return INFO_ERROR_RETRY;
        }

        if (mTsPlayback != null) {
            return writeToTsPlayback(buffer, offset, size);
        } else {
            ASPlayerLog.w("%s writeData failed", getTag());
            return INFO_INVALID_OPERATION;
        }
    }

    private int writeToTsPlayback(byte[] buffer, int offset, int size) {
        long ret = mTsPlayback.write(buffer, offset, size);
        if (ret > 0) {
            return (int)ret;
        } else if (ret == 0) {
            return INFO_BUSY;
        } else {
            ASPlayerLog.w("%s writeData error, ret: %d", getTag(), ret);
            return INFO_ERROR_RETRY;
        }
    }

    @Override
    public int flush() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                handleFlush();
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.e("%s flush called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    private void handleFlush() {
        mRendererScheduler.flush();
    }

    @Override
    public int flushDvr() {
        if (isAlive() && mTsPlayback != null) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                TsPlayback tsPlayback = mTsPlayback;
                if (tsPlayback != null) {
                    tsPlayback.stop();
                    tsPlayback.flush();
                    tsPlayback.start();
                }
                lock.open();
                ASPlayerLog.i("%s flushDvr success", getTag());
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.e("%s flushDvr failed, playerHandler: %s, tsPlayback: %s",
                    getTag(), mPlayerHandler, mTsPlayback);
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setWorkMode(int mode) {
        if (DEBUG) ASPlayerLog.d("%s [KPI-FCC] setWorkMode start, work mode: %d", getTag(), mode);

        if (isAlive()) {
//            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.postAtFrontOfQueue(() -> {
                if (mode == WorkMode.CACHING_ONLY) {
                    initFccDummySurface();
                }
                mRendererScheduler.setWorkMode(mode);
//                lock.open();
            });
//            lock.block();
        } else {
            mRendererScheduler.setWorkMode(mode);
            ASPlayerLog.w("%s setWorkMode called, but playerHandler is null", getTag());
        }
        setThreadPriority(mode);
        return ErrorCode.SUCCESS;
    }

    @Override
    public int resetWorkMode() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                mVideoOutputPath.resetWorkMode();
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    private void initFccDummySurface() {
        if (mFccDummySurface == null) {
            createFccDummySurfaceBySurfaceControl();
        }
        mVideoOutputPath.setDummySurface(mFccDummySurface);
    }

    private void setThreadPriority(int workMode) {
        if (mPlayerThread == null) {
            return;
        }

        mPlayerThread.setPriority(workMode == WorkMode.NORMAL ? Thread.MAX_PRIORITY : Thread.NORM_PRIORITY);
    }

    private void createFccDummySurfaceBySurfaceControl() {
        mFccDummySurfaceControl = new SurfaceControl.Builder()
                .setName(String.format("asp-dum-%d", mId))
                .setBufferSize(960, 540)
                .build();
        mFccDummySurface = new Surface(mFccDummySurfaceControl);
    }

    @Override
    public int setPIPMode(int mode) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mRendererScheduler.setPIPMode(mode);
            });
        } else {
            ASPlayerLog.e("%s setPIPMode called, but playerHandler is null", getTag());
            mRendererScheduler.setPIPMode(mode);
        }
        return ErrorCode.SUCCESS;
    }

    @Override
    public void setSyncMode(int mode) {
        if (DEBUG) ASPlayerLog.d("%s setSyncMode start", getTag());

    }

    @Override
    public int getSyncMode() {
        if (DEBUG) ASPlayerLog.d("%s getSyncMode start", getTag());
        return 0;
    }

    @Override
    public int setPcrPid(int pid) {
        if (DEBUG) ASPlayerLog.d("%s setPcrPid start", getTag());
        return 0;
    }

    @Override
    public int startFast(float scale) {
        if (DEBUG) ASPlayerLog.d("%s startFast start, scale: %.3f", getTag(), scale);
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                handleStartFast(scale);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s startFast called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    private void handleStartFast(float scale) {
        mRendererScheduler.setSpeed(scale);
    }

    private void handleStopFast() {
        mRendererScheduler.setSpeed(1.0f);
    }

    @Override
    public int stopFast() {
        if (DEBUG) ASPlayerLog.d("%s stopFast start", getTag());
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                handleStopFast();
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s stopFast called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setTrickMode(int trickMode) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                handleSetTrickMode(trickMode);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s setTrickMode called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    private void handleSetTrickMode(int trickMode) {
        mRendererScheduler.setTrickMode(trickMode);
    }

    @Override
    public int setSurface(Surface surface) {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.postAtFrontOfQueue(() -> {
                ASPlayerLog.i("%s [KPI-FCC] setSurface start", getTag());
                mVideoOutputPath.setSurface(surface);
                ASPlayerLog.i("%s [KPI-FCC] setSurface done", getTag());
                lock.open();
            });
            lock.block();
        } else {
            mVideoOutputPath.setSurface(surface);
            ASPlayerLog.w("%s setSurface called, but playerHandler is null", getTag());
        }
        return ErrorCode.SUCCESS;
    }

    @Override
    public void setVideoParams(VideoParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException {
        if (params == null) {
            throw new NullPointerException("VideoParams can not be null");
        }

        int playbackMode = mConfig.getPlaybackMode();
        if (playbackMode == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH && params.getHasVideo()) {
            int filterId = params.getTrackFilterId();
            int avSyncHwId = params.getAvSyncHwId();
            if (filterId < 0) {
                String msg = String.format("invalid filter id: %d", filterId);
                ASPlayerLog.e("%s setVideoParams failed, error: %s", getTag(), msg);
                throw new IllegalArgumentException(msg);
            } else if (avSyncHwId < 0) {
                String msg = String.format("invalid avSyncHwId id: %d", avSyncHwId);
                ASPlayerLog.e("%s setVideoParams failed, error: %s", getTag(), msg);
                throw new IllegalArgumentException(msg);
            }
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleSetVideoParams(params.clone());
            });
        } else {
            ASPlayerLog.e("%s failed to set video params, playerHandler is null", getTag());
            throw new IllegalStateException("ASPlayer not prepared");
        }
    }

    private void handleSetVideoParams(VideoParams params) {
        ASPlayerLog.i("%s handleSetVideoParams, params: %s", getTag(), params);
        if (params != null) {
            if (!params.getHasVideo()) {
                // audio only program, but need to control surface by MediaCodec
                handleSetVideoParamsAudioOnly(params);
            } else {
                handleSetVideoParamsNormal(params);
            }
        } else {
            mVideoOutputPath.setVideoParams(null);
            mVideoOutputPath.setSyncInstanceId(INVALID_SYNC_INSTANCE_ID);
            mRendererScheduler.onSetVideoParams(false);
        }
    }

    private void handleSetVideoParamsNormal(VideoParams params) {
        if (params == null) {
            ASPlayerLog.i("%s setVideoParams failed, params is null", getTag());
            return;
        }

        int pid = params.getPid();
        int filterId = params.getTrackFilterId();
        int avSyncHwId = params.getAvSyncHwId();

        setSyncInstanceIdByAvSyncId(avSyncHwId);

        ASPlayerLog.i("%s setVideoParams pid: 0x%04x, filterId: 0x%016x, avsyncHwId: 0x%x, " +
                        "width: %d, height: %d, scrambled: %b, hasVideo: %b, media format: %s",
                getTag(), pid, filterId, avSyncHwId, params.getWidth(), params.getHeight(),
                params.isScrambled(), params.getHasVideo(), params.getMediaFormat());

        mVideoOutputPath.setVideoParams(params);
        mVideoOutputPath.setSyncInstanceId(getSyncInstanceIdByAvSyncId(avSyncHwId));
        mRendererScheduler.onSetVideoParams(true);
    }

    private void handleSetVideoParamsAudioOnly(VideoParams params) {
        if (params == null) {
            ASPlayerLog.i("%s setVideoParams failed, params is null", getTag());
            return;
        }

        mVideoOutputPath.setVideoParams(params);
        mVideoOutputPath.setSyncInstanceId(INVALID_AV_SYNC_ID);
        mRendererScheduler.onSetVideoParams(true);
    }

    private void setSyncInstanceIdByAvSyncId(int avSyncHwId) {
        mAvSyncHwId = avSyncHwId;
        int instanceId = getSyncInstanceIdByAvSyncId(avSyncHwId);

        if (instanceId != mSyncInstanceId) {
            ASPlayerLog.i("%s setInstanceId change instanceId %d to %d", getTag(), mSyncInstanceId, instanceId);
        } else {
            ASPlayerLog.i("%s setInstanceId %d", getTag(), mSyncInstanceId);
        }
        mSyncInstanceId = instanceId;

        if (mTsPlayback != null) {
            mTsPlayback.setSyncInstanceId(mSyncInstanceId);
        }
        mRendererScheduler.setSyncInstanceId(mSyncInstanceId);
        mEventNotifier.setSyncInstanceId(mSyncInstanceId);

        if (mOnGetSyncInstanceIdListener != null) {
            mOnGetSyncInstanceIdListener.onGetSyncInstanceId(mSyncInstanceId);
        }
    }

    private int getSyncInstanceIdByAvSyncId(int avSyncHwId) {
        return Utils.getSyncInstanceIdByAvSyncId(avSyncHwId);
    }

    @Override
    public int setTransitionModeBefore(int transitionModeBefore) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mVideoOutputPath.setTransitionModeBefore(transitionModeBefore);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s setTransitionModeBefore called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setTransitionModeAfter(int transitionModeAfter) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mVideoOutputPath.setTransitionModeAfter(transitionModeAfter);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s setTransitionModeAfter called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setTransitionPreRollRate(float rate) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mVideoOutputPath.setTransitionPreRollRate(rate);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s setTransitionPreRollRate called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setTransitionPreRollAVTolerance(int milliSecond) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mVideoOutputPath.setTransitionPreRollAVTolerance(milliSecond);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s setTransitionPreRollAVTolerance called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setVideoMute(int mute) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mVideoOutputPath.setVideoMute(mute);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s setVideoMute called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setScreenColor(int screenColorMode, int screenColor) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                ASPlayerLog.i("%s setScreenColor, mode: %d, color: %d",
                        getTag(), screenColorMode, screenColor);
                mVideoOutputPath.setScreenColor(screenColorMode, screenColor);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s setScreenColor called, but playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public MediaFormat getVideoInfo() {
        if (mVideoOutputPath == null) {
            return null;
        }

        MediaFormat mediaFormat = new MediaFormat();
        mediaFormat.setInteger(VideoFormat.KEY_WIDTH, mVideoOutputPath.getVideoWidth());
        mediaFormat.setInteger(VideoFormat.KEY_HEIGHT, mVideoOutputPath.getVideoHeight());
        mediaFormat.setInteger(VideoFormat.KEY_FRAME_RATE, mVideoOutputPath.getFrameRate());
        mediaFormat.setInteger(VideoFormat.KEY_ASPECT_RATIO, mVideoOutputPath.getPixelAspectRatio());
        mediaFormat.setInteger(VideoFormat.KEY_VF_TYPE, mVideoOutputPath.getVFType());

        return mediaFormat;
    }

    @Override
    public int startVideoDecoding() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                if (mVideoFormatState != null) {
                    mVideoFormatState.reset();
                }
                handleStart();
                mRendererScheduler.startVideoDecoding();
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s startVideoDecoding failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    private void handleStart() {
        if (mTsPlayback != null) {
            mTsPlayback.start();
        }

        mRendererScheduler.prepareStart();
    }

    private boolean needTsPlayback() {
        return mConfig.getInputSourceType() == InputSourceType.TS_MEMORY;
    }

    private void prepareTsPlayback() {
        if (mTsPlayback != null) {
            mTsPlayback.release();
            mTsPlayback = null;
        }

        mTsPlayback = new TsPlayback(mId, mTuner, PLAYBACK_BUFFER_SIZE);
        DvrSettings dvrSettings = new DvrSettings.Builder()
                .setDataFormat(DvrSettings.DATA_FORMAT_TS)
                .setPacketSize(TS_PACKET_SIZE)
                .setStatusMask(Filter.STATUS_DATA_READY)
                .setStatusMask(DvrPlayback.PLAYBACK_STATUS_EMPTY | DvrPlayback.PLAYBACK_STATUS_ALMOST_EMPTY | DvrPlayback.PLAYBACK_STATUS_ALMOST_FULL | DvrPlayback.PLAYBACK_STATUS_FULL)
                .setLowThreshold(PLAYBACK_BUFFER_SIZE / 4)
                .setHighThreshold((PLAYBACK_BUFFER_SIZE * 3) / 4)
                .build();
        mTsPlayback.configure(dvrSettings);
    }

    @Override
    public int pauseVideoDecoding() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                mRendererScheduler.pauseVideoDecoding();
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s pauseVideoDecoding failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int resumeVideoDecoding() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                mRendererScheduler.resumeVideoDecoding();
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s resumeVideoDecoding failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int stopVideoDecoding() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                if (mRendererScheduler != null) {
                    mRendererScheduler.stopVideoDecoding();
                }
                if (mVideoFormatState != null) {
                    mVideoFormatState.reset();
                }
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s stopVideoDecoding failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    private void handleStop() {
        if (mTsPlayback != null) {
            mTsPlayback.stop();
            mTsPlayback.flush();
        }

        mRendererScheduler.stop();
    }

    @Override
    public int setAudioVolume(int volume) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                if (mAudioOutputPath != null) {
                    float vol = 1.0f * volume / 100;
                    mAudioOutputPath.setVolume(vol);
                }
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s setAudioVolume failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int getAudioVolume() {
        float vol = mAudioOutputPath.getVolume();
        int volume = (int)(vol * 100);
        ASPlayerLog.i("%s getAudioVolume, volume: %.2f, return: %d", getTag(), vol, volume);
        return volume;
    }

    @Override
    public int setAudioDualMonoMode(int dualMonoMode) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                boolean success = false;
                if (mAudioOutputPath != null) {
                    success = mAudioOutputPath.setDualMonoMode(dualMonoMode);
                }

                ASPlayerLog.i("%s setAudioDualMonoMode result: %s",
                        getTag(), success ? "success" : "failed");
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s setAudioDualMonoMode failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int getAudioDualMonoMode() {
        if (mAudioOutputPath != null) {
            return mAudioOutputPath.getDualMonoMode();
        }
        return AudioTrack.DUAL_MONO_MODE_OFF;
    }

    @Override
    public int setAudioMute(boolean analogMute, boolean digitalMute) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                if (mAudioOutputPath != null) {
                    mAudioOutputPath.setMuted(digitalMute);
                }
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s setAudioMute failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int getAudioAnalogMute() {
        return 0;
    }

    @Override
    public int getAudioDigitMute() {
        return 0;
    }

    @Override
    public void setAudioParams(AudioParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException {
        if (DEBUG) ASPlayerLog.d("%s setAudioParams start, params: %s", getTag(), params);
        if (params == null) {
            throw new NullPointerException("AudioParams can not be null");
        }

        int playbackMode = mConfig.getPlaybackMode();
        if (playbackMode == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            int filterId = params.getTrackFilterId();
            int avSyncHwId = params.getAvSyncHwId();
            if (filterId < 0) {
                String msg = String.format("invalid filter id: %d", filterId);
                ASPlayerLog.e("%s setAudioParams failed, error: %s", getTag(), msg);
                throw new IllegalArgumentException(msg);
            } else if (avSyncHwId < 0) {
                String msg = String.format("invalid avSyncHw id: %d", avSyncHwId);
                ASPlayerLog.e("%s setAudioParams failed, error: %s", getTag(), msg);
                throw new IllegalArgumentException(msg);
            }
        }

        if (isAlive()) {
            mPlayerHandler.post(() -> {
                handleSetAudioParams(params.clone());
            });
        } else {
            ASPlayerLog.e("%s setAudioParams failed, playerHandler is null", getTag());
            throw new IllegalStateException("ASPlayer not prepared");
        }
    }

    private void handleSetAudioParams(AudioParams params) {
        if (params != null) {
            MediaFormat mediaFormat = params.getMediaFormat();
            int pid = params.getPid();
            int filterId = params.getTrackFilterId();
            int avSyncHwId = params.getAvSyncHwId();

            setSyncInstanceIdByAvSyncId(avSyncHwId);

            ASPlayerLog.i("%s setAudioParams pid: 0x%04x, filterId: 0x%016x, avSyncHwId: 0x%x," +
                            " format: %s, extraInfo: %s",
                    getTag(), pid, filterId, avSyncHwId, mediaFormat, params.getExtraInfo());

            mAudioOutputPath.setAudioParams(params);

            mAudioOutputPath.setSyncInstanceId(getSyncInstanceIdByAvSyncId(avSyncHwId));

            mRendererScheduler.onSetAudioParams(true);
        } else {
            ASPlayerLog.i("%s setAudioParams params is null", getTag());
            mAudioOutputPath.setAudioParams(null);

            mAudioOutputPath.setSyncInstanceId(INVALID_SYNC_INSTANCE_ID);

            mRendererScheduler.onSetAudioParams(false);
        }
    }

    @Override
    public int switchAudioTrack(AudioParams params) {
        if (params == null) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        int playbackMode = mConfig.getPlaybackMode();
        if (playbackMode == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            int filterId = params.getTrackFilterId();
            int avSyncHwId = params.getAvSyncHwId();
            if (filterId < 0) {
                String msg = String.format("invalid filter id: %d", filterId);
                ASPlayerLog.e("%s switchAudioTrack failed, error: %s", getTag(), msg);
                return ErrorCode.ERROR_INVALID_PARAMS;
            } else if (avSyncHwId < 0) {
                String msg = String.format("invalid avSyncHw id: %d", avSyncHwId);
                ASPlayerLog.e("%s switchAudioTrack failed, error: %s", getTag(), msg);
                return ErrorCode.ERROR_INVALID_PARAMS;
            }
        }

        if (isAlive()) {
            mPlayerHandler.post(() -> {
                handleSwitchAudioTrack(params.clone());
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.e("%s switchAudioTrack failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    private void handleSwitchAudioTrack(AudioParams params) {
        if (params != null) {
            MediaFormat mediaFormat = params.getMediaFormat();
            int pid = params.getPid();
            int filterId = params.getTrackFilterId();
            int avSyncHwId = params.getAvSyncHwId();

            setSyncInstanceIdByAvSyncId(avSyncHwId);

            ASPlayerLog.i("%s switchAudioTrack pid: 0x%04x, filterId: 0x%016x, avSyncHwId: 0x%x," +
                            " format: %s, extraInfo: %s",
                    getTag(), pid, filterId, avSyncHwId, mediaFormat, params.getExtraInfo());

            mAudioOutputPath.switchAudioTrack(params);

            mAudioOutputPath.setSyncInstanceId(getSyncInstanceIdByAvSyncId(avSyncHwId));

            mRendererScheduler.onSetAudioParams(true);
        } else {
            ASPlayerLog.i("%s switchAudioTrack params is null", getTag());
            mAudioOutputPath.switchAudioTrack(null);

            mAudioOutputPath.setSyncInstanceId(INVALID_SYNC_INSTANCE_ID);

            mRendererScheduler.onSetAudioParams(false);
        }
    }

    @Override
    public MediaFormat getAudioInfo() {
        if (DEBUG) ASPlayerLog.d("%s getAudioInfo start", getTag());
        return null;
    }

    @Override
    public int startAudioDecoding() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                handleStart();
                mRendererScheduler.startAudioDecoding();
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s startAudioDecoding failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int pauseAudioDecoding() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                mRendererScheduler.pauseAudioDecoding();
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s pauseAudioDecoding failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int resumeAudioDecoding() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                mRendererScheduler.resumeAudioDecoding();
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s resumeAudioDecoding failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int stopAudioDecoding() {
        if (isAlive()) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                mRendererScheduler.stopAudioDecoding();
                lock.open();
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s stopAudioDecoding failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setADParams(AudioParams params) {
        if (DEBUG) ASPlayerLog.d("%s setAudioDescriptionParams start", getTag());
        if (params == null) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (isAlive()) {
            mPlayerHandler.post(() -> {
                ASPlayerLog.i("%s setADParams pid: %d, filterId: %d, format: %s",
                        getTag(), params.getPid(), params.getTrackFilterId(), params.getMediaFormat());
                if (mAudioOutputPath instanceof AudioOutputPathV3) {
                    AudioOutputPathV3 outputPathV3 = (AudioOutputPathV3)mAudioOutputPath;
                    outputPathV3.setSubTrackAudioParams(params.clone());
                }
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s setADParams failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setADVolumeDB(float adVolumeDb) {
        if (adVolumeDb < AudioUtils.VOLUME_MIN_DB || adVolumeDb > AudioUtils.VOLUME_MAX_DB) {
            ASPlayerLog.w("%s setADVolumeDB invalid parameter, ad volume should in (-758, 48), current: %.2f",
                    getTag(), adVolumeDb);
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mAudioOutputPath.setADVolumeDb(adVolumeDb);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s setADVolumeDB failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public float getADVolumeDB() {
        float volume = mAudioOutputPath.getADVolumeDb();
        ASPlayerLog.i("%s getADVolumeDB return: %.2f", getTag(), volume);
        return volume;
    }

    @Override
    public int enableADMix() {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mAudioOutputPath.enableADMix();
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s enableADMix failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int disableADMix() {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mAudioOutputPath.disableADMix();
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s disableADMix failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setADMixLevel(int mixLevel) {
        if (isAlive()) {
            mPlayerHandler.post(() -> {
                mAudioOutputPath.setADMixLevel(mixLevel);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s setADMixLevel failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int getADMixLevel() {
        int mixLevel = mAudioOutputPath.getADMixLevel();
        ASPlayerLog.i("%s getADMixLevel, mixLevel: %d", getTag(), mixLevel);
        return mixLevel;
    }

    @Override
    public int setParameters(Bundle parameters) {
        ASPlayerLog.d("%s setParameters start", getTag());
        if (parameters == null) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        int ret = ErrorCode.ERROR_INVALID_OPERATION;

        Set<String> keys = parameters.keySet();
        Set<String> unHandledKeys = new HashSet<>(keys.size());
        unHandledKeys.addAll(keys);

        for (String key : keys) {
            if (TextUtils.isEmpty(key)) {
                break;
            }

            if (!unHandledKeys.contains(key)) {
                // key has been handled
                continue;
            }

            ret = setParameters(key, parameters, unHandledKeys);
            if (ret != ErrorCode.SUCCESS) {
                ASPlayerLog.e("%s setParameters failed, key: %s, result: %d", getTag(), key, ret);
                continue;
            }
        }

        for (String key : unHandledKeys) {
            ASPlayerLog.e("%s setParameters failed, unhandled key: %s", getTag(), key);
        }

        return ret;
    }

    private int setParameters(final String key, final Bundle parameters, Set<String> unhandledKeys) {
        if (TextUtils.isEmpty(key)) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        Set<String> keysHandled = new HashSet<>();

        int ret = ErrorCode.ERROR_INVALID_OPERATION;
        try {
            switch (key) {
                case Parameters.KEY_AUDIO_PRESENTATION_ID:
                    ret = setAudioPresentationId(parameters, keysHandled);
                    keysHandled.add(key);
                    break;
                case Parameters.KEY_FIRST_LANGUAGE:
                    ret = setAudioLanguage(parameters, keysHandled);
                    keysHandled.add(key);
                    break;
                case Parameters.KEY_SPDIF_PROTECTION_MODE:
                    ret = setAudioSpdifProtectionMode(parameters, keysHandled);
                    keysHandled.add(key);
                    break;
                default:
                    ASPlayerLog.i("%s unhandled key: %s", getTag(), key);
                    break;
            }

            if (!keysHandled.isEmpty()) {
                unhandledKeys.removeAll(keysHandled);
            }
        } catch (ClassCastException e) {
            ASPlayerLog.e("%s setParameters failed, ClassCastException, key: %s, error: %s",
                    getTag(), key, Log.getStackTraceString(e));
            return ErrorCode.ERROR_INVALID_PARAMS;
        } catch (Exception e) {
            ASPlayerLog.e("%s setParameters failed, key: %s, error: %s",
                    getTag(), key, Log.getStackTraceString(e));
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        return ret;
    }

    private int setAudioPresentationId(Bundle parameters, Set<String> keysHandled) {
        int audioPresentationId = parameters.getInt(Parameters.KEY_AUDIO_PRESENTATION_ID, UNKNOWN_AUDIO_PRESENTATION_ID);
        int programId = parameters.getInt(Parameters.KEY_AUDIO_PROGRAM_ID, UNKNOWN_AUDIO_PROGRAM_ID);

        keysHandled.add(Parameters.KEY_AUDIO_PRESENTATION_ID);
        keysHandled.add(Parameters.KEY_AUDIO_PROGRAM_ID);

        return setAudioPresentationId(audioPresentationId, programId);
    }

    private int setAudioPresentationId(int presentationId, int programId) {
        ASPlayerLog.i("%s setAudioPresentationId presentationId: %d, programId: %d",
                getTag(), presentationId, programId);

        if (isAlive()) {
            if (mAudioOutputPath != null) {
                return mAudioOutputPath.setAudioPresentationId(presentationId, programId);
            } else {
                ASPlayerLog.e("%s setAudioPresentationId failed, AudioOutputPath is null", getTag());
                return ErrorCode.ERROR_INVALID_OBJECT;
            }
        } else {
            ASPlayerLog.e("%s setAudioPresentationId failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    private int setAudioLanguage(Bundle parameters, Set<String> keysHandled) {
        int firstLanguage = parameters.getInt(Parameters.KEY_FIRST_LANGUAGE, UNKNOWN_AUDIO_LANGUAGE);
        int secondLanguage = parameters.getInt(Parameters.KEY_SECOND_LANGUAGE, UNKNOWN_AUDIO_LANGUAGE);

        keysHandled.add(Parameters.KEY_FIRST_LANGUAGE);
        keysHandled.add(Parameters.KEY_SECOND_LANGUAGE);

        return setAudioLanguage(firstLanguage, secondLanguage);
    }

    private int setAudioLanguage(int firstLanguage, int secondLanguage) {
        ASPlayerLog.i("%s setAudioLanguage firstLanguage: %d, secondLanguage: %d",
                getTag(), firstLanguage, secondLanguage);

        if (isAlive()) {
            if (mAudioOutputPath != null) {
                return mAudioOutputPath.setAudioLanguage(firstLanguage, secondLanguage);
            } else {
                ASPlayerLog.e("%s setAudioLanguage failed, AudioOutputPath is null", getTag());
                return ErrorCode.ERROR_INVALID_OBJECT;
            }
        } else {
            ASPlayerLog.e("%s setAudioLanguage failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    private int setAudioSpdifProtectionMode(Bundle parameters, Set<String> keysHandled) {
        int mode = parameters.getInt(Parameters.KEY_SPDIF_PROTECTION_MODE, SpdifProtectionMode.SPDIF_PROTECTION_MODE_NONE);

        keysHandled.add(Parameters.KEY_SPDIF_PROTECTION_MODE);

        return setAudioSpdifProtectionMode(mode);
    }

    private int setAudioSpdifProtectionMode(int mode) {
        if (mode != SpdifProtectionMode.SPDIF_PROTECTION_MODE_NONE
                && mode != SpdifProtectionMode.SPDIF_PROTECTION_MODE_NEVER
                && mode != SpdifProtectionMode.SPDIF_PROTECTION_MODE_ONCE) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (isAlive()) {
            if (mAudioOutputPath == null) {
                return ErrorCode.ERROR_INVALID_OPERATION;
            }

            int result = mAudioOutputPath.setSpdifProtectionMode(mode);
            if (result == ErrorCode.SUCCESS) {
                ASPlayerLog.i("%s setAudioSpdifProtectionMode success, mode: %d", getTag(), mode);
            } else {
                ASPlayerLog.e("%s setAudioSpdifProtectionMode failed, result: %d, mode: %d",
                        getTag(), result, mode);
            }
            return result;
        } else {
            ASPlayerLog.e("%s setAudioSpdifProtectionMode failed, playerHandler is null", getTag());
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public Bundle getParameters(String[] keys) {
        if (keys == null || keys.length <= 0) {
            return null;
        }

        Bundle bundle = new Bundle();

        for (String key : keys) {
            if (TextUtils.isEmpty(key)) {
                continue;
            }

            ASPlayerLog.i("%s getParameters for %s", getTag(), key);

            switch (key) {
                case Parameters.KEY_AUDIO_PRESENTATION_ID:
                    bundle.putInt(Parameters.KEY_AUDIO_PRESENTATION_ID, getAudioPresentationId());
                    break;
                default:
                    ASPlayerLog.i("%s getParameters unhandled key: %s", getTag(), key);
                    break;
            }
        }

        return null;
    }

    private Bundle getParameters(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }

        String[] keyArray = keys.stream().toArray(String[]::new);
        return getParameters(keyArray);
    }

    private Bundle getParameters(String key) {
        if (TextUtils.isEmpty(key)) {
            return null;
        }

        return getParameters(new String[] { key });
    }

    private int getAudioPresentationId() {
        if (mAudioOutputPath == null) {
            ASPlayerLog.e("%s getAudioPresentationId failed, audioOutputPath is null", getTag());
            return UNKNOWN_AUDIO_PRESENTATION_ID;
        }

        int audioPresentationId = mAudioOutputPath.getAudioPresentationId();;
        ASPlayerLog.i("%s getAudioPresentationId return: %d", getTag(), audioPresentationId);
        return audioPresentationId;
    }

    @Override
    public Object getParameter(String key) {
        Bundle parameters = getParameters(key);
        if (parameters == null) {
            return null;
        }

        return parameters.get(key);
    }

    @Override
    public boolean getParameterBoolean(String key, boolean fallback) {
        Bundle parameters = getParameters(key);
        return parameters != null ? parameters.getBoolean(key, fallback) : fallback;
    }

    @Override
    public double getParameterDouble(String key, double fallback) {
        Bundle parameters = getParameters(key);
        return parameters != null ? parameters.getDouble(key, fallback) : fallback;
    }

    @Override
    public int getParameterInt(String key, int fallback) {
        Bundle parameters = getParameters(key);
        return parameters != null ? parameters.getInt(key, fallback) : fallback;
    }

    @Override
    public long getParameterLong(String key, long fallback) {
        Bundle parameters = getParameters(key);
        return parameters != null ? parameters.getLong(key, fallback) : fallback;
    }

    @Override
    public float getParameterFloat(String key, float fallback) {
        Bundle parameters = getParameters(key);
        return parameters != null ? parameters.getFloat(key, fallback) : fallback;
    }

    @Override
    public String getParameterString(String key) {
        Bundle parameters = getParameters(key);
        return parameters != null ? parameters.getString(key, null) : null;
    }

    @Override
    public Pts getFirstPts(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s getFirstPts start", getTag());
        return null;
    }

    private class VideoFormatListenerAdapter implements VideoOutputPath.VideoFormatListener {

        @Override
        public void onVideoSizeInfoChanged(int width, int height, int pixelAspectRatio) {
            if (mVideoFormatState != null) {
                mVideoFormatState.onVideoSizeInfoChanged(width, height, pixelAspectRatio);
            }
        }

        @Override
        public void onAfdInfoChanged(byte activeFormat) {
            if (mVideoFormatState != null) {
                mVideoFormatState.onAfdInfoChanged(activeFormat);
            }
        }

        @Override
        public void onFrameRateChanged(int frameRate) {
            if (mVideoFormatState != null) {
                mVideoFormatState.onFrameRateChanged(frameRate);
            }
        }

        @Override
        public void onVFType(int vfType) {
            if (mVideoFormatState != null) {
                mVideoFormatState.onVFType(vfType);
            }
        }
    }

    private String getTag() {
        return String.format("[No-%d]-[%d]ASPlayer", mSyncInstanceId, mId);
    }
}