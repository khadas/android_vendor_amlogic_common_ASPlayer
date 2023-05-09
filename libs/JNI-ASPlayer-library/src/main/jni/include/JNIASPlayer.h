/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_H
#define JNI_ASPLAYER_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef uint8_t         bool_t;
/*Call back event type*/
typedef enum {
    JNI_ASPLAYER_EVENT_TYPE_PTS = 0,        // Pts in for some stream
    JNI_ASPLAYER_EVENT_TYPE_DTV_SUBTITLE,   // External subtitle of dtv
    JNI_ASPLAYER_EVENT_TYPE_USERDATA_AFD,   // User data (afd)
    JNI_ASPLAYER_EVENT_TYPE_USERDATA_CC,    // User data (cc)
    JNI_ASPLAYER_EVENT_TYPE_VIDEO_CHANGED,  // Video format changed
    JNI_ASPLAYER_EVENT_TYPE_AUDIO_CHANGED,  // Audio format changed
    JNI_ASPLAYER_EVENT_TYPE_DATA_LOSS,      // Demod data loss
    JNI_ASPLAYER_EVENT_TYPE_DATA_RESUME,    // Demod data resume
    JNI_ASPLAYER_EVENT_TYPE_SCRAMBLING,     // Scrambling status changed
    JNI_ASPLAYER_EVENT_TYPE_FIRST_FRAME,     // First video frame showed
    JNI_ASPLAYER_EVENT_TYPE_STREAM_MODE_EOF, //Endof stream mode
    JNI_ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO, //The video decoder outputs the first frame
    JNI_ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO, //The audio decoder outputs the first frame
    JNI_ASPLAYER_EVENT_TYPE_AV_SYNC_DONE,     //Av sync done
    JNI_ASPLAYER_EVENT_TYPE_INPUT_VIDEO_BUFFER_DONE,  // Input video buffer done
    JNI_ASPLAYER_EVENT_TYPE_INPUT_AUDIO_BUFFER_DONE,  // Input audio buffer done
    JNI_ASPLAYER_EVENT_TYPE_DECODE_FRAME_ERROR_COUNT,  // The video decoder frame error count
    JNI_ASPLAYER_EVENT_TYPE_VIDEO_OVERFLOW, //Video amstream buffer overflow
    JNI_ASPLAYER_EVENT_TYPE_VIDEO_UNDERFLOW, //Video amstream buffer underflow
    JNI_ASPLAYER_EVENT_TYPE_AUDIO_OVERFLOW, //Audio amstream buffer overflow
    JNI_ASPLAYER_EVENT_TYPE_AUDIO_UNDERFLOW, //Audio amstream buffer underflow
    JNI_ASPLAYER_EVENT_TYPE_VIDEO_INVALID_TIMESTAMP, //Video invalid timestamp
    JNI_ASPLAYER_EVENT_TYPE_VIDEO_INVALID_DATA, //Video invalid data
    JNI_ASPLAYER_EVENT_TYPE_AUDIO_INVALID_TIMESTAMP, //Audio invalid timestamp
    JNI_ASPLAYER_EVENT_TYPE_AUDIO_INVALID_DATA, //Audio invalid data
    JNI_ASPLAYER_EVENT_TYPE_DECODE_VIDEO_UNSUPPORT, // Video is not supported
    JNI_ASPLAYER_EVENT_TYPE_PREEMPTED  // Instance was preempted, apk need release this instance
} jni_asplayer_event_type;


typedef enum {
    JNI_ASPLAYER_KEY_AUDIO_PRESENTATION_ID = 0,
    JNI_ASPLAYER_KEY_VIDEO_SECLEVEL,
    JNI_ASPLAYER_KEY_SET_AUDIO_PATCH_MANAGE_MODE,
    JNI_ASPLAYER_KEY_AUDIO_SECLEVEL,
    JNI_ASPLAYER_KEY_SET_SPDIF_STATUS,
    JNI_ASPLAYER_KEY_SET_VIDEO_RECOVERY_MODE,
    JNI_ASPLAYER_KEY_SET_OSD,
    JNI_ASPLAYER_KEY_SET_LOGGER_LEVEL,
    JNI_ASPLAYER_KEY_SET_WMA_DESCR,
    JNI_ASPLAYER_KEY_SET_ES_AUDIO_EXTRA_PARAM,
    JNI_ASPLAYER_KEY_SET_STREAM_EOF,
    JNI_ASPLAYER_KEY_BOOTPLAY_MODE,
    JNI_ASPLAYER_KEY_ENABLE_VFRAME_COUNTER,
    JNI_ASPLAYER_KEY_SET_AUDIO_LANG,
} jni_asplayer_parameter;

typedef enum
{
    AUDIO_PATCH_MANAGE_AUTO = -1,
    AUDIO_PATCH_MANAGE_FORCE_DISABLE,
    AUDIO_PATCH_MANAGE_FORCE_ENABLE,
} jni_asplayer_audio_patch_manage_mode;

typedef enum {
    JNI_ASPLAYER_KEY_VIDEO_STATE = 0,
} jni_asplayer_state_type;

typedef enum {
    JNI_ASPLAYER_KEY_SPDIF_MODE_NONE  = 0,
    JNI_ASPLAYER_KEY_SPDIF_MODE_NEVER = 1,
    JNI_ASPLAYER_KEY_SPDIF_MODE_ONCE  = 2,
} jni_asplayer_spdif_mode;

typedef enum {
    JNI_ASPLAYER_AV_INFO  = 0,              // Get audio and video information
    JNI_ASPLAYER_AUDIO_INFO = 1,            // Get audio information only
    JNI_ASPLAYER_VIDEO_INFO  = 2,           // Get video information only
    JNI_ASPLAYER_VFRAME_COUNTER_INFO  = 3,  // Get video frame counter info only
} jni_asplayer_av_info_state;

typedef enum {
    JNI_ASPLAYER_EXTENDED_BOOTPLAY_MODE = 0,        //BootPlay uses videotunnel and software audio decoder lib
} jni_asplayer_extended_setup;

typedef struct {
    uint8_t *data;      // Call to provide buffer pointer
    size_t data_len;    // The length of the buffer
    size_t actual_len;  // Copy the length of the actual json
    jni_asplayer_av_info_state av_flag;  // Information acquisition flags for audio and video
} jni_asplayer_state_t;


/*Callback event mask*/
#define JNI_ASPLAYER_EVENT_TYPE_PTS_MASK            (1 << JNI_ASPLAYER_EVENT_TYPE_PTS)
#define JNI_ASPLAYER_EVENT_TYPE_DTV_SUBTITLE_MASK   (1 << JNI_ASPLAYER_EVENT_TYPE_DTV_SUBTITLE)
#define JNI_ASPLAYER_EVENT_TYPE_USERDATA_AFD_MASK   (1 << JNI_ASPLAYER_EVENT_TYPE_USERDATA_AFD)
#define JNI_ASPLAYER_EVENT_TYPE_USERDATA_CC_MASK    (1 << JNI_ASPLAYER_EVENT_TYPE_USERDATA_CC)
#define JNI_ASPLAYER_EVENT_TYPE_VIDEO_CHANGED_MASK  (1 << JNI_ASPLAYER_EVENT_TYPE_VIDEO_CHANGED)
#define JNI_ASPLAYER_EVENT_TYPE_AUDIO_CHANGED_MASK  (1 << JNI_ASPLAYER_EVENT_TYPE_AUDIO_CHANGED)
#define JNI_ASPLAYER_EVENT_TYPE_DATA_LOSS_MASK      (1 << JNI_ASPLAYER_EVENT_TYPE_DATA_LOSS)
#define JNI_ASPLAYER_EVENT_TYPE_DATA_RESUME_MASK    (1 << JNI_ASPLAYER_EVENT_TYPE_DATA_RESUME)
#define JNI_ASPLAYER_EVENT_TYPE_SCRAMBLING_MASK     (1 << JNI_ASPLAYER_EVENT_TYPE_SCRAMBLING)
#define JNI_ASPLAYER_EVENT_TYPE_FIRST_FRAME_MASK    (1 << JNI_ASPLAYER_EVENT_TYPE_FIRST_FRAME)

/*Secure level which should be consistent with definition of dmx.h*/
#define JNI_ASPLAYER_DMX_FILTER_SEC_LEVEL1   (1 << 10)
#define JNI_ASPLAYER_DMX_FILTER_SEC_LEVEL2   (2 << 10)
#define JNI_ASPLAYER_DMX_FITLER_SEC_LEVEL3   (3 << 10)
#define JNI_ASPLAYER_DMX_FILTER_SEC_LEVEL4   (4 << 10)
#define JNI_ASPLAYER_DMX_FILTER_SEC_LEVEL5   (5 << 10)
#define JNI_ASPLAYER_DMX_FITLER_SEC_LEVEL6   (6 << 10)
#define JNI_ASPLAYER_DMX_FITLER_SEC_LEVEL7   (7 << 10)

/*JniASPlayer extended setup mask*/
#define JNI_ASPLAYER_EXTENDED_BOOTPLAY_MODE_MASK (1 << JNI_ASPLAYER_EXTENDED_BOOTPLAY_MODE)

/*Function return type*/
typedef enum {
    JNI_ASPLAYER_OK  = 0,                      // OK
    JNI_ASPLAYER_ERROR_INVALID_PARAMS = -1,    // Parameters invalid
    JNI_ASPLAYER_ERROR_INVALID_OPERATION = -2, // Operation invalid
    JNI_ASPLAYER_ERROR_INVALID_OBJECT = -3,    // Object invalid
    JNI_ASPLAYER_ERROR_RETRY = -4,             // Retry
    JNI_ASPLAYER_ERROR_BUSY = -5,              // Device busy
    JNI_ASPLAYER_ERROR_END_OF_STREAM = -6,     // End of stream
    JNI_ASPLAYER_ERROR_IO            = -7,     // Io error
    JNI_ASPLAYER_ERROR_WOULD_BLOCK   = -8,     // Blocking error
    JNI_ASPLAYER_ERROR_MAX = -254
} jni_asplayer_result;

/** Playback mode */
typedef enum
{
    PLAYBACK_MODE_PASSTHROUGH   = 0,    // Passthrough mode
    PLAYBACK_MODE_ES_MODE       = 1,    // ES mode
} jni_asplayer_playback_mode;

/*Data input source type*/
typedef enum
{
    TS_DEMOD = 0,                          // TS Data input from demod
    TS_MEMORY = 1,                         // TS Data input from memory
    ES_MEMORY = 2,                         // ES Data input from memory
} jni_asplayer_input_source_type;

/*Input buffer type*/
typedef enum {
    TS_INPUT_BUFFER_TYPE_NORMAL = 0,       // Input buffer is normal buffer
    TS_INPUT_BUFFER_TYPE_SECURE = 1,       // Input buffer is secure buffer
    TS_INPUT_BUFFER_TYPE_TVP = 2           // Input buffer is normal but tvp enabled
} jni_asplayer_input_buffer_type;

/*Ts stream type*/
typedef enum {
    TS_STREAM_VIDEO = 0,                   // Video
    TS_STREAM_AUDIO = 1,                   // Audio
    TS_STREAM_AD = 2,                      // Audio description
    TS_STREAM_SUB = 3,                     // Subtitle
} jni_asplayer_stream_type;

/*Ts media time type*/
typedef enum {
    TS_MEDIA_TIME_VIDEO = 0,                //Video
    TS_MEDIA_TIME_AUDIO = 1,                //Audio
    TS_MEDIA_TIME_PCR   = 2,                //PCR
    TS_MEDIA_TIME_STC   = 3,                //System time clock
    TS_MEDIA_TIME_MAX,
} jni_asplayer_media_time_type;

/*Ts time type*/
typedef enum {
    TS_UNIT_MS = 0,
    TS_UNIT_US,
    TS_UNIT_PTS,
    TS_UNIT_MAX,
} jni_asplayer_time_unit;

/*Avsync mode*/
typedef enum {
    TS_SYNC_VMASTER = 0,                   // Video Master
    TS_SYNC_AMASTER = 1,                   // Audio Master
    TS_SYNC_PCRMASTER = 2,                 // PCR Master
    TS_SYNC_NOSYNC = 3                     // Free run
} jni_asplayer_avsync_mode;

/*Player working mode*/
typedef enum {
    AS_PLAYER_MODE_NORMAL = 0,             // Normal mode
    AS_PLAYER_MODE_CACHING_ONLY = 1,       // Only caching data, do not decode. Used in FCC
    AS_PLAYER_MODE_DECODE_ONLY = 2         // Decode data but do not output
} jni_asplayer_work_mode;

/*Audio stereo output mode*/
typedef enum {
    AV_AUDIO_STEREO = 0,                   // Stereo mode
    AV_AUDIO_LEFT = 1,                     // Output left channel
    AV_AUDIO_RIGHT = 2,                    // Output right channel
    AV_AUDIO_SWAP = 3,                     // Swap left and right channels
    AV_AUDIO_LRMIX = 4                     // Mix left and right channels
} jni_asplayer_audio_stereo_mode;

/*Audio Output mode*/
typedef enum {
    AV_AUDIO_OUT_PCM = 0,                  // PCM out
    AV_AUDIO_OUT_PASSTHROUGH = 1,          // Passthrough out
    AV_AUDIO_OUT_AUTO = 2,                 // Auto
} jni_asplayer_audio_out_mode;

/*Video decoder trick mode*/
typedef enum {
    AV_VIDEO_TRICK_MODE_NONE = 0,          // Disable trick mode
    AV_VIDEO_TRICK_MODE_PAUSE = 1,         // Pause the video decoder
    AV_VIDEO_TRICK_MODE_PAUSE_NEXT = 2,    // Pause the video decoder when a new frame displayed
    AV_VIDEO_TRICK_MODE_IONLY = 3          // Decode and out I frame only
} jni_asplayer_video_trick_mode;

/*Video display match mode*/
typedef enum {
    AV_VIDEO_MATCH_MODE_NONE = 0,          // Keep original
    AV_VIDEO_MATCH_MODE_FULLSCREEN = 1,    // Stretch the video to the full window
    AV_VIDEO_MATCH_MODE_LETTER_BOX = 2,    // Letter box match mode
    AV_VIDEO_MATCH_MODE_PAN_SCAN = 3,      // Pan scan match mode
    AV_VIDEO_MATCH_MODE_COMBINED = 4,      // Combined pan scan and letter box
    AV_VIDEO_MATCH_MODE_WIDTHFULL = 5,     // Stretch the video width to the full window
    AV_VIDEO_MATCH_MODE_HEIGHTFULL = 6,      // Stretch the video height to the full window
    AV_VIDEO_WIDEOPTION_4_3_LETTER_BOX = 7,
    AV_VIDEO_WIDEOPTION_4_3_PAN_SCAN = 8,
    AV_VIDEO_WIDEOPTION_4_3_COMBINED = 9,
    AV_VIDEO_WIDEOPTION_16_9_IGNORE = 10,
    AV_VIDEO_WIDEOPTION_16_9_LETTER_BOX = 11,
    AV_VIDEO_WIDEOPTION_16_9_PAN_SCAN = 12,
    AV_VIDEO_WIDEOPTION_16_9_COMBINED = 13,
    AV_VIDEO_WIDEOPTION_CUSTOM = 14
} jni_asplayer_video_match_mode;

/*JniASPlayer handle*/
typedef size_t jni_asplayer_handle;

/*JniASPlayer init parameters*/
typedef struct {
    jni_asplayer_playback_mode playback_mode; // playback mode
    jni_asplayer_input_source_type source;  // Input source type
    jni_asplayer_input_buffer_type drmmode; // Input buffer type (normal, secure, tvp)
    int32_t dmx_dev_id;                    // Demux device id
    int32_t event_mask;                    // Mask the event type needed by caller
} jni_asplayer_init_params;

/*JniASPlayer input buffer type*/
typedef struct {
    jni_asplayer_input_buffer_type buf_type;// Input buffer type (secure/no secure)
    void *buf_data;                        // Input buffer addr
    int32_t offset;
    int32_t buf_size;                      // Input buffer size
} jni_asplayer_input_buffer;

/*JniASPlayer input buffer type*/
typedef struct {
    jni_asplayer_input_buffer_type buf_type;// Input buffer type (secure/no secure)
    void *buf_data;                        // Input buffer addr
    int32_t buf_size;                      // Input buffer size
    uint64_t pts;                          //Frame pts,used only for frame mode
    int32_t isvideo;
} jni_asplayer_input_frame_buffer;

/*JniASPlayer video init parameters*/
typedef struct {
    const char* mimeType;                  // Video mimeType
    int32_t width;                         // Video width
    int32_t height;                        // Video height
    int32_t pid;                           // Video pid in TS
    int32_t filterId;                      // video track filter id in Tuner
    int32_t avSyncHwId;                    // AvSyncHwId
    jobject mediaFormat;                   // Video MediaFormat
} jni_asplayer_video_params;

/*JniASPlayer audio init parameters*/
typedef struct {
    const char* mimeType;                   // Audio mimeType
    int32_t sampleRate;                     // Audio sampleRate
    int32_t channelCount;                   // Audio channel count
    int32_t pid;                            // Audio pid in ts
    int32_t filterId;                       // Audio track filter id in Tuner
    int32_t avSyncHwId;                     // AvSyncHwId
    int32_t seclevel;                       // Audio security level
    jobject mediaFormat;                    // Audio MediaFormat
} jni_asplayer_audio_params;

/*JniASPlayer stream buffer status*/
typedef struct {
    int32_t size;                          // Buffer size
    int32_t data_len;                      // The length of data in buffer
    int32_t free_len;                      // The length of free in buffer
} jni_asplayer_buffer_stat;

/*Video basic information*/
typedef struct {
    uint32_t width;                        // Video frame width
    uint32_t height;                       // Video frame height
    uint32_t framerate;                    // Video frame rate
    uint32_t bitrate;                      // Video bitrate
    uint64_t ratio64;                      // Video aspect ratio
} jni_asplayer_video_info;

/*Video qos information*/
typedef struct {
    uint32_t num;
    uint32_t type;
    uint32_t size;
    uint32_t pts;
    uint32_t max_qp;
    uint32_t avg_qp;
    uint32_t min_qp;
    uint32_t max_skip;
    uint32_t avg_skip;
    uint32_t min_skip;
    uint32_t max_mv;
    uint32_t min_mv;
    uint32_t avg_mv;
    uint32_t decode_buffer;
} jni_asplayer_video_qos;

/*Video decoder real time information*/
typedef struct {
    jni_asplayer_video_qos qos;
    uint32_t  decode_time_cost;/*us*/
    uint32_t frame_width;
    uint32_t frame_height;
    uint32_t frame_rate;
    uint32_t bit_depth_luma;//Original bit_rate;
    uint32_t frame_dur;
    uint32_t bit_depth_chroma;//Original frame_data;
    uint32_t error_count;
    uint32_t status;
    uint32_t frame_count;
    uint32_t error_frame_count;
    uint32_t drop_frame_count;
    uint64_t total_data;
    uint32_t double_write_mode;//Original samp_cnt;
    uint32_t offset;
    uint32_t ratio_control;
    uint32_t vf_type;
    uint32_t signal_type;
    uint32_t pts;
    uint64_t pts_us64;
} jni_asplayer_vdec_stat;

/*Audio basic information*/
typedef struct {
    uint32_t sample_rate;                  // Audio sample rate
    uint32_t channels;                     // Audio channels
    uint32_t channel_mask;                 // Audio channel mask
    uint32_t bitrate;                      // Audio bitrate
} jni_asplayer_audio_info;

/*Audio decoder real time information*/
typedef struct {
    uint32_t frame_count;
    uint32_t error_frame_count;
    uint32_t drop_frame_count;
} jni_asplayer_adec_stat;

typedef struct {
    uint32_t frame_width;
    uint32_t frame_height;
    uint32_t frame_rate;
    uint32_t frame_aspectratio;
} jni_asplayer_video_format_t;

typedef struct {
    uint32_t sample_rate;
    uint32_t channels;
    uint32_t channel_mask;
} jni_asplayer_audio_format_t;

typedef struct {
    int32_t first_lang;
    int32_t second_lang;
} jni_asplayer_audio_lang;


typedef struct {
    jni_asplayer_stream_type stream_type;
    uint64_t  pts;
} jni_asplayer_pts_t;

typedef struct {
    uint8_t  *data;
    size_t   len;
} mpeg_user_data_t;

typedef struct {
    jni_asplayer_stream_type stream_type;
    bool_t  scramling;
} scamling_t;

typedef struct {
    uint32_t video_overflow_num;                        // Video overflow num
    uint32_t video_underflow_num;                       // Video underflow num
    uint32_t audio_overflow_num;                        // Audio overflow num
    uint32_t audio_underflow_num;                       // Audio underflow num
} av_flow_t;

/*JniASPlayer call back event*/
typedef struct {
    jni_asplayer_event_type type;           // Call back event type
    union {
        /*If type is VIDEO_CHANGED send new video basic info*/
        jni_asplayer_video_format_t video_format;
        /*If type is AUDIO_CHANGED send new video basic info*/
        jni_asplayer_audio_format_t audio_format;
        /*Audio/Video/Subtitle pts after pes parser*/
        jni_asplayer_pts_t pts;
        /*User data send cc /afd /dvb subtitle to caller*/
        mpeg_user_data_t mpeg_user_data;
        /*Scrambling status changed send scrambling info to caller*/
        scamling_t scramling;
        /*Callback audio/video input buffer ptr*/
        void* bufptr;
        /*If Audio/Video overflow/underflow count the num*/
        av_flow_t av_flow_cnt;
    } event;
}jni_asplayer_event;

/*Event callback function ptr*/
typedef void (*event_callback) (void *user_data, jni_asplayer_event *event);

/**
 * @brief:        Initialize JNI environment.
 *                Must called from Java thread.
 *                Java thread -> c/cpp(JNI) -> registerJNI()
 * @param: env    JNIEnv    JNIEnv from JNI method call
 * @return:       The JniASPlayer result.
 */
jni_asplayer_result JniASPlayer_registerJNI(JNIEnv *env);

/**
 * @brief:        Create JniASPlayer instance.
 *                Set input mode demux_id and event mask to JniASPlayer.
 * @param:        Params    Init params with input mode demux_id and event mask.
 * @param:        *pHandle  JniASPlayer handle.
 * @return:       The JniASPlayer result.
*/
jni_asplayer_result  JniASPlayer_create(jni_asplayer_init_params Params, void *tuner, jni_asplayer_handle *pHandle);

/**
 * @brief:        Get ASPlayer instance.
 * @param:        handle    JniASPlayer handle.
 * @param:        *pASPlayer ASPlayer instance.
 * @return:       The JniASPlayer result.
*/
jni_asplayer_result  JniASPlayer_getJavaASPlayer(jni_asplayer_handle handle, jobject *pASPlayer);

/**
 * @brief:        Prepare JniASPlayer instance.
 * @param:        Handle  JniASPlayer handle.
 * @return:       The JniASPlayer result.
*/
jni_asplayer_result  JniASPlayer_prepare(jni_asplayer_handle Handle);

/**
 *@brief:        Get JniASPlayer interface version information.
 *@param:        *versionM    JniASPlayer interface version.
 *@param:        *VersionL    JniASPlayer interface version.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getVersion(uint32_t *versionM,
                                          uint32_t *VersionL);

/**
 *@brief:        Get the instance number of specified JniASPlayer.
 *@param:        Handle    JniASPlayer handle.
 *@param:        *Numb     JniASPlayer instance number.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getInstansNo(jni_asplayer_handle Handle, uint32_t *Numb);

/**
 *@brief:        Get the sync instance number of specified JniASPlayer .
 *@param:        Handle    JniASPlayer handle.
 *@param:        *Numb     JniASPlayer instance number.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getSyncInstansNo(jni_asplayer_handle Handle, int32_t *Numb);

/**
 *@brief:        Register event callback to specified JniASPlayer
 *@param:        Handle    JniASPlayer handle.
 *@param:        pfunc     Event callback function ptr.
 *@param:        *param    Extra data ptr.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_registerCb(jni_asplayer_handle Handle, event_callback pfunc, void *param);

/**
 *@brief:        Get event callback to specified JniASPlayer
 *@param:        Handle      JniASPlayer handle.
 *@param:        *pfunc      ptr of Event callback function ptr.
 *@param:        *ppParam    Set the callback, with a pointer to the parameter.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getCb(jni_asplayer_handle Handle, event_callback *pfunc, void* *ppParam);

/**
 *@brief:        Release specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_release(jni_asplayer_handle handle);

/**
 *@brief:        Flush specified JniASPlayer instance.
 *@param:        Handle         JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_flush(jni_asplayer_handle handle);

/**
 *@brief:        Flush DvrPlayback of specified JniASPlayer instance.
 *@param:        Handle         JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_flushDvr(jni_asplayer_handle handle);

/**
 *@brief:        Write Frame data to specified JniASPlayer instance.
 *               It will only work when TS input's source type is TS_MEMORY.
 *@param:        Handle       JniASPlayer handle.
 *@param:        *buf         Input buffer struct (1.Buffer type:secure/no
 *                            2.secure buffer ptr 3.buffer len).
 *@param:        timeout_ms   Time out limit.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_writeFrameData(jni_asplayer_handle Handle,
                                              jni_asplayer_input_frame_buffer *buf,
                                              uint64_t timeout_ms);

/**
 *@brief:        Write data to specified JniASPlayer instance.
 *               It will only work when TS input's source type is TS_MEMORY.
 *@param:        Handle         JniASPlayer handle.
 *@param:        *buf           Input buffer struct (1.Buffer type:secure/no
 *                              2.secure buffer ptr 3.buffer len).
 *@param:        timeout_ms     Time out limit .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_writeData(jni_asplayer_handle Handle, jni_asplayer_input_buffer *buf, uint64_t timeout_ms);

/**
 *@brief:        Set work mode to specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        mode       The enum of work mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setWorkMode (jni_asplayer_handle Handle, jni_asplayer_work_mode mode);

/*AV sync*/
/**
 *@brief:        Get the playing time of specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        *time      Playing time.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getCurrentTime(jni_asplayer_handle Handle, int64_t *time);

/**
 *@brief:        Get the pts of specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        StrType    stream type.
 *@param:        *pts       pts.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getPts(jni_asplayer_handle Handle, jni_asplayer_stream_type StrType, uint64_t *pts);

/**
 *@brief:        Get the time of specified JniASPlayer instance.
 *@param:        Handle           JniASPlayer handle.
 *@param:        mediaTimeType    stream type.
 *@param:        tunit            time unit.
 *@param:        *time            pts.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getMediaTime(jni_asplayer_handle Handle, jni_asplayer_media_time_type mediaTimeType, jni_asplayer_time_unit tunit, uint64_t *time);

/**
 *@brief:        Set the tsync mode for specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        mode       The enum of avsync mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setSyncMode(jni_asplayer_handle Handle, jni_asplayer_avsync_mode mode);

/**
 *@brief:        Get the tsync mode for specified JniASPlayer instance.
 *@param:        Handle    JniASPlayer handle.
 *@param:        *mode     The avsync mode of specified JniASPlayer instance.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getSyncMode(jni_asplayer_handle Handle, jni_asplayer_avsync_mode *mode);

/**
 *@brief:        Set pcr pid to specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        pid        The pid of pcr.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setPcrPid(jni_asplayer_handle Handle, uint32_t pid);

/**
 *@brief:        Get the delay time for specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        *time      The JniASPlayer delay time.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getDelayTime(jni_asplayer_handle Handle, int64_t *time);


/*Player control interface*/
/**
 *@brief:        Start Fast play for specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        scale      Fast play speed.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startFast(jni_asplayer_handle Handle, float scale);

/**
 *@brief:        Stop Fast play for specified JniASPlayer instance.
 *@param:        Handle       JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopFast(jni_asplayer_handle Handle);

/**
 *@brief:        Set trick mode for specified JniASPlayer instance.
 *@param:        Handle        JniASPlayer handle.
 *@param:        trickmode     The enum of trick mode type
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setTrickMode(jni_asplayer_handle Handle, jni_asplayer_video_trick_mode trickmode);

/**
 *@brief:        Get buffer status for specified JniASPlayer instance.
 *@param:        Handle       JniASPlayer handle.
 *@param:        StrType      The stream type we want to check.
 *@param:        *pBufStat    The struct of buffer status.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getBufferStat(jni_asplayer_handle Handle, jni_asplayer_stream_type StrType,
                                             jni_asplayer_buffer_stat *pBufStat);

/*Video interface*/
/**
 *@brief:        Set the video display rect size for specified
 *               JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        x          The display rect x.
 *@param:        y          The display rect y.
 *@param:        width      The display rect width.
 *@param:        height     The display rect height.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setVideoWindow(jni_asplayer_handle Handle,
                                              int32_t x,int32_t y,
                                              int32_t width,int32_t height);

/*Video interface*/
/**
*@brief:        Set the video crop rect size for specified
*               JniASPlayer instance.
*@param:        Handle     JniASPlayer handle.
*@param:        left       The video crop rect left.
*@param:        top        The video crop rect top.
*@param:        right      The video crop rect right.
*@param:        bottom     The video crop rect bottom.
*@return:       The JniASPlayer result.
*/
jni_asplayer_result  JniASPlayer_setVideoCrop(jni_asplayer_handle Handle,
                                            int32_t left,
                                            int32_t top,
                                            int32_t right,
                                            int32_t bottom);

/**
 *@brief:        Set Surface ptr to specified JniASPlayer instance.
 *@param:        Handle       JniASPlayer handle.
 *@param:        *pSurface    Surface ptr
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setSurface(jni_asplayer_handle Handle, void* pSurface);

/**
 *@brief:        Show the video frame display for specified
 *               JniASPlayer instance.
 *@param:        Handle       JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_showVideo(jni_asplayer_handle Handle);

/**
 *@brief:        Hide the video frame display for specified
 *               JniASPlayer instance.
 *@param:        Handle       JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_hideVideo(jni_asplayer_handle Handle);

/**
 *@brief:        Set video display match mode for specified
                 JniASPlayer instance.
 *@param:        Handle       JniASPlayer handle.
 *@param:        MathMod      The enum of video display match mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setVideoMatchMode(jni_asplayer_handle Handle, jni_asplayer_video_match_mode MathMod);

/**
 *@brief:        Set video params need by demuxer and video decoder
 *               for specified JniASPlayer instance.
 *@param:        Handle      JniASPlayer handle.
 *@param:        *pParams    Params need by demuxer and video decoder.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setVideoParams(jni_asplayer_handle Handle, jni_asplayer_video_params *pParams);

/**
 *@brief:        Set if need keep last frame for video display
 *               for specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        blackout   If blackout for last frame.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setVideoBlackOut(jni_asplayer_handle Handle, bool_t blackout);

/**
 *@brief:        Get video basic info of specified JniASPlayer instance.
 *@param:        Handle      JniASPlayer handle.
 *@param:        *pInfo      The ptr of video basic info struct .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getVideoInfo(jni_asplayer_handle Handle, jni_asplayer_video_info *pInfo);

/**
 *@brief:        Get video decoder real time info
 *               of specified JniASPlayer instance.
 *@param:        Handle   JniASPlayer handle.
 *@param:        *pStat   The ptr of video decoder real time info struct
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getVideoStat(jni_asplayer_handle Handle, jni_asplayer_vdec_stat *pStat);

/**
 *@brief:        Start video decoding for specified JniASPlayer instance .
 *@param:        Handle      JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startVideoDecoding(jni_asplayer_handle Handle);

/**
 *@brief:        Pause video decoding for specified JniASPlayer instance .
 *@param:        Handle       JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_pauseVideoDecoding(jni_asplayer_handle Handle);

/**
 *@brief:        Resume video decoding for specified JniASPlayer instance .
 *@param:        Handle      JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_resumeVideoDecoding(jni_asplayer_handle Handle);

/**
 *@brief:        Stop video decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopVideoDecoding(jni_asplayer_handle Handle);


/*Audio interface*/
/**
 *@brief:        Set audio volume to specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@param:        volume     Volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioVolume(jni_asplayer_handle Handle, int32_t volume);

/**
 *@brief:        Get audio volume value from specified JniASPlayer instance .
 *@param:        Handle      JniASPlayer handle.
 *@param:        *volume     Volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getAudioVolume(jni_asplayer_handle Handle, int32_t *volume);

/*Audio interface*/
/**
 *@brief:        Set AD volume to specified JniASPlayer instance .
 *@param:        Handle      JniASPlayer handle.
 *@param:        volume      Volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setADVolume(jni_asplayer_handle Handle, int32_t volume);

/**
 *@brief:        Get AD volume value from specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@param:        *volume    Volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getADVolume(jni_asplayer_handle Handle, int32_t *volume);

/**
 *@brief:        Set audio stereo mode to specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@param:        Mode       Stereo mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioStereoMode(jni_asplayer_handle Handle, jni_asplayer_audio_stereo_mode Mode);

/**
 *@brief:        Get audio stereo mode to specified JniASPlayer instance .
 *@param:        Handle    JniASPlayer handle.
 *@param:        *pMode    Stereo mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getAudioStereoMode(jni_asplayer_handle Handle, jni_asplayer_audio_stereo_mode *pMode);

/**
 *@brief:        Set audio output mute to specified JniASPlayer instance .
 *@param:        Handle         JniASPlayer handle.
 *@param:        analog_mute    If analog mute or unmute .
 *@param:        digital_mute   If digital mute or unmute .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioMute(jni_asplayer_handle Handle, bool_t analog_mute, bool_t digital_mute);

/**
 *@brief:        Get audio output mute status from specified
                 JniASPlayer instance .
 *@param:        Handle            JniASPlayer handle.
 *@param:        *analog_unmute    If analog mute or unmute .
 *@param:        *digital_unmute   If digital mute or unmute .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getAudioMute(jni_asplayer_handle Handle, bool_t *analog_unmute, bool_t *digital_unmute);

/**
 *@brief:        Set audio params need by demuxer and audio decoder
 *               to specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        *pParams   Params need by demuxer and audio decoder.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioParams(jni_asplayer_handle Handle, jni_asplayer_audio_params *pParams);

/**
 *@brief:        Set audio output mode to specified JniASPlayer instance.
 *@param:        Handle   JniASPlayer handle.
 *@param:        Mode     Enum of audio output mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioOutMode(jni_asplayer_handle Handle, jni_asplayer_audio_out_mode Mode);

/**
 *@brief:        Get audio basic info of specified JniASPlayer instance.
 *@param:        Handle      JniASPlayer handle.
 *@param:        *pInfo      The ptr of audio basic info struct .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getAudioInfo(jni_asplayer_handle Handle,  jni_asplayer_audio_info *pInfo);

/**
 *@brief:        Get audio decoder real time info
 *               of specified JniASPlayer instance.
 *@param:        Handle    JniASPlayer handle.
 *@param:        *pStat    The ptr of audio decoder real time info struct
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getAudioStat(jni_asplayer_handle Handle, jni_asplayer_adec_stat *pStat);

/**
 *@brief:        Start audio decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startAudioDecoding(jni_asplayer_handle Handle);

/**
 *@brief:        Pause audio decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_pauseAudioDecoding(jni_asplayer_handle Handle);

/**
 *@brief:        Resume audio decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_resumeAudioDecoding(jni_asplayer_handle Handle);

/**
 *@brief:        Stop audio decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopAudioDecoding(jni_asplayer_handle Handle);

/**
 *@brief:        Set audio description params need by demuxer
 *               and audio decoder to specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        *pParams   Params need by demuxer and audio decoder.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setADParams(jni_asplayer_handle Handle, jni_asplayer_audio_params *pParams);

/*Audio description interface*/
/**
 *@brief:        Set audio description mix level (master vol and ad vol)
 *@param:        Handle        JniASPlayer handle.
 *@param:        master_vol    Master volume value.
 *@param:        slave_vol     Slave volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setADMixLevel(jni_asplayer_handle Handle, int32_t master_vol, int32_t slave_vol);

/**
 *@brief:        Get audio description mix level (master vol and ad vol)
 *@param:        Handle        JniASPlayer handle.
 *@param:        *master_vol   Master volume value.
 *@param:        *slave_vol    Slave volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getADMixLevel(jni_asplayer_handle Handle, int32_t *master_vol, int32_t *slave_vol);

/**
 *@brief:        Enable audio description mix with master audio
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_enableADMix(jni_asplayer_handle Handle);

/**
 *@brief:        Disable audio description mix with master audio
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_disableADMix(jni_asplayer_handle Handle);

/**
 *@brief:        Get audio description basic info of specified
 *               JniASPlayer instance.
 *@param:        Handle    JniASPlayer handle.
 *@param:        *pInfo    The ptr of audio basic info struct .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getADInfo(jni_asplayer_handle Handle, jni_asplayer_audio_info *pInfo);

/**
 *@brief:        Get audio description decoder real time info
 *               of specified JniASPlayer instance.
 *@param:        Handle    JniASPlayer handle.
 *@param:        *pStat    The ptr of audio decoder real time info struct
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getADStat(jni_asplayer_handle Handle, jni_asplayer_adec_stat *pStat);

/*Subtitle interface*/
/**
 *@brief:        Set subtitle pid for specified JniASPlayer instance .
 *@param:        Handle    JniASPlayer handle.
 *@param:        pid       The pid of subtitle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setSubPid(jni_asplayer_handle Handle, uint32_t pid);

/**
 *@brief:        get Params for specified JniASPlayer instance .
 *@param:        Handle    JniASPlayer handle.
 *@param:        type      JniASPlayer parameter type.
 *@param:        *arg      The qualified pointer returned
                           by the function.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getParams(jni_asplayer_handle Handle, jni_asplayer_parameter type, void* arg);

/**
 *@brief:        set Params for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setParams(jni_asplayer_handle Handle, jni_asplayer_parameter type, void* arg);

/**
 *@brief:        get State for specified JniASPlayer instance .
 *@param:        Handle    JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result JniASPlayer_getState(jni_asplayer_handle Handle,jni_asplayer_state_t* state);

/**
 *@brief:        Start subtitle for specified JniASPlayer instance .
 *@param:        Handle    JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startSub(jni_asplayer_handle Handle);

/**
 *@brief:        Stop subtitle for specified JniASPlayer instance .
 *@param:        Handle    JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopSub(jni_asplayer_handle Handle);

/**
 *@brief:        Get the first pts of specified JniASPlayer instance.
 *@param:        Handle    JniASPlayer handle.
 *@param:        StrType   stream type.
 *@param:        *pts      output pts.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getFirstPts(jni_asplayer_handle Handle, jni_asplayer_stream_type StrType, uint64_t *pts);


#ifdef __cplusplus
};
#endif


#endif //JNI_ASPLAYER_H
