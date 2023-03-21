/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.live;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.amlogic.asplayer.demo.Constant;
import com.amlogic.asplayer.demo.R;


public class LiveTvPlayerSettingActivity extends Activity {

    private static final String TAG = Constant.LOG_TAG;

    private EditText mEditFrequency;
    private EditText mEditVideoPid;
    private EditText mEditVideoMimeType;
    private EditText mEditAudioPid;
    private EditText mEditAudioMimeType;

    private Button mBtnStartPlay;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_live_tv_player_setting);

        initViews();
    }

    private void initViews() {
        mEditFrequency = findViewById(R.id.edit_frequency);
        mEditFrequency.setHint(String.valueOf(Constant.DEFAULT_DVBT_FREQUENCY));

        mEditVideoPid = findViewById(R.id.edit_video_pid);
        mEditVideoPid.setHint(String.valueOf(Constant.DEFAULT_VIDEO_PID));
        mEditVideoMimeType = findViewById(R.id.edit_video_mimetype);
        mEditVideoMimeType.setHint(Constant.DEFAULT_VIDEO_MIMETYPE);

        mEditAudioPid = findViewById(R.id.edit_audio_pid);
        mEditAudioPid.setHint(String.valueOf(Constant.DEFAULT_AUDIO_PID));
        mEditAudioMimeType = findViewById(R.id.edit_audio_mimetype);
        mEditAudioMimeType.setHint(Constant.DEFAULT_AUDIO_MIMETYPE);

        mBtnStartPlay = findViewById(R.id.btn_fullscreen_play);
        mBtnStartPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoVideoPlayActivity();
            }
        });

        mBtnStartPlay.requestFocus();
    }

    private String getInputText(EditText editText) {
        String str = editText.getText().toString().trim();
        if (TextUtils.isEmpty(str)) {
            str = editText.getHint().toString().trim();
        }
        return str;
    }

    private int getInputNumber(EditText editText, int defaultValue) {
        int number = defaultValue;
        try {
            String str = editText.getText().toString().trim();
            if (TextUtils.isEmpty(str)) {
                str = editText.getHint().toString().trim();
            }
            number = Integer.parseInt(str);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return number;
    }

    private void gotoVideoPlayActivity() {
        int frequency = getInputNumber(mEditFrequency, 0);

        if (frequency <= 0) {
            Toast.makeText(this, "请输入DVB-T频率", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(LiveTvPlayerSettingActivity.this, LiveTvPlayerActivity.class);

        // program info bundle
        Bundle programBundle = new Bundle();
        int videoPid = getInputNumber(mEditVideoPid, 0);
        programBundle.putInt(Constant.EXTRA_VIDEO_PID, videoPid);
        String videoMimeType = getInputText(mEditVideoMimeType);
        programBundle.putString(Constant.EXTRA_VIDEO_MIMETYPE, videoMimeType);
        int audioPid = getInputNumber(mEditAudioPid, 0);
        programBundle.putInt(Constant.EXTRA_AUDIO_PID, audioPid);
        String audioMimeType = getInputText(mEditAudioMimeType);
        programBundle.putString(Constant.EXTRA_AUDIO_MIMETYPE, audioMimeType);
        intent.putExtra(Constant.EXTRA_PROGRAM_BUNDLE, programBundle);

        // DVB-T info bundle
        Bundle dvbtBundle = new Bundle();
        dvbtBundle.putInt(Constant.EXTRA_FRONTEND_FREQUENCY, frequency * 1000000);
        intent.putExtra(Constant.EXTRA_FRONTEND_BUNDLE, dvbtBundle);

        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
