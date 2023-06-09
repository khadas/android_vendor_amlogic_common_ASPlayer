/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.widget;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;

import androidx.annotation.Nullable;

import com.amlogic.asplayer.demo.Constant;
import com.amlogic.asplayer.demo.R;
import com.amlogic.asplayer.demo.utils.TunerHelper;
import com.amlogic.asplayer.demo.utils.TvLog;
import com.amlogic.asplayer.demo.utils.ViewUtils;

public class ProgramInputSettingView extends LinearLayout {

    private enum TunerHalVersion {
        VERSION_1_0,
        VERSION_1_1
    }

    public static class ProgramInfo {
        public int mTunerHalVersion;
        public int mVideoPid;
        public String mVideoMimeType;
        public String mVideoStreamType;

        public int mAudioPid;
        public String mAudioMimeType;
        public String mAudioStreamType;
    }

    private TunerHalVersion mVersion = TunerHalVersion.VERSION_1_0;

    private RadioButton mRdoTunerVersion_1_0;
    private RadioButton mRdoTunerVersion_1_1;

    // Video Parameters
    private EditText mEditVideoPid;
    private View mLayoutVideoMimeType;
    private EditText mEditVideoMimeType;
    private View mLayoutVideoStreamType;
    private Spinner mSpinnerVideoStreamType;

    // Audio Parameters
    private EditText mEditAudioPid;
    private View mLayoutAudioMimeType;
    private EditText mEditAudioMimeType;
    private View mLayoutAudioStreamType;
    private Spinner mSpinnerAudioStreamType;

    private String[] mVideoStreamTypes;
    private String[] mAudioStreamTypes;
    private String mVideoStreamType;
    private String mAudioStreamType;

    public ProgramInputSettingView(Context context) {
        this(context, null);
    }

    public ProgramInputSettingView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgramInputSettingView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        super.setOrientation(VERTICAL);

        LayoutInflater layoutInflater = LayoutInflater.from(context);
        layoutInflater.inflate(R.layout.layout_asplayer_setting, this, true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        initViews();
    }

    private void initViews() {
        mRdoTunerVersion_1_0 = findViewById(R.id.rdo_tuner_hal_1_0);
        mRdoTunerVersion_1_1 = findViewById(R.id.rdo_tuner_hal_1_1);

        mEditVideoPid = findViewById(R.id.edit_video_pid);
        mLayoutVideoMimeType = findViewById(R.id.layout_video_mime_type);
        mEditVideoMimeType = findViewById(R.id.edit_video_mime_type);
        mLayoutVideoStreamType = findViewById(R.id.layout_video_stream_type);
        mSpinnerVideoStreamType = findViewById(R.id.spinner_video_stream_type);

        mEditAudioPid = findViewById(R.id.edit_audio_pid);
        mLayoutAudioMimeType = findViewById(R.id.layout_audio_mime_type);
        mEditAudioMimeType = findViewById(R.id.edit_audio_mime_type);
        mLayoutAudioStreamType = findViewById(R.id.layout_audio_stream_type);
        mSpinnerAudioStreamType = findViewById(R.id.spinner_audio_stream_type);

        mRdoTunerVersion_1_0.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    selectTunerVersion(TunerHalVersion.VERSION_1_0);
                }
            }
        });

        mRdoTunerVersion_1_1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    selectTunerVersion(TunerHalVersion.VERSION_1_1);
                }
            }
        });

        mSpinnerVideoStreamType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mVideoStreamType = mVideoStreamTypes[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mVideoStreamType = "";
            }
        });

        mSpinnerAudioStreamType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mAudioStreamType = mAudioStreamTypes[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mAudioStreamType = "";
            }
        });

        initDefaultValues();

        // set default tuner hal version
        switch (mVersion) {
            case VERSION_1_1:
                mRdoTunerVersion_1_1.setChecked(true);
                break;
            case VERSION_1_0:
            default:
                mRdoTunerVersion_1_0.setChecked(true);
                break;
        }

        // check tuner version
        int tunerVersion = TunerHelper.TunerVersionChecker.getTunerVersion();
        Boolean isTunerHal1_1 = TunerHelper.TunerVersionChecker.isHigherOrEqualVersionToOrNull(
                TunerHelper.TunerVersionChecker.TUNER_VERSION_1_1);
        if (tunerVersion > 0 && isTunerHal1_1 != null) {
            if (isTunerHal1_1) {
                mRdoTunerVersion_1_0.setEnabled(false);
                mRdoTunerVersion_1_1.setEnabled(true);
            } else {
                mRdoTunerVersion_1_0.setEnabled(true);
                mRdoTunerVersion_1_1.setEnabled(false);
            }
        } else {
            TvLog.i("getTunerVersion failed, enable tuner1.0 and tuner1.1 setting");
        }
    }

    private void initDefaultValues() {
        mEditVideoPid.setHint(String.valueOf(Constant.DEFAULT_VIDEO_PID));
        mEditVideoMimeType.setHint(Constant.DEFAULT_VIDEO_MIMETYPE);
        mVideoStreamTypes = getContext().getResources().getStringArray(R.array.video_stream_type_array);
        int videoStreamTypeIndex = findStreamTypeIndex(mVideoStreamTypes, Constant.DEFAULT_VIDEO_STREAM_TYPE);
        if (videoStreamTypeIndex >= 0) {
            mSpinnerVideoStreamType.setSelection(videoStreamTypeIndex);
        }

        mEditAudioPid.setHint(String.valueOf(Constant.DEFAULT_AUDIO_PID));
        mEditAudioMimeType.setHint(Constant.DEFAULT_AUDIO_MIMETYPE);
        mAudioStreamTypes = getContext().getResources().getStringArray(R.array.audio_stream_type_array);
        int audioStreamTypeIndex = findStreamTypeIndex(mAudioStreamTypes, Constant.DEFAULT_AUDIO_STREAM_TYPE);
        if (audioStreamTypeIndex >= 0) {
            mSpinnerAudioStreamType.setSelection(audioStreamTypeIndex);
        }
    }

    private static int findStreamTypeIndex(String[] streamTypes, String targetStreamType) {
        if (streamTypes == null || streamTypes.length == 0 || TextUtils.isEmpty(targetStreamType)) {
            return -1;
        }

        int index = -1;
        for (int i = 0; i < streamTypes.length; i++) {
            if (TextUtils.equals(streamTypes[i], targetStreamType)) {
                index = i;
                break;
            }
        }
        return index;
    }

    private void selectTunerVersion(TunerHalVersion version) {
        switch (version) {
            case VERSION_1_1:
                showTunerHal11Settings();
                break;
            case VERSION_1_0:
            default:
                showTunerHal10Settings();
                break;
        }
        mVersion = version;
    }

    private void showTunerHal10Settings() {
        mLayoutVideoStreamType.setVisibility(View.GONE);
        mLayoutVideoMimeType.setVisibility(View.VISIBLE);

        mLayoutAudioStreamType.setVisibility(View.GONE);
        mLayoutAudioMimeType.setVisibility(View.VISIBLE);
    }

    private void showTunerHal11Settings() {
        mLayoutVideoMimeType.setVisibility(View.GONE);
        mLayoutVideoStreamType.setVisibility(View.VISIBLE);

        mLayoutAudioMimeType.setVisibility(View.GONE);
        mLayoutAudioStreamType.setVisibility(View.VISIBLE);
    }

    public ProgramInfo getProgramInfo() {
        ProgramInfo programInfo = new ProgramInfo();
        if (mVersion == TunerHalVersion.VERSION_1_1) {
            programInfo.mTunerHalVersion = Constant.TUNER_HAL_VERSION_1_1;
        } else {
            programInfo.mTunerHalVersion = Constant.TUNER_HAL_VERSION_1_0;
        }

        programInfo.mVideoPid = ViewUtils.getInputNumber(mEditVideoPid, 0);
        programInfo.mVideoMimeType = ViewUtils.getInputText(mEditVideoMimeType);
        programInfo.mVideoStreamType = mVideoStreamType;

        programInfo.mAudioPid = ViewUtils.getInputNumber(mEditAudioPid, 0);
        programInfo.mAudioMimeType = ViewUtils.getInputText(mEditAudioMimeType);
        programInfo.mAudioStreamType = mAudioStreamType;

        return programInfo;
    }
}