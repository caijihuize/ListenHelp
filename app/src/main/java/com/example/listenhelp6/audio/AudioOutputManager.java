package com.example.listenhelp6.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class AudioOutputManager {
    private static final String TAG = "AudioOutputManager";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;

    private final Context context;
    private final AudioManager audioManager;
    private AudioTrack audioTrack;
    private boolean isPlaying = false;
    private int outputVolume = 80; // 默认输出音量(0-100)
    private AudioDeviceInfo selectedOutputDevice;

    public AudioOutputManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public List<AudioDeviceInfo> getAvailableOutputDevices() {
        List<AudioDeviceInfo> outputDevices = new ArrayList<>();
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                    device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                outputDevices.add(device);
            }
        }
        
        return outputDevices;
    }

    public void setOutputDevice(AudioDeviceInfo deviceInfo) {
        this.selectedOutputDevice = deviceInfo;
        restartAudioOutput();
    }

    public void setOutputVolume(int volume) {
        this.outputVolume = Math.max(0, Math.min(100, volume));
        applyVolume();
    }

    private void applyVolume() {
        if (audioTrack != null) {
            float volumeLevel = outputVolume / 100.0f;
            audioTrack.setVolume(volumeLevel);
        }
    }

    public void startAudioOutput() {
        if (isPlaying) {
            return;
        }
        
        try {
            releaseAudioTrack();

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            
            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AUDIO_FORMAT)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build();
            
            audioTrack = new AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(BUFFER_SIZE)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            
            if (selectedOutputDevice != null) {
                boolean preferredDeviceSet = audioTrack.setPreferredDevice(selectedOutputDevice);
                if (!preferredDeviceSet) {
                    Log.w(TAG, "无法设置首选输出设备: " + selectedOutputDevice.getProductName());
                    
                    // 尝试获取当前实际使用的设备并记录
                    AudioDeviceInfo currentDevice = audioTrack.getRoutedDevice();
                    if (currentDevice != null) {
                        Log.i(TAG, "当前路由到设备: " + currentDevice.getProductName());
                    }
                } else {
                    Log.d(TAG, "成功设置输出设备: " + selectedOutputDevice.getProductName());
                }
            } else {
                Log.i(TAG, "未指定输出设备，使用系统默认设备");
            }
            
            applyVolume();
            audioTrack.play();
            isPlaying = true;
            
        } catch (Exception e) {
            Log.e(TAG, "启动音频输出失败: " + e.getMessage());
            releaseAudioTrack();
        }
    }

    public void writeAudioData(short[] buffer, int size) {
        if (audioTrack != null && isPlaying) {
            try {
                audioTrack.write(buffer, 0, size);
            } catch (Exception e) {
                Log.e(TAG, "写入音频数据失败: " + e.getMessage());
            }
        }
    }

    public void stopAudioOutput() {
        isPlaying = false;
        releaseAudioTrack();
    }

    private void restartAudioOutput() {
        boolean wasPlaying = isPlaying;
        
        stopAudioOutput();
        
        if (wasPlaying) {
            startAudioOutput();
        }
    }

    private void releaseAudioTrack() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.flush();
            } catch (Exception e) {
                Log.e(TAG, "停止AudioTrack失败: " + e.getMessage());
            } finally {
                audioTrack.release();
                audioTrack = null;
            }
        }
    }

    public int getOutputVolume() {
        return outputVolume;
    }

    public int getAudioSessionId() {
        return audioTrack != null ? audioTrack.getAudioSessionId() : 0;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void release() {
        stopAudioOutput();
    }
} 