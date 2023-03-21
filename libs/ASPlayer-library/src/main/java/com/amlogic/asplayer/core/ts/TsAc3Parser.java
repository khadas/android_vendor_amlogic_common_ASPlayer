package com.amlogic.asplayer.core.ts;

import android.media.MediaFormat;

import com.amlogic.asplayer.core.ASPlayerLog;
import com.amlogic.asplayer.core.sipsi.mpeg.Mpeg;


/**
 * TsAc3Parser
 * Ref document :
 * - A52-201212-17.pdf "ATSC Standard: Digital Audio Compression (AC-3, E-AC-3)
 * - ETSI TS 102 366
 */
public class TsAc3Parser extends TsAudioParser {

    public static final String KEY_SYNCFRAME_SIZE = "ac3-syncframe-size";
    public static final String KEY_NB_SAMPLES_BY_SYNCFRAME = "ac3-samples-per-syncframe";

    private static final int XAC3_MIN_HEADER_SIZE = 7;


    /**
     * The number of new samples per (E-)AC-3 audio block.
     * section 4.1 (ETSI TS 102 366)
     */
    private static final int SAMPLES_PER_AUDIO_BLOCK = 256;
    /**
     * Sample rates, indexed by fscod (AC-3)
     * section 5.4.1.3, see Table 5.6 and AnnexE 2.3.1.4, see Table E2.2
     */
    private static final int[] SAMPLE_RATE_BY_FSCOD = new int[]{48000, 44100, 32000};
    /**
     * Sample rates, indexed by fscod2 (EAC-3).
     * section AnnexE 2.3.1.5, see Table E2.3
     */
    private static final int[] SAMPLE_RATE_BY_FSCOD2 = new int[]{24000, 22050, 16000};
    /**
     * Channel counts, indexed by acmod.
     * section 5.4.2.3, see Table 5.8
     */
    private static final int[] CHANNEL_COUNT_BY_ACMOD = new int[]{2, 1, 2, 3, 3, 4, 4, 5};
    /**
     * Frame size (AC-3), by bit rate
     * see section 5.3.1, 5.4.1.4, 5.4.4.1 and table 5.18
     */
    private static final int[] FRAME_SIZE_BY_CODE_32KHZ = new int[]
            {96, 96, 120, 120, 144, 144, 168, 168, 192, 192, 240, 240,
                    288, 288, 336, 336, 384, 384, 480, 480, 576, 576, 672, 672,
                    768, 768, 960, 960, 1152, 1152, 1344, 1344, 1536, 1536, 1728, 1728, 1920, 1920};
    private static final int[] FRAME_SIZE_BY_CODE_44_1KHZ = new int[]
            {69, 70, 87, 88, 104, 105, 121, 122, 139, 140, 174, 175,
                    208, 209, 243, 244, 278, 279, 348, 349, 417, 418, 487, 488,
                    557, 558, 696, 697, 835, 836, 975, 976, 1114, 1115, 1253, 1254, 1393, 1394};
    private static final int[] FRAME_SIZE_BY_CODE_48KHZ = new int[]
            {64, 64, 80, 80, 96, 96, 112, 112, 128, 128, 160, 160,
                    192, 192, 224, 224, 256, 256, 320, 320, 384, 384, 448, 448,
                    512, 512, 640, 640, 768, 768, 896, 896, 1024, 1024, 1152, 1152, 1280, 1280};

    /**
     * Number of blocks per sync frame
     * see E.1.3.1.5 (ETSI TS 102 366)
     */
    private static final int[] NB_BLOCKS_PER_SYNCFRAME = new int[]{
            1, 2, 3, 6
    };

    // work data
    private byte[] mPartialHeader;
    private int mPartialHeaderLength;
    private boolean mIsEac3;
    private int mChannelCount;
    private int mSampleRate;
    private int mFrameSize;
    private int mNbSamplesBySyncFrame;

    public TsAc3Parser() {
        mPartialHeader = new byte[XAC3_MIN_HEADER_SIZE];
    }

    private boolean skipToNextSync() {
        int firstByte = 0;
        int secondByte;
        while (mParser.getPosInBytes() < mParser.getLength()) {
            secondByte = mParser.readInt(8, "synchro byte");
            if (firstByte == 0x0B && secondByte == 0x77) {
                mParser.setPosInBits((mParser.getPosInBytes() - 2) * 8);
                return true;
            }
            firstByte = secondByte;
        }
        return false;
    }

    private boolean parseAc3() {
        // syncinfo : section 5.4.1
        mParser.skip(16, "syncword");
        mParser.skip(16, "crc1");
        int fscod = mParser.readInt(2, "fscod");
        int frmSizeCode = mParser.readInt(6, "frmsizecod");
        // bsi bit stream information : section 5.4.2
        mParser.skip(5, "bsid");
        mParser.skip(3, "bsmod");
        int acmod = mParser.readInt(3, "acmod");
        if ((acmod & 0x01) != 0 && acmod != 1) {
            mParser.skip(2, "cmixlev");
        }
        if ((acmod & 0x04) != 0) {
            mParser.skip(2, "surmixlev");
        }
        if (acmod == 2) {
            mParser.skip(2, "dsurmod");
        }
        boolean lfeon = mParser.readBool("lfeon");

        // get info from fields
        mChannelCount = CHANNEL_COUNT_BY_ACMOD[acmod] + (lfeon ? 1 : 0);

        if (fscod >= SAMPLE_RATE_BY_FSCOD.length) {
            ASPlayerLog.w("invalid fscod: %d", fscod);
            return false;
        }
        mSampleRate = SAMPLE_RATE_BY_FSCOD[fscod];

        int[] frameSizeByCode;
        if (mSampleRate == 32000) frameSizeByCode = FRAME_SIZE_BY_CODE_32KHZ;
        else if (mSampleRate == 44100) frameSizeByCode = FRAME_SIZE_BY_CODE_44_1KHZ;
        else frameSizeByCode = FRAME_SIZE_BY_CODE_48KHZ;

        if (frmSizeCode >= frameSizeByCode.length) {
            ASPlayerLog.w("invalid frmSizeCode: %d", frmSizeCode);
            return false;
        }

        mFrameSize = frameSizeByCode[frmSizeCode] * 2;

        // section 4.1 (ETSI TS 102 366)
        mNbSamplesBySyncFrame = SAMPLES_PER_AUDIO_BLOCK * 6;

        return true;
    }

    private boolean parseEac3() {
        // syncinfo : Annex E, section 2.2.1
        mParser.skip(16, "syncword");
        // bsi bit stream information : Annex E, section 2.2.2
        int streamType = mParser.readInt(2, "strmtyp");
        int substreamId = mParser.readInt(3, "substreamid");
        int frameSize = mParser.readInt(11, "frmsiz");
        int fscod = mParser.readInt(2, "fscod");
        int fscod2 = 0;
        int numblkscod;
        if (fscod == 0x03) {
            fscod2 = mParser.readInt(2, "fscod2");
            numblkscod = 3;
        } else {
            numblkscod = mParser.readInt(2, "numblkscod");
        }
        int acmod = mParser.readInt(3, "acmod");
        boolean lfeon = mParser.readBool("lfeon");

        // get info from fields
        mChannelCount = CHANNEL_COUNT_BY_ACMOD[acmod] + (lfeon ? 1 : 0);

        if (fscod == 0x03 && fscod2 >= SAMPLE_RATE_BY_FSCOD2.length) {
            ASPlayerLog.w("invalid fscode2: %d", fscod2);
            return false;
        }
        if (fscod == 0x03) mSampleRate = SAMPLE_RATE_BY_FSCOD2[fscod2];
        else mSampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
        mFrameSize = 2 * (frameSize + 1);

        //  E.1.3.1.5 (ETSI TS 102 366)
        mNbSamplesBySyncFrame = SAMPLES_PER_AUDIO_BLOCK * NB_BLOCKS_PER_SYNCFRAME[numblkscod];
        /*
        android.util.Log.i("TsExtractor", String.format("substreamId:%d, channelCount:%d frameSize:%d, samplerate:%d",
                substreamId, mChannelCount, mFrameSize, mSampleRate));
                */
        return (substreamId == 0) && (streamType == 0 || streamType == 2);
    }

    public void reset() {
        super.reset();
        mPartialHeaderLength = 0;
    }

    void parse(byte[] pesPayload, int offset, int length) throws UnsupportedFeature {
        mIsEac3 = false;
        mChannelCount = 0;
        mSampleRate = 0;

        // partial header
        if (mPartialHeaderLength != 0) {
            byte[] tmpPayload = new byte[length + XAC3_MIN_HEADER_SIZE];
            System.arraycopy(pesPayload, offset, tmpPayload, offset + mPartialHeaderLength, length - offset);
            System.arraycopy(mPartialHeader, 0, tmpPayload, offset, mPartialHeaderLength);
            mParser.setBytes(tmpPayload, offset, length + mPartialHeaderLength);
            length += mPartialHeaderLength;
            mPartialHeaderLength = 0;
        } else {
            mParser.setBytes(pesPayload, offset, length);
        }

        // copy partial data
        fillUnitBlock();

        // extract frames
        int unitBlockIndex = 0;
        long unitBlockDurationUs;
        long pesTimestampUs = getPesTimestampUs();
        while (skipToNextSync()) {
            int marker = mParser.getPosInBytes();

            // check if there is enough data for full header
            if (marker + XAC3_MIN_HEADER_SIZE > length) {
                mPartialHeaderLength = length - mParser.getPosInBytes();
                System.arraycopy(mParser.getBytes(), mParser.getPosInBytes(), mPartialHeader, 0, mPartialHeaderLength);
                break;
            }

            // get bsid
            // for AC3, see section 5.3.1 and 5.3.2
            // for EAC3, AnnexE, section 2.1
            mParser.skip(40, "syncinfo (sync, crc1, fscod, frmsizecod)");
            int bsid = mParser.readInt(5, "bsid");
            mIsEac3 = ( bsid>10 && bsid <= 16);

            //
            mParser.setPosInBits(marker * 8);

            boolean mustKeep;
            if (mIsEac3) {
                mustKeep = parseEac3();
            } else {
                mustKeep = parseAc3();
            }

            if (mustKeep) {
                MediaFormat oldFormat = getMediaFormat();
                if ((oldFormat == null) ||
                        (oldFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != mChannelCount) ||
                        (oldFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) != mSampleRate)) {

                    MediaFormat format = MediaFormat.createAudioFormat(mIsEac3 ? MediaFormat.MIMETYPE_AUDIO_EAC3 : MediaFormat.MIMETYPE_AUDIO_AC3,
                            mSampleRate, mChannelCount);
                    format.setInteger(KEY_NB_SAMPLES_BY_SYNCFRAME, mNbSamplesBySyncFrame);
                    format.setInteger(KEY_SYNCFRAME_SIZE, mFrameSize);
                    setMediaFormat(format);
                }

                unitBlockDurationUs = mNbSamplesBySyncFrame * 1000000L / mSampleRate;
                setUnitBlockSize(mFrameSize);
                setUnitBlockTimestampUs((pesTimestampUs + unitBlockIndex * unitBlockDurationUs) % Mpeg.PTS_MAX_VALUE_IN_US);
                mParser.setPosInBits(marker * 8);
                fillUnitBlock();
                unitBlockIndex++;
            } else {
                // fallback today : we send the whole pes
                // In the future to downgrade from 7.1 to 5.1, we should just ignore those frames
                clearUnitBlocks();
                addUnitBlock(offset, length - offset);
                break;
            }

            mParser.setPosInBits((marker + mFrameSize) * 8);
        }
    }
}
