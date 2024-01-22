/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.jni.wrapper;

import android.media.MediaFormat;
import android.media.tv.tuner.Tuner;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;

import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.ErrorCode;
import com.amlogic.asplayer.api.InitParams;
import com.amlogic.asplayer.api.InputBuffer;
import com.amlogic.asplayer.api.InputFrameBuffer;
import com.amlogic.asplayer.api.IASPlayer;
import com.amlogic.asplayer.api.Pts;
import com.amlogic.asplayer.api.TsPlaybackListener;
import com.amlogic.asplayer.api.ASPlayer;
import com.amlogic.asplayer.api.Version;
import com.amlogic.asplayer.api.VideoParams;

import java.util.ArrayList;
import java.util.List;

public class JniASPlayerWrapper implements IASPlayer {

    private static final String TAG = "JniASPlayerWrapper";

    private long mNativeContext;

    private ASPlayer mRealASPlayer = null;

    private List<JniTsPlaybackListenerWrapper> mPlaybackListeners = new ArrayList<>();
    private Object mListenersLock = new Object();

    static {
        System.loadLibrary("jniasplayer-jni");

        // JniASPlayerWrapper built in app
        // JniASPlayer built in app
        loadAppSo();
    }

    static void loadAppSo() {
        try {
            // load libjniasplayer-wrapper.so inside Demo app
            System.loadLibrary("jniasplayer-wrapper");

            native_init();
            Log.d(TAG, "loadAppSo() Loaded app jnitaplayer-wrapper success");
        } catch (UnsatisfiedLinkError error) {
            Log.e(TAG, "loadAppSo() failed to load jniasplayer-wrapper native lib, error: "
                    + (error != null ? error.getMessage() : ""), error);
        }
    }

    public JniASPlayerWrapper(InitParams initParams, Tuner tuner) {
        native_create(initParams, tuner);
        Object asPlayer = native_getJavaASPlayer();
        Log.d(TAG, "JniASPlayerWrapper getJavaASPlayer: " + asPlayer);
        if (asPlayer != null && asPlayer instanceof ASPlayer) {
            mRealASPlayer = (ASPlayer) asPlayer;
            Log.d(TAG, "JniASPlayerWrapper getJavaASPlayer success");
        } else {
            Log.d(TAG, "JniASPlayerWrapper getJavaASPlayer failed");
        }
    }

    @Override
    public void addPlaybackListener(TsPlaybackListener listener) {
        synchronized (mListenersLock) {
            for (JniTsPlaybackListenerWrapper wrapper : mPlaybackListeners) {
                if (wrapper.mRealPlaybackListener == listener) {
                    // listener exists
                    return;
                }
            }

            JniTsPlaybackListenerWrapper listenerWrapper = new JniTsPlaybackListenerWrapper(listener);
            native_addPlaybackListener(listenerWrapper);
            mPlaybackListeners.add(listenerWrapper);
        }
    }

    @Override
    public synchronized void removePlaybackListener(TsPlaybackListener listener) {
        synchronized (mListenersLock) {
            JniTsPlaybackListenerWrapper listenerWrapper = null;
            for (JniTsPlaybackListenerWrapper wrapper : mPlaybackListeners) {
                if (wrapper.mRealPlaybackListener == listener) {
                    listenerWrapper = wrapper;
                    break;
                }
            }

            if (listenerWrapper != null) {
                native_removePlaybackListener(listenerWrapper);
                mPlaybackListeners.remove(listenerWrapper);
            }
        }
    }

    @Override
    public int prepare() {
        return native_prepare();
    }

    @Override
    public Version getVersion() {
        return null;
    }

    @Override
    public int getInstanceNo() {
        return native_getInstanceNo();
    }

    @Override
    public int getSyncInstanceNo() {
        return native_getSyncInstanceNo();
    }

    @Override
    public void release() {
        for (JniTsPlaybackListenerWrapper listenerWrapper : mPlaybackListeners) {
            native_removePlaybackListener(listenerWrapper);
        }
        mPlaybackListeners.clear();
        native_release();
        mRealASPlayer = null;
    }

    @Override
    public int writeFrameData(InputFrameBuffer inputFrameBuffer, long timeoutMillSecond) {
        return 0;
    }

    @Override
    public int writeData(InputBuffer inputBuffer, long timeoutMillSecond) {
        return native_writeData(inputBuffer, timeoutMillSecond);
    }

    @Override
    public int flush() {
        return native_flush();
    }

    @Override
    public int flushDvr() {
        return native_flushDvr();
    }

    @Override
    public int setWorkMode(int mode) {
        return native_setWorkMode(mode);
    }

    @Override
    public int resetWorkMode() {
        return native_resetWorkMode();
    }

    @Override
    public int setPIPMode(int mode) {
        return native_setPIPMode(mode);
    }

    @Override
    public void setSyncMode(int mode) {

    }

    @Override
    public int getSyncMode() {
        return 0;
    }

    @Override
    public int setPcrPid(int pid) {
        return 0;
    }

    @Override
    public int startFast(float scale) {
        return native_startFast(scale);
    }

    @Override
    public int stopFast() {
        return native_stopFast();
    }

    @Override
    public int setTrickMode(int trickMode) {
        return native_setTrickMode(trickMode);
    }

    @Override
    public int setSurface(Surface surface) {
        return native_setSurface(surface);
    }

    @Override
    public void setVideoParams(VideoParams params) throws NullPointerException, IllegalArgumentException, IllegalStateException {
        int ret = native_setVideoParams(params);
        if (ret == ErrorCode.ERROR_INVALID_OBJECT) {
            throw new IllegalStateException("setVideoParams failed");
        } else if (ret == ErrorCode.ERROR_INVALID_PARAMS) {
            throw new IllegalArgumentException("invalid params");
        } else if (ret == ErrorCode.ERROR_INVALID_OPERATION) {
            throw new IllegalStateException("setVideoParams failed,  invalid operation");
        }
    }

    @Override
    public int setTransitionModeBefore(int transitionModeBefore) {
        return native_setTransitionModeBefore(transitionModeBefore);
    }

    @Override
    public int setTransitionModeAfter(int transitionModeAfter) {
        return native_setTransitionModeAfter(transitionModeAfter);
    }

    @Override
    public int setTransitionPreRollRate(float rate) {
        return native_setTransitionPrerollRate(rate);
    }

    @Override
    public int setTransitionPreRollAVTolerance(int milliSecond) {
        return native_setTransitionPrerollAvTolerance(milliSecond);
    }

    @Override
    public int setVideoMute(int mute) {
        return native_setVideoMute(mute);
    }

    @Override
    public int setScreenColor(int screenColorMode, int screenColor) {
        return native_setScreenColor(screenColorMode, screenColor);
    }

    @Override
    public MediaFormat getVideoInfo() {
        return native_getVideoInfo();
    }

    @Override
    public int startVideoDecoding() {
        return native_startVideoDecoding();
    }

    @Override
    public int pauseVideoDecoding() {
        return native_pauseVideoDecoding();
    }

    @Override
    public int resumeVideoDecoding() {
        return native_resumeVideoDecoding();
    }

    @Override
    public int stopVideoDecoding() {
        return native_stopVideoDecoding();
    }

    @Override
    public int setAudioVolume(int volume) {
        return native_setAudioVolume(volume);
    }

    @Override
    public int getAudioVolume() {
        return native_getAudioVolume();
    }

    @Override
    public int setAudioDualMonoMode(int dualMonoMode) {
        return native_setAudioDualMonoMode(dualMonoMode);
    }

    @Override
    public int getAudioDualMonoMode() {
        return native_getAudioDualMonoMode();
    }

    @Override
    public int setAudioMute(boolean analogMute, boolean digitalMute) {
        return native_setAudioMute(analogMute, digitalMute);
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
    public void setAudioParams(AudioParams params)  throws NullPointerException, IllegalArgumentException, IllegalStateException {
        int ret =  native_setAudioParams(params);
        if (ret == ErrorCode.ERROR_INVALID_PARAMS) {
            throw new IllegalArgumentException("invalid argument");
        } else if (ret == ErrorCode.ERROR_INVALID_OBJECT) {
            throw new IllegalStateException("setAudioParams failed");
        } else if (ret == ErrorCode.ERROR_INVALID_OPERATION) {
            throw new IllegalStateException("setAudioParams failed, invalid operation");
        }
    }

    @Override
    public int switchAudioTrack(AudioParams params) {
        return native_switchAudioTrack(params);
    }

    @Override
    public MediaFormat getAudioInfo() {
        return null;
    }

    @Override
    public int startAudioDecoding() {
        return native_startAudioDecoding();
    }

    @Override
    public int pauseAudioDecoding() {
        return native_pauseAudioDecoding();
    }

    @Override
    public int resumeAudioDecoding() {
        return native_resumeAudioDecoding();
    }

    @Override
    public int stopAudioDecoding() {
        return native_stopAudioDecoding();
    }

    @Override
    public int setADParams(AudioParams params) {
        return native_setADParams(params);
    }

    @Override
    public int setADVolumeDB(float adVolumeDb) {
        return native_setADVolumeDB(adVolumeDb);
    }

    @Override
    public float getADVolumeDB() {
        return native_getADVolumeDB();
    }

    @Override
    public int enableADMix() {
        return native_enableADMix();
    }

    @Override
    public int disableADMix() {
        return native_disableADMix();
    }

    @Override
    public int setADMixLevel(int mixLevel) {
        return native_setADMixLevel(mixLevel);
    }

    @Override
    public int getADMixLevel() {
        return native_getADMixLevel();
    }

    @Override
    public int setParameters(Bundle parameters) {
        String[] keys = new String[parameters.size()];
        Object[] values = new Object[parameters.size()];

        int i = 0;
        for (final String key : parameters.keySet()) {
            keys[i] = key;
            values[i] = parameters.get(key);
            i++;
        }

        return native_setParameters(keys, values);
    }

    @Override
    public Bundle getParameters(String[] keys) {
        return native_getParameters(keys);
    }

    private Bundle getParameters(String key) {
        return getParameters(new String[] { key });
    }

    @Override
    public Object getParameter(String key) {
        Bundle parameters = getParameters(key);
        return parameters != null ? parameters.get(key) : null;
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
        return parameters != null ? parameters.getString(key) : null;
    }

    public Pts getFirstPts(int streamType) {
        return null;
    }

    private static native final void native_init();
    private native void native_create(InitParams params, Tuner tuner);
    private native Object native_getJavaASPlayer();
    private native void native_addPlaybackListener(TsPlaybackListener listener);
    private native void native_removePlaybackListener(TsPlaybackListener listener);
    private native int native_prepare();
    private native int native_getInstanceNo();
    private native int native_getSyncInstanceNo();
    private native int native_startVideoDecoding();
    private native int native_stopVideoDecoding();
    private native int native_pauseVideoDecoding();
    private native int native_resumeVideoDecoding();
    private native int native_startAudioDecoding();
    private native int native_stopAudioDecoding();
    private native int native_pauseAudioDecoding();
    private native int native_resumeAudioDecoding();
    private native int native_setVideoParams(VideoParams params);
    private native int native_setAudioParams(AudioParams params);
    private native int native_switchAudioTrack(AudioParams params);
    private native int native_flush();
    private native int native_flushDvr();
    private native int native_writeData(InputBuffer buffer, long timeoutMillSecond);
    private native int native_setSurface(Surface surface);
    private native int native_setAudioMute(boolean analogMute, boolean digitMute);
    private native int native_setAudioVolume(int volume);
    private native int native_getAudioVolume();
    private native int native_startFast(float speed);
    private native int native_stopFast();
    private native int native_setTrickMode(int trickMode);
    private native int native_setTransitionModeBefore(int mode);
    private native int native_setTransitionModeAfter(int mode);
    private native int native_setTransitionPrerollRate(float rate);
    private native int native_setTransitionPrerollAvTolerance(int milliSecond);
    private native int native_setVideoMute(int mute);
    private native int native_setScreenColor(int mode, int color);
    private native int native_setWorkMode(int mode);
    private native int native_resetWorkMode();
    private native int native_setPIPMode(int mode);
    private native int native_setADParams(AudioParams params);
    private native int native_enableADMix();
    private native int native_disableADMix();
    private native int native_setADVolumeDB(float volumeDb);
    private native float native_getADVolumeDB();
    private native int native_setADMixLevel(int mixLevel);
    private native int native_getADMixLevel();
    private native int native_setAudioDualMonoMode(int mode);
    private native int native_getAudioDualMonoMode();
    private native int native_setParameters(String[] keys, Object[] values);
    private native Bundle native_getParameters(String[] keys);
    private native MediaFormat native_getVideoInfo();
    private native void native_release();
}
