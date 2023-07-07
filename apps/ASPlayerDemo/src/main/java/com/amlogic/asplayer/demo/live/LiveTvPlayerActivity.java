/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.live;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.frontend.DvbtFrontendSettings;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.amlogic.asplayer.api.TsPlaybackListener;
import com.amlogic.asplayer.demo.Constant;
import com.amlogic.asplayer.demo.R;
import com.amlogic.asplayer.demo.player.TvPlayer;
import com.amlogic.asplayer.demo.utils.ToastUtils;


public class LiveTvPlayerActivity extends Activity implements TsPlaybackListener {

    private static final String TAG = LiveTvPlayerActivity.class.getSimpleName();

    private Bundle mProgramBundle;
    private Bundle mFrontendBundle;

    private SurfaceView mSurfaceView;
    private Surface mSurface;

    private TvPlayer mTvPlayer;
    private HandlerThread mPlayerThread;

    private boolean mMute = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_live_tv_player);

        initFromIntent();

        createTvPlayer();
        initViews();
    }

    private void initFromIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }

        mProgramBundle = intent.getBundleExtra(Constant.EXTRA_PROGRAM_BUNDLE);
        mFrontendBundle = intent.getBundleExtra(Constant.EXTRA_FRONTEND_BUNDLE);
    }

    private void initViews() {
        mSurfaceView = findViewById(R.id.surface_view);
        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                mSurface = holder.getSurface();
                startPlayVideo();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                mSurface = null;
            }
        });
        mSurfaceView.getHolder().setKeepScreenOn(true);
    }

    private void createTvPlayer() {
        mPlayerThread = new HandlerThread("player-thread");
        mPlayerThread.start();
        mTvPlayer = new TvPlayer(getApplicationContext(), mPlayerThread.getLooper());
        mTvPlayer.prepare();
        mTvPlayer.setASPlayerPlaybackListener(this::onPlaybackEvent);

        mTvPlayer.setTuneListener(new LiveTvTuneListener());
    }

    private void releaseTvPlayer() {
        if (mTvPlayer != null) {
            mTvPlayer.setSurface(null);
            mTvPlayer.setTuneListener(null);
            mTvPlayer.stop();
            mTvPlayer.release();
            mTvPlayer = null;
        }
        if (mPlayerThread != null) {
            mPlayerThread.quitSafely();
            mPlayerThread = null;
        }
    }

    @Override
    public void onPlaybackEvent(final PlaybackEvent event) {
        Log.d(TAG, "onPlaybackEvent " + event);

    }

    private void startPlayVideo() {
        mTvPlayer.setSurface(mSurface);
        mTvPlayer.start(null, mProgramBundle);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        releaseTvPlayer();
    }

    private void changeAudioVolume() {
        int volume = mTvPlayer.getAudioVolume();
        volume = volume % 100 + 10;

        if (volume >= 100) {
            volume = 100;
        } else if (volume < 0) {
            volume = 0;
        }

        mTvPlayer.setAudioVolume(volume);
        ToastUtils.showToast(getApplicationContext(), "set volume: " + volume);
    }

    private class LiveTvTuneListener implements TvPlayer.TuneListener {

        @Override
        public void execTune(Tuner tuner) {
            int frequency = mFrontendBundle.getInt(Constant.EXTRA_FRONTEND_FREQUENCY);

            DvbtFrontendSettings settings = DvbtFrontendSettings.builder()
                    .setFrequency(frequency)
                    .setBandwidth(DvbtFrontendSettings.BANDWIDTH_8MHZ)
                    .setStandard(DvbtFrontendSettings.STANDARD_T)
                    .setTransmissionMode(DvbtFrontendSettings.TRANSMISSION_MODE_AUTO)
                    .build();
            tuner.tune(settings);
        }

        @Override
        public void onCancelTune(Tuner tuner) {

        }
    }
}
