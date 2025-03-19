package com.example.listenhelp6.audio;

import android.util.Log;

/**
 * AAudio处理器的JNI包装类，用于调用原生AAudio API
 */
public class AAudioProcessorJNI {
    private static final String TAG = "AAudioProcessorJNI";
    
    static {
        try {
            System.loadLibrary("audioproc");
            Log.d(TAG, "加载audioproc库成功");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "加载audioproc库失败: " + e.getMessage());
        }
    }
    
    // 本地方法句柄
    private long nativeHandle;
    
    // 波形回调
    private WaveformCallback inputWaveformCallback;
    private WaveformCallback outputWaveformCallback;
    
    public AAudioProcessorJNI() {
        nativeHandle = nativeCreateProcessor();
        if (nativeHandle == 0) {
            throw new RuntimeException("无法创建AAudioProcessor实例");
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
        
        nativeSetWaveformCallback(nativeHandle, 
                inputCallback != null ? new WaveformCallbackWrapper(inputCallback) : null,
                outputCallback != null ? new WaveformCallbackWrapper(outputCallback) : null);
    }
    
    /**
     * 设置音频流参数
     * @param sampleRate 采样率
     * @param channelCount 声道数
     * @param format 音频格式（参考AAudio格式常量）
     * @param inputDeviceId 输入设备ID
     * @param outputDeviceId 输出设备ID
     * @return 是否设置成功
     */
    public boolean setupStreams(int sampleRate, int channelCount, int format, 
                               int inputDeviceId, int outputDeviceId) {
        if (nativeHandle == 0) {
            Log.e(TAG, "原生对象已释放");
            return false;
        }
        return nativeSetupStreams(nativeHandle, sampleRate, channelCount, format, 
                                 inputDeviceId, outputDeviceId);
    }
    
    /**
     * 开始音频处理
     * @return 是否成功开始
     */
    public boolean start() {
        if (nativeHandle == 0) {
            Log.e(TAG, "原生对象已释放");
            return false;
        }
        return nativeStart(nativeHandle);
    }
    
    /**
     * 停止音频处理
     */
    public void stop() {
        if (nativeHandle != 0) {
            nativeStop(nativeHandle);
        }
    }
    
    /**
     * 设置输入音量
     * @param volume 音量值（0-100）
     */
    public void setInputVolume(int volume) {
        if (nativeHandle != 0) {
            nativeSetInputVolume(nativeHandle, volume);
        }
    }
    
    /**
     * 设置输出音量
     * @param volume 音量值（0-100）
     */
    public void setOutputVolume(int volume) {
        if (nativeHandle != 0) {
            nativeSetOutputVolume(nativeHandle, volume);
        }
    }
    
    /**
     * 设置音频放大倍数
     * @param factor 放大倍数（0.1-10.0）
     */
    public void setAmplificationFactor(float factor) {
        if (nativeHandle != 0) {
            nativeSetAmplificationFactor(nativeHandle, factor);
        }
    }
    
    /**
     * 设置是否启用降噪
     * @param enabled 是否启用
     */
    public void setNoiseReduction(boolean enabled) {
        if (nativeHandle != 0) {
            nativeSetNoiseReduction(nativeHandle, enabled);
        }
    }
    
    /**
     * 设置均衡器频段增益
     * @param band 频段索引（0-7）
     * @param gain 增益值（-15至15）
     */
    public void setEqualizerBand(int band, int gain) {
        if (nativeHandle != 0) {
            nativeSetEqualizerBand(nativeHandle, band, gain);
        }
    }
    
    /**
     * 释放资源
     */
    public void release() {
        if (nativeHandle != 0) {
            nativeReleaseProcessor(nativeHandle);
            nativeHandle = 0;
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            release();
        } finally {
            super.finalize();
        }
    }
    
    /**
     * 内部波形回调包装类，用于JNI回调
     */
    private static class WaveformCallbackWrapper {
        private final WaveformCallback callback;
        
        public WaveformCallbackWrapper(WaveformCallback callback) {
            this.callback = callback;
        }
        
        public void onWaveformData(float[] data) {
            if (callback != null) {
                callback.onWaveformData(data);
            }
        }
    }
    
    // 原生方法声明
    private native long nativeCreateProcessor();
    private native void nativeReleaseProcessor(long handle);
    private native boolean nativeSetupStreams(long handle, int sampleRate, int channelCount, int format, 
                                             int inputDeviceId, int outputDeviceId);
    private native boolean nativeStart(long handle);
    private native void nativeStop(long handle);
    private native void nativeSetInputVolume(long handle, int volume);
    private native void nativeSetOutputVolume(long handle, int volume);
    private native void nativeSetAmplificationFactor(long handle, float factor);
    private native void nativeSetNoiseReduction(long handle, boolean enabled);
    private native void nativeSetEqualizerBand(long handle, int band, int gain);
    private native void nativeSetWaveformCallback(long handle, WaveformCallbackWrapper inputCallback, 
                                                WaveformCallbackWrapper outputCallback);
} 