package com.amlogic.asplayer.core.sipsi;


import android.util.Log;

import com.amlogic.asplayer.core.Constant;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;


public class BufferParser {

    private static final String TAG = Constant.LOG_TAG;

    static private long sMasks[];

    static {
        sMasks = new long[65];
        for (int i = 0; i < sMasks.length; ++i) {
            for (int j = 0; j < i; j++) {
                sMasks[i] = (sMasks[i] << 1) | 1;
            }
        }
    }

    static private String sDvbEncodings[] = {
        /* 0x00 - 0x0f */
            "", "ISO-8859-5", "ISO-8859-6", "ISO-8859-7", "ISO-8859-8",
            "ISO-8859-9", "ISO-8859-10", "ISO-8859-11", "", "ISO-8859-13",
            "ISO-8859-14", "ISO-8859-15", "", "", "", "",

        /* 0x10 - 0x1f */
            "", "UTF-16", "KSC5601-1987", "GB2312", "BIG-5", "UTF-8",
            "", "", "", "", "", "", "", "", "", "", null
    };
    static private String sDvbEncodings10[] = {
            "", "ISO-8859-1", "ISO-8859-2", "ISO-8859-3", "ISO-8859-4",
            "ISO-8859-5", "ISO-8859-6", "ISO-8859-7", "ISO-8859-8", "ISO-8859-9",
            "ISO-8859-10", "ISO-8859-11", "", "ISO-8859-13", "ISO-8859-14",
            "ISO-8859-15", null
    };

    private byte[] mBytes;
    private int mOffsetInBytes;
    private int mLength;
    private long mPosInBits;

    public BufferParser() {
    }

    public void setBytes(byte[] bytes, int offset) {
        setBytes(bytes, offset, bytes.length);
    }

    public void setBytes(byte[] bytes, int offset, int length) {
        if (length > bytes.length)
            throw new ArrayIndexOutOfBoundsException(String.format(Locale.US, "bytes.length=%d offset=%d length=%d",
                    bytes.length, offset, length));

        mBytes = bytes;
        mOffsetInBytes = offset;
        mPosInBits = offset * 8;
        mLength = length;
    }

    public byte[] getBytes() {
        return mBytes;
    }

    public int getLength() {
        return mLength;
    }

    public int remaining() {
        return (mLength + mOffsetInBytes - (int) (mPosInBits / 8));
    }

    public int readInt(int nbBits, String description) {
        if (nbBits > 32)
            throw new IllegalArgumentException(String.format(Locale.US, "nbBits(%d>32) field '%s'",
                    nbBits, description));

        return (int) readLong(nbBits, description);
    }

    public int readBcdInt(int nbBits, String description) {
        if (nbBits > 32 || nbBits % 4 != 0)
            throw new IllegalArgumentException(String.format(Locale.US, "nbBits(%d) field '%s'",
                    nbBits, description));

        int bcdInt = 0;
        while (nbBits > 0) {
            bcdInt *= 10;
            bcdInt += readInt(4, description);
            nbBits -= 4;
        }

        return bcdInt;
    }

    public long readLong(int nbBits, String description) {
        long mask = sMasks[nbBits];
        int posInBytes = (int) (mPosInBits >> 3);
        long value = 0;
        int nbBytes;

        if (mPosInBits + nbBits > mLength * 8) {
            throw new IllegalArgumentException(String.format(Locale.US, "Out of bounds (pos=%d, nbBits=%d, length=%d) field='%s'",
                    mPosInBits, nbBits, mLength, description));
        }

        nbBytes = (nbBits + (int) (mPosInBits % 8) - 1) / 8 + 1;
        for (int i = nbBytes; i > 0; i--) {
            value = (value << 8) | (mBytes[posInBytes + (nbBytes - i)] & 0xFF);
        }

        value = (value >> (nbBytes * 8 - nbBits - mPosInBits % 8)) & mask;

        mPosInBits += nbBits;

        return value;
    }

    // TODO : does not take into account encoding correctly
    // TODO : use a separate class for string conversion and
    //                use ideas from EventInfo.dvbTextToString in dtvplayer
    public String readString(int nbBits, String description) {
        if (mPosInBits % 8 != 0 || nbBits % 8 != 0) {
            throw new IllegalArgumentException(String.format(Locale.US, "nbBits=%d, mPosInBits=%d field='%s'",
                    mPosInBits, nbBits, description));
        }


        int posInBytes = (int) (mPosInBits / 8);
        mPosInBits += nbBits;

        if (posInBytes > mLength) {
            throw new IllegalArgumentException(String.format(Locale.US, "String too long : pos=%d nbBytes=%d, length=%d field='%s'",
                    posInBytes, nbBits / 8, mLength, description));
        }

        // easy case
        if (nbBits < 8)
            return "";

        // get encoding
        String encoding = "ISO-8859-1";
        byte first = mBytes[posInBytes];
        byte second = 0;
        byte third = 0;
        if (first > 0 && first < 0x20) {
            posInBytes++;
            nbBits -= 8;
            if (first == 0x10 && nbBits >= 16) {
                second = mBytes[posInBytes++];
                nbBits -= 8;
                third = mBytes[posInBytes++];
                nbBits -= 8;

                if (second != 0x0 || third == 0 || third >= 0x10)
                    encoding = "ISO-8859-1";
                else
                    encoding = sDvbEncodings10[third];
            } else if (first == 0x1f && nbBits >= 8) {
                posInBytes++;
                nbBits -= 8;
                encoding = "ISO-8859-1";
            } else {
                encoding = sDvbEncodings[first];
            }
        }

       return extractString(posInBytes, nbBits, description, encoding);
    }

    public String readString(int nbBits, String description, String encoding) {
        if (mPosInBits % 8 != 0 || nbBits % 8 != 0) {
            throw new IllegalArgumentException(String.format(Locale.US, "nbBits=%d, mPosInBits=%d field='%s'",
                    mPosInBits, nbBits, description));
        }

        int posInBytes = (int) (mPosInBits / 8);
        mPosInBits += nbBits;

        if (posInBytes > mLength) {
            throw new IllegalArgumentException(String.format(Locale.US, "String too long : pos=%d nbBytes=%d, length=%d field='%s'",
                    posInBytes, nbBits / 8, mLength, description));
        }

        // easy case
        if (nbBits < 8)
            return "";

        return extractString(posInBytes, nbBits, description, encoding);
    }

    private String extractString(int posInBytes, int nbBits, String description, String encoding) {

        // check control codes and invalid characters
        int lastPos = posInBytes + nbBits / 8;
        byte[] modifiedBytes = null;
        int modifiedBytesSize = 0;
        for (int i = posInBytes; i < lastPos; ++i) {
            int currentByte = (mBytes[i] & 0xFF);
            // check if character needs to be modified or removed
            boolean needSubstitution = false;
            if (currentByte < 0x20) {
                switch (currentByte) {
                    case 0x09:  // TAB
                    case 0x0A:  // LF
                    case 0x0D:  // CR
                        break;
                    default:
                        needSubstitution = true;
                        break;
                }
            } else if (currentByte >= 0x80 && currentByte <= 0x9F) {
                needSubstitution = true;
            }
            // copy/change character
            if (needSubstitution) {
                if (modifiedBytes == null) {
                    modifiedBytes = Arrays.copyOfRange(mBytes, posInBytes, lastPos);
                    modifiedBytesSize = i - posInBytes;
                }
                switch (currentByte) {
                    case 0x8a: // CR/LF
                        modifiedBytes[modifiedBytesSize] = '\n';
                        modifiedBytesSize++;
                        break;
                    case 0x86: // emphasis on
                    case 0x87: // emphasis on
                    default: // reserved for future
                        break;
                }
            } else if (modifiedBytes != null) {
                modifiedBytes[modifiedBytesSize] = mBytes[i];
                modifiedBytesSize++;
            }
        }

        if (encoding.isEmpty()) {
            Log.w(TAG, "nul encoding unexpected ");
            encoding = "UTF-8";
        }

        // build and return string
        if (modifiedBytes != null) {
            return new String(modifiedBytes, 0, modifiedBytesSize, Charset.forName(encoding));
        } else {
            return new String(mBytes, posInBytes, nbBits / 8, Charset.forName(encoding));
        }
    }

    public boolean readBool(String description) {
        return (readInt(1, description) == 1);
    }

    public void skip(int nbBits, String reason) {
        mPosInBits += nbBits;

        if (mPosInBits / 8 > mLength) {
            throw new IllegalArgumentException(String.format(Locale.US, "Out of range : pos=%d nbBytes=%d length=%d field='%s'",
                    mPosInBits / 8, nbBits / 8, mLength, reason));
        }
    }

    public int getPosInBytes() {
        return (int) (mPosInBits / 8);
    }

    public long getPosInBits() {
        return mPosInBits;
    }

    public void setPosInBits(long pos) {
        mPosInBits = pos;
    }
}
