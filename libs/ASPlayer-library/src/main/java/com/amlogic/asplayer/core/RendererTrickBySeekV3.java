package com.amlogic.asplayer.core;

import android.os.SystemClock;


import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_REQUEST_FRAME;
import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_BY_SEEK;
import static com.amlogic.asplayer.core.VideoOutputPathV3.PARAMS_TRICK_NONE;


class RendererTrickBySeekV3 extends Renderer {
    private static final String TAG = "RendererTrickBySeekV3";

    private static final int BOUNDS_MARGIN_US = 2000000;
    private static final int DELAY_FOR_FLUSH_MS = 50;
    private static final int TIMEOUT_REQUEST_FRAME_MS = 2000;

    private long mOriginPositionUs;
    private long mOriginPositionWhenMs;

    private long mRequestedPositionWhenMs;

    private long mPendingNextPositionTimeMs;
    private long mRequestedFrameTimeMs;

    private final int mId;

    RendererTrickBySeekV3(int id, RendererScheduler rendererScheduler) {
        super(rendererScheduler);
        mId = id;
    }

    void setPositionUs(long positionUs) {
        super.setPositionUs(positionUs);

        // set current origin
        mOriginPositionUs = positionUs;
        mOriginPositionWhenMs = SystemClock.elapsedRealtime();

        // set next position
        mRequestedPositionWhenMs = mOriginPositionWhenMs;

        mVideoOutputPath.flush();
    }

    @Override
    void setSpeed(Renderer previousRenderer, double speed) {
        ASPlayerLog.i("RendererTrickBySeekV3-%d setSpeed: %f", mId, speed);
        super.setSpeed(previousRenderer, speed);

        // set current origin
        mOriginPositionUs = mPositionHandler.getPositionUs();
        mOriginPositionWhenMs = SystemClock.elapsedRealtime();

        // set next position
        mRequestedPositionWhenMs = mOriginPositionWhenMs;
        mRequestedPositionUs = mOriginPositionUs;
        mRequestedPositionUs = Math.min(mPositionHandler.getEndPositionUs() - 1000000, mRequestedPositionUs);
        mRequestedPositionUs = Math.max(mPositionHandler.getStartPositionUs(), mRequestedPositionUs);
        mRequestedPositionSet = true;

        //
        mPendingNextPositionTimeMs = 0;

        // stop audio
        if (mAudioOutputPath != null) {
            // release or flush ?
            mAudioOutputPath.reset();
        }

        // configure video for trick mode
        mVideoOutputPath.setTrickModeSpeed(speed);
        mVideoOutputPath.setParameters(PARAMS_TRICK_BY_SEEK);
    }

    private void freezeToPosition(long position) {
        ASPlayerLog.i("RendererTrickBySeekV3-%d freezeToPosition:%d", mId, position);

        // set current origin
        mOriginPositionUs = position;
        mOriginPositionWhenMs = SystemClock.elapsedRealtime();

        // set next position
        mRequestedPositionWhenMs = mOriginPositionWhenMs;
        mRequestedPositionUs = position;
        mRequestedPositionSet = true;
    }

    @Override
    protected void reset(int reason) {
        switch (reason) {
            case RESET_REASON_NEW_POSITION:
                mVideoOutputPath.flush();
                mRequestedFrameTimeMs = SystemClock.elapsedRealtime();
                break;
            case RESET_REASON_RENDERER_CHANGED:
                mVideoOutputPath.setParameters(PARAMS_TRICK_NONE);
                break;
        }
    }

    @Override
    protected void pumpFeederData() {
        mVideoOutputPath.pushInputBuffer();
    }

    @Override
    long doSomeWork() {
        long delayUs = 10000;

        if (hasNextPositionPending()) {
            if ((SystemClock.elapsedRealtime() - mPendingNextPositionTimeMs) < 0) {
                return 10000;
            } else {
                applyPendingNextPosition();
            }
        }

        if (isRequestFrameNeeded()) {
            requestFrameAdvance();
        }

        // inject
        handleFeeding();
        mVideoOutputPath.pushInputBuffer();

        // check decoders
        mVideoOutputPath.checkErrors();

        long elapsedSinceSetPositionMs =
                SystemClock.elapsedRealtime() - mRequestedPositionWhenMs;

        boolean needToRequestPosition;
        needToRequestPosition = false;

        if (mSpeed != 0 && elapsedSinceSetPositionMs > TIMEOUT_REQUEST_FRAME_MS) {
            ASPlayerLog.w("RendererTrickBySeekV3-%d timeout, ", mId, elapsedSinceSetPositionMs);
            needToRequestPosition = true;
        } else if (mSpeed != 0 && !hasRequestedFrame() && !hasNextPositionPending()
                && mVideoOutputPath.isFirstFrameDisplayed()) {
            needToRequestPosition = true;
        }

        if (needToRequestPosition) {
            mRequestedFrameTimeMs = 0;
            armNextRequestedPosition();
            delayUs = 0;
        }

        return delayUs;
    }

    private boolean hasNextPositionPending() {
        return (mPendingNextPositionTimeMs != 0);
    }

    private void applyPendingNextPosition() {
        mPendingNextPositionTimeMs = 0;
        mRequestedPositionWhenMs = SystemClock.elapsedRealtime();
        mRequestedPositionUs = mOriginPositionUs +
                (long) ((SystemClock.elapsedRealtime() - mOriginPositionWhenMs) * 1000. * mSpeed) -
                1000000;
        mRequestedPositionSet = true;
        ASPlayerLog.i("RendererTrickBySeekV3-%d position: %d", mId, mRequestedPositionUs);

        long startPositionUs = mPositionHandler.getStartPositionUs();
        long endPositionUs = mPositionHandler.getEndPositionUs();

        if (mSpeed > 0 && mRequestedPositionUs > endPositionUs - BOUNDS_MARGIN_US) {
            freezeToPosition(endPositionUs - BOUNDS_MARGIN_US);
            mPositionHandler.setPositionUs(endPositionUs);
        } else if (mSpeed < 0 && mRequestedPositionUs < startPositionUs + BOUNDS_MARGIN_US) {
            freezeToPosition(startPositionUs);
            mPositionHandler.setPositionUs(startPositionUs);
        } else {
            mPositionHandler.setPositionUs(mRequestedPositionUs);
        }
    }

    private void armNextRequestedPosition() {
        mPendingNextPositionTimeMs = SystemClock.elapsedRealtime() + DELAY_FOR_FLUSH_MS;
    }

    private boolean hasRequestedFrame() {
        return mRequestedFrameTimeMs > 0;
    }

    private boolean isRequestFrameNeeded() {
        return hasRequestedFrame() && (SystemClock.elapsedRealtime() - mRequestedFrameTimeMs) >= 0;
    }

    private void requestFrameAdvance() {
        ASPlayerLog.i("RendererTrickBySeekV3-%d frame-advance", mId);
        mVideoOutputPath.setParameters(PARAMS_REQUEST_FRAME);
        mRequestedFrameTimeMs = 0;
    }
}
