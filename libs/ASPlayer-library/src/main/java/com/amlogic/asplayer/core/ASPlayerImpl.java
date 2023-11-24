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
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrSettings;
import android.media.tv.tuner.filter.Filter;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceControl;

import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.ErrorCode;
import com.amlogic.asplayer.api.InputBuffer;
import com.amlogic.asplayer.api.InputFrameBuffer;
import com.amlogic.asplayer.api.InputSourceType;
import com.amlogic.asplayer.api.TransitionSettings;
import com.amlogic.asplayer.api.TsPlaybackListener;
import com.amlogic.asplayer.api.VideoFormat;
import com.amlogic.asplayer.api.VideoParams;
import com.amlogic.asplayer.api.IASPlayer;
import com.amlogic.asplayer.api.WorkMode;
import com.amlogic.asplayer.core.utils.Utils;

import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.amlogic.asplayer.api.ASPlayer.INFO_BUSY;
import static com.amlogic.asplayer.api.ASPlayer.INFO_ERROR_RETRY;
import static com.amlogic.asplayer.api.ASPlayer.INFO_INVALID_OPERATION;
import static com.amlogic.asplayer.api.ASPlayer.INFO_INVALID_PARAMS;
import static com.amlogic.asplayer.core.TsPlaybackConfig.PLAYBACK_BUFFER_SIZE;
import static com.amlogic.asplayer.core.TsPlaybackConfig.TS_PACKET_SIZE;

public class ASPlayerImpl implements IASPlayer, VideoOutputPath.VideoFormatListener,
        AudioOutputPathBase.AudioFormatListener {

    private static final boolean DEBUG = false;
    private static final String TAG = Constant.LOG_TAG;

    private Tuner mTuner;
    private Context mContext;

    private ASPlayerConfig mConfig;

    private HandlerThread mPlayerThread;
    private Handler mPlayerHandler;

    private VideoOutputPath mVideoOutputPath;
    private AudioOutputPathBase mAudioOutputPath;

    private RendererScheduler mRendererScheduler;
    private TsPlayback mTsPlayback;

    private SurfaceControl mFccDummySurfaceControl;
    private Surface mFccDummySurface;

    private EventNotifier mEventNotifier;
    private Looper mEventLooper;
    private HandlerThread mEventThread;
    private CopyOnWriteArraySet<TsPlaybackListener> mPendingPlaybackListeners;

    private static class VideoInfo {
        private int mWidth;
        private int mHeight;
        private int mAspectRatio;
        private int mFrameRate;

        private VideoInfo(int width, int height, int aspectRatio) {
            mWidth = width;
            mHeight = height;
            mAspectRatio = aspectRatio;
            mFrameRate = 0;
        }
    }

    private VideoInfo mVideoInfo;

    private final int mId;
    private int mSyncInstanceId = Constant.INVALID_SYNC_INSTANCE_ID;
    private int mAvSyncHwId = Constant.INVALID_AV_SYNC_ID;

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

        if (needTsPlayback()) {
            prepareTsPlayback();
        }

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

        mPlayerHandler.post(this::handlePrepare);

        return ErrorCode.SUCCESS;
    }

    private void handlePrepare() {
        mRendererScheduler.setVideoFormatListener(this);
        mRendererScheduler.setAudioFormatListener(this);
        mRendererScheduler.prepare(mPlayerHandler);
    }

    @Override
    public void onVideoSizeInfoChanged(int width, int height, int pixelAspectRatio) {
        if (mVideoInfo == null) {
            mVideoInfo = new VideoInfo(width, height, pixelAspectRatio);
            tryNotifyVideoFormatChanged();
        } else if (mVideoInfo != null &&
                (width != mVideoInfo.mWidth || height != mVideoInfo.mHeight ||
                        pixelAspectRatio != mVideoInfo.mAspectRatio)) {
            mVideoInfo = new VideoInfo(width, height, pixelAspectRatio);
            tryNotifyVideoFormatChanged();
        }
    }

    @Override
    public void onAfdInfoChanged(byte activeFormat) {

    }

    @Override
    public void onFrameRateChanged(int frameRate) {
        MediaFormat format = MediaFormat.createVideoFormat("", 0, 0);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, (int) frameRate);
        if (mVideoInfo != null && frameRate != mVideoInfo.mFrameRate) {
            mVideoInfo.mFrameRate = (int)frameRate;
            tryNotifyVideoFormatChanged();
        }
    }

    private void tryNotifyVideoFormatChanged() {
        if (mVideoInfo != null && mVideoInfo.mWidth > 0 && mVideoInfo.mHeight > 0
            && mVideoInfo.mAspectRatio > 0 && mVideoInfo.mFrameRate > 0) {
            MediaFormat mediaFormat = new MediaFormat();
            mediaFormat.setInteger(VideoFormat.KEY_WIDTH, mVideoInfo.mWidth);
            mediaFormat.setInteger(VideoFormat.KEY_HEIGHT, mVideoInfo.mHeight);
            mediaFormat.setInteger(VideoFormat.KEY_FRAME_RATE, mVideoInfo.mFrameRate);
            mediaFormat.setInteger(VideoFormat.KEY_ASPECT_RATIO, mVideoInfo.mAspectRatio);

            mEventNotifier.notifyVideoFormatChange(mediaFormat);
        }
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
    public int getMajorVersion() {
        if (DEBUG) ASPlayerLog.d("%s getMajorVersion start", getTag());
        return 0;
    }

    @Override
    public int getMinorVersion() {
        if (DEBUG) ASPlayerLog.d("%s getMinorVersion start", getTag());
        return 0;
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

        mAvSyncHwId = Constant.INVALID_AV_SYNC_ID;
        mSyncInstanceId = Constant.INVALID_SYNC_INSTANCE_ID;

        mPendingPlaybackListeners.clear();

        if (mEventNotifier != null) {
            mEventNotifier.release();
            mEventNotifier = null;
        }

        if (mEventThread != null) {
            mEventThread.quitSafely();
            mEventThread = null;
        }

        mEventLooper = null;

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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null && mTsPlayback != null) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                mTsPlayback.stop();
                mTsPlayback.flush();
                mTsPlayback.start();
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

        if (mPlayerHandler != null && mPlayerThread != null && mPlayerThread.isAlive()) {
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
        if (mPlayerHandler != null && mPlayerThread != null && mPlayerThread.isAlive()) {
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
        if (mPlayerHandler != null && mPlayerThread != null && mPlayerThread.isAlive()) {
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
    public long getCurrentTime() {
        if (DEBUG) ASPlayerLog.d("%s getCurrentTime start", getTag());
        return 0;
    }

    @Override
    public long getPts(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s getPts start", getTag());
        return 0;
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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null && mPlayerThread != null && mPlayerThread.isAlive()) {
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
        if (playbackMode == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
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
                handleSetVideoParams(params);
            });
        } else {
            ASPlayerLog.e("%s failed to set video params, playerHandler is null", getTag());
            throw new IllegalStateException("ASPlayer not prepared");
        }
    }

    private void handleSetVideoParams(VideoParams params) {
        if (params != null) {
            MediaFormat format = params.getMediaFormat();
            int pid = params.getPid();
            int filterId = params.getTrackFilterId();
            int avSyncHwId = params.getAvSyncHwId();

            setSyncInstanceIdByAvSyncId(avSyncHwId);

            ASPlayerLog.i("%s setVideoParams pid: 0x%04x, filterId: 0x%016x, avsyncHwId: 0x%x, " +
                            "scrambled: %b, media format: %s",
                    getTag(), pid, filterId, avSyncHwId, params.isScrambled(), format);

            if (format == null) {
                format = MediaFormat.createVideoFormat(params.getMimeType(), params.getWidth(), params.getHeight());
                ASPlayerLog.i("%s setVideoParams create MediaFormat, mimetype: %s, width: %d, height: %d",
                        getTag(), params.getMimeType(), params.getWidth(), params.getHeight());
            }
            if (params.isScrambled() && !format.containsFeature(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback)) {
                format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback, true);
            }
            mVideoOutputPath.setVideoFormat(format);

            if (mVideoOutputPath instanceof VideoOutputPathV3) {
                VideoOutputPathV3 outputPathV3 = (VideoOutputPathV3) mVideoOutputPath;
                outputPathV3.setTrackFilterId(filterId);
                outputPathV3.setAvSyncHwId(avSyncHwId);
            }

            mVideoOutputPath.setSyncInstanceId(getSyncInstanceIdByAvSyncId(avSyncHwId));

            mRendererScheduler.onSetVideoParams(true);
        } else {
            mVideoOutputPath.setVideoFormat(null);
            if (mVideoOutputPath instanceof VideoOutputPathV3) {
                VideoOutputPathV3 outputPathV3 = (VideoOutputPathV3) mVideoOutputPath;
                outputPathV3.setTrackFilterId(MediaContainerExtractor.INVALID_FILTER_ID);
                outputPathV3.setAvSyncHwId(MediaContainerExtractor.INVALID_AV_SYNC_HW_ID);
            }

            mVideoOutputPath.setSyncInstanceId(Constant.INVALID_SYNC_INSTANCE_ID);

            mRendererScheduler.onSetVideoParams(false);
        }
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
        if (transitionModeBefore != TransitionSettings.TransitionModeBefore.BLACK
                && transitionModeBefore != TransitionSettings.TransitionModeBefore.LAST_IMAGE) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mVideoOutputPath.setTransitionModeBefore(transitionModeBefore);
            });
            return ErrorCode.SUCCESS;
        } else {
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
        if (mPlayerHandler != null) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
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
            return;
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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
            ConditionVariable lock = new ConditionVariable();
            mPlayerHandler.post(() -> {
                mRendererScheduler.stopVideoDecoding();
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
        if (volume < 0 || volume > 100) {
            ASPlayerLog.w("%s setAudioVolume invalid parameter, volume should in[0, 100], current: %d", getTag(), volume);
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                float vol = 1.0f * volume / 100;
                mAudioOutputPath.setVolume(vol);
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
    public void setAudioStereoMode(int audioStereoMode) {
    }

    @Override
    public int getAudioStereoMode() {
        return 0;
    }

    @Override
    public int setAudioMute(boolean analogMute, boolean digitalMute) {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mAudioOutputPath.setMuted(digitalMute);
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

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleSetAudioParams(params);
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

            mAudioOutputPath.setSyncInstanceId(Constant.INVALID_SYNC_INSTANCE_ID);

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

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleSwitchAudioTrack(params);
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

            mAudioOutputPath.setSyncInstanceId(Constant.INVALID_SYNC_INSTANCE_ID);

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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                ASPlayerLog.i("%s setADParams pid: %d, filterId: %d, format: %s",
                        getTag(), params.getPid(), params.getTrackFilterId(), params.getMediaFormat());
                if (mAudioOutputPath instanceof AudioOutputPathV3) {
                    AudioOutputPathV3 outputPathV3 = (AudioOutputPathV3)mAudioOutputPath;
                    outputPathV3.setSubTrackAudioParams(params);
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

        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
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
        if (mPlayerHandler != null) {
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
        if (mixLevel < 0 || mixLevel > 100) {
            ASPlayerLog.w("%s setADMixLevel invalid parameter, volume should in [0, 100], current: %d",
                    getTag(), mixLevel);
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayerHandler != null) {
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
    public int setSubtitlePid(int pid) {
        if (DEBUG) ASPlayerLog.d("%s setSubtitlePid start", getTag());
        return 0;
    }

    @Override
    public com.amlogic.asplayer.api.State getState() {
        if (DEBUG) ASPlayerLog.d("%s getState start", getTag());
        return null;
    }

    @Override
    public int startSubtitle() {
        if (DEBUG) ASPlayerLog.d("%s startSubtitle start", getTag());
        return 0;
    }

    @Override
    public int stopSubtitle() {
        if (DEBUG) ASPlayerLog.d("%s stopSubtitle start", getTag());
        return 0;
    }

    @Override
    public long getFirstPts(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s getFirstPts start", getTag());
        return 0;
    }

    private String getTag() {
        return String.format("[No-%d]-[%d]ASPlayer", mSyncInstanceId, mId);
    }
}