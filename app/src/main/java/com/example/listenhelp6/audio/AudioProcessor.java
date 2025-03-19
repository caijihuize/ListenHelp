package com.example.listenhelp6.audio;

import android.content.Context;
import android.media.audiofx.NoiseSuppressor;
import android.media.audiofx.Equalizer;
import android.util.Log;
import android.util.Pair;

public class AudioProcessor {
    private static final String TAG = "AudioProcessor";
    private static final int BAND_COUNT = 8;
    
    private final Context context;
    private Equalizer equalizer;
    private NoiseSuppressor noiseSuppressor;
    private boolean isNoiseReductionEnabled = false;
    private float amplificationFactor = 1.0f;
    private int audioSessionId = 0;
    private ProcessorCallback callback;

    public interface ProcessorCallback {
        void onProcessedAudioBuffer(short[] buffer, int size);
    }

    public AudioProcessor(Context context) {
        this.context = context;
    }

    public void setAudioSessionId(int audioSessionId) {
        this.audioSessionId = audioSessionId;
        releaseEffects();
        initializeEffects();
    }

    private void initializeEffects() {
        try {
            // 初始化均衡器
            equalizer = new Equalizer(0, audioSessionId);
            equalizer.setEnabled(true);
            
            // 设置默认值为0 dB
            for (short i = 0; i < BAND_COUNT && i < equalizer.getNumberOfBands(); i++) {
                equalizer.setBandLevel(i, (short) 0);
            }
            
            // 如果启用了降噪功能，则初始化NoiseSuppressor
            if (isNoiseReductionEnabled && NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "初始化音频效果失败: " + e.getMessage());
        }
    }

    private void releaseEffects() {
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        
        if (noiseSuppressor != null) {
            noiseSuppressor.release();
            noiseSuppressor = null;
        }
    }

    public void setCallback(ProcessorCallback callback) {
        this.callback = callback;
    }

    public void setNoiseReduction(boolean enabled) {
        if (this.isNoiseReductionEnabled != enabled) {
            this.isNoiseReductionEnabled = enabled;
            
            if (noiseSuppressor != null) {
                noiseSuppressor.setEnabled(enabled);
            } else if (enabled && audioSessionId != 0 && NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId);
                if (noiseSuppressor != null) {
                    noiseSuppressor.setEnabled(true);
                }
            }
        }
    }

    public void setAmplificationFactor(float factor) {
        // 确保放大因子在合理范围内
        this.amplificationFactor = Math.max(0.1f, Math.min(10.0f, factor));
    }

    public void setBandLevel(int bandIndex, short level) {
        if (equalizer != null && bandIndex >= 0 && bandIndex < BAND_COUNT) {
            try {
                // level应该在范围内
                equalizer.setBandLevel((short) bandIndex, level);
            } catch (Exception e) {
                Log.e(TAG, "设置均衡器频段失败: " + e.getMessage());
            }
        }
    }

    public Pair<Short, Short> getBandLevelRange() {
        if (equalizer != null) {
            short[] range = equalizer.getBandLevelRange();
            return new Pair<>(range[0], range[1]);
        }
        return new Pair<>((short) -1500, (short) 1500); // 默认范围
    }

    public short[] getBandLevels() {
        if (equalizer != null) {
            short[] levels = new short[BAND_COUNT];
            for (short i = 0; i < BAND_COUNT && i < equalizer.getNumberOfBands(); i++) {
                levels[i] = equalizer.getBandLevel(i);
            }
            return levels;
        }
        return new short[BAND_COUNT]; // 默认返回全0数组
    }

    public int getBandCount() {
        return BAND_COUNT;
    }

    public float[] getFrequencyBands() {
        float[] freqBands = new float[BAND_COUNT];
        if (equalizer != null) {
            for (short i = 0; i < BAND_COUNT && i < equalizer.getNumberOfBands(); i++) {
                int[] freqRange = equalizer.getBandFreqRange(i);
                // 取频率范围的中心点
                freqBands[i] = (freqRange[0] + freqRange[1]) / 2.0f;
            }
        } else {
            // 默认频率中心点（Hz）
            freqBands[0] = 60f;
            freqBands[1] = 230f;
            freqBands[2] = 910f;
            freqBands[3] = 1800f;
            freqBands[4] = 3600f;
            freqBands[5] = 7200f;
            freqBands[6] = 14000f;
            freqBands[7] = 20000f;
        }
        return freqBands;
    }

    public void processAudioBuffer(short[] inputBuffer, int size, int inputVolume) {
        // 如果没有回调，不处理
        if (callback == null) {
            return;
        }

        // 创建处理后的缓冲区副本
        short[] processedBuffer = new short[size];
        float volumeFactor = inputVolume / 100.0f; // 将0-100范围转换为0-1
        
        // 应用音量和放大效果
        for (int i = 0; i < size; i++) {
            // 应用音量和放大
            float sample = inputBuffer[i] * volumeFactor * amplificationFactor;
            
            // 限制到short范围
            if (sample > Short.MAX_VALUE) {
                sample = Short.MAX_VALUE;
            } else if (sample < Short.MIN_VALUE) {
                sample = Short.MIN_VALUE;
            }
            
            processedBuffer[i] = (short) sample;
        }
        
        // 处理后的数据通过回调返回
        callback.onProcessedAudioBuffer(processedBuffer, size);
    }

    public void release() {
        releaseEffects();
    }

    public boolean isNoiseReductionEnabled() {
        return isNoiseReductionEnabled;
    }

    public float getAmplificationFactor() {
        return amplificationFactor;
    }
} 