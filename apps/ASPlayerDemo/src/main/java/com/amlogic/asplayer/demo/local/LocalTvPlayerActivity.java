/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.local;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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
import com.amlogic.asplayer.demo.player.DvrPlayer;
import com.amlogic.asplayer.demo.utils.ToastUtils;

import java.io.File;


public class LocalTvPlayerActivity extends Activity implements TsPlaybackListener {

    private static final String TAG = LocalTvPlayerActivity.class.getSimpleName();

    private Uri mUri;
    private Bundle mProgramBundle;

    private SurfaceView mSurfaceView;
    private Surface mSurface;

    private DvrPlayer mDvrPlayer;
    private HandlerThread mPlayerThread;

    private boolean mMute = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_local_tv_player);

        initFromIntent();

        if (mUri == null) {
            finish();
            return;
        }

        createTvPlayer();
        initViews();
    }

    private void initFromIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }

        if (intent.hasExtra(Constant.EXTRA_TS_PATH)) {
            String tsPath = intent.getStringExtra(Constant.EXTRA_TS_PATH);
            mUri = Uri.fromFile(new File(tsPath));
        }
        mProgramBundle = intent.getBundleExtra(Constant.EXTRA_PROGRAM_BUNDLE);
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
        mDvrPlayer = new DvrPlayer(getApplicationContext(), mPlayerThread.getLooper());
        mDvrPlayer.prepare();
        mDvrPlayer.setLoop(true);
        mDvrPlayer.setASPlayerPlaybackListener(this::onPlaybackEvent);
    }

    private void releaseTvPlayer() {
        if (mDvrPlayer != null) {
            mDvrPlayer.stop();
            mDvrPlayer.release();
            mDvrPlayer = null;
        }
        if (mPlayerThread != null) {
            mPlayerThread.quitSafely();
            mPlayerThread = null;
        }
    }

    @Override
    public void onPlaybackEvent(final TsPlaybackListener.PlaybackEvent event) {
        Log.d(TAG, "onPlaybackEvent " + event);
    }

    private void startPlayVideo() {
        mDvrPlayer.setSurface(mSurface);
        mDvrPlayer.start(mUri, mProgramBundle);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        releaseTvPlayer();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp keyCode: " + keyCode + ", " + KeyEvent.keyCodeToString(keyCode));
        boolean handled = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
                toggleAudioMute();
                handled = true;
                break;
            default:
                break;
        }

        if (handled) {
            return handled;
        }

        return super.onKeyUp(keyCode, event);
    }

    private void toggleAudioMute() {
        if (mMute) {
            // 当前是静音状态
            mDvrPlayer.unMuteAudio();
            mMute = false;
            ToastUtils.showToast(getApplicationContext(), "取消静音");
        } else {
            // 当前是非静音状态
            mDvrPlayer.muteAudio();
            mMute = true;
            ToastUtils.showToast(getApplicationContext(), "静音");
        }
    }

    private void changeAudioVolume() {
        int volume = mDvrPlayer.getAudioVolume();
        volume = volume % 100 + 10;

        if (volume >= 100) {
            volume = 100;
        } else if (volume < 0) {
            volume = 0;
        }

        mDvrPlayer.setAudioVolume(volume);
        ToastUtils.showToast(getApplicationContext(), "设置音量：" + volume);
    }
}
