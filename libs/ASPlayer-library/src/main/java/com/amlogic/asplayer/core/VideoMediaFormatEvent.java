package com.amlogic.asplayer.core;


import com.amlogic.asplayer.core.sipsi.BufferParser;

public class VideoMediaFormatEvent {
    private static final boolean DEBUG = true;

    public static final long SYNC_BITS = 0B11L << 62;

    public static final int EVENT_TYPE_RESOLUTION = 1;
    public static final int EVENT_TYPE_ASPECT_RATIO = 2;
    public static final int EVENT_TYPE_AFD = 3;
    public static final int EVENT_TYPE_FRAME_RATES = 4;
    public static final int EVENT_TYPE_VIDEO_VF_TYPE = 5;

    public static final float EVENT_VALUE_DECIMAL_SCALE_FACTOR = 10000;

    public static final String KEY_EVENT_FLAGS = "vendor.tunerhal.passthrough.event-mask";
    public static final long EVENT_FLAGS_RESOLUTION =           0x1 << EVENT_TYPE_RESOLUTION;
    public static final long EVENT_FLAGS_PIXEL_ASPECT_RATIO =   0x1 << EVENT_TYPE_ASPECT_RATIO;
    public static final long EVENT_FLAGS_AFD =                  0x1 << EVENT_TYPE_AFD;
    public static final long EVENT_FLAGS_FRAME_RATES =          0x1 << EVENT_TYPE_FRAME_RATES;
    public static final long EVENT_FLAGS_VIDEO_VF_TYPE =        0x1 << EVENT_TYPE_VIDEO_VF_TYPE;

    int type;

    public static boolean isEventData(long event) {
        return (event & SYNC_BITS) == SYNC_BITS;
    }

    private VideoMediaFormatEvent(int type) {
        this.type = type;
    }

    public static VideoMediaFormatEvent parse(byte[] bytes) {
        if (bytes == null || bytes.length < Long.BYTES) return null;

        try {
            BufferParser parser = new BufferParser();
            parser.setBytes(bytes, 0);
            parser.readInt(2, "sync bits");
            int eventType = parser.readInt(6, "sync bits");
            parser.readInt(24, "reserved");
            ASPlayerLog.i("VideoMediaFormatEvent event type: %d", eventType);

            switch (eventType) {
                case EVENT_TYPE_RESOLUTION:
                    return new ResolutionEvent(parser);
                case EVENT_TYPE_ASPECT_RATIO:
                    return new AspectRatioEvent(parser);
                case EVENT_TYPE_AFD:
                    return new AfdEvent(parser);
                case EVENT_TYPE_FRAME_RATES:
                    return new FrameRateEvent(parser);
                case EVENT_TYPE_VIDEO_VF_TYPE:
                    return new VideoVFTypeEvent(parser);
                default:
                    ASPlayerLog.w("not defined event type: %d", eventType);
                    break;
            }
        } catch (Exception e) {
            ASPlayerLog.e("error occurred during parsing the event", e);
        }
        return null;
    }

    static class ResolutionEvent extends VideoMediaFormatEvent {
        int width;
        int height;
        public ResolutionEvent(BufferParser parser) {
            super(EVENT_TYPE_RESOLUTION);
            width = parser.readInt(16, "width");
            height = parser.readInt(16, "height");
            if (DEBUG) ASPlayerLog.i("width: %d, height: %d", width, height);
        }
    }

    static class FrameRateEvent extends VideoMediaFormatEvent {
        int frameRate;
        public FrameRateEvent(BufferParser parser) {
            super(EVENT_TYPE_FRAME_RATES);
            long eventValue = parser.readInt(32, "frame rate") & 0xFFFFFFFFL;
            frameRate = (int)eventValue;
            if (DEBUG) ASPlayerLog.i("frameRate: %d", frameRate);
        }
    }

    static class AspectRatioEvent extends VideoMediaFormatEvent {
        int aspectRatio;
        public AspectRatioEvent(BufferParser parser) {
            super(EVENT_TYPE_ASPECT_RATIO);
            long eventValue = parser.readInt(32, "aspect ratio") & 0xFFFFFFFFL;
            aspectRatio = (int)eventValue;
            if (DEBUG) ASPlayerLog.i("aspectRatio: %d", aspectRatio);
        }
    }

    static class AfdEvent extends VideoMediaFormatEvent {
        byte activeFormat;
        public AfdEvent(BufferParser parser) {
            super(EVENT_TYPE_AFD);
            parser.readInt(1, "0");
            int afFlag = parser.readInt(1, "active_format_flag");
            parser.readInt(6, "reserved");
            if (DEBUG) ASPlayerLog.i("afFlag: %d", afFlag);
            if (afFlag == 1) {
                parser.readInt(4, "reserved");
                activeFormat = (byte) (parser
                        .readInt(4, "active_format") & 0B1111);
            }
            if (DEBUG) ASPlayerLog.i("AFD: %s", Integer.toBinaryString(activeFormat));
        }
    }

    static class VideoVFTypeEvent extends VideoMediaFormatEvent {
        int vfType;
        private VideoVFTypeEvent(BufferParser parser) {
            super(EVENT_TYPE_VIDEO_VF_TYPE);
            long eventValue = parser.readInt(32, "vf_type") & 0xFFFFFFFFL;
            vfType = (int)eventValue;
            if (DEBUG) ASPlayerLog.i("vf_type: %d, 0x%08x", vfType, vfType);
        }
    }
}