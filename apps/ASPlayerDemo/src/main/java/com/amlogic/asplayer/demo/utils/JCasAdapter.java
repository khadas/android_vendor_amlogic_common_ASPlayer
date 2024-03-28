/*
 * Copyright (c) 2024 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

package com.amlogic.asplayer.demo.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import android.media.tv.tuner.Tuner;
import android.media.tv.TvInputService;

import android.os.Handler;
import android.os.HandlerThread;

import com.droidlogic.jcas.CasManager;
import com.droidlogic.jcas.CasExtractor;
import com.droidlogic.jcas.CasConnection;
import com.droidlogic.jcas.CasSession;
import com.droidlogic.jcas.CasException;
import com.droidlogic.jcas.tuner.TunerTsExtractor;
import com.droidlogic.jcas.CasInfo;

public class JCasAdapter {

    private static final String TAG = JCasAdapter.class.getSimpleName();

    private Context mContext;

    private CasManager mCasManager;

    private String mTvInputSessionId;

    private int mTvInputUseCase;

    private CasConnection mCasConnection;

    private CasExtractor mCasExtractor;

    private TunerTsExtractor mExtractor;

    private CasSession mCasSession;

    private int mCaSystemId;

    private Tuner mTuner;

    public JCasAdapter(Context context, int casSystemId, String tvInputServiceSessionId,
            int tvInputServiceUseCase) {
        mContext = context;
        mCaSystemId = casSystemId;
        mTvInputSessionId = tvInputServiceSessionId;
        mTvInputUseCase = tvInputServiceUseCase;
    }

    public JCasAdapter(Context context, int casSystemId, String tvInputServiceSessionId,
            int tvInputServiceUseCase, Tuner tuner) {
            this(context, casSystemId, tvInputServiceSessionId, tvInputServiceUseCase);
        AM_SetTuner(tuner);
    }

    public void AM_SetTuner(Tuner tuner) {
        mTuner = tuner;
        Log.d(TAG, "Set tuner:" + mTuner);
    }

    public void AM_CasManagerInit() {
        if (mCasManager == null) {
            Log.d(TAG, "Prepare the CasManager.");
            mCasManager = new CasManager();
            mCasManager.prepare();
        } else {
            Log.w(TAG, "The CasManager has been created!");
        }
    }

    public void AM_CasManagerTerm() {
        if (mCasManager != null) {
            Log.d(TAG, "Release the CasManager.");
            mCasManager.release();
        } else {
            Log.w(TAG, "The CasManager has not been initialized!");
        }
    }

    public int[] AM_GetDefaultCaSystemIds() {
        return mCasManager == null ? null : mCasManager.getDefaultCaSystemIds();
    }

    public boolean AM_IsSystemIdSupported(int caSystemId) {
        return mCasManager == null ? false : mCasManager.canSupport(caSystemId);
    }

    public String AM_CreateCasPlugin(CasConnection.CasEventListener listener, Handler handler, int instanceId) {
        String connectionId = createConnection();
        if (mCasConnection == null) {
            Log.w(TAG, "CasConnection is null!");
            return null;
        }

        addListener(connectionId, listener, handler);

        prepareExtractor(instanceId, CasConnection.SESSION_USAGE_PLAYBACK_LIVE);

        try {
            mCasConnection.configure(mCaSystemId);
        } catch (CasException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Create CasPlugin pluginId:" + connectionId);
        return connectionId;
    }

    public byte[] AM_OpenCasSession(String pluginId, CasSessionInfo info) {
        byte[] sessionId = null;
        mCasExtractor.clear();

        CasInfo mCasInfo = new CasInfo();
        mCasInfo.caSystemId = mCaSystemId;
        mCasInfo.ecmPid = info.getEcmPid();
        mCasInfo.isProgramLevel = info.getIsProgramLevel();
        mCasInfo.scramblingMode = info.getScramblingMode();
        mCasInfo.privateData = info.getPrivateData();
        int[] scrambledEsPids = info.getScrambledEsPids();
        for (int i = 0; i < scrambledEsPids.length; i++) {
            mCasInfo.scrambledEsPids.add(scrambledEsPids[i]);
        }

        mCasExtractor.AM_addCasInfo(mCasInfo);
        mCasExtractor.createDescrambler();
        mCasExtractor.setScrambleStatus(CasExtractor.SUPPORTED_SCRAMBLED);
        mCasExtractor.processCas();
        mCasExtractor.AM_createCasSession();
        CasSession session = mCasExtractor.AM_getCasSession(mCasInfo.ecmPid);
        Log.d(TAG, "Create CasSession:" + session);

        try {
            sessionId = session.getSessionId();
        } catch (CasException e) {
            e.printStackTrace();
        }
        return sessionId == null ? null : sessionId;
    }

    public void AM_StartDescrambling(String pluginId, byte[] sessionId, int[] scrambledEsPids) {
        for (int i = 0; i < scrambledEsPids.length; i++) {
            if (scrambledEsPids[i] > 0 && scrambledEsPids[i] != 0x1FFF)
                mCasExtractor.addDescramblerPid(scrambledEsPids[i]);
        }
    }

    public void AM_StopDescrambling(String pluginId, byte[] sessionId, int[] scrambledEsPids) {
        for (int i = 0; i < scrambledEsPids.length; i++) {
            if (scrambledEsPids[i] > 0 && scrambledEsPids[i] != 0x1FFF)
                mCasExtractor.removeDescramblerPid(scrambledEsPids[i]);
        }
    }

    public void AM_CloseCasSession(String pluginId, byte[] sessionId) {
        Log.d(TAG, "Close CasSession");
        //mCasExtractor.AM_removeCasInfo(mCasInfo);
        mCasExtractor.setScrambleStatus(CasExtractor.UNDEFINED);
    }

    public void AM_DestroyCasPlugin(String pluginId, CasConnection.CasEventListener listener) {
        Log.d(TAG, "Destroy CasPlugin pluginId:" + pluginId);
        mCasExtractor.clear();
        mExtractor.release();
        removeListener(pluginId, listener);
        mCasManager.deleteConnection(mCasConnection);
        mCasExtractor = null;
        mCasConnection = null;
        mExtractor = null;
    }

    private String createConnection() {
        if (mCasManager != null) {
            mCasConnection = mCasManager.createConnection(mTvInputSessionId, mTvInputUseCase, null);
            Log.d(TAG, "Create Connection:" + mCasConnection);
            if (mCasConnection != null) {
                Uri uri = Uri.parse("content://android.media.tv/channel/test");
                mCasConnection.prepare(uri);
                return mCasConnection.getConnectionId();
            }
        }
        return null;
    }

    private void prepareExtractor(int id, int usage) {
        if (mTuner != null)
            mExtractor = new TunerTsExtractor(mTuner);
        mCasExtractor = new CasExtractor(mCasConnection, mExtractor);
        if (mExtractor != null) {
            mExtractor.AM_prepare(id);
            mExtractor.reset();
        }
        if (mCasExtractor != null) {
            mCasExtractor.reset();
            mCasExtractor.prepare(usage);
        }
    }

    private void addListener(String pluginId, CasConnection.CasEventListener listener, Handler handler) {
        mCasConnection.addListener(listener, handler);
    }

    private void removeListener(String pluginId, CasConnection.CasEventListener listener) {
        mCasConnection.removeListener(listener);
    }
}
