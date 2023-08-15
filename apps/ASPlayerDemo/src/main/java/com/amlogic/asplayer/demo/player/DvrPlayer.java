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
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.amlogic.asplayer.api.EventMask;
import com.amlogic.asplayer.api.InitParams;
import com.amlogic.asplayer.api.InputBuffer;
import com.amlogic.asplayer.api.InputBufferType;
import com.amlogic.asplayer.api.InputSourceType;
import com.amlogic.asplayer.api.ASPlayer;
import com.amlogic.asplayer.demo.Constant;
import com.amlogic.asplayer.demo.utils.TvLog;
import com.amlogic.asplayer.jni.wrapper.JniASPlayerWrapper;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class DvrPlayer extends TvPlayer {

    private static final String TAG = DvrPlayer.class.getSimpleName();

    private static final int TS_PACKET_NUMBER = 100;
    private static final int TS_PACKET_BUFFER_SIZE = 188 * TS_PACKET_NUMBER;

    private static final int SOURCE_STATUS_UNKNOWN_ERROR = -1;
    private static final int SOURCE_STATUS_EOF = -2;

    private DataSource mDataSource;
    private boolean mLoop = false;

    private byte[] mTsPacket;
    private InputBuffer mInputBuffer;

    private HandlerThread mHandlerThread = null;
    private Handler mHandler = null;
    private Runnable mFeedDataTask = null;

    private boolean mStarted = false;
    private int mAvailableBufferSize = 0;
    private int mRemainBufferSize = 0;

    public DvrPlayer(Context context, Looper looper) {
        super(context, looper);
        mLoop = false;
    }

    @Override
    protected void initASPlayer(Looper looper) {
        InitParams initParams = new InitParams.Builder()
                .setPlaybackMode(InitParams.PLAYBACK_MODE_PASSTHROUGH)
                .setInputSourceType(InputSourceType.TS_MEMORY)
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

    @Override
    protected int getTunerTvInputUseCase() {
        return TvInputService.PRIORITY_HINT_USE_CASE_TYPE_PLAYBACK;
    }

    @Override
    public void prepare() {
        super.prepare();

        mHandlerThread = new HandlerThread("dvr-player");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mFeedDataTask = this::handleFeedData;
        mTsPacket = new byte[TS_PACKET_BUFFER_SIZE];
        mInputBuffer = new InputBuffer(mTsPacket, 0, 0);
    }

    public void setLoop(final boolean loop) {
        if (mHandler != null) {
            mHandler.post(() -> {
                mLoop = loop;
            });
        } else {
            mLoop = loop;
        }
    }

    @Override
    public void start(Uri uri, Bundle bundle) {
        TvLog.d("start uri: %s", uri);
        super.start(uri, bundle);

        synchronized (this) {
            mStarted = true;
        }

        mHandler.post(() -> {
            handleStart(uri, bundle);
        });
    }

    private void handleStart(Uri uri, Bundle bundle) {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String filePath = uri.getPath();
            DataSource dataSource = new DataSource(filePath);
            mDataSource = dataSource;
        }

        mHandler.post(mFeedDataTask);
    }

    @Override
    protected void prepareTunerForStart(Uri uri) {
        // Nothing todo
    }

    @Override
    public void stop() {
        super.stop();

        synchronized (this) {
            mStarted = false;
        }

        mHandler.post(() -> {
            handleStop();
        });
    }

    private void handleStop() {
        mHandler.removeCallbacks(mFeedDataTask);

        if (mDataSource != null) {
            mDataSource.close();
            mDataSource = null;
        }
    }

    @Override
    public void pause() {
        super.pause();

        synchronized (this) {
            mStarted = false;
        }

        mHandler.post(() -> {
            handlePause();
        });
    }

    private void handlePause() {
        mHandler.removeCallbacks(mFeedDataTask);
    }

    @Override
    public void resume() {
        super.resume();

        synchronized (this) {
            mStarted = true;
        }

        mHandler.post(() -> {
            handleResume();
        });
    }

    private void handleResume() {
        mHandler.removeCallbacks(mFeedDataTask);
        mHandler.post(mFeedDataTask);
    }

    @Override
    public void release() {
        super.release();

        synchronized (this) {
            mStarted = false;
        }

        mHandler.post(() -> {
            handleRelease();
        });
    }

    private void handleRelease() {
        handleStop();

        mHandlerThread.quitSafely();
        mHandlerThread = null;
        mHandler = null;
        mHandlerExecutor = null;
    }

    private void handleFeedData() {
        if (!mDataSource.isOpened()) {
            mDataSource.open();

            mASPlayer.flushDvr();
        }

        synchronized (this) {
            if (!mStarted) {
                return;
            }
        }

//        Log.d(TAG, "handleFeedData available size: " + mAvailableBufferSize);
        if (mAvailableBufferSize < mTsPacket.length) {
            int read = mDataSource.read(mTsPacket, mRemainBufferSize, mTsPacket.length - mRemainBufferSize);
            if (read < 0) {
                // read error
                Log.d(TAG, "read data error, read: " + read);
                if (SOURCE_STATUS_EOF == read && mLoop) {
                    mDataSource.setPositionInBytes(0);
                    repostFeedDataTask(10);
                } else {
                    repostFeedDataTask(1000);
                }
                return;
            } else if (read == 0) {
                // eof??
                Log.w(TAG, "read 0 bytes, EOF??");
                repostFeedDataTask(1000);
                return;
            } else {
                mAvailableBufferSize = mRemainBufferSize + read;
//                Log.d(TAG, "read success, read: " + read + " bytes");
            }
        }

        if (mAvailableBufferSize <= 0) {
            Log.d(TAG, "available data is empty");
            repostFeedDataTask(50);
            return;
        }

        synchronized (this) {
            if (!mStarted) {
                return;
            }
        }

        int delayMs = 0;

        mInputBuffer.mBuffer = mTsPacket;
        mInputBuffer.mOffset = 0;
        mInputBuffer.mBufferSize = mAvailableBufferSize;

        int write = mASPlayer.writeData(mInputBuffer, 0);
        if (write < 0) {
            Log.i(TAG, "write ts error, ret: " + write);
            if (write == ASPlayer.INFO_INVALID_OPERATION) {
                // write error
                delayMs = 1000;
            } else if (write == ASPlayer.INFO_ERROR_RETRY) {
                delayMs = 50;
            } else if (write == ASPlayer.INFO_BUSY) {
                delayMs = 100;
            } else {
                // not reachable
                delayMs = 10;
            }
        } else if (write == 0) {
            // retry??
            delayMs = 10;
        } else { // write > 0
            mRemainBufferSize = mAvailableBufferSize - write;
            if (write != mAvailableBufferSize) {
                // write part
                System.arraycopy(mTsPacket, write, mTsPacket, 0, mRemainBufferSize);
//                Log.d(TAG, "write ts error, length not match data size: " + mAvailableBufferSize + ", write size: " + write);
            }
//            Log.d(TAG, "write data size: " + mAvailableBufferSize + ", write size: " + write);
            mAvailableBufferSize = mRemainBufferSize;
            delayMs = 1;
        }
        repostFeedDataTask(delayMs);
    }

    private void repostFeedDataTask(int delay) {
        if (!mStarted) {
            return;
        }

        if (mHandler != null) {
            mHandler.postDelayed(mFeedDataTask, delay);
        }
    }

    private static class DataSource {

        private String mFilePath;
        private RandomAccessFile mFile;

        private DataSource(String filePath) {
            mFilePath = filePath;
        }

        private boolean open() {
            if (mFile != null) {
                return true;
            }

            if (TextUtils.isEmpty(mFilePath)) {
                return false;
            }

            File file = new File(mFilePath);
            if (!file.exists()) {
                return false;
            }

            try {
                mFile = new RandomAccessFile(mFilePath, "r");
                return true;
            } catch (Throwable tr) {
                tr.printStackTrace();
                return false;
            }
        }

        private void close() {
            if (mFile != null) {
                try {
                    mFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mFile = null;
            }
        }

        private boolean isOpened() {
            return mFile != null;
        }

        private long getPositionInBytes() {
            try {
                if (mFile != null) {
                    return mFile.getFilePointer();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return -1;
        }

        private long setPositionInBytes(long position) {
            try {
                if (mFile != null) {
                    mFile.seek(position);
                }
            } catch (IOException e) {
                e.printStackTrace();
                position = -1;
            }
            return position;
        }

        private int read(byte[] buffer, int offset, int length) {
            if (mFile == null) {
                return SOURCE_STATUS_UNKNOWN_ERROR;
            }

            try {
                int readBytes = mFile.read(buffer, offset, length);
                if (readBytes == -1) {
                    // no more data, EOF
                    return SOURCE_STATUS_EOF;
                }
                return readBytes;
            } catch (EOFException e) {
                return SOURCE_STATUS_EOF;
            } catch (IOException e) {
                e.printStackTrace();
                return SOURCE_STATUS_UNKNOWN_ERROR;
            }
        }

        private long getTotalSize() {
            long length = 0;
            File file = new File(mFilePath);
            if (file.exists()) {
                return file.length();
            }
            return length;
        }
    }
}
