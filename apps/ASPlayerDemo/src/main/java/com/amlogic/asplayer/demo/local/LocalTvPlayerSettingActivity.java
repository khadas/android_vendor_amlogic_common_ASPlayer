/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.local;

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


public class LocalTvPlayerSettingActivity extends Activity {

    private static final String TAG = Constant.LOG_TAG;

    private EditText mEditTsPath;
    private EditText mEditVideoPid;
    private EditText mEditVideoMimeType;
    private EditText mEditAudioPid;
    private EditText mEditAudioMimeType;

    private Button mBtnStartPlay;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tv_player_setting);

        initViews();
    }

    private void initViews() {
        mEditTsPath = findViewById(R.id.edit_ts_path);
        mEditTsPath.setHint(Constant.DEFAULT_TS_PATH);

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

    private String getLocalTsPath() {
        return getInputText(mEditTsPath);
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
        String tsPath = getLocalTsPath();

        if (TextUtils.isEmpty(tsPath)) {
            Toast.makeText(this, "请输入Ts文件路径", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(LocalTvPlayerSettingActivity.this, LocalTvPlayerActivity.class);
        intent.putExtra(Constant.EXTRA_TS_PATH, tsPath);

        Bundle bundle = new Bundle();

        int videoPid = getInputNumber(mEditVideoPid, 0);
        bundle.putInt(Constant.EXTRA_VIDEO_PID, videoPid);

        String videoMimeType = getInputText(mEditVideoMimeType);
        bundle.putString(Constant.EXTRA_VIDEO_MIMETYPE, videoMimeType);

        int audioPid = getInputNumber(mEditAudioPid, 0);
        bundle.putInt(Constant.EXTRA_AUDIO_PID, audioPid);

        String audioMimeType = getInputText(mEditAudioMimeType);
        bundle.putString(Constant.EXTRA_AUDIO_MIMETYPE, audioMimeType);

        intent.putExtra(Constant.EXTRA_PROGRAM_BUNDLE, bundle);

        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
