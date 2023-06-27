/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.pip;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.frontend.DvbtFrontendSettings;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.amlogic.asplayer.api.PIPMode;
import com.amlogic.asplayer.demo.Constant;
import com.amlogic.asplayer.demo.R;
import com.amlogic.asplayer.demo.player.SimpleTvPlayer;
import com.amlogic.asplayer.demo.player.TvPlayer;

public class LivePIPPlayerActivity extends Activity {

    private static final String TAG = LivePIPPlayerActivity.class.getSimpleName();

    private Bundle mProgramBundle;
    private Bundle mPipProgramBundle;
    private Bundle mFrontendBundle;

    private SurfaceView mSurfaceView;
    private Surface mSurface;
    private SurfaceView mPipSurfaceView;
    private Surface mPipSurface;

    private TvPlayer mMainTvPlayer;
    private TvPlayer mPipTvPlayer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_live_pip_tv_player);

        initFromIntent();

        initViews();

        createTvPlayers();
    }

    private void initFromIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }

        mProgramBundle = intent.getBundleExtra(Constant.EXTRA_PROGRAM_BUNDLE);
        mFrontendBundle = intent.getBundleExtra(Constant.EXTRA_FRONTEND_BUNDLE);
        mPipProgramBundle = intent.getBundleExtra(Constant.EXTRA_PIP_PROGRAM_BUNDLE);
    }

    private void createTvPlayers() {
        mMainTvPlayer = SimpleTvPlayer.create(getApplicationContext());
        mMainTvPlayer.prepare();
        mMainTvPlayer.setTuneListener(new LiveTvTuneListener());

        mPipTvPlayer = SimpleTvPlayer.create(getApplicationContext());
        mPipTvPlayer.prepare();
//        mPipTvPlayer.setTuneListener(new LiveTvTuneListener());
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

        mPipSurfaceView = findViewById(R.id.pip_surface_view);
        mPipSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                mPipSurface = holder.getSurface();
                startPlayPipVideo();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });
    }

    private void startPlayVideo() {
        mMainTvPlayer.setSurface(mSurface);
        mMainTvPlayer.start(null, mProgramBundle);
    }

    private void startPlayPipVideo() {
        Log.d(TAG, "startPlayPipVideo");
        mPipTvPlayer.setSurface(mPipSurface);
        mPipTvPlayer.setPIPMode(PIPMode.PIP);
        mPipTvPlayer.start(null, mPipProgramBundle);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mPipTvPlayer != null) {
            mPipTvPlayer.release();
            mPipTvPlayer = null;
        }

        if (mMainTvPlayer != null) {
            mMainTvPlayer.release();
            mMainTvPlayer = null;
        }
    }
}
