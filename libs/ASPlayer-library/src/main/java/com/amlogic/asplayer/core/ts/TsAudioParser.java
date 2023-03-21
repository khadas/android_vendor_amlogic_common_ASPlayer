package com.amlogic.asplayer.core.ts;

import android.media.MediaFormat;


import com.amlogic.asplayer.core.ASPlayerLog;
import com.amlogic.asplayer.core.sipsi.BufferParser;
import com.amlogic.asplayer.core.sipsi.mpeg.Pes;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Base class of every audio parser that must split pes into blocks of data handled by MediaCodec
 */
public abstract class TsAudioParser {

    private static final int MAX_UNIT_BLOCK_SIZE = 1024 * 8;
    private static final int MAX_UNIT_BLOCKS = 32;

    private class UnitBlock {
        UnitBlock() {
            data = new byte[MAX_UNIT_BLOCK_SIZE];
        }

        void clear() {
            length = 0;
            writePosition = 0;
            timestampUs = 0;
        }

        int length;
        int writePosition;
        long timestampUs;
        byte[] data;
    }

    class UnsupportedFeature extends Exception {
        UnsupportedFeature(String message, Object... args) {
            super(String.format(Locale.US, message, args));
        }
    }

    // parsing
    BufferParser mParser;

    // timestamp extracted from current pes;
    private long mPesTimestampUs;

    // chunk extracted from pes
    private UnitBlock[] mUnitBlocks;
    private int mUnitBlocksWritePos;
    private int mUnitBlockReadPos;

    // format extracted from pes
    private MediaFormat mFormat;

    TsAudioParser() {
        mParser = new BufferParser();
        mUnitBlocks = new UnitBlock[MAX_UNIT_BLOCKS];
        for (int i = 0; i < MAX_UNIT_BLOCKS; ++i)
            mUnitBlocks[i] = new UnitBlock();
    }

    /**
     * Parses a pes to split it into blocks of data
     *
     * @param pes pes to parse.
     * @return true if the pes is successfully parsed, false otherwise. In particular if a parser
     * detects that a feature is not handled, it will return false.
     */
    public boolean setPes(Pes pes, long timestamp) {
        try {
            mPesTimestampUs = timestamp;
            parse(pes.getBuffer(), pes.getHeaderLength(), pes.getLength());
            pes.release();
            return true;
        } catch (UnsupportedFeature exception) {
            ASPlayerLog.w("unsupported feature : %s", exception);
            return false;
        }
    }

    /**
     * Returns the last MediaFormat found while parsing a pes (with setPes).
     *
     * @return MediaFormat found while parsing pes. Might be null if pes did not contain
     * configuration data
     */
    public MediaFormat getMediaFormat() {
        return mFormat;
    }

    /**
     * Indicates if there are still some blocks of data
     *
     * @return true if there are remaining blocks of data
     */
    public boolean hasAudioBuffer() {
        return (mUnitBlocksWritePos != mUnitBlockReadPos);
    }

    /**
     * Fills buffer with current block of data
     *
     * @param buffer that will be filled with data of the current block
     */
    public void getAudioBufferData(ByteBuffer buffer) {
        if (mUnitBlocksWritePos == mUnitBlockReadPos)
            return;
        UnitBlock block = mUnitBlocks[mUnitBlockReadPos];
        buffer.clear();
        buffer.put(block.data, 0, block.length);
        buffer.flip();
    }

    /**
     * Returns timestamp in microseconds of the current timestamp
     * Current implementation might return the timestamp of the pes if we are not able to compute
     * the exact timestamp of a buffer. In other words some consecutive buffers might share the
     * same timestamp.
     *
     * @return timestamp in microseconds
     */
    public long getAudioBufferTimestampUs() {
        if (mUnitBlocksWritePos == mUnitBlockReadPos)
            return 0;
        UnitBlock block = mUnitBlocks[mUnitBlockReadPos];
        if (block.timestampUs != 0)
            return block.timestampUs;
        return mPesTimestampUs;
    }


    /**
     * Move to next buffer
     *
     * @return true if the next buffer is available
     */
    public boolean moveToNext() {
        if (mUnitBlocksWritePos == mUnitBlockReadPos)
            return false;
        mUnitBlockReadPos++;
        mUnitBlockReadPos %= MAX_UNIT_BLOCKS;
        return (mUnitBlocksWritePos != mUnitBlockReadPos);
    }

    /**
     * Clear any buffers and intermediate buffer
     */
    public void reset() {
        clearUnitBlocks();
    }

    abstract void parse(byte[] pesPayload, int offset, int length) throws UnsupportedFeature;

    private void commitBlock() throws UnsupportedFeature {
        if (((mUnitBlocksWritePos + 1) % MAX_UNIT_BLOCKS) == mUnitBlockReadPos)
            throw new UnsupportedFeature("too many units : write:%d(read:%d)",
                    mUnitBlocksWritePos, mUnitBlockReadPos);

        mUnitBlocksWritePos++;
        mUnitBlocksWritePos%=MAX_UNIT_BLOCKS;
        mUnitBlocks[mUnitBlocksWritePos].clear();
    }

    void addUnitBlock(int startPos, int frameSize) throws UnsupportedFeature {
        mParser.setPosInBits(startPos * 8);
        addUnitBlock(frameSize);
    }

    void addUnitBlock(int frameSize) throws UnsupportedFeature {
        UnitBlock block = mUnitBlocks[mUnitBlocksWritePos];
        block.clear();
        block.length = frameSize;
        block.writePosition = frameSize;
        if (mParser.getPosInBits() % 8 == 0) {
            System.arraycopy(mParser.getBytes(), mParser.getPosInBytes(), block.data, 0, frameSize);
            mParser.setPosInBits((mParser.getPosInBytes()+frameSize)*8);
        } else {
            for (int i = 0; i < block.length; ++i) {
                block.data[i] = (byte) (mParser.readInt(8, "payload") & 0xff);
            }
        }
        commitBlock();
    }

    void setUnitBlockSize(int size) {
        UnitBlock block = mUnitBlocks[mUnitBlocksWritePos];
        block.clear();
        block.length = size;
    }

    void setUnitBlockTimestampUs(long timestampUs) {
        UnitBlock block = mUnitBlocks[mUnitBlocksWritePos];
        block.timestampUs = timestampUs;
    }

    void fillUnitBlock() throws UnsupportedFeature {
        UnitBlock block = mUnitBlocks[mUnitBlocksWritePos];
        if (block.length == 0)
            return;
        int remaining = mParser.getLength()-mParser.getPosInBytes();
        int toCopy = block.length-block.writePosition;
        toCopy = Math.min(remaining, toCopy);
        if (mParser.getPosInBits() % 8 == 0) {
            System.arraycopy(mParser.getBytes(), mParser.getPosInBytes(), block.data, block.writePosition, toCopy);
            mParser.setPosInBits((mParser.getPosInBytes()+toCopy)*8);
        } else {
            for (int i = 0; i < toCopy; ++i) {
                block.data[i] = (byte) (mParser.readInt(8, "payload") & 0xff);
            }
        }
        block.writePosition+=toCopy;
        if (block.writePosition == block.length)
            commitBlock();
    }

    void clearUnitBlocks() {
        mUnitBlockReadPos = mUnitBlocksWritePos;
    }

    long getPesTimestampUs() {
        return mPesTimestampUs;
    }

    void setMediaFormat(MediaFormat format) {
        mFormat = format;
    }
}
