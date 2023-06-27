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
import android.view.Surface;

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

import java.util.Locale;

import static com.amlogic.asplayer.api.ASPlayer.INFO_BUSY;
import static com.amlogic.asplayer.api.ASPlayer.INFO_ERROR_RETRY;
import static com.amlogic.asplayer.api.ASPlayer.INFO_INVALID_OPERATION;
import static com.amlogic.asplayer.api.ASPlayer.INFO_INVALID_PARAMS;
import static com.amlogic.asplayer.core.TsPlaybackConfig.PLAYBACK_BUFFER_SIZE;
import static com.amlogic.asplayer.core.TsPlaybackConfig.TS_PACKET_SIZE;

public class ASPlayerImpl implements IASPlayer, VideoOutputPath.VideoFormatListener,
        AudioOutputPath.AudioFormatListener {

    private static final boolean DEBUG = false;
    private static final String TAG = Constant.LOG_TAG;

    private Tuner mTuner;

    private ASPlayerConfig mConfig;

    private HandlerThread mPlayerThread;
    private Handler mPlayerHandler;

    private VideoOutputPath mVideoOutputPath;
    private AudioOutputPath mAudioOutputPath;

    private RendererScheduler mRendererScheduler;
    private TsPlayback mTsPlayback;

    private EventNotifier mEventNotifier;

    private static class VideoSizeInfo {
        private int mWidth;
        private int mHeight;
        private int mAspectRatio;
        private int mFrameRate;

        private VideoSizeInfo(int width, int height, int aspectRatio) {
            mWidth = width;
            mHeight = height;
            mAspectRatio = aspectRatio;
            mFrameRate = 0;
        }
    }

    private VideoSizeInfo mVideoSizeInfo;

    private int mId;

    public ASPlayerImpl(int id, Context context, Tuner tuner, ASPlayerConfig config, Looper looper) {
        mId = id;
        mTuner = tuner;
        mConfig = config;

        mEventNotifier = new EventNotifier(id, looper);
        mVideoOutputPath = new VideoOutputPathV3(mId, ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH);
        mAudioOutputPath = new AudioOutputPathV3(mId);

        mVideoOutputPath.setDataListener(this::onFirstMediaData);
        mAudioOutputPath.setDataListener(this::onFirstMediaData);

        mRendererScheduler = new RendererScheduler(mId, context, this, mConfig, mVideoOutputPath, mAudioOutputPath, mEventNotifier);
    }

    @Override
    public void addPlaybackListener(TsPlaybackListener listener) {
        mEventNotifier.mPlaybackListeners.add(listener);
    }

    @Override
    public void removePlaybackListener(TsPlaybackListener listener) {
        mEventNotifier.mPlaybackListeners.remove(listener);
    }

    @Override
    public int prepare() {
        if (mPlayerThread != null) {
            ASPlayerLog.w("Player-%d already prepared", mId);
            return ErrorCode.ERROR_INVALID_OPERATION;
        }

        mPlayerThread = new HandlerThread(String.format(Locale.US, "AsPlayer:%d", mId),
                Process.THREAD_PRIORITY_AUDIO);
        mPlayerThread.start();
        mPlayerHandler = new Handler(mPlayerThread.getLooper());
        mPlayerHandler.post(this::handlePrepare);

        if (needTsPlayback()) {
            prepareTsPlayback();
        }

        return ErrorCode.SUCCESS;
    }

    private void handlePrepare() {
        mRendererScheduler.setVideoFormatListener(this);
        mRendererScheduler.setAudioFormatListener(this);
        mRendererScheduler.prepare(mId, mPlayerHandler);
    }

    private void onFirstMediaData(MediaOutputPath outputPath) {
        if (outputPath == mVideoOutputPath) {

        } else if (outputPath == mAudioOutputPath) {

        }
    }

    @Override
    public void onVideoSizeInfoChanged(int width, int height, int pixelAspectRatio) {
        if (mVideoSizeInfo == null) {
            mVideoSizeInfo = new VideoSizeInfo(width, height, pixelAspectRatio);
            tryNotifyVideoFormatChanged();
        } else if (mVideoSizeInfo != null &&
                (width != mVideoSizeInfo.mWidth || height != mVideoSizeInfo.mHeight ||
                        pixelAspectRatio != mVideoSizeInfo.mAspectRatio)) {
            mVideoSizeInfo = new VideoSizeInfo(width, height, pixelAspectRatio);
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
        if (mVideoSizeInfo != null && frameRate != mVideoSizeInfo.mFrameRate) {
            mVideoSizeInfo.mFrameRate = (int)frameRate;
            tryNotifyVideoFormatChanged();
        }
    }

    private void tryNotifyVideoFormatChanged() {
        if (mVideoSizeInfo != null && mVideoSizeInfo.mWidth > 0 && mVideoSizeInfo.mHeight > 0
            && mVideoSizeInfo.mAspectRatio > 0 && mVideoSizeInfo.mFrameRate > 0) {
            MediaFormat mediaFormat = new MediaFormat();
            mediaFormat.setInteger(VideoFormat.KEY_WIDTH, mVideoSizeInfo.mWidth);
            mediaFormat.setInteger(VideoFormat.KEY_HEIGHT, mVideoSizeInfo.mHeight);
            mediaFormat.setInteger(VideoFormat.KEY_FRAME_RATE, mVideoSizeInfo.mFrameRate);
            mediaFormat.setInteger(VideoFormat.KEY_ASPECT_RATIO, mVideoSizeInfo.mAspectRatio);

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
        if (DEBUG) ASPlayerLog.d("%s-%d getMajorVersion start", TAG, mId);
        return 0;
    }

    @Override
    public int getMinorVersion() {
        if (DEBUG) ASPlayerLog.d("%s-%d getMinorVersion start", TAG, mId);
        return 0;
    }

    @Override
    public int getInstancesNumber() {
        if (DEBUG) ASPlayerLog.d("%s-%d getInstancesNumber start", TAG, mId);
        return 0;
    }

    @Override
    public int getSyncInstancesNumber() {
        if (DEBUG) ASPlayerLog.d("%s-%d getSyncInstancesNumber start", TAG, mId);
        return 0;
    }

    @Override
    public void release() {
        if (DEBUG) ASPlayerLog.d("%s-%d release start", TAG, mId);
        if (mPlayerHandler == null) {
            ASPlayerLog.d("%s-%d already released", TAG, mId);
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

        mPlayerThread.quitSafely();
        mPlayerThread = null;

        mPlayerHandler = null;

        mVideoOutputPath = null;
        mAudioOutputPath = null;

        mTuner = null;
    }

    private void handleRelease() {
        handleStop();
        mRendererScheduler.setDataListener(null);
        mRendererScheduler.setVideoFormatListener(null);
        mRendererScheduler.setAudioFormatListener(null);
        mRendererScheduler.release();

        releaseTsPlayback();

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
        if (DEBUG) ASPlayerLog.d("%s-%d writeFrameData start", TAG, mId);
        return 0;
    }

    @Override
    public int writeData(InputBuffer inputBuffer, long timeoutMillSecond) {
//        if (DEBUG) ASPlayerLog.d("%s-%d writeData start, buffer size: %d", TAG, mId, (inputBuffer != null ? inputBuffer.mBufferSize : 0));
        if (inputBuffer == null) {
            ASPlayerLog.i("%s-%d writeData failed, invalid param, inputBuffer is null", TAG, mId);
            return INFO_INVALID_PARAMS;
        } else if (inputBuffer.mBuffer == null) {
            ASPlayerLog.i("%s-%d writeData failed, invalid param, inputBuffer.mBuffer is null", TAG, mId);
            return INFO_INVALID_PARAMS;
        } else if (inputBuffer.mOffset < 0 || inputBuffer.mBufferSize < 0) {
            ASPlayerLog.i("%s-%d writeData failed, invalid param, offset: %d, bufferSize: %d",
                    TAG, mId, inputBuffer.mOffset, inputBuffer.mBufferSize);
            return INFO_INVALID_PARAMS;
        } else if (inputBuffer.mBufferSize == 0) {
            ASPlayerLog.i("%s-%d writeData buffer is empty", TAG, mId);
            return INFO_ERROR_RETRY;
        }

        if (mTsPlayback != null) {
            return writeToTsPlayback(inputBuffer.mBuffer, inputBuffer.mOffset, inputBuffer.mBufferSize);
        } else {
            ASPlayerLog.w("%s-%d writeData failed", TAG, mId);
            return INFO_INVALID_OPERATION;
        }
    }

    public int writeData(int inputBufferType, byte[] buffer, int offset, int size, long timeoutMillSecond) {
        if (buffer == null) {
            ASPlayerLog.i("%s-%d writeData failed, invalid param, buffer is null", TAG, mId);
            return INFO_INVALID_PARAMS;
        } else if (offset < 0 || size < 0) {
            ASPlayerLog.i("%s-%d writeData failed, invalid param, offset: %d, size: %d",
                    TAG, mId, offset, size);
            return INFO_INVALID_PARAMS;
        } else if (size == 0) {
            ASPlayerLog.i("%s-%d writeData buffer is empty", TAG, mId);
            return INFO_ERROR_RETRY;
        }

        if (mTsPlayback != null) {
            return writeToTsPlayback(buffer, offset, size);
        } else {
            ASPlayerLog.w("%s-%d writeData failed", TAG, mId);
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
            ASPlayerLog.w("%s-%d writeData error, ret: %d", TAG, mId, ret);
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
            ASPlayerLog.e("%s-%d flush called, but playerHandler is null", TAG, mId);
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
            });
            lock.block();
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.e("%s-%d flushDvr failed, playerHandler: %s, tsPlayback: %s",
                    TAG, mId, mPlayerHandler, mTsPlayback);
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setWorkMode(int mode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setWorkMode start", TAG, mId);
        return 0;
    }

    @Override
    public int setPIPMode(int mode) {
        if (mPlayerHandler != null && mPlayerThread != null && mPlayerThread.isAlive()) {
            mPlayerHandler.post(() -> {
                mRendererScheduler.setPIPMode(mode);
            });
        } else {
            ASPlayerLog.e("%s-%d setPIPMode called, but playerHandler is null", TAG, mId);
            mRendererScheduler.setPIPMode(mode);
        }
        return ErrorCode.SUCCESS;
    }

    @Override
    public long getCurrentTime() {
        if (DEBUG) ASPlayerLog.d("%s-%d getCurrentTime start", TAG, mId);
        return 0;
    }

    @Override
    public long getPts(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s-%d getPts start", TAG, mId);
        return 0;
    }

    @Override
    public void setSyncMode(int mode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setSyncMode start", TAG, mId);

    }

    @Override
    public int getSyncMode() {
        if (DEBUG) ASPlayerLog.d("%s-%d getSyncMode start", TAG, mId);
        return 0;
    }

    @Override
    public int setPcrPid(int pid) {
        if (DEBUG) ASPlayerLog.d("%s-%d setPcrPid start", TAG, mId);
        return 0;
    }

    @Override
    public int startFast(float scale) {
        if (DEBUG) ASPlayerLog.d("%s-%d startFast start, scale: %.3f", TAG, mId, scale);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStartFast(scale);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s-%d startFast called, but playerHandler is null", TAG, mId);
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
        if (DEBUG) ASPlayerLog.d("%s-%d stopFast start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStopFast();
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.w("%s-%d stopFast called, but playerHandler is null", TAG, mId);
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
            ASPlayerLog.w("%s-%d setTrickMode called, but playerHandler is null", TAG, mId);
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
                mVideoOutputPath.setSurface(surface);
                lock.open();
            });
            lock.block();
        } else {
            mVideoOutputPath.setSurface(surface);
            ASPlayerLog.w("%s-%d setSurface called, but playerHandler is null", TAG, mId);
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
                ASPlayerLog.e("%s-%d setVideoParams failed, error: %s", TAG, mId, msg);
                throw new IllegalArgumentException(msg);
            } else if (avSyncHwId < 0) {
                String msg = String.format("invalid avSyncHwId id: %d", avSyncHwId);
                ASPlayerLog.e("%s-%d setVideoParams failed, error: %s", TAG, mId, msg);
                throw new IllegalArgumentException(msg);
            }
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleSetVideoParams(params);
            });
        } else {
            ASPlayerLog.e("%s-%d failed to set video params, playerHandler is null", TAG, mId);
            throw new IllegalStateException("ASPlayer not prepared");
        }
    }

    private void handleSetVideoParams(VideoParams params) {
        if (params != null) {
            MediaFormat format = params.getMediaFormat();
            int pid = params.getPid();
            int filterId = params.getTrackFilterId();
            int avSyncHwId = params.getAvSyncHwId();
            ASPlayerLog.i("%s-%d setVideoParams pid: %d, filterId: %d, avsyncHwId: %d, media format: %s",
                    TAG, mId, pid, filterId, avSyncHwId, format);

            if (format == null) {
                format = MediaFormat.createVideoFormat(params.getMimeType(), params.getWidth(), params.getHeight());
                ASPlayerLog.i("%s-%d setVideoParams create MediaFormat, mimetype: %s, width: %d, height: %d",
                        TAG, mId, params.getMimeType(), params.getWidth(), params.getHeight());
            }
            mVideoOutputPath.setMediaFormat(format);

            if (mVideoOutputPath instanceof VideoOutputPathV3) {
                VideoOutputPathV3 outputPathV3 = (VideoOutputPathV3) mVideoOutputPath;
                outputPathV3.setTrackFilterId(filterId);
                outputPathV3.setAvSyncHwId(avSyncHwId);
            }

            mRendererScheduler.onSetVideoParams(true);
        } else {
            mVideoOutputPath.setMediaFormat(null);
            if (mVideoOutputPath instanceof VideoOutputPathV3) {
                VideoOutputPathV3 outputPathV3 = (VideoOutputPathV3) mVideoOutputPath;
                outputPathV3.setTrackFilterId(MediaContainerExtractor.INVALID_FILTER_ID);
                outputPathV3.setAvSyncHwId(MediaContainerExtractor.INVALID_AV_SYNC_HW_ID);
            }

            mRendererScheduler.onSetVideoParams(false);
        }
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
        return null;
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
            ASPlayerLog.i("%s-%d startVideoDecoding failed, playerHandler is null", TAG, mId);
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
            ASPlayerLog.i("%s-%d pauseVideoDecoding failed, playerHandler is null", TAG, mId);
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
            ASPlayerLog.i("%s-%d resumeVideoDecoding failed, playerHandler is null", TAG, mId);
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
            ASPlayerLog.i("%s-%d stopVideoDecoding failed, playerHandler is null", TAG, mId);
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
            ASPlayerLog.w("%s-%d setAudioVolume invalid parameter, volume should in[0, 100], current: %d", TAG, mId, volume);
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                float vol = 1.0f * volume / 100;
                mAudioOutputPath.setVolume(vol);
            });
            return ErrorCode.SUCCESS;
        } else {
            ASPlayerLog.i("%s-%d setAudioVolume failed, playerHandler is null", TAG, mId);
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int getAudioVolume() {
        float vol = mAudioOutputPath.getVolume();
        int volume = (int)(vol * 100);
        ASPlayerLog.i("%s-%d getAudioVolume, volume: %.2f, return: %d", TAG, mId, vol, volume);
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
            ASPlayerLog.w("%s-%d setAudioMute failed, playerHandler is null", TAG, mId);
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
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioParams start, params: %s", TAG, mId, params);
        if (params == null) {
            throw new NullPointerException("AudioParams can not be null");
        }

        int playbackMode = mConfig.getPlaybackMode();
        if (playbackMode == ASPlayerConfig.PLAYBACK_MODE_PASSTHROUGH) {
            int filterId = params.getTrackFilterId();
            int avSyncHwId = params.getAvSyncHwId();
            if (filterId < 0) {
                String msg = String.format("invalid filter id: %d", filterId);
                ASPlayerLog.e("%s-%d setAudioParams failed, error: %s", TAG, mId, msg);
                throw new IllegalArgumentException(msg);
            } else if (avSyncHwId < 0) {
                String msg = String.format("invalid avSyncHw id: %d", avSyncHwId);
                ASPlayerLog.e("%s-%d setAudioParams failed, error: %s", TAG, mId, msg);
                throw new IllegalArgumentException(msg);
            }
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleSetAudioParams(params);
            });
        } else {
            ASPlayerLog.e("%s-%d setAudioParams failed, playerHandler is null", TAG, mId);
        }
    }

    private void handleSetAudioParams(AudioParams params) {
        if (params != null) {
            MediaFormat mediaFormat = params.getMediaFormat();
            int pid = params.getPid();
            int filterId = params.getTrackFilterId();
            int avSyncHwId = params.getAvSyncHwId();
            ASPlayerLog.i("%s-%d setAudioParams pid: %d, filterId: %d, avSyncHwId: %d, format: %s",
                    TAG, mId, pid, filterId, avSyncHwId, mediaFormat);

            if (mediaFormat == null) {
                mediaFormat = MediaFormat.createAudioFormat(params.getMimeType(), params.getSampleRate(), params.getChannelCount());
                ASPlayerLog.i("%s-%d setAudioParams create MediaFormat, mimeType: %s, sampleRate: %d, channelCount: %d",
                        TAG, mId, params.getMimeType(), params.getSampleRate(), params.getChannelCount());
            }
            mAudioOutputPath.setMediaFormat(mediaFormat);

            if (mAudioOutputPath instanceof AudioOutputPathV3) {
                AudioOutputPathV3 outputPathV3 = (AudioOutputPathV3)mAudioOutputPath;
                outputPathV3.setAudioFilterId(params.getTrackFilterId());
                outputPathV3.setAvSyncHwId(params.getAvSyncHwId());
            }

            mRendererScheduler.onSetAudioParams(true);
        } else {
            ASPlayerLog.i("%s-%d setAudioParams params is null", TAG, mId);
            mAudioOutputPath.setMediaFormat(null);
            if (mAudioOutputPath instanceof AudioOutputPathV3) {
                AudioOutputPathV3 outputPathV3 = (AudioOutputPathV3) mAudioOutputPath;
                outputPathV3.setAudioFilterId(MediaContainerExtractor.INVALID_FILTER_ID);
                outputPathV3.setAudioSubTrackFilterId(MediaContainerExtractor.INVALID_FILTER_ID);
                outputPathV3.setAvSyncHwId(MediaContainerExtractor.INVALID_AV_SYNC_HW_ID);
            }

            mRendererScheduler.onSetAudioParams(false);
        }
    }

    @Override
    public MediaFormat getAudioInfo() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioInfo start", TAG, mId);
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
            ASPlayerLog.i("%s-%d startAudioDecoding failed, playerHandler is null", TAG, mId);
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
            ASPlayerLog.i("%s-%d pauseAudioDecoding failed, playerHandler is null", TAG, mId);
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
            ASPlayerLog.i("%s-%d resumeAudioDecoding failed, playerHandler is null", TAG, mId);
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
            ASPlayerLog.i("%s-%d stopAudioDecoding failed, playerHandler is null", TAG, mId);
            return ErrorCode.ERROR_INVALID_OPERATION;
        }
    }

    @Override
    public int setAudioDescriptionParams(AudioParams params) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioDescriptionParams start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                ASPlayerLog.i("%s-%d setAudioDescriptionParams pid: %d, filterId: %d, format: %s", TAG, mId, params.getPid(), params.getTrackFilterId(), params.getMediaFormat());
                if (mAudioOutputPath instanceof AudioOutputPathV3) {
                    AudioOutputPathV3 outputPathV3 = (AudioOutputPathV3)mAudioOutputPath;
                    outputPathV3.setAudioSubTrackFilterId(params.getTrackFilterId());
                }
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d setAudioDescriptionParams failed, playerHandler is null", TAG, mId);
            return -1;
        }
    }

    @Override
    public MediaFormat getAudioDescriptionInfo() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioDescriptionInfo start", TAG, mId);
        return null;
    }

    @Override
    public int setSubtitlePid(int pid) {
        if (DEBUG) ASPlayerLog.d("%s-%d setSubtitlePid start", TAG, mId);
        return 0;
    }

    @Override
    public com.amlogic.asplayer.api.State getState() {
        if (DEBUG) ASPlayerLog.d("%s-%d getState start", TAG, mId);
        return null;
    }

    @Override
    public int startSubtitle() {
        if (DEBUG) ASPlayerLog.d("%s-%d startSubtitle start", TAG, mId);
        return 0;
    }

    @Override
    public int stopSubtitle() {
        if (DEBUG) ASPlayerLog.d("%s-%d stopSubtitle start", TAG, mId);
        return 0;
    }

    @Override
    public long getFirstPts(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s-%d getFirstPts start", TAG, mId);
        return 0;
    }
}
