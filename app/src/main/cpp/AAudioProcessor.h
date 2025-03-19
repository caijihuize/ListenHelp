#ifndef LISTENHELP6_AAUDIOPROCESSOR_H
#define LISTENHELP6_AAUDIOPROCESSOR_H

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <memory>
#include <atomic>
#include <mutex>
#include <vector>
#include <functional>

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "AAudioProcessor", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AAudioProcessor", __VA_ARGS__)

// 音频回调函数类型，用于波形数据
typedef std::function<void(const float*, size_t, bool)> AudioDataCallback;

class AAudioProcessor {
public:
    AAudioProcessor();
    ~AAudioProcessor();

    // 初始化音频流
    bool setupStreams(int32_t sampleRate, int32_t channelCount, int32_t format, 
                     int32_t inputDeviceId, int32_t outputDeviceId);
    
    // 启动音频处理
    bool start();

    // 停止音频处理
    void stop();

    // 释放资源
    void cleanup();

    // 设置输入音量（0-100）
    void setInputVolume(int volume);

    // 设置输出音量（0-100）
    void setOutputVolume(int volume);

    // 设置放大倍数（0.1-100.0）
    void setAmplificationFactor(float factor);

    // 启用/禁用降噪
    void setNoiseReduction(bool enabled);

    // 设置均衡器频段增益
    void setEqualizerBand(int band, int gain);
    
    // 设置波形数据回调
    void setWaveformCallback(AudioDataCallback inputCallback, AudioDataCallback outputCallback);

    // AAudio数据回调函数
    static aaudio_data_callback_result_t dataCallback(
            AAudioStream *stream,
            void *userData,
            void *audioData,
            int32_t numFrames);

    // AAudio错误回调函数
    static void errorCallback(
            AAudioStream *stream,
            void *userData,
            aaudio_result_t error);

private:
    // 音频处理函数
    aaudio_data_callback_result_t processAudioData(
            void *audioData,
            int32_t numFrames);

    // 处理音频样本
    void processAudioSample(float &sample);

    // 应用均衡器
    void applyEqualizer(float *buffer, int32_t numFrames);
    
    // 发送波形数据
    void sendWaveformData(const float* data, size_t size, bool isInput);
    
    // 音频流
    AAudioStream *mInputStream;
    AAudioStream *mOutputStream;

    // 处理参数
    std::atomic<float> mInputVolume;      // 输入音量 (0.0-1.0)
    std::atomic<float> mOutputVolume;     // 输出音量 (0.0-1.0)
    std::atomic<float> mAmplification;   // 放大倍数 (0.1-100.0)
    std::atomic<bool> mNoiseReduction;    // 是否启用降噪

    // 均衡器参数
    static const int kNumEqualizerBands = 8;
    std::mutex mEqualizerMutex;
    std::vector<float> mEqualizerGains;  // 均衡器各频段增益
    
    // 波形回调
    std::mutex mCallbackMutex;
    AudioDataCallback mInputWaveformCallback;
    AudioDataCallback mOutputWaveformCallback;
    int mWaveformCallbackCounter;  // 控制回调频率
    
    // 状态标志
    std::atomic<bool> mIsInitialized;
    std::atomic<bool> mIsRunning;
};

#endif //LISTENHELP6_AAUDIOPROCESSOR_H 