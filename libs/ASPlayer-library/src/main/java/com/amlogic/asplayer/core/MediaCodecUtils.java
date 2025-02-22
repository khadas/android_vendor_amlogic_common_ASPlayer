package com.amlogic.asplayer.core;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.text.TextUtils;
import android.util.Log;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;

import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback;
import static android.media.MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback;

public class MediaCodecUtils {
    private static final boolean DEBUG = true;
    private static final String TAG = "MediaCodecUtils";

    private static final int CREATE_CODEC_RETRY_COUNT = 3;
    private static final int CREATE_CODEC_RETRY_INTERVAL_MS = 1;

    private static Boolean mIsTunneledPlaybackSupported;
    private static Hashtable<String, ArrayList<MediaCodecInfo>> mCodecInfos = new Hashtable<>();

    private static synchronized void ensureTunneledPlaybackCapabilitiesIsLoaded() {
        if (mIsTunneledPlaybackSupported != null)
            return;

        String[] videoMimeTypes = {
                MediaFormat.MIMETYPE_VIDEO_MPEG2,
                MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC
        };

        MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
        mIsTunneledPlaybackSupported = true;
        for (String mimeType : videoMimeTypes) {
            boolean tunneledSupportedByCurrentMimeType = false;
            for (MediaCodecInfo codecInfo : mediaCodecInfos) {
                if (codecInfo.isEncoder())
                    continue;

                boolean mimeTypeSupported = false;
                String[] supportedTypes = codecInfo.getSupportedTypes();
                for (String type : supportedTypes) {
                    if (type.equals(mimeType)) {
                        mimeTypeSupported = true;
                        break;
                    }
                }
                if (!mimeTypeSupported)
                    continue;

                MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
                if (capabilities == null)
                    continue;
                if (capabilities.isFeatureSupported(FEATURE_TunneledPlayback)) {
                    tunneledSupportedByCurrentMimeType = true;
                    break;
                }
            }
            if (!tunneledSupportedByCurrentMimeType) {
                mIsTunneledPlaybackSupported = false;
                break;
            }
        }
    }

    private static synchronized void ensureMediaCodecInfosLoaded(String mimeType) {
        if (mCodecInfos.containsKey(mimeType))
            return;

        ASPlayerLog.i("ensureMediaCodecInfosLoaded mimeType: %s", mimeType);

        MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();
        ArrayList<MediaCodecInfo> codecInfosForMimeType = new ArrayList<>();
        for (MediaCodecInfo codecInfo : mediaCodecInfos) {
            if (codecInfo.isEncoder())
                continue;

            ASPlayerLog.i("codec name: %s, is vendor: %b", codecInfo.getName(), codecInfo.isVendor());

            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType))
                    codecInfosForMimeType.add(codecInfo);
            }
        }
        mCodecInfos.put(mimeType, codecInfosForMimeType);
    }

    private static boolean isCodecTunneled(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        return capabilities.isFeatureSupported(FEATURE_TunneledPlayback);
    }

    private static boolean isCodecSecure(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        return capabilities.isFeatureSupported(FEATURE_SecurePlayback);
    }

    private static boolean isSamePriority(MediaCodecInfo codecInfo1, MediaCodecInfo codecInfo2, String mimeType) {
        return isCodecSecure(codecInfo1, mimeType) == isCodecSecure(codecInfo2, mimeType)
                && isCodecTunneled(codecInfo1, mimeType) == isCodecTunneled(codecInfo2, mimeType)
                && codecInfo1.isVendor() == codecInfo2.isVendor()
                && codecInfo1.isHardwareAccelerated() == codecInfo2.isHardwareAccelerated();
    }

    static MediaCodec findMediaCodec(String mimeType, boolean mustBeTunneled, boolean securePlaybackRequested) throws IOException {
        ensureMediaCodecInfosLoaded(mimeType);

        ArrayList<MediaCodecInfo> mediaCodecInfos = mCodecInfos.get(mimeType);
        if (mediaCodecInfos == null || mediaCodecInfos.isEmpty())
            return null;

        // priority
        // 1. secure
        // 2. soc vendor
        // 3. tunneled as requested
        MediaCodecInfo bestCandidate = null;
        for (MediaCodecInfo codecInfo : mediaCodecInfos) {
            if (DEBUG) {
                Log.d(TAG, "codec name: " + codecInfo.getName());
                Log.d(TAG, "codec isVendor: " + codecInfo.isVendor());
                Log.d(TAG, "codec isHardwareAccelerated: " + codecInfo.isHardwareAccelerated());
                Log.d(TAG, "codec isCodecTunneled: " + isCodecTunneled(codecInfo, mimeType));
                Log.d(TAG, "codec isCodecSecure: " + isCodecSecure(codecInfo, mimeType));
            }

            if (bestCandidate != null) {
                if (securePlaybackRequested && !isCodecSecure(codecInfo, mimeType)) {
                    continue;
                }
                if (!codecInfo.isVendor() && bestCandidate.isVendor()) {
                    continue;
                }
                if (!codecInfo.isHardwareAccelerated() && bestCandidate.isHardwareAccelerated()) {
                    continue;
                }
                if (isCodecTunneled(bestCandidate, mimeType) == mustBeTunneled &&
                        isCodecTunneled(codecInfo, mimeType) != mustBeTunneled) {
                    continue;
                }
                if (!securePlaybackRequested && isCodecSecure(codecInfo, mimeType) && !isCodecSecure(bestCandidate, mimeType)) {
                    continue;
                }
                if (isSamePriority(codecInfo, bestCandidate, mimeType)) {
                    continue;
                }
            }
            bestCandidate = codecInfo;
            if (DEBUG) {
                Log.v(TAG, "set as candidate");
            }
        }

        if (bestCandidate == null) {
            return null;
        }

        if (DEBUG) {
            Log.v(TAG, "found codec name: " + bestCandidate.getName());
        }

        return MediaCodec.createByCodecName(bestCandidate.getName());
    }

    static boolean isTunneledPlaybackSupported() {
        ensureTunneledPlaybackCapabilitiesIsLoaded();
        return mIsTunneledPlaybackSupported;
    }

    static MediaCodec findDolbyVisionMediaCodec(int codecType, boolean mustBeTunneled, boolean securePlaybackRequested) throws IOException {
        String mimeType = MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION;
        ensureMediaCodecInfosLoaded(mimeType);

        ArrayList<MediaCodecInfo> mediaCodecInfos = mCodecInfos.get(mimeType);
        if (mediaCodecInfos == null || mediaCodecInfos.isEmpty()) {
            ASPlayerLog.i("Dolby Vision failed to get MediaCodecList");
            return null;
        }

        String codecNameKey = "";
        if (codecType == DolbyVisionUtils.CODEC_TYPE_HEVC) {
            codecNameKey = DolbyVisionUtils.CODEC_NAME_HEVC;
        } else if (codecType == DolbyVisionUtils.CODEC_TYPE_AVC) {
            codecNameKey = DolbyVisionUtils.CODEC_NAME_AVC;
        } else if (codecType == DolbyVisionUtils.CODEC_TYPE_AV1) {
            codecNameKey = DolbyVisionUtils.CODEC_NAME_AV1;
        } else {
            ASPlayerLog.i("Dolby Vision unknown codecType: %d", codecType);
        }

        if (TextUtils.isEmpty(codecNameKey)) {
            return null;
        }

        ArrayList<MediaCodecInfo> dvCodecInfos = new ArrayList<>();
        if (!TextUtils.isEmpty(codecNameKey)) {
            for (MediaCodecInfo codecInfo : mediaCodecInfos) {
                if (DEBUG) {
                    Log.v(TAG, "codec name: " + codecInfo.getName());
                    Log.v(TAG, "codec isVendor: " + codecInfo.isVendor());
                    Log.v(TAG, "codec isHardwareAccelerated: " + codecInfo.isHardwareAccelerated());
                    Log.v(TAG, "codec isCodecTunneled: " + isCodecTunneled(codecInfo, mimeType));
                    Log.v(TAG, "codec isCodecSecure: " + isCodecSecure(codecInfo, mimeType));
                }

                String codecName = codecInfo.getName();
                if (codecName.contains("decoder") && codecName.contains(codecNameKey)) {
                    dvCodecInfos.add(codecInfo);
                }
            }
        }

        // priority
        // 1. secure
        // 2. soc vendor
        // 3. tunneled as requested
        MediaCodecInfo bestCandidate = null;
        for (MediaCodecInfo codecInfo : dvCodecInfos) {
            if (DEBUG) {
                Log.v(TAG, "codec name: " + codecInfo.getName());
                Log.v(TAG, "codec isVendor: " + codecInfo.isVendor());
                Log.v(TAG, "codec isHardwareAccelerated: " + codecInfo.isHardwareAccelerated());
                Log.v(TAG, "codec isCodecTunneled: " + isCodecTunneled(codecInfo, mimeType));
                Log.v(TAG, "codec isCodecSecure: " + isCodecSecure(codecInfo, mimeType));
            }

            if (bestCandidate != null) {
                if (securePlaybackRequested && !isCodecSecure(codecInfo, mimeType)) {
                    continue;
                }
                if (!codecInfo.isVendor() && bestCandidate.isVendor()) {
                    continue;
                }
                if (!codecInfo.isHardwareAccelerated() && bestCandidate.isHardwareAccelerated()) {
                    continue;
                }
                if (isCodecTunneled(bestCandidate, mimeType) == mustBeTunneled &&
                        isCodecTunneled(codecInfo, mimeType) != mustBeTunneled) {
                    continue;
                }
                if (!securePlaybackRequested && isCodecSecure(codecInfo, mimeType) && !isCodecSecure(bestCandidate, mimeType)) {
                    continue;
                }
                if (isSamePriority(codecInfo, bestCandidate, mimeType)) {
                    continue;
                }
            }
            bestCandidate = codecInfo;
            if (DEBUG) {
                Log.v(TAG, "set as candidate");
            }
        }

        if (bestCandidate == null) {
            return null;
        }

        if (DEBUG) {
            Log.v(TAG, "found codec name: " + bestCandidate.getName());
        }

        return MediaCodec.createByCodecName(bestCandidate.getName());
    }
}
