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
import com.amlogic.asplayer.api.InputBuffer;
import com.amlogic.asplayer.api.InputFrameBuffer;
import com.amlogic.asplayer.api.InputSourceType;
import com.amlogic.asplayer.api.TsPlaybackListener;
import com.amlogic.asplayer.api.VideoParams;
import com.amlogic.asplayer.api.AudioDecoderStat;
import com.amlogic.asplayer.api.BufferStat;
import com.amlogic.asplayer.api.IASPlayer;
import com.amlogic.asplayer.api.VideoDecoderStat;

import java.util.Locale;

import static com.amlogic.asplayer.api.ASPlayer.INFO_BUSY;
import static com.amlogic.asplayer.api.ASPlayer.INFO_ERROR_RETRY;
import static com.amlogic.asplayer.api.ASPlayer.INFO_UNKNOWN_ERROR;
import static com.amlogic.asplayer.core.TsPlaybackConfig.PLAYBACK_BUFFER_SIZE;
import static com.amlogic.asplayer.core.TsPlaybackConfig.TS_PACKET_SIZE;

public class ASPlayerImpl implements IASPlayer, VideoOutputPath.VideoFormatListener {

    private static final boolean DEBUG = true;
    private static final String TAG = Constant.LOG_TAG;

    private enum State {
        STATE_NONE,
        STATE_READY,
        STATE_PLAYING,
        STATE_ERROR,
    }

    private Tuner mTuner;

    private ASPlayerConfig mConfig;

    // state of the player
    private State mState;
    private int mErrorBits;

    private HandlerThread mPlayerThread;
    private Handler mPlayerHandler;

    private VideoOutputPath mVideoOutputPath;
    private AudioOutputPath mAudioOutputPath;

    private RendererScheduler mRendererScheduler;
    private TsPlayback mTsPlayback;

    private EventNotifier mEventNotifier;

    private static class VideoSizeInfo {
        private final int mWidth;
        private final int mHeight;
        private final float mAspectRatio;

        private VideoSizeInfo(int width, int height, float aspectRatio) {
            mWidth = width;
            mHeight = height;
            mAspectRatio = aspectRatio;
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
        if (DEBUG) ASPlayerLog.d("%s-%d prepare start", TAG, mId);
        if (mPlayerThread != null) {
            ASPlayerLog.w("Player-%d already prepared", mId);
            return -1;
        }

        mPlayerThread = new HandlerThread(String.format(Locale.US, "TvPlayer:%d", mId),
                Process.THREAD_PRIORITY_AUDIO);
        mPlayerThread.start();
        mPlayerHandler = new Handler(mPlayerThread.getLooper());
        mPlayerHandler.post(this::handlePrepare);

        if (needTsPlayback()) {
            prepareTsPlayback();
        }

        mState = State.STATE_READY;
        mErrorBits = 0;
        return 0;
    }

    private void handlePrepare() {
        mRendererScheduler.setVideoListener(this);
        mRendererScheduler.prepare(mId, mPlayerHandler);
    }

    private void onFirstMediaData(MediaOutputPath outputPath) {
        if (outputPath == mVideoOutputPath) {

        } else if (outputPath == mAudioOutputPath) {

        }
    }

    @Override
    public void onVideoSizeInfoChanged(int width, int height, float pixelAspectRatio) {
        if (mVideoSizeInfo == null) {
            mVideoSizeInfo = new VideoSizeInfo(width, height, pixelAspectRatio);
            MediaFormat format = MediaFormat.createVideoFormat("", width, height);
            mEventNotifier.notifyVideoFormatChange(format);
            return;
        } else if (mVideoSizeInfo != null &&
                (width != mVideoSizeInfo.mWidth || height != mVideoSizeInfo.mHeight ||
                        pixelAspectRatio != mVideoSizeInfo.mAspectRatio)) {
            MediaFormat format = MediaFormat.createVideoFormat("", width, height);
            mEventNotifier.notifyVideoFormatChange(format);
            mVideoSizeInfo = new VideoSizeInfo(width, height, pixelAspectRatio);
        }
    }

    @Override
    public void onAfdInfoChanged(byte activeFormat) {

    }

    @Override
    public void onFrameRateChanged(float frameRate) {
        MediaFormat format = MediaFormat.createVideoFormat("", 0, 0);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, (int) frameRate);
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
            mRendererScheduler.setDataListener(null);
            mRendererScheduler.setVideoListener(null);
            mRendererScheduler.release();
            releaseTsPlayback();
            mVideoOutputPath = null;
            mAudioOutputPath = null;
            mTuner = null;
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

        mTuner = null;
        mState = State.STATE_NONE;
        mErrorBits = 0;
    }

    private void handleRelease() {
        handleStop();
        mRendererScheduler.setDataListener(null);
        mRendererScheduler.setVideoListener(null);
        mRendererScheduler.release();

        releaseTsPlayback();
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
        if (inputBuffer == null || inputBuffer.mBuffer == null || inputBuffer.mBufferSize < 0) {
            ASPlayerLog.i("%s-%d writeData failed, invalid param", TAG, mId);
            return INFO_UNKNOWN_ERROR;
        } else if (inputBuffer.mBufferSize == 0) {
            ASPlayerLog.i("%s-%d writeData buffer is empty", TAG, mId);
            return INFO_ERROR_RETRY;
        }

        if (mTsPlayback != null) {
//            byte[] buffer = new byte[inputBuffer.bufferSize];
//            System.arraycopy(inputBuffer.buffer, 0, buffer, 0, inputBuffer.bufferSize);
            byte[] buffer = inputBuffer.mBuffer;
            long offset = inputBuffer.mOffset;
            long ret = mTsPlayback.write(buffer, offset, inputBuffer.mBufferSize);
            if (ret > 0) {
                return (int)ret;
            } else if (ret == 0) {
//                ASPlayerLog.i("%s-%d writeData ret 0", TAG, mId);
                return INFO_BUSY;
            } else {
                ASPlayerLog.w("%s-%d writeData error, ret: %d", TAG, mId);
                return INFO_ERROR_RETRY;
            }
        } else {
            ASPlayerLog.w("%s-%d writeData failed", TAG, mId);
        }
        return INFO_UNKNOWN_ERROR;
    }

    public long writeData(byte[] buffer, long offset, long size) {
        if (mTsPlayback != null) {
            return mTsPlayback.write(buffer, offset, size);
        }
        return INFO_UNKNOWN_ERROR;
    }

    @Override
    public void flush() {
        ASPlayerLog.i("%s-%d flush start", TAG, mId);
        if (mTsPlayback != null) {
            mTsPlayback.stop();
            mTsPlayback.flush();
            mTsPlayback.start();
        } else {
            ASPlayerLog.e("%s-%d flush failed, mTsPlayback is null", TAG, mId);
        }
    }

    @Override
    public int setWorkMode(int mode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setWorkMode start", TAG, mId);
        return 0;
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
    public long getDelayTime() {
        if (DEBUG) ASPlayerLog.d("%s-%d getDelayTime start", TAG, mId);
        return 0;
    }

    @Override
    public int startFast(float scale) {
        if (DEBUG) ASPlayerLog.d("%s-%d startFast start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStartFast(scale);
            });
            return 0;
        } else {
            ASPlayerLog.w("%s-%d startFast called, but playerHandler is null", TAG, mId);
            return -1;
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
            return 0;
        } else {
            ASPlayerLog.w("%s-%d stopFast called, but playerHandler is null", TAG, mId);
            return -1;
        }
    }

    @Override
    public int setTrickMode(int trickMode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setTrickMode start", TAG, mId);
        return 0;
    }

    @Override
    public BufferStat getBufferStat(int streamType) {
        if (DEBUG) ASPlayerLog.d("%s-%d getBufferStat start", TAG, mId);
        return null;
    }

    @Override
    public int setVideoWindow(int x, int y, int width, int height) {
        if (DEBUG) ASPlayerLog.d("%s-%d setVideoWindow start", TAG, mId);
        return 0;
    }

    @Override
    public int setVideoCrop(int left, int top, int right, int bottom) {
        if (DEBUG) ASPlayerLog.d("%s-%d setVideoCrop start", TAG, mId);
        return 0;
    }

    @Override
    public int setSurface(Surface surface) {
        ASPlayerLog.i("%s-%d setSurface start, surface: %s", TAG, mId, surface);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mVideoOutputPath.setSurface(surface);
            });
            return 0;
        } else {
            mVideoOutputPath.setSurface(surface);
            ASPlayerLog.w("%s-%d setSurface called, but playerHandler is null", TAG, mId);
            return 0;
        }
    }

    @Override
    public int setFccDummySurface(Surface surface) {
        ASPlayerLog.i("%s-%d setFccDummySurface start", TAG, mId);
        return 0;
    }

    @Override
    public void setVideoMatchMode(int videoMatchMode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setVideoMatchMode start", TAG, mId);

    }

    @Override
    public int setVideoParams(VideoParams params) {
        ASPlayerLog.i("%s-%d setVideoParams start, params: %s", TAG, mId, params);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleSetVideoParams(params);
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d failed to set video params, playerHandler is null", TAG, mId);
            return -1;
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
        } else {
            mVideoOutputPath.setMediaFormat(null);
            if (mVideoOutputPath instanceof VideoOutputPathV3) {
                VideoOutputPathV3 outputPathV3 = (VideoOutputPathV3) mVideoOutputPath;
                outputPathV3.setTrackFilterId(MediaContainerExtractor.INVALID_FILTER_ID);
                outputPathV3.setAvSyncHwId(MediaContainerExtractor.INVALID_AV_SYNC_HW_ID);
            }
        }
        mRendererScheduler.onSetVideoParams();
    }

    @Override
    public void setVideoBlackOut(boolean blackout) {
        if (DEBUG) ASPlayerLog.d("%s-%d setVideoBlackOut start", TAG, mId);
    }

    @Override
    public MediaFormat getVideoInfo() {
        if (DEBUG) ASPlayerLog.d("%s-%d getVideoInfo start", TAG, mId);
        return null;
    }

    @Override
    public VideoDecoderStat getVideoStat() {
        if (DEBUG) ASPlayerLog.d("%s-%d getVideoStat start", TAG, mId);
        return null;
    }

    @Override
    public int startVideoDecoding() {
        if (DEBUG) ASPlayerLog.d("%s-%d startVideoDecoding start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStart();
                mRendererScheduler.startVideoDecoding();
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d startVideoDecoding failed, playerHandler is null", TAG, mId);
            return -1;
        }
    }

    private void handleStart() {
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
        if (DEBUG) ASPlayerLog.d("%s-%d pauseVideoDecoding start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mRendererScheduler.pauseVideoDecoding();
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d pauseVideoDecoding failed, playerHandler is null", TAG, mId);
            return -1;
        }
    }

    @Override
    public int resumeVideoDecoding() {
        if (DEBUG) ASPlayerLog.d("%s-%d resumeVideoDecoding start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mRendererScheduler.resumeVideoDecoding();
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d resumeVideoDecoding failed, playerHandler is null", TAG, mId);
            return -1;
        }
    }

    @Override
    public int stopVideoDecoding() {
        if (DEBUG) ASPlayerLog.d("%s-%d stopVideoDecoding start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mRendererScheduler.stopVideoDecoding();
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d stopVideoDecoding failed, playerHandler is null", TAG, mId);
            return -1;
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
    public void setAudioVolume(int volume) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioVolume start", TAG, mId);
        if (volume < 0 || volume > 100) {
            ASPlayerLog.w("%s-%d setAudioVolume invalid parameter, volume should in[0, 100], current: %d", TAG, mId, volume);
            return;
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                float vol = 1.0f * volume / 100;
                mAudioOutputPath.setVolume(vol);
            });
        }
    }

    @Override
    public int getAudioVolume() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioVolume start", TAG, mId);
        float vol = mAudioOutputPath.getVolume();
        int volume = (int)(vol * 100);
        ASPlayerLog.i("%s-%d getAudioVolume, volume: %.2f, return: %d", TAG, mId, vol, volume);
        return volume;
    }

    @Override
    public void setAudioStereoMode(int audioStereoMode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioStereoMode start", TAG, mId);

    }

    @Override
    public int getAudioStereoMode() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioStereoMode start", TAG, mId);
        return 0;
    }

    @Override
    public int setAudioMute(boolean analogMute, boolean digitalMute) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioMute start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mAudioOutputPath.setMuted(digitalMute);
            });
            return 0;
        } else {
            ASPlayerLog.w("%s-%d setAudioMute failed, playerHandler is null", TAG, mId);
            return -1;
        }
    }

    @Override
    public int getAudioAnalogMute() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioAnalogMute start", TAG, mId);
        return 0;
    }

    @Override
    public int getAudioDigitMute() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioDigitMute start", TAG, mId);
        return 0;
    }

    @Override
    public int setAudioParams(AudioParams params) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioParams start, params: %s", TAG, mId, params);
        if (params == null) {
            return 0;
        }

        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleSetAudioParams(params);
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d setAudioParams failed, playerHandler is null", TAG, mId);
            return -1;
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
        } else {
            ASPlayerLog.i("%s-%d setAudioParams params is null", TAG, mId);
            mAudioOutputPath.setMediaFormat(null);
            if (mAudioOutputPath instanceof AudioOutputPathV3) {
                AudioOutputPathV3 outputPathV3 = (AudioOutputPathV3) mAudioOutputPath;
                outputPathV3.setAudioFilterId(MediaContainerExtractor.INVALID_FILTER_ID);
                outputPathV3.setAudioSubTrackFilterId(MediaContainerExtractor.INVALID_FILTER_ID);
                outputPathV3.setAvSyncHwId(MediaContainerExtractor.INVALID_AV_SYNC_HW_ID);
            }
        }
        mRendererScheduler.onSetAudioParams();
    }

    @Override
    public void setAudioOutMode(int audioOutputMode) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioOutMode start", TAG, mId);

    }

    @Override
    public MediaFormat getAudioInfo() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioInfo start", TAG, mId);
        return null;
    }

    @Override
    public AudioDecoderStat getAudioStat() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioStat start", TAG, mId);
        return null;
    }

    @Override
    public int startAudioDecoding() {
        if (DEBUG) ASPlayerLog.d("%s-%d startAudioDecoding start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStart();
                mRendererScheduler.startAudioDecoding();
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d startAudioDecoding failed, playerHandler is null", TAG, mId);
            return -1;
        }
    }

    @Override
    public int pauseAudioDecoding() {
        if (DEBUG) ASPlayerLog.d("%s-%d pauseAudioDecoding start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mRendererScheduler.pauseAudioDecoding();
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d pauseAudioDecoding failed, playerHandler is null", TAG, mId);
            return -1;
        }
    }

    @Override
    public int resumeAudioDecoding() {
        if (DEBUG) ASPlayerLog.d("%s-%d resumeAudioDecoding start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mRendererScheduler.resumeAudioDecoding();
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d resumeAudioDecoding failed, playerHandler is null", TAG, mId);
            return -1;
        }
    }

    @Override
    public int stopAudioDecoding() {
        if (DEBUG) ASPlayerLog.d("%s-%d stopAudioDecoding start", TAG, mId);
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                mRendererScheduler.stopAudioDecoding();
            });
            return 0;
        } else {
            ASPlayerLog.i("%s-%d stopAudioDecoding failed, playerHandler is null", TAG, mId);
            return -1;
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
    public int setAudioDescriptionMixLevel(int masterVolume, int slaveVolume) {
        if (DEBUG) ASPlayerLog.d("%s-%d setAudioDescriptionMixLevel start", TAG, mId);
        return 0;
    }

    @Override
    public AudioVolume getAudioDescriptionMixLevel() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioDescriptionMixLevel start", TAG, mId);
        return null;
    }

    @Override
    public int enableAudioDescriptionMix() {
        if (DEBUG) ASPlayerLog.d("%s-%d enableAudioDescriptionMix start", TAG, mId);
        return 0;
    }

    @Override
    public int disableAudioDescriptionMix() {
        if (DEBUG) ASPlayerLog.d("%s-%d disableAudioDescriptionMix start", TAG, mId);
        return 0;
    }

    @Override
    public MediaFormat getAudioDescriptionInfo() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioDescriptionInfo start", TAG, mId);
        return null;
    }

    @Override
    public AudioDecoderStat getAudioDescriptionStat() {
        if (DEBUG) ASPlayerLog.d("%s-%d getAudioDescriptionStat start", TAG, mId);
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
