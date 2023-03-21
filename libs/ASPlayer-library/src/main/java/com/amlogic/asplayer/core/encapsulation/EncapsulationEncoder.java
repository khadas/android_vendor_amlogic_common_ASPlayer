package com.amlogic.asplayer.core.encapsulation;

import java.nio.ByteBuffer;
import java.util.List;

public class EncapsulationEncoder {
    public static final int SYNC_BYTES = 0x5555;
    public static final int VERSION = 0x0003;
    public static final int FLAGS_METADATA_PRESENT = 0x01 << 14;

    public static final int HEADER_BYTES = 10;
    public static final int METADATA_HEADER_BYTES = 6;
    public static final int METADATA_UNIT_HEADER_BYTES = 4;

    public static final int METADATA_TYPE_TUNER = 1;
    public static final int METADATA_TYPE_MIX_PLACEMENT = 10002;

    public static final int METADATA_LENGTH_TUNER = METADATA_UNIT_HEADER_BYTES + 9;
    public static final int METADATA_LENGTH_MIX_PLACEMENT = METADATA_UNIT_HEADER_BYTES + 1;

    public static void encodeEmptyPacket(ByteBuffer byteBuffer, int length) {
        byteBuffer.clear();
        writeHeader(byteBuffer, length, 0);
        byteBuffer.rewind();
    }

    public static void encodePacket(ByteBuffer byteBuffer, List<Metadata> metadataList, int length) {
        byteBuffer.clear();
        int totalLengthInBytes = HEADER_BYTES;
        int flags = 0;

        byteBuffer.position(HEADER_BYTES);

        if (metadataList != null && !metadataList.isEmpty()) {
            int metadataLengthInBytes = METADATA_HEADER_BYTES;
            int metadataCount = 0;
            int metadataPosition = byteBuffer.position();
            byteBuffer.position(metadataPosition + METADATA_HEADER_BYTES);
            for (Metadata metadata : metadataList) {
                flags |= FLAGS_METADATA_PRESENT;
                if (metadata instanceof Metadata.TunerMetadata) {
                    metadataCount++;
                    metadataLengthInBytes += write(byteBuffer, (Metadata.TunerMetadata) metadata);
                } else if (metadata instanceof Metadata.PlacementMetadata) {
                    metadataCount++;
                    metadataLengthInBytes
                            += write(byteBuffer, (Metadata.PlacementMetadata) metadata);
                }
            }
            byteBuffer.position(metadataPosition);
            writeMetadataHeader(byteBuffer, metadataLengthInBytes, metadataCount);
            totalLengthInBytes += metadataLengthInBytes;
        }

        byteBuffer.position(0);
        writeHeader(byteBuffer, totalLengthInBytes, flags);

        if (flags == 0) {
            byteBuffer.limit(0);
        } else {
            byteBuffer.limit(length);
        }
        byteBuffer.rewind();
    }

    private static void writeHeader(ByteBuffer byteBuffer, int lengthInBytes, int flags) {
        byteBuffer.putShort((short) SYNC_BYTES);
        byteBuffer.putShort((short) VERSION);
        byteBuffer.putInt(lengthInBytes);
        byteBuffer.putShort((short) flags);
    }

    private static void writeMetadataHeader(ByteBuffer byteBuffer, int lengthInBytes, int count) {
        byteBuffer.putInt(lengthInBytes);
        byteBuffer.putShort((short) count);
    }

    private static int write(ByteBuffer byteBuffer, Metadata.TunerMetadata tunerMetadata) {
        byteBuffer.putShort((short) METADATA_TYPE_TUNER);
        byteBuffer.putShort((short) METADATA_LENGTH_TUNER);
        byteBuffer.putInt(tunerMetadata.filterId);
        byteBuffer.putInt(tunerMetadata.encodingType); //encoding type
        byteBuffer.put((byte) tunerMetadata.audioType);
        return METADATA_LENGTH_TUNER;
    }

    private static int write(ByteBuffer byteBuffer, Metadata.PlacementMetadata placementMetadata) {
        byteBuffer.putShort((short) METADATA_TYPE_MIX_PLACEMENT);
        byteBuffer.putShort((short) METADATA_LENGTH_MIX_PLACEMENT);
        byteBuffer.put((byte) placementMetadata.placement);
        return METADATA_LENGTH_MIX_PLACEMENT;
    }
}
