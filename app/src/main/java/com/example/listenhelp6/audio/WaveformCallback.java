package com.example.listenhelp6.audio;

/**
 * 波形数据回调接口
 */
public interface WaveformCallback {
    /**
     * 当有新的波形数据可用时调用
     * @param data 波形数据数组
     */
    void onWaveformData(float[] data);
} 