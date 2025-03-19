package com.example.listenhelp6.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioInputManager {
    private static final String TAG = "AudioInputManager";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;

    private final Context context;
    private final AudioManager audioManager;
    private AudioRecord audioRecord;
    private ExecutorService executor;
    private boolean isRecording = false;
    private int inputVolume = 80; // 默认输入音量(0-100)
    private AudioDeviceInfo selectedInputDevice;
    private AudioInputCallback callback;
    private final Handler mainHandler;

    public interface AudioInputCallback {
        void onAudioBufferReceived(short[] buffer, int readSize);
    }

    public AudioInputManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setAudioInputCallback(AudioInputCallback callback) {
        this.callback = callback;
    }

    public List<AudioDeviceInfo> getAvailableInputDevices() {
        List<AudioDeviceInfo> inputDevices = new ArrayList<>();
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC ||
                    device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                inputDevices.add(device);
            }
        }
        
        return inputDevices;
    }

    public void setInputDevice(AudioDeviceInfo deviceInfo) {
        this.selectedInputDevice = deviceInfo;
        restartAudioInput();
    }

    public void setInputVolume(int volume) {
        this.inputVolume = Math.max(0, Math.min(100, volume));
        // 音量变化应用在处理阶段，这里只记录设置
    }

    public void startAudioInput() {
        if (isRecording) {
            return;
        }
        
        try {
            releaseAudioRecord();

            // 初始化AudioRecord，优先使用VOICE_RECOGNITION音源以获得更好的质量
            int audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
            
            audioRecord = new AudioRecord(
                    audioSource,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE);

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败");
                return;
            }
            
            // 如果指定了输入设备，尝试设置为首选设备
            if (selectedInputDevice != null) {
                boolean result = audioRecord.setPreferredDevice(selectedInputDevice);
                if (!result) {
                    Log.w(TAG, "无法设置首选输入设备: " + selectedInputDevice.getProductName());
                } else {
                    Log.d(TAG, "成功设置输入设备: " + selectedInputDevice.getProductName());
                }
            }

            audioRecord.startRecording();
            isRecording = true;
            
            executor = Executors.newSingleThreadExecutor();
            executor.execute(this::processAudioInput);
            
        } catch (Exception e) {
            Log.e(TAG, "启动音频输入失败: " + e.getMessage());
            releaseAudioRecord();
        }
    }

    private void processAudioInput() {
        short[] buffer = new short[BUFFER_SIZE / 2];
        
        while (isRecording && audioRecord != null) {
            int readSize = audioRecord.read(buffer, 0, buffer.length);
            
            if (readSize > 0 && callback != null) {
                final short[] processedBuffer = buffer.clone();
                final int finalReadSize = readSize;
                
                mainHandler.post(() -> callback.onAudioBufferReceived(processedBuffer, finalReadSize));
            }
        }
    }

    public void stopAudioInput() {
        isRecording = false;
        releaseAudioRecord();
        
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    private void restartAudioInput() {
        boolean wasRecording = isRecording;
        
        stopAudioInput();
        
        if (wasRecording) {
            startAudioInput();
        }
    }

    private void releaseAudioRecord() {
        if (audioRecord != null) {
            try {
                if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop();
                }
            } catch (Exception e) {
                Log.e(TAG, "停止AudioRecord失败: " + e.getMessage());
            } finally {
                audioRecord.release();
                audioRecord = null;
            }
        }
    }

    public int getInputVolume() {
        return inputVolume;
    }

    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    public int getBufferSize() {
        return BUFFER_SIZE;
    }

    public boolean isRecording() {
        return isRecording;
    }
} 