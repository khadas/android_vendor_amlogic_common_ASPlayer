/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.player;

import android.content.Context;
import android.media.MediaFormat;
import android.media.tv.TvInputService;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.filter.AvSettings;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterConfiguration;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.Settings;
import android.media.tv.tuner.filter.TsFilterConfiguration;
import android.media.tv.tuner.frontend.OnTuneEventListener;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import com.amlogic.asplayer.api.ASPlayer;
import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.EventMask;
import com.amlogic.asplayer.api.IASPlayer;
import com.amlogic.asplayer.api.InitParams;
import com.amlogic.asplayer.api.InputSourceType;
import com.amlogic.asplayer.api.TsPlaybackListener;
import com.amlogic.asplayer.api.VideoParams;
import com.amlogic.asplayer.api.VideoTrickMode;
import com.amlogic.asplayer.demo.Constant;
import com.amlogic.asplayer.demo.utils.HandlerExecutor;
import com.amlogic.asplayer.demo.utils.TunerHelper;
import com.amlogic.asplayer.demo.utils.TvLog;
import com.amlogic.asplayer.jni.wrapper.JniASPlayerWrapper;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;


public class TvPlayer {

    private static final String TAG = TvPlayer.class.getSimpleName();

    private static final double SPEED_DIFF_THRESHOLD = 0.001;

    protected Context mContext;
    protected Tuner mTuner;

    protected HandlerThread mPlayerThread;
    protected Handler mPlayerHandler;
    protected HandlerExecutor mHandlerExecutor;

    // Mainly for debug
    private int mId;
    private static AtomicInteger sId = new AtomicInteger(0);

    protected IASPlayer mASPlayer;
    private TsPlaybackListener mTsPlaybackListener;

    private int mTunerHalVersion = Constant.TUNER_HAL_VERSION_1_0;

    private Filter mVideoFilter;
    private Filter mAudioFilter;
    private Filter mSubAudioFilter;
    private TrackInfo.VideoTrackInfo mVideoTrackInfo;
    private TrackInfo.AudioTrackInfo mAudioTrackInfo;

    public interface TuneListener {
        void execTune(Tuner tuner);
        void onCancelTune(Tuner tuner);
    }

    private TuneListener mTuneListener;

    public TvPlayer(Context context, Looper looper) {
        mId = sId.getAndIncrement();

        mContext = context;
        int tvInputUseCase = getTunerTvInputUseCase();
        mTuner = new Tuner(context, null, tvInputUseCase);

        initASPlayer(looper);
    }

    public void setTuneListener(TuneListener listener) {
        mTuneListener = listener;
    }

    public void setASPlayerPlaybackListener(TsPlaybackListener listener) {
        mTsPlaybackListener = listener;
    }

    protected void initASPlayer(Looper looper) {
        InitParams initParams = new InitParams.Builder()
                .setPlaybackMode(InitParams.PLAYBACK_MODE_PASSTHROUGH)
                .setInputSourceType(InputSourceType.TS_DEMOD)
                .setEventMask(EventMask.EVENT_TYPE_PTS_MASK)
                .build();
        if (Constant.USE_JNI_AS_PLAYER) {
            mASPlayer = new JniASPlayerWrapper(initParams, mTuner);
        } else {
            mASPlayer = new ASPlayer(initParams, mTuner, null);
        }
        mASPlayer.addPlaybackListener(this::onPlaybackEvent);
        mASPlayer.prepare();
    }

    protected int getTunerTvInputUseCase() {
        return TvInputService.PRIORITY_HINT_USE_CASE_TYPE_LIVE;
    }

    public void prepare() {
        if (mPlayerThread != null) {
            TvLog.w("Player-%d already prepared", mId);
            return;
        }

        mPlayerThread = new HandlerThread(String.format(Locale.US, "TvPlayer:%d", mId),
                Process.THREAD_PRIORITY_AUDIO);
        mPlayerThread.start();
        mPlayerHandler = new Handler(mPlayerThread.getLooper());
        mHandlerExecutor = new HandlerExecutor(mPlayerHandler);

        mPlayerHandler.post(this::handlePrepare);

        mTuner.setResourceLostListener(mHandlerExecutor, new Tuner.OnResourceLostListener() {
            @Override
            public void onResourceLost(Tuner tuner) {
                if (tuner == mTuner) {
                    TvLog.i("Player-%d onResourceLost", mId);
                }
            }
        });
    }

    private void handlePrepare() {
    }

    public void setSurface(Surface surface) {
        if (mPlayerHandler != null) {
            mPlayerHandler.postAtFrontOfQueue(() -> {
                handleSetSurface(surface);
            });
        } else {
            if (mASPlayer != null) {
                mASPlayer.setSurface(surface);
            }
        }
    }

    private void handleSetSurface(Surface surface) {
        if (mASPlayer != null) {
            mASPlayer.setSurface(surface);
        }
    }

    public void start(Uri uri, Bundle bundle) {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStart(uri, bundle);
            });
        } else {
            TvLog.d("Player-%d start failed, handler is null", mId);
        }
    }

    private void handleStart(Uri uri, Bundle bundle) {
        prepareTunerForStart(uri);

        mTunerHalVersion = bundle.getInt(Constant.EXTRA_TUNER_HAL_VERSION, Constant.TUNER_HAL_VERSION_1_0);

        if (mASPlayer != null) {
            mVideoTrackInfo = getVideoTrackInfoFromBundle(bundle);

            startVideoFilter();
            if (mVideoFilter == null) {
                return;
            }

            String videoMimeType = mVideoTrackInfo.mimeType;
            int width = 1920;
            int height = 1080;
            if (mVideoTrackInfo.mimeType.contains("hevc")) {
                width = 3840;
                height = 2160;
            }

            VideoParams.Builder videoParamsBuilder = new VideoParams.Builder(videoMimeType, width, height)
                    .setPid(mVideoTrackInfo.pid)
                    .setTrackFilterId(mVideoFilter.getId())
                    .setAvSyncHwId(mTuner.getAvSyncHwId(mVideoFilter));
            VideoParams videoParams = videoParamsBuilder.build();
            TvLog.i("videofilterId: %d", mVideoFilter.getId());
            TvLog.i("videoAvSyncHwId: %d", mTuner.getAvSyncHwId(mVideoFilter));

            setVideoParams(videoParams);
            mASPlayer.startVideoDecoding();

            mAudioTrackInfo = getAudioTrackInfoFromBundle(bundle);

            startAudioFilter();
            if (mAudioFilter == null) {
                return;
            }

            String audioMimeType = mAudioTrackInfo.mimeType;
            AudioParams.Builder audioParamsBuilder = new AudioParams.Builder(audioMimeType, 48000, 2)
                    .setPid(mAudioTrackInfo.pid)
                    .setTrackFilterId(mAudioFilter.getId())
                    .setAvSyncHwId(mTuner.getAvSyncHwId(mAudioFilter));
            AudioParams audioParams = audioParamsBuilder.build();
            TvLog.i("audiofilterId: %d", mAudioFilter.getId());
            TvLog.i("audioAvSyncHwId: %d", mTuner.getAvSyncHwId(mAudioFilter));

            setAudioParams(audioParams);
            mASPlayer.startAudioDecoding();
        } else {
            TvLog.e("Player-%d handleStart failed, asplayer is null", mId);
        }
    }

    private static TrackInfo.VideoTrackInfo getVideoTrackInfoFromBundle(Bundle bundle) {
        TrackInfo.VideoTrackInfo videoTrackInfo = new TrackInfo.VideoTrackInfo();
        videoTrackInfo.pid = bundle.getInt(Constant.EXTRA_VIDEO_PID, -1);
        videoTrackInfo.mimeType = bundle.getString(Constant.EXTRA_VIDEO_MIME_TYPE, "");
        videoTrackInfo.videoStreamType = bundle.getString(Constant.EXTRA_VIDEO_STREAM_TYPE, "");
        return videoTrackInfo;
    }

    private static TrackInfo.AudioTrackInfo getAudioTrackInfoFromBundle(Bundle bundle) {
        TrackInfo.AudioTrackInfo audioTrackInfo = new TrackInfo.AudioTrackInfo();
        audioTrackInfo.pid = bundle.getInt(Constant.EXTRA_AUDIO_PID, -1);
        audioTrackInfo.mimeType = bundle.getString(Constant.EXTRA_AUDIO_MIME_TYPE, "");
        audioTrackInfo.audioStreamType = bundle.getString(Constant.EXTRA_AUDIO_STREAM_TYPE, "");
        return audioTrackInfo;
    }

    private void setVideoParams(VideoParams videoParams) {
        if (mASPlayer == null) {
            return;
        }

        try {
            mASPlayer.setVideoParams(videoParams);
        } catch (Exception e) {
            TvLog.e("setVideoParams failed, error: %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        }
    }

    private void setAudioParams(AudioParams audioParams) {
        if (mASPlayer == null) {
            return;
        }

        try {
            mASPlayer.setAudioParams(audioParams);
        } catch (Exception e) {
            TvLog.e("setAudioParams failed, error: %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        }
    }

    protected void prepareTunerForStart(Uri uri) {
        mTuner.setOnTuneEventListener(mHandlerExecutor, new OnTuneEventListener() {
            @Override
            public void onTuneEvent(int tuneEvent) {
                TvLog.d("Player-%d onTuneEvent event: %d", mId, tuneEvent);
                switch (tuneEvent) {
                    case SIGNAL_LOCKED:
                        break;
                    case SIGNAL_LOST_LOCK:
                    case SIGNAL_NO_SIGNAL:
                        break;
                }
            }
        });

        if (mTuneListener != null) {
            mTuneListener.execTune(mTuner);
        }
    }

    private Filter openVideoFilter(int pid, String videoStreamType) {
        long bufferSize = 1024 * 1024 * 4;
        Filter filter = mTuner.openFilter(Filter.TYPE_TS, Filter.SUBTYPE_VIDEO, bufferSize, mHandlerExecutor, new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {

            }

            @Override
            public void onFilterStatusChanged(Filter filter, int status) {

            }
        });
        if (filter == null) {
            TvLog.e("openVideoFilter failed");
            return null;
        }

        AvSettings.Builder builder = AvSettings.builder(Filter.TYPE_TS, false)
                .setPassthrough(true);
        if (Build.VERSION.SDK_INT >= 31 && mTunerHalVersion == Constant.TUNER_HAL_VERSION_1_1) {
            // Android S
            TunerHelper.setVideoStreamType(builder, videoStreamType);
        }
        Settings settings = builder.build();
        FilterConfiguration filterConfiguration = TsFilterConfiguration.builder()
                .setTpid(pid)
                .setSettings(settings)
                .build();
        filter.configure(filterConfiguration);
        filter.start();
        return filter;
    }

    private Filter openAudioFilter(int pid, String audioStreamType) {
        long bufferSize = 1024 * 1024 * 2;
        Filter filter = mTuner.openFilter(Filter.TYPE_TS, Filter.SUBTYPE_AUDIO, bufferSize, mHandlerExecutor, new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {

            }

            @Override
            public void onFilterStatusChanged(Filter filter, int status) {

            }
        });
        if (filter == null) {
            TvLog.e("openAudioFilter failed");
            return null;
        }

        AvSettings.Builder builder = AvSettings.builder(Filter.TYPE_TS, true)
                .setPassthrough(true);
        if (Build.VERSION.SDK_INT >= 31 && mTunerHalVersion == Constant.TUNER_HAL_VERSION_1_1) {
            // Android S
            TunerHelper.setAudioStreamType(builder, audioStreamType);
        }
        Settings settings = builder.build();
        FilterConfiguration filterConfiguration = TsFilterConfiguration.builder()
                .setTpid(pid)
                .setSettings(settings)
                .build();
        filter.configure(filterConfiguration);
        filter.start();
        return filter;
    }

    private void startVideoFilter() {
        if (mVideoFilter != null) {
            closeVideoFilter();
        }

        if (mVideoTrackInfo.pid > 0) {
            mVideoFilter = openVideoFilter(mVideoTrackInfo.pid, mVideoTrackInfo.videoStreamType);
        }
    }

    private void startAudioFilter() {
        if (mAudioFilter != null) {
            closeAudioFilter();
        }

        if (mAudioTrackInfo.pid > 0) {
            mAudioFilter = openAudioFilter(mAudioTrackInfo.pid, mAudioTrackInfo.audioStreamType);
        }
    }

    private void closeVideoFilter() {
        if (mVideoFilter != null) {
            closeFilter(mVideoFilter);
            mVideoFilter = null;
        }
    }

    private void closeAudioFilter() {
        if (mAudioFilter != null) {
            closeFilter(mAudioFilter);
            mAudioFilter = null;
        }
    }

    private void closeFilter(Filter filter) {
        if (filter != null) {
            try {
                filter.flush();
                filter.stop();
                filter.close();
            } catch (Throwable tr) {
                TvLog.e("closeFilter failed, error: " + (tr != null ? tr.getMessage() : ""));
                tr.printStackTrace();
            }
        }
    }

    public void switchAudioTrack(Bundle bundle) {
        TrackInfo.AudioTrackInfo audioTrackInfo = getAudioTrackInfoFromBundle(bundle);
        Filter newAudioFilter = openAudioFilter(audioTrackInfo.pid, audioTrackInfo.audioStreamType);
        AudioParams audioParams = new AudioParams.Builder(audioTrackInfo.mimeType, 48000, 2)
                .setPid(audioTrackInfo.pid)
                .setTrackFilterId(newAudioFilter.getId())
                .setAvSyncHwId(mTuner.getAvSyncHwId(newAudioFilter))
                .build();

        if (mASPlayer != null) {
            mASPlayer.switchAudioTrack(audioParams);
        }

        closeAudioFilter();
        mAudioFilter = newAudioFilter;
    }

    public void setADParams(Bundle bundle) {
        TrackInfo.AudioTrackInfo audioTrackInfo = getAudioTrackInfoFromBundle(bundle);
        mSubAudioFilter = openAudioFilter(audioTrackInfo.pid, audioTrackInfo.audioStreamType);
        AudioParams audioParams = new AudioParams.Builder(audioTrackInfo.mimeType, 48000, 2)
                .setPid(audioTrackInfo.pid)
                .setTrackFilterId(mSubAudioFilter.getId())
                .setAvSyncHwId(mTuner.getAvSyncHwId(mSubAudioFilter))
                .build();

        if (mASPlayer != null) {
            mASPlayer.setADParams(audioParams);
        }
    }

    public void enableADMix() {
        if (mASPlayer != null) {
            mASPlayer.enableADMix();
        }
    }

    public void disableADMix() {
        if (mASPlayer != null) {
            mASPlayer.disableADMix();
        }
    }

    private void cancelTuning() {
        if (mTuner != null) {
            mTuner.cancelTuning();
        }
        if (mTuneListener != null) {
            mTuneListener.onCancelTune(mTuner);
        }
    }

    private MediaFormat getVideoMediaFormat(String mimeType, int width, int height) {
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(mimeType, width, height);
        return mediaFormat;
    }

    private MediaFormat getAudioMediaFormat(String mimeType, int sampleRate, int channelCount) {
        MediaFormat mediaFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);
        return mediaFormat;
    }

    public void stop() {
        TvLog.i("TvPlayer stop called");
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStop();
            });
        } else {
            TvLog.d("Player-%d stop failed, handler is null", mId);
        }
    }

    private void handleStop() {
        handleStopVideoDecoding();
        handleStopAudioDecoding();

        cancelTuning();
    }

    public void pause() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handlePause();
            });
        } else {
            TvLog.d("Player-%d pause failed, handler is null", mId);
        }
    }

    private void handlePause() {
        handlePauseVideoDecoding();
        handlePauseAudioDecoding();
    }

    public void resume() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleResume();
            });
        } else {
            TvLog.d("Player-%d resume failed, handler is null", mId);
        }
    }

    private void handleResume() {
        handleResumeVideoDecoding();
        handleResumeAudioDecoding();
    }

    public void startVideoDecoding() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStartVideoDecoding();
            });
        } else {
            TvLog.d("Player-%d startVideoDecoding failed, handler is null", mId);
        }
    }

    private void handleStartVideoDecoding() {
        startVideoFilter();
        if (mASPlayer != null) {
            mASPlayer.startVideoDecoding();
        }
    }

    public void stopVideoDecoding() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStopVideoDecoding();
            });
        } else {
            TvLog.d("Player-%d stopVideoDecoding failed, handler is null", mId);
        }
    }

    private void handleStopVideoDecoding() {
        if (mASPlayer != null) {
            mASPlayer.stopVideoDecoding();
        }
        closeVideoFilter();
    }

    public void pauseVideoDecoding() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handlePauseVideoDecoding();
            });
        } else {
            TvLog.d("Player-%d pauseVideoDecoding failed, handler is null", mId);
        }
    }

    private void handlePauseVideoDecoding() {
        if (mASPlayer != null) {
            mASPlayer.pauseVideoDecoding();
        }
    }

    public void resumeVideoDecoding() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleResumeVideoDecoding();
            });
        } else {
            TvLog.d("Player-%d resumeVideoDecoding failed, handler is null", mId);
        }
    }

    private void handleResumeVideoDecoding() {
        if (mASPlayer != null) {
            mASPlayer.resumeVideoDecoding();
        }
    }

    public void startAudioDecoding() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStartAudioDecoding();
            });
        } else {
            TvLog.d("Player-%d startAudioDecoding failed, handler is null", mId);
        }
    }

    private void handleStartAudioDecoding() {
        startAudioFilter();
        if (mASPlayer != null) {
            mASPlayer.startAudioDecoding();
        }
    }

    public void stopAudioDecoding() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleStopAudioDecoding();
            });
        } else {
            TvLog.d("Player-%d stopAudioDecoding failed, handler is null", mId);
        }
    }

    private void handleStopAudioDecoding() {
        if (mASPlayer != null) {
            mASPlayer.stopAudioDecoding();
        }
        closeAudioFilter();
        if (mSubAudioFilter != null) {
            closeFilter(mSubAudioFilter);
            mSubAudioFilter = null;
        }
    }

    public void pauseAudioDecoding() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handlePauseAudioDecoding();
            });
        } else {
            TvLog.d("Player-%d pauseAudioDecoding failed, handler is null", mId);
        }
    }

    private void handlePauseAudioDecoding() {
        if (mASPlayer != null) {
            mASPlayer.pauseAudioDecoding();
        }
    }

    public void resumeAudioDecoding() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleResumeAudioDecoding();
            });
        } else {
            TvLog.d("Player-%d resumeAudioDecoding failed, handler is null", mId);
        }
    }

    private void handleResumeAudioDecoding() {
        if (mASPlayer != null) {
            mASPlayer.resumeAudioDecoding();
        }
    }

    public void setAudioVolume(int volume) {
        if (mASPlayer != null) {
            mASPlayer.setAudioVolume(volume);
        } else {
            TvLog.d("Player-%d setAudioVolume failed, asplayer is null", mId);
        }
    }

    public int getAudioVolume() {
        return mASPlayer != null ? mASPlayer.getAudioVolume() : 0;
    }

    public void muteAudio() {
        if (mASPlayer != null) {
            mASPlayer.setAudioMute(true, true);
        } else {
            TvLog.d("Player-%d muteAudio failed, asplayer is null", mId);
        }
    }

    public void unMuteAudio() {
        if (mASPlayer != null) {
            mASPlayer.setAudioMute(false, false);
        } else {
            TvLog.d("Player-%d unMuteAudio failed, asplayer is null", mId);
        }
    }

    public MediaFormat getVideoInfo() {
        if (mASPlayer != null) {
            return mASPlayer.getVideoInfo();
        }
        return null;
    }

    public void setADVolumeDb(float adVolumeDb) {
        if (mASPlayer != null) {
            mASPlayer.setADVolumeDB(adVolumeDb);
        }
    }

    public float getADVolumeDb() {
        if (mASPlayer != null) {
            return mASPlayer.getADVolumeDB();
        }
        return 0.0f;
    }

    public void setADMixLevel(int mixLevel) {
        if (mASPlayer != null) {
            mASPlayer.setADMixLevel(mixLevel);
        }
    }

    public int getADMixLevel() {
        if (mASPlayer != null) {
            return mASPlayer.getADMixLevel();
        }
        return 0;
    }

    public void setSpeed(float speed) {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleSetSpeed(speed);
            });
        } else {
            TvLog.d("Player-%d setSpeed failed, handler is null", mId);
        }
    }

    private void handleSetSpeed(float speed) {
        boolean isNormalPlaySpeed = Math.abs(speed - 1) < SPEED_DIFF_THRESHOLD;
        boolean isPauseSpeed = Math.abs(speed) < SPEED_DIFF_THRESHOLD;

        if (isPauseSpeed) {
            // TODO
            mASPlayer.pauseVideoDecoding();
            mASPlayer.pauseAudioDecoding();
        } else if (isNormalPlaySpeed) {
            mASPlayer.stopFast();
        } else if (speed > 0f && speed <= 2.0f) {
            mASPlayer.setTrickMode(VideoTrickMode.TRICK_MODE_SMOOTH);
            mASPlayer.startFast(speed);
        } else {
            mASPlayer.setTrickMode(VideoTrickMode.TRICK_MODE_BY_SEEK);
            mASPlayer.startFast(speed);
        }
    }

    public void setPIPMode(int pipMode) {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                if (mASPlayer != null) {
                    mASPlayer.setPIPMode(pipMode);
                }
            });
        }
    }

    public void resetWorkMode() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                if (mASPlayer != null) {
                    mASPlayer.resetWorkMode();
                }
            });
        }
    }

    public void setWorkMode(int workMode) {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
               if (mASPlayer != null) {
                   mASPlayer.setWorkMode(workMode);
               }
            });
        }
    }

    protected void onPlaybackEvent(TsPlaybackListener.PlaybackEvent event) {
        if (event instanceof TsPlaybackListener.VideoFormatChangeEvent) {
            TsPlaybackListener.VideoFormatChangeEvent ev = (TsPlaybackListener.VideoFormatChangeEvent) event;
            MediaFormat mediaFormat = ev.getVideoFormat();
            int videoWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
            int videoHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
            TvLog.i("TvPlayer-%d VideoFormatChangeEvent size: %dx%d", mId, videoWidth, videoHeight);
        } else if (event instanceof TsPlaybackListener.AudioFormatChangeEvent) {
            TvLog.i("TvPlayer-%d AudioFormatChangeEvent", mId);
        } else if (event instanceof TsPlaybackListener.VideoFirstFrameEvent) {
            TvLog.i("TvPlayer-%d VideoFirstFrameEvent", mId);
            TsPlaybackListener.VideoFirstFrameEvent ev = (TsPlaybackListener.VideoFirstFrameEvent)event;
            notifyFrame(ev.getPositionMs(), true);
        } else if (event instanceof TsPlaybackListener.AudioFirstFrameEvent) {
            TvLog.i("TvPlayer-%d AudioFirstFrameEvent", mId);
            TsPlaybackListener.AudioFirstFrameEvent ev = (TsPlaybackListener.AudioFirstFrameEvent) event;
            notifyFrame(ev.getPositionMs(), false);
        } else if (event instanceof TsPlaybackListener.DecodeFirstVideoFrameEvent) {
            TvLog.i("TvPlayer-%d DecodeFirstVideoFrameEvent", mId);
        } else if (event instanceof TsPlaybackListener.DecodeFirstAudioFrameEvent) {
            TvLog.i("TvPlayer-%d DecodeFirstAudioFrameEvent", mId);
        } else if (event instanceof TsPlaybackListener.PtsEvent) {
            // TsPlaybackListener.PtsEvent ev = (TsPlaybackListener.PtsEvent) event;
            // TvLog.d("TvPlayer-%d PtsEvent, stream: %s, pts: %d, rendertime: %d",
            //         mId, StreamType.toString(ev.mStreamType), ev.mPts, ev.mRenderTime);
        }

        if (mTsPlaybackListener != null) {
            mTsPlaybackListener.onPlaybackEvent(event);
        }
    }

    void notifyFrame(long positionMs, boolean video) {

    }

    public long getPositionMs() {
        if (mASPlayer != null) {
            return mASPlayer.getCurrentTime();
        }
        return 0;
    }

    public void release() {
        if (mPlayerHandler != null) {
            mPlayerHandler.post(() -> {
                handleRelease();
            });
        } else {
            TvLog.d("Player-%d release failed, handler is null", mId);
        }
    }

    private void handleRelease() {
        handleStop();

        if (mASPlayer != null) {
            mASPlayer.release();
            mASPlayer = null;
        }

        if (mPlayerThread != null) {
            mPlayerThread.quitSafely();
            mPlayerThread = null;
        }

        closeVideoFilter();
        closeAudioFilter();

        if (mTuner != null) {
            try {
                mTuner.close();
            } catch (Throwable tr) {
                TvLog.e("handleRelease failed, close tuner error: " + (tr != null ? tr.getMessage() : ""));
            }
            mTuner = null;
        }
    }
}
