package com.amlogic.asplayer.core;

import android.content.Context;
import android.os.Handler;

import com.amlogic.asplayer.api.IASPlayer;


abstract class Renderer {

    static final int RESET_REASON_NEW_POSITION = 1;
    static final int RESET_REASON_NO_MEDIA = 2;
    static final int RESET_REASON_BAD_DATA = 3;
    static final int RESET_REASON_NO_DATA = 4;
    static final int RESET_REASON_DISCONTINUITY = 5;
    static final int RESET_REASON_DECODERS_BLOCKED = 6;
    static final int RESET_REASON_RENDERER_CHANGED = 7;

    // for notification
    protected IASPlayer mPlayer;

    // output paths
    protected AudioOutputPath mAudioOutputPath;
    protected VideoOutputPath mVideoOutputPath;

    // video start or not
    protected boolean mVideoStarted;
    protected boolean mAudioStarted;

    // position
    protected long mRequestedPositionUs;
    protected boolean mRequestedPositionSet;
    protected PositionHandler mPositionHandler;

    // speed
    protected double mSpeed;

    private int mId;
    protected int mInstanceId = Constant.INVALID_INSTANCE_ID;

    Renderer(int id, RendererScheduler rendererScheduler) {
        mId = id;
        mPlayer = rendererScheduler.getASPlayer();
        mVideoOutputPath = rendererScheduler.getVideoOutputPath();
        mAudioOutputPath = rendererScheduler.getAudioOutputPath();
        mPositionHandler = rendererScheduler.getPositionHandler();
    }

    protected String getTag() {
        return String.format("[No-%d]-[%d]%s", mInstanceId, mId, getName());
    }

    protected abstract String getName();

    void prepare(Context context, Handler handler) {}

    void release() {}

    void setInstanceId(int instanceId) {
        mInstanceId = instanceId;
    }

    void startVideo() {
        mVideoStarted = true;
    }

    void stopVideo() {
        mVideoStarted = false;
    }

    boolean isVideoStarted() {
        return mVideoStarted;
    }

    void startAudio() {
        mAudioStarted = true;
    }

    void stopAudio() {
        mAudioStarted = false;
    }

    boolean iAudioStarted() {
        return mAudioStarted;
    }

    void setSpeed(Renderer previousRenderer, double speed) {
        mSpeed = speed;
    }

    void setPositionUs(long positionUs) {
        mRequestedPositionSet = true;
        mRequestedPositionUs = positionUs;
        mPositionHandler.setPositionUs(positionUs);
    }

    abstract long doSomeWork();

    abstract void reset(int reason);

    abstract protected void pumpFeederData();

    abstract void setWorkMode(int workMode);

    void setPIPMode(int pipMode) {

    }

    protected boolean updateFeederPosition() {
        if (!mRequestedPositionSet)
            return false;

        mRequestedPositionSet = false;
        reset(RESET_REASON_NEW_POSITION);
        return true;
    }

    protected void handleFeeding() {
        if (updateFeederPosition())
            return;

        pumpFeederData();
    }
}

