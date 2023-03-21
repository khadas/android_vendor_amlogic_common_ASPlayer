package com.amlogic.asplayer.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.Handler;

import java.util.Arrays;

class AudioCaps {

    interface Listener {
        void onAudioCapabilitiesChanged(AudioCaps audioCapabilities);
    }

    private class HdmiAudioPlugBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isInitialStickyBroadcast()) {
                updateHdmiCapabilities(intent);
            }
        }
    }

    private Listener mListener;
    private AudioManager mAudioManager;

    private Context mContext;
    private HdmiAudioPlugBroadcastReceiver mHdmiReceiver;
    private int[] mHdmiSupportedEncodings;
    private int[] mHdmiSupportedSampleRates;
    private int mHdmiMaxChannelCount;
    private boolean mHdmiPlugged;

    private int[] mBluetoothSupportedEncodings;
    private int[] mBluetoothSupportedSampleRates;
    private int mBluetoothMaxChannelCount;
    private boolean mBluetoothDeviceReady;

    private AudioDeviceCallback mAudioDeviceCallback;

    AudioCaps(Context context, Handler handler) {
        mContext = context;

        mAudioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        mAudioDeviceCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                updateBluetoothAudioDevice();
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                updateBluetoothAudioDevice();
            }
        };
        mAudioManager.registerAudioDeviceCallback(mAudioDeviceCallback, handler);

        mHdmiReceiver = new HdmiAudioPlugBroadcastReceiver();
        Intent stickyIntent =
                mContext.registerReceiver(mHdmiReceiver,
                        new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG), null,
                        handler);

        updateHdmiCapabilities(stickyIntent);
        updateBluetoothAudioDevice();
    }

    void release() {
        mAudioManager.unregisterAudioDeviceCallback(mAudioDeviceCallback);
        mContext.unregisterReceiver(mHdmiReceiver);
    }

    void setListener(Listener listener) {
        mListener = listener;
    }

    private int getMaxChannelCount(int[] channelCounts) {
        int maxChannelCount = 0;
        for (int channelCount : channelCounts) {
            maxChannelCount = Math.max(maxChannelCount, channelCount);
        }
        return maxChannelCount;
    }

    private void updateHdmiCapabilities(Intent intent) {
        int[] newEncodings;
        int maxChannelCount;
        if (intent == null) {
            newEncodings = new int[]{AudioFormat.ENCODING_PCM_16BIT};
            maxChannelCount = 2;
            mHdmiPlugged = false;
        } else {
            newEncodings = intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS);
            maxChannelCount = intent.getIntExtra(AudioManager.EXTRA_MAX_CHANNEL_COUNT, 0);
            mHdmiPlugged = (intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) == 1);
        }

        if (maxChannelCount != mHdmiMaxChannelCount ||
                !Arrays.equals(newEncodings, mHdmiSupportedEncodings)) {
            mHdmiMaxChannelCount = maxChannelCount;
            mHdmiSupportedEncodings = newEncodings;
            AudioDeviceInfo deviceInfo = getFirstAudioDeviceInfo(AudioDeviceInfo.TYPE_HDMI);
            if (deviceInfo != null)
                mHdmiSupportedSampleRates = deviceInfo.getSampleRates();
            else
                mHdmiSupportedSampleRates = null;
            if (mListener != null)
                mListener.onAudioCapabilitiesChanged(this);
        }
    }

    private void updateBluetoothAudioDevice() {
        mBluetoothMaxChannelCount = 0;
        mBluetoothSupportedEncodings = null;
        mBluetoothSupportedSampleRates = null;
        boolean bluetoothDeviceWasReady = mBluetoothDeviceReady;
        mBluetoothDeviceReady = false;

        AudioDeviceInfo bluetoothDeviceInfo =
                getFirstAudioDeviceInfo(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
        if (bluetoothDeviceInfo != null) {
            mBluetoothSupportedEncodings = bluetoothDeviceInfo.getEncodings();
            mBluetoothMaxChannelCount = getMaxChannelCount(bluetoothDeviceInfo.getChannelCounts());
            mBluetoothSupportedSampleRates = bluetoothDeviceInfo.getSampleRates();
            mBluetoothDeviceReady = true;
        }

        if (bluetoothDeviceWasReady != mBluetoothDeviceReady) {
            if (mListener != null)
                mListener.onAudioCapabilitiesChanged(this);
        }
    }

    private AudioDeviceInfo getFirstAudioDeviceInfo(int type) {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo deviceInfo : devices) {
            if (deviceInfo.getType() == type)
                return deviceInfo;
        }
        return null;
    }

    boolean isEncodingSupported(int encoding) {
        if (mHdmiSupportedEncodings == null && mBluetoothSupportedEncodings == null)
            return false;

        if (mBluetoothSupportedEncodings != null) {
            for (int supportedEncoding : mBluetoothSupportedEncodings) {
                if (supportedEncoding == encoding)
                    return true;
            }
            return false;
        } else {
            for (int supportedEncoding : mHdmiSupportedEncodings) {
                if (supportedEncoding == encoding)
                    return true;
            }
            return false;
        }
    }

    boolean isHdmiPlugged() {
        return mHdmiPlugged;
    }

    int getHdmiMaxChannelCount() {
        return mHdmiMaxChannelCount;
    }

    int[] getHdmiSupportedSampleRates() {
        return mHdmiSupportedSampleRates;
    }

    boolean isBluetoothDeviceReady() {
        return mBluetoothDeviceReady;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (mBluetoothDeviceReady) {
            builder.append("BT plugged");
            builder.append(" max_channels:");
            builder.append(mBluetoothMaxChannelCount);
            if (mBluetoothSupportedEncodings != null)
                builder.append(AudioUtils.dbgFromEncodingArrayToString(" encodings", mBluetoothSupportedEncodings));
            else
                builder.append(" no_encoding");
            if (mBluetoothSupportedSampleRates != null)
                builder.append(AudioUtils.dbgFromIntArrayToString("sample_rates", mBluetoothSupportedSampleRates));
            else
                builder.append(" no_sample_rates");
            if (mHdmiPlugged)
                builder.append("; HDMI plugged");
            else
                builder.append("; HDMI unplugged ");
        } else {
            if (mHdmiPlugged)
                builder.append("HDMI");
            else
                builder.append("HDMI unplugged ");
            builder.append(" max_channels:");
            builder.append(mHdmiMaxChannelCount);
            if (mHdmiSupportedEncodings != null)
                builder.append(AudioUtils.dbgFromEncodingArrayToString(" encodings", mHdmiSupportedEncodings));
            else
                builder.append(" no_encoding");
            if (mHdmiSupportedSampleRates != null)
                builder.append(AudioUtils.dbgFromIntArrayToString("sample_rates", mHdmiSupportedSampleRates));
            else
                builder.append(" no_sample_rates");
        }

        return builder.toString();
    }
}
