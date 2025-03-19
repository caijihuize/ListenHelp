package com.example.listenhelp6.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 使用AAudio API管理音频处理的核心类
 */
public class AAudioManager {
    private static final String TAG = "AAudioManager";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_COUNT = 1; // 单声道
    private static final int FORMAT = 2; // AAUDIO_FORMAT_PCM_FLOAT

    private final Context context;
    private final AudioManager audioManager;
    private final AAudioProcessorJNI audioProcessor;
    
    private boolean isRunning = false;
    private int inputVolume = 80; // 默认输入音量(0-100)
    private int outputVolume = 80; // 默认输出音量(0-100)
    private float amplificationFactor = 1.0f; // 默认放大倍数
    private boolean noiseReductionEnabled = false; // 降噪默认关闭
    
    private AudioDeviceInfo selectedInputDevice;
    private AudioDeviceInfo selectedOutputDevice;
    
    // 波形回调
    private WaveformCallback inputWaveformCallback;
    private WaveformCallback outputWaveformCallback;
    
    // 均衡器设置
    private static final int EQ_BAND_COUNT = 8;
    private final short[] equalizerBandLevels = new short[EQ_BAND_COUNT];
    
    // 锁屏处理
    private boolean wasRunningBeforeLock = false;
    
    public AAudioManager(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.audioProcessor = new AAudioProcessorJNI();
        
        // 初始化均衡器为中性
        for (int i = 0; i < EQ_BAND_COUNT; i++) {
            equalizerBandLevels[i] = 0;
        }
    }
    
    /**
     * 设置波形数据回调
     * @param inputCallback 输入波形回调
     * @param outputCallback 输出波形回调
     */
    public void setWaveformCallback(WaveformCallback inputCallback, WaveformCallback outputCallback) {
        this.inputWaveformCallback = inputCallback;
        this.outputWaveformCallback = outputCallback;
        audioProcessor.setWaveformCallback(inputCallback, outputCallback);
    }
    
    /**
     * 获取可用的音频输入设备
     */
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
    
    /**
     * 获取可用的音频输出设备
     */
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
    
    /**
     * 设置输入设备
     */
    public void setInputDevice(AudioDeviceInfo deviceInfo) {
        this.selectedInputDevice = deviceInfo;
        restartAudio();
    }
    
    /**
     * 设置输出设备
     */
    public void setOutputDevice(AudioDeviceInfo deviceInfo) {
        this.selectedOutputDevice = deviceInfo;
        restartAudio();
    }
    
    /**
     * 设置输入音量
     */
    public void setInputVolume(int volume) {
        this.inputVolume = Math.max(0, Math.min(100, volume));
        if (isRunning) {
            audioProcessor.setInputVolume(inputVolume);
        }
    }
    
    /**
     * 设置输出音量
     */
    public void setOutputVolume(int volume) {
        this.outputVolume = Math.max(0, Math.min(100, volume));
        if (isRunning) {
            audioProcessor.setOutputVolume(outputVolume);
        }
    }
    
    /**
     * 设置放大倍数
     */
    public void setAmplificationFactor(float factor) {
        // 确保放大因子在合理范围内，助听器应用需要非常大的放大倍数
        this.amplificationFactor = Math.max(0.1f, Math.min(100.0f, factor));
        if (isRunning) {
            audioProcessor.setAmplificationFactor(amplificationFactor);
        }
    }
    
    /**
     * 设置降噪
     */
    public void setNoiseReduction(boolean enabled) {
        this.noiseReductionEnabled = enabled;
        if (isRunning) {
            audioProcessor.setNoiseReduction(enabled);
        }
    }
    
    /**
     * 设置均衡器频段增益
     */
    public void setEqualizerBand(int band, short level) {
        if (band >= 0 && band < EQ_BAND_COUNT) {
            equalizerBandLevels[band] = level;
            if (isRunning) {
                audioProcessor.setEqualizerBand(band, level);
            }
        }
    }
    
    /**
     * 获取均衡器频段值
     */
    public short[] getEqualizerBands() {
        return equalizerBandLevels.clone();
    }
    
    /**
     * 开始音频处理
     */
    public boolean startAudio() {
        if (isRunning) {
            return true;
        }
        
        try {
            // 获取设备ID
            int inputDeviceId = selectedInputDevice != null ? selectedInputDevice.getId() : 0;
            int outputDeviceId = selectedOutputDevice != null ? selectedOutputDevice.getId() : 0;
            
            // 设置流
            boolean success = audioProcessor.setupStreams(
                    SAMPLE_RATE, CHANNEL_COUNT, FORMAT, inputDeviceId, outputDeviceId);
            
            if (!success) {
                Log.e(TAG, "设置音频流失败");
                return false;
            }
            
            // 设置波形回调
            if (inputWaveformCallback != null || outputWaveformCallback != null) {
                audioProcessor.setWaveformCallback(inputWaveformCallback, outputWaveformCallback);
            }
            
            // 应用设置
            audioProcessor.setInputVolume(inputVolume);
            audioProcessor.setOutputVolume(outputVolume);
            audioProcessor.setAmplificationFactor(amplificationFactor);
            audioProcessor.setNoiseReduction(noiseReductionEnabled);
            
            // 应用均衡器设置
            for (int i = 0; i < EQ_BAND_COUNT; i++) {
                audioProcessor.setEqualizerBand(i, equalizerBandLevels[i]);
            }
            
            // 启动处理
            success = audioProcessor.start();
            if (success) {
                isRunning = true;
                Log.d(TAG, "音频处理已启动");
            } else {
                Log.e(TAG, "启动音频处理失败");
            }
            
            return success;
        } catch (Exception e) {
            Log.e(TAG, "启动音频处理时发生异常", e);
            return false;
        }
    }
    
    /**
     * 停止音频处理
     */
    public void stopAudio() {
        if (!isRunning) {
            return;
        }
        
        audioProcessor.stop();
        isRunning = false;
        Log.d(TAG, "音频处理已停止");
    }
    
    /**
     * 重启音频处理
     */
    private void restartAudio() {
        boolean wasRunning = isRunning;
        
        stopAudio();
        
        if (wasRunning) {
            startAudio();
        }
    }
    
    /**
     * 清除输入设备选择，使用系统默认设备
     */
    public void clearInputDevice() {
        this.selectedInputDevice = null;
        Log.d(TAG, "已清除输入设备选择，将使用系统默认设备");
        restartAudio();
    }
    
    /**
     * 清除输出设备选择，使用系统默认设备
     */
    public void clearOutputDevice() {
        this.selectedOutputDevice = null;
        Log.d(TAG, "已清除输出设备选择，将使用系统默认设备");
        restartAudio();
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopAudio();
        audioProcessor.release();
        Log.d(TAG, "资源已释放");
    }
    
    /**
     * 获取输入音量
     */
    public int getInputVolume() {
        return inputVolume;
    }
    
    /**
     * 获取输出音量
     */
    public int getOutputVolume() {
        return outputVolume;
    }
    
    /**
     * 获取放大倍数
     */
    public float getAmplificationFactor() {
        return amplificationFactor;
    }
    
    /**
     * 获取降噪状态
     */
    public boolean isNoiseReductionEnabled() {
        return noiseReductionEnabled;
    }
    
    /**
     * 获取运行状态
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * 准备锁屏
     * 记录当前状态，但不停止音频处理
     */
    public void prepareForLock() {
        wasRunningBeforeLock = isRunning;
        // 不再停止音频处理，即使锁屏也继续运行
    }
    
    /**
     * 从锁屏恢复
     * 如果之前在运行，则继续运行
     */
    public void resumeFromLock() {
        if (wasRunningBeforeLock && !isRunning) {
            startAudio();
        }
    }
} 