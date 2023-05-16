package com.amlogic.asplayer.core;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaDescrambler;
import android.media.MediaFormat;


import java.nio.ByteBuffer;

/**
 * AudioCodeRendered must
 * - decode
 * - give information on output buffers
 * - render/play output buffers
 * - notify when an output buffer is consumed/rendered
 */
interface AudioCodecRenderer {

    /**
     * Listener that will be notified when an output buffer is played(rendered) or
     * discarded(consumed)
     */
    interface OutputBufferListener {
        void onRender(long presentationTimeUs);

        void onConsume(long presentationTimeUs);
    }

    /**
     * Extra information on output buffers
     */
    class BufferInfo {
        int flags;
        int offset;
        long presentationTimeUs;
        int size;
    }

    /**
     * Sets listener
     *
     * @param listener
     */
    void setOutputBufferListener(OutputBufferListener listener);

    /**
     * Sets clock used for rendering synchronization
     *
     * @param timestampUs
     */
    void setClockOrigin(long timestampUs);

    /**
     * Sets a session id for synchronization when tunneled playback is activated
     *
     * @param sessionId
     */
    void setAudioSessionId(int sessionId);

    /**
     * Activates tunneled playback
     *
     * @param activated true to active tunneled playback
     */
    void setTunneledPlayback(boolean activated);

    /**
     * Sets audio capabilities and configuration of output devices
     *
     * @param audioCaps
     */
    void setAudioCaps(AudioCaps audioCaps);

    /**
     * Sets volume
     *
     * @param gain 0.f for mute, 1.f for 100% volume
     */
    void setVolume(float gain);

    /**
     * Renders frame without synchronization with external track
     *
     * @return number of us to wait before calling this function again
     */
    long renderFreeRun();

    /**
     * Renders frame with synchronization with external track
     *
     * @param inputQueueSizeUs
     * @return number of us to wait before calling this function again
     */
    long renderSynchro(long inputQueueSizeUs);

    /**
     * Configures decoder with the given format
     *
     * @param format
     */
    void configure(MediaFormat format, MediaDescrambler descrambler);

    /**
     * Set speed of audio track
     *
     * @param speed
     */
    void setSpeed(double speed);

    /**
     * Releases all underlying resources associated with the decoder and audio track
     */
    void release();

    /**
     * Resets decoder and flush queues
     */
    void reset();

    /**
     * Checks if there are errors
     */
    void checkErrors();

    /**
     * Returns last error or null if there is none
     *
     * @return
     */
    String getErrorMessage();

    /**
     * Indicates if there is an available input buffer
     *
     * @return
     */
    boolean hasInputBuffer();

    /**
     * Pops index of the oldest input buffer
     *
     * @return
     */
    int getNextInputBufferIndex();

    /**
     * Get input buffer
     *
     * @return
     */
    ByteBuffer getInputBuffer(int index);

    /**
     * Queue input buffer
     *
     * @return
     */
    void queueInputBuffer(int index, int offset, int size, long timestampUs, int flags);

    /**
     * Queue secure input buffer
     *
     * @return
     */
    default void queueSecureInputBuffer(int index, int offset, MediaCodec.CryptoInfo info, long presentationTimeUs, int flags) throws MediaCodec.CryptoException {

    }

    /**
     * Indicates if there is an available output buffer for rendering
     *
     * @return
     */
    boolean hasOutputBuffer();

    /**
     * Gets number of available output buffers
     *
     * @return
     */
    int getNbOutputBuffers();

    /**
     * Gets timestamp for the next output buffer
     *
     * @return
     */
    long getNextOutputTimestampUs();

    /**
     * Gets margin, ie minimum amount of data necessary to avoid underrun
     *
     * @return margin in microseconds
     */
    long getMarginUs();

    /**
     * Returns the position of the last displayed frame
     *
     * @return last displayed frame position
     */
    long getDisplayPositionUs();

    /**
     * Indicates of the position of the last displayed frame is available
     *
     * @return true if getDisplayPositionUs returns a valid position
     */
    boolean isDisplayPositionValid();

    /**
     * Indicates if the audio track is playing
     *
     * @return true if the audio track has data and is in play state
     */
    boolean isPlaying();

    void pause();

    void resume();

    AudioFormat getAudioFormat();
}
