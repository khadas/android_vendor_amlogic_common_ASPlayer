package com.amlogic.asplayer.core;

import static com.amlogic.asplayer.core.Constant.UNKNOWN_AUDIO_PRESENTATION_ID;
import static com.amlogic.asplayer.core.Constant.UNKNOWN_AUDIO_PROGRAM_ID;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPresentation;
import android.media.AudioTrack;
import android.media.MediaFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;


import com.amlogic.asplayer.api.AudioParams;
import com.amlogic.asplayer.api.ErrorCode;
import com.amlogic.asplayer.core.ts.TsAc3Parser;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AudioUtils {

    private static final String TAG = Constant.LOG_TAG + "_AudioUtils";

    // Absolute min volume in dB (can be represented in single precision normal float value)
    public static final float VOLUME_MIN_DB = -758.0f;

    public static final float VOLUME_MAX_DB = 48.0f;

    public static final String CMD_SET_AUDIO_PRESENTATION_ID = "hal_param_dtv_media_presentation_id"; // not used
    public static final String CMD_GET_AUDIO_PRESENTATION_ID = "ac4_active_pres_id";
    public static final String CMD_SET_AUDIO_FIRST_LANG = "hal_param_dtv_media_first_lang";
    public static final String CMD_SET_AUDIO_SECOND_LANG = "hal_param_dtv_media_second_lang";

    public static int getEncoding(AudioParams audioParams) {
        MediaFormat mediaFormat = audioParams.getMediaFormat();
        if (mediaFormat != null) {
            return getEncoding(mediaFormat);
        }

        int encoding = AudioFormat.ENCODING_DEFAULT;
        String mimeType = audioParams.getMimeType();
        if (TextUtils.isEmpty(mimeType)) {
            return encoding;
        }

        if (mimeType.equalsIgnoreCase("audio/mp4a-latm")) {
            return AudioFormat.ENCODING_AAC_HE_V1;
        } else if (mimeType.equalsIgnoreCase("audio/aac")) {
            return AudioFormat.ENCODING_AAC_HE_V2;
        }

        mediaFormat = new MediaFormat();
        mediaFormat.setString(MediaFormat.KEY_MIME, mimeType);
        return getEncoding(mediaFormat);
    }

    private static int getEncoding(MediaFormat format) {
        int encoding = AudioFormat.ENCODING_DEFAULT;
        String mimeType = format.getString(MediaFormat.KEY_MIME);
        switch (mimeType) {
            case MediaFormat.MIMETYPE_AUDIO_AC3:
                encoding = AudioFormat.ENCODING_AC3;
                break;
            case MediaFormat.MIMETYPE_AUDIO_EAC3:
                encoding = AudioFormat.ENCODING_E_AC3;
                break;
            case MediaFormat.MIMETYPE_AUDIO_AAC:
                if (format.getInteger(MediaFormat.KEY_IS_ADTS, 0) == 1) {
                    encoding = AudioFormat.ENCODING_AAC_HE_V2;
                } else {
                    encoding = AudioFormat.ENCODING_AAC_HE_V1;
                }
                break;
            case MediaFormat.MIMETYPE_AUDIO_MPEG:
                encoding = AudioFormat.ENCODING_MP3;
                break;
            case MediaFormat.MIMETYPE_AUDIO_AC4:
                encoding = AudioFormat.ENCODING_AC4;
                break;
            default:
                ASPlayerLog.w("Invalid mime type: %s", mimeType);
        }
        return encoding;
    }

    private static long getFromAudioTrackLatencyUs(AudioTrack track) {
        try {
            Method method = AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
            long trackLatency = (Integer)
                    method.invoke(track, (Object[]) null);
            return trackLatency * 1000;
        } catch (NoSuchMethodException exception) {
            Log.w(TAG, "getMethod", exception);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            Log.w(TAG, "invoke", exception);
        }
        return 0;
    }

    static long getBufferDurationUs(int bufferSizeInBytes, int sampleRate, int channelCount) {
        return ((bufferSizeInBytes / (2 * channelCount)) * 1000000L / sampleRate);
    }

    static int getBufferSizeFromDurationUs(long durationUs, int sampleRate, int channelCount) {
        return (int) ((2 * channelCount * durationUs * sampleRate) / 1000000L);
    }

	static long getEstimatedLatencyUs(AudioTrack track, MediaFormat format) {
        long latencyUs;

        String mimeType = format.getString(MediaFormat.KEY_MIME);

        long audioTrackLatency = getFromAudioTrackLatencyUs(track);

        long bufferDurationUs;
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int bufferSizeInFrames = track.getBufferSizeInFrames();
        switch (mimeType) {
            // audio track that will contain encoded data
            case MediaFormat.MIMETYPE_AUDIO_AC3:
            case MediaFormat.MIMETYPE_AUDIO_EAC3:
                int nbSamplesPerSyncFrame = format.getInteger(TsAc3Parser.KEY_NB_SAMPLES_BY_SYNCFRAME);
                int syncFrameSize = format.getInteger(TsAc3Parser.KEY_SYNCFRAME_SIZE);
                int nbFrames = bufferSizeInFrames / syncFrameSize;
                bufferDurationUs = (nbFrames * nbSamplesPerSyncFrame * 1000000L / sampleRate);
                break;
            // audio track that will contain pcm data
            case MediaFormat.MIMETYPE_AUDIO_RAW:
                bufferDurationUs =
                        (bufferSizeInFrames * 1000000L / sampleRate);
                break;
            // ????
            default:
            case MediaFormat.MIMETYPE_AUDIO_AAC:
                Log.w(TAG, String.format("AudioUtils: mime-type:%s not supported",
                        mimeType));
                bufferDurationUs = 0;
                break;
        }
        latencyUs = audioTrackLatency - bufferDurationUs;

        // On AMLOGIC, today (7 Fev 2017), there is no way to get latency from audiotrack
        // Indeed, AudioTrack.getLatency()
        // - is ok for audio/raw
        // - is ko for audio/ac3 and audio/eac3
        // With measurements :
        // - for audio/ac3, latency is 0ms up to 6 frames, and after frames should be taken
        //   into account
        // - for audio/eac3, latency is 40ms up to 6 frames, and after frames should be taken
        //   into account

        switch (mimeType) {
            // audio track that will contain encoded data
            case MediaFormat.MIMETYPE_AUDIO_AC3:
                Log.i(TAG, String.format("override ac3 latency %d => 0",
                        latencyUs));
                latencyUs = 0;
                break;
            case MediaFormat.MIMETYPE_AUDIO_EAC3:
                Log.i(TAG, String.format("override eac3 latency %d => 40000",
                        latencyUs));
                latencyUs = 40000;
                break;
            // audio track that will contain pcm data
            case MediaFormat.MIMETYPE_AUDIO_RAW:
                int delayUs = 5000;
                Log.i(TAG, String.format("pcm latency %d => %d",
                        latencyUs, latencyUs + delayUs));
                latencyUs += delayUs;
                break;
            case MediaFormat.MIMETYPE_AUDIO_AAC:
            default:
                break;
        }

        // sanity check
        if (latencyUs < 0) {
            Log.i(TAG, String.format("suspicious latency found:%d, format:%s", latencyUs, format));
            latencyUs = 0;
        }
        if (latencyUs > 1000000) {
            Log.i(TAG, String.format("suspicious latency found:%d, format:%s", latencyUs, format));
            latencyUs = 1000000;
        }

        Log.i(TAG, String.format("AudioUtils: estimated audio latency %d ms for board:%s and format:%s",
                latencyUs / 1000,
                Build.BOARD, format));

        return latencyUs;
    }

    static int getChannelConfig(int channelCount) {
        switch (channelCount) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;
            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;
            case 3:
                return AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
            case 4:
                return AudioFormat.CHANNEL_OUT_QUAD;
            case 5:
                return AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
            case 6:
                return AudioFormat.CHANNEL_OUT_5POINT1;
            case 7:
                return AudioFormat.CHANNEL_OUT_5POINT1 | AudioFormat.CHANNEL_OUT_BACK_CENTER;
            case 8:
                return AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
            default:
                Log.w(TAG, "AudioUtils : can't get channel config with channel count " + channelCount);
                return AudioFormat.CHANNEL_OUT_STEREO;
        }
    }

    static String channelConfigDescription(int channelConfig) {
        switch (channelConfig) {
            case AudioFormat.CHANNEL_OUT_MONO:
                return "MONO";
            case AudioFormat.CHANNEL_OUT_STEREO:
                return "STEREO";
            case AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER:
                return "STEREO AND FRONT_CENTER";
            case AudioFormat.CHANNEL_OUT_QUAD:
                return "QUAD";
            case AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER:
                return "QUAD AND FRONT_CENTER";
            case AudioFormat.CHANNEL_OUT_5POINT1:
                return "5.1";
            case AudioFormat.CHANNEL_OUT_5POINT1 | AudioFormat.CHANNEL_OUT_BACK_CENTER:
                return "5.1 AND FRONT_CENTER";
            case AudioFormat.CHANNEL_OUT_7POINT1_SURROUND:
                return "7.1";
            default:
                return String.format("?channel_config:%x?", channelConfig);
        }
    }

    static String encodingDescription(int encoding) {
        switch (encoding) {
            case AudioFormat.ENCODING_AC3:
                return "AC3";
            case AudioFormat.ENCODING_DEFAULT:
                return "DEFAULT";
            case AudioFormat.ENCODING_DTS:
                return "DTS";
            case AudioFormat.ENCODING_DTS_HD:
                return "DTS_HD";
            case AudioFormat.ENCODING_E_AC3:
                return "E_AC3";
            case AudioFormat.ENCODING_IEC61937:
                return "IEC61937";
            case AudioFormat.ENCODING_INVALID:
                return "INVALID";
            case AudioFormat.ENCODING_PCM_8BIT:
                return "PCM_8BIT";
            case AudioFormat.ENCODING_PCM_16BIT:
                return "PCM_16BIT";
            case AudioFormat.ENCODING_PCM_FLOAT:
                return "PCM_FLOAT";
            default:
                return String.format(Locale.US, "?encoding:%d?", encoding);
        }
    }

    static String dbgFromIntArrayToString(String message, int[] values) {
        StringBuilder builder = new StringBuilder();
        builder.append(message);
        builder.append("[");
        for (int value : values) {
            builder.append(value);
            builder.append(" ");
        }
        builder.append("]");

        return builder.toString();
    }

    static String dbgFromEncodingArrayToString(String message, int[] values) {
        StringBuilder builder = new StringBuilder();
        builder.append(message);
        builder.append("[");
        for (int value : values) {
            builder.append(encodingDescription(value));
            builder.append(" ");
        }
        builder.append("]");

        return builder.toString();
    }

    static String dbgFromTypeToString(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_HDMI:
                return "HDMI";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL:
                return "SPDIF";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                return "SPEAKER";
            default:
                return String.format(Locale.US, "?type:%d?", type);
        }
    }

    static void releaseAudioTrack(AudioTrack audioTrack) {
        if (audioTrack == null)
            return;

        // On bcm platform, it is necessary to flush the audiotrack before releasing it
        // Otherwise, audiotrack will first play data from its buffer, delay the release
        // and as a consequence creation of a new AudioTrack will fail.
        try {
            audioTrack.pause();
            audioTrack.flush();
        } catch (IllegalStateException exception) {
            ASPlayerLog.i("can't flush the audiotrack which is in bad state");
        }

        audioTrack.release();
    }

    public static float dbToAmpl(float db) {
        if (db <= VOLUME_MIN_DB) {
            return 0.f;
        }
        return (float) Math.exp(db * 0.115129f); // exp( dB * ln(10) / 20 )
    }

    public static float amplToDb(float amplification) {
        if (amplification == 0) {
            return VOLUME_MIN_DB;
        }
        return (float) (20 * Math.log10(amplification));
    }

    public static float convertADMixLevelToDB(int mixLevel) {
        // mixLevel in [0, 100]
        float db = (mixLevel * 64 - 32 * 100) / 100; // [0, 100] mapping to [-32, 32]
        return db;
    }

    static int setAudioPresentationId(AudioTrack audioTrack, int presentationId, int programId, String tag) {
        if (audioTrack == null) {
            ASPlayerLog.e("%s setAudioPresentationId failed, AudioTrack is null, presentationId: %d," +
                    " programId: %d", tag, presentationId, programId);
            return ErrorCode.ERROR_INVALID_OBJECT;
        }

        try {
            AudioPresentation.Builder builder = new AudioPresentation.Builder(presentationId);
            if (programId != UNKNOWN_AUDIO_PROGRAM_ID) {
                builder.setProgramId(programId);
            }

            AudioPresentation presentation = builder.build();
            int result = audioTrack.setPresentation(presentation);
            if (result == AudioTrack.SUCCESS) {
                ASPlayerLog.i("%s setAudioPresentationId success, presentationId: %d, programId: %d",
                        tag, presentationId, programId);
                return ErrorCode.SUCCESS;
            } else {
                ASPlayerLog.e("%s setAudioPresentationId failed, result: %d, presentationId: %d," +
                                " programId: %d", tag, result, presentationId, programId);
                if (result == AudioTrack.ERROR_BAD_VALUE) {
                    return ErrorCode.ERROR_INVALID_PARAMS;
                } else if (result == AudioTrack.ERROR_INVALID_OPERATION) {
                    return ErrorCode.ERROR_INVALID_OPERATION;
                } else {
                    return ErrorCode.ERROR_UNKNOWN;
                }
            }
        } catch (Exception e) {
            ASPlayerLog.e("%s setAudioPresentationId failed, error: %s, presentationId: %d, programId: %d",
                    tag, (e != null ? e.getMessage() : ""), presentationId, programId);
            ASPlayerLog.e("%s setAudioPresentationId failed, error: %s", tag, Log.getStackTraceString(e));
        }

        return ErrorCode.ERROR_UNKNOWN;
    }

    static int setAudioPresentation(AudioTrack audioTrack, AudioPresentation presentation, String tag) {
        if (presentation == null) {
            ASPlayerLog.e("%s setAudioPresentation failed, audioPresentation is null", tag);
            return ErrorCode.ERROR_INVALID_PARAMS;
        }
        if (audioTrack == null) {
            ASPlayerLog.e("%s setAudioPresentation failed, AudioTrack is null, presentationId: %d," +
                    " programId: %d", tag, presentation.getPresentationId(), presentation.getProgramId());
            return ErrorCode.ERROR_INVALID_OBJECT;
        }

        try {
            int result = audioTrack.setPresentation(presentation);
            if (result == AudioTrack.SUCCESS) {
                ASPlayerLog.i("%s setAudioPresentation success, presentationId: %d, programId: %d",
                        tag, presentation.getPresentationId(), presentation.getProgramId());
                return ErrorCode.SUCCESS;
            } else {
                ASPlayerLog.e("%s setAudioPresentation failed, result: %d, presentationId: %d," +
                        " programId: %d",
                        tag, result, presentation.getPresentationId(), presentation.getProgramId());
                if (result == AudioTrack.ERROR_BAD_VALUE) {
                    return ErrorCode.ERROR_INVALID_PARAMS;
                } else if (result == AudioTrack.ERROR_INVALID_OPERATION) {
                    return ErrorCode.ERROR_INVALID_OPERATION;
                } else {
                    return ErrorCode.ERROR_UNKNOWN;
                }
            }
        } catch (Exception e) {
            ASPlayerLog.e("%s setAudioPresentation failed, error: %s, presentationId: %d, programId: %d",
                    tag, (e != null ? e.getMessage() : ""),
                    presentation.getPresentationId(), presentation.getProgramId());
            ASPlayerLog.e("%s setAudioPresentation failed, error: %s", tag, Log.getStackTraceString(e));
        }

        return ErrorCode.ERROR_UNKNOWN;
    }

    static int getAudioPresentationId(String tag) {
        Map<String, String> parameters = getParametersFromAudioManager(CMD_GET_AUDIO_PRESENTATION_ID, tag);
        if (parameters == null || parameters.isEmpty() || !parameters.containsKey(CMD_GET_AUDIO_PRESENTATION_ID)) {
            ASPlayerLog.e("%s getAudioPresentationId failed", tag);
            return UNKNOWN_AUDIO_PRESENTATION_ID;
        }

        int presentationId = UNKNOWN_AUDIO_PRESENTATION_ID;

        try {
            String presentationIdStr = parameters.get(CMD_GET_AUDIO_PRESENTATION_ID);
            presentationId = Integer.parseInt(presentationIdStr);
        } catch (Exception e) {
            ASPlayerLog.e("%s getAudioPresentationId error: %s, \n%s",
                    tag, (e != null ? e.getMessage() : ""), Log.getStackTraceString(e));
        }

        return presentationId;
    }

    protected static <T> int setParameterToAudioManager(String key, T value, String tag) {
        if (TextUtils.isEmpty(key)) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        Context context = BaseAppContext.getAppContext();
        if (context == null) {
            ASPlayerLog.e("%s setParameterToAudioManager failed, failed to get context", tag);
            return ErrorCode.ERROR_INVALID_OBJECT;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            ASPlayerLog.e("%s setParameterToAudioManager failed, failed to get AudioManager", tag);
            return ErrorCode.ERROR_INVALID_OBJECT;
        }

        String parameters = key + "=" + value;

        ASPlayerLog.i("%s setParametersToAudioManager, parameter: %s", tag, parameters);

        audioManager.setParameters(parameters);

        return ErrorCode.SUCCESS;
    }

    protected static int setParametersToAudioManager(Map<String, Object> parameters, String tag) {
        if (parameters == null || parameters.isEmpty()) {
            return ErrorCode.ERROR_INVALID_PARAMS;
        }

        Context context = BaseAppContext.getAppContext();
        if (context == null) {
            ASPlayerLog.e("%s setParametersToAudioManager failed, failed to get context", tag);
            return ErrorCode.ERROR_INVALID_OBJECT;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            ASPlayerLog.e("%s setParametersToAudioManager failed, failed to get AudioManager", tag);
            return ErrorCode.ERROR_INVALID_OBJECT;
        }

        StringBuilder sb = new StringBuilder();
        for (String key : parameters.keySet()) {
            if (TextUtils.isEmpty(key)) {
                continue;
            }

            Object value = parameters.get(key);
            if (value == null) {
                continue;
            }

            sb.append(key);
            sb.append("=");

            if (value instanceof Integer) {
                sb.append(((Integer) value).intValue());
            } else if (value instanceof Long) {
                sb.append(((Long) value).longValue());
            } else if (value instanceof Boolean) {
                boolean boolValue = ((Boolean) value).booleanValue();
                sb.append(boolValue ? 1 : 0);
            } else if (value instanceof Float) {
                sb.append(((Float) value).floatValue());
            } else if (value instanceof Double) {
                sb.append(((Double) value).doubleValue());
            } else {
                sb.append(value);
            }

            sb.append(";");
        }

        String parameterStr = sb.toString();
        ASPlayerLog.i("%s setParametersToAudioManager, parameters: %s", tag, parameterStr);

        audioManager.setParameters(parameterStr);

        return ErrorCode.SUCCESS;
    }

    protected static Map<String, String> getParametersFromAudioManager(String keys, String tag) {
        if (TextUtils.isEmpty(keys)) {
            return null;
        }

        Context context = BaseAppContext.getAppContext();
        if (context == null) {
            ASPlayerLog.e("%s getParametersFromAudioManager failed, failed to get context", tag);
            return null;
        }

        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            ASPlayerLog.e("%s getParametersFromAudioManager failed, failed to get AudioManager", tag);
            return null;
        }

        String resultStr = null;

        try {
            resultStr = audioManager.getParameters(keys);
        } catch (Exception e) {
            ASPlayerLog.e("%s getParametersFromAudioManager failed, error: %s,\n%s",
                    tag, (e != null ? e.getMessage() : ""), Log.getStackTraceString(e));
        }

        ASPlayerLog.i("%s getParametersFromAudioManager, keys: %s, parameters: %s",
                tag, keys, resultStr);

        if (TextUtils.isEmpty(resultStr)) {
            ASPlayerLog.e("% getParametersFromAudioManager failed, result is empty", tag);
            return null;
        }

        String[] kvs = resultStr.split(";");
        if (kvs == null) {
            ASPlayerLog.e("% getParametersFromAudioManager failed, no parameters returned", tag);
            return null;
        }

        Map<String, String> result = new HashMap<>();

        Arrays.stream(kvs).forEach(s -> {
            String[] kv = s.split("=");
            if (kv != null && kv.length == 2) {
                result.put(kv[0], kv[1]);
            }
        });

        return result;
    }
}
