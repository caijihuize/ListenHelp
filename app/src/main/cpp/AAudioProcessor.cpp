#include "AAudioProcessor.h"
#include <cmath>
#include <algorithm>

AAudioProcessor::AAudioProcessor()
    : mInputStream(nullptr)
    , mOutputStream(nullptr)
    , mInputVolume(0.8f)      // 默认输入音量80%
    , mOutputVolume(0.8f)     // 默认输出音量80%
    , mAmplification(1.0f)    // 默认放大倍数1.0
    , mNoiseReduction(false)  // 默认关闭降噪
    , mWaveformCallbackCounter(0)
    , mIsInitialized(false)
    , mIsRunning(false) {
    
    // 初始化均衡器增益为中性（0dB）
    mEqualizerGains.resize(kNumEqualizerBands, 1.0f);
}

AAudioProcessor::~AAudioProcessor() {
    cleanup();
}

bool AAudioProcessor::setupStreams(int32_t sampleRate, int32_t channelCount, int32_t format,
                                  int32_t inputDeviceId, int32_t outputDeviceId) {
    cleanup();
    
    // 记录开始时间，用于判断是否超时
    auto startTime = std::chrono::high_resolution_clock::now();
    
    // 创建输入流构建器
    AAudioStreamBuilder *inputBuilder;
    aaudio_result_t result = AAudio_createStreamBuilder(&inputBuilder);
    if (result != AAUDIO_OK) {
        LOGE("创建输入流构建器失败: %s", AAudio_convertResultToText(result));
        return false;
    }
    
    // 配置输入流
    AAudioStreamBuilder_setDirection(inputBuilder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setSharingMode(inputBuilder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setPerformanceMode(inputBuilder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setFormat(inputBuilder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setSampleRate(inputBuilder, sampleRate);
    AAudioStreamBuilder_setChannelCount(inputBuilder, channelCount);
    AAudioStreamBuilder_setDataCallback(inputBuilder, dataCallback, this);
    AAudioStreamBuilder_setErrorCallback(inputBuilder, errorCallback, this);
    
    // 如果指定了输入设备ID，设置设备ID
    if (inputDeviceId > 0) {
        AAudioStreamBuilder_setDeviceId(inputBuilder, inputDeviceId);
    }
    
    // 打开输入流
    result = AAudioStreamBuilder_openStream(inputBuilder, &mInputStream);
    AAudioStreamBuilder_delete(inputBuilder);
    
    if (result != AAUDIO_OK) {
        LOGE("打开输入流失败: %s", AAudio_convertResultToText(result));
        return false;
    }
    
    // 创建输出流构建器
    AAudioStreamBuilder *outputBuilder;
    result = AAudio_createStreamBuilder(&outputBuilder);
    if (result != AAUDIO_OK) {
        LOGE("创建输出流构建器失败: %s", AAudio_convertResultToText(result));
        if (mInputStream) {
            AAudioStream_close(mInputStream);
            mInputStream = nullptr;
        }
        return false;
    }
    
    // 配置输出流
    AAudioStreamBuilder_setDirection(outputBuilder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(outputBuilder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setPerformanceMode(outputBuilder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setFormat(outputBuilder, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setSampleRate(outputBuilder, sampleRate);
    AAudioStreamBuilder_setChannelCount(outputBuilder, channelCount);
    
    // 如果指定了输出设备ID，设置设备ID
    if (outputDeviceId > 0) {
        AAudioStreamBuilder_setDeviceId(outputBuilder, outputDeviceId);
    }
    
    // 打开输出流
    result = AAudioStreamBuilder_openStream(outputBuilder, &mOutputStream);
    AAudioStreamBuilder_delete(outputBuilder);
    
    if (result != AAUDIO_OK) {
        LOGE("打开输出流失败: %s", AAudio_convertResultToText(result));
        if (mInputStream) {
            AAudioStream_close(mInputStream);
            mInputStream = nullptr;
        }
        return false;
    }
    
    // 确保输入输出流的格式匹配
    int32_t inputSampleRate = AAudioStream_getSampleRate(mInputStream);
    int32_t outputSampleRate = AAudioStream_getSampleRate(mOutputStream);
    
    if (inputSampleRate != outputSampleRate) {
        LOGE("输入输出采样率不匹配: %d vs %d", inputSampleRate, outputSampleRate);
        cleanup();
        return false;
    }
    
    // 检查创建过程是否超时
    auto endTime = std::chrono::high_resolution_clock::now();
    auto timeElapsed = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime).count();
    LOGD("音频流设置完成，耗时 %lld ms", (long long)timeElapsed);
    
    mIsInitialized = true;
    return true;
}

bool AAudioProcessor::start() {
    if (!mIsInitialized || mIsRunning) {
        LOGE("AAudioProcessor::start - 流未初始化或已在运行");
        return false;
    }
    
    // 启动输出流
    aaudio_result_t result = AAudioStream_requestStart(mOutputStream);
    if (result != AAUDIO_OK) {
        LOGE("启动输出流失败: %s", AAudio_convertResultToText(result));
        return false;
    }
    
    // 启动输入流
    result = AAudioStream_requestStart(mInputStream);
    if (result != AAUDIO_OK) {
        LOGE("启动输入流失败: %s", AAudio_convertResultToText(result));
        AAudioStream_requestStop(mOutputStream);
        return false;
    }
    
    mIsRunning = true;
    LOGD("AAudio流已开始运行");
    return true;
}

void AAudioProcessor::stop() {
    if (!mIsRunning) {
        return;
    }
    
    // 停止输入流
    if (mInputStream) {
        aaudio_result_t result = AAudioStream_requestStop(mInputStream);
        if (result != AAUDIO_OK) {
            LOGE("停止输入流失败: %s", AAudio_convertResultToText(result));
        }
    }
    
    // 停止输出流
    if (mOutputStream) {
        aaudio_result_t result = AAudioStream_requestStop(mOutputStream);
        if (result != AAUDIO_OK) {
            LOGE("停止输出流失败: %s", AAudio_convertResultToText(result));
        }
    }
    
    mIsRunning = false;
    LOGD("AAudio流已停止");
}

void AAudioProcessor::cleanup() {
    stop();
    
    // 关闭输入流
    if (mInputStream) {
        AAudioStream_close(mInputStream);
        mInputStream = nullptr;
    }
    
    // 关闭输出流
    if (mOutputStream) {
        AAudioStream_close(mOutputStream);
        mOutputStream = nullptr;
    }
    
    mIsInitialized = false;
    LOGD("AAudio资源已释放");
}

void AAudioProcessor::setInputVolume(int volume) {
    // 转换为0.0-1.0范围
    float normalizedVolume = std::max(0, std::min(100, volume)) / 100.0f;
    mInputVolume = normalizedVolume;
}

void AAudioProcessor::setOutputVolume(int volume) {
    // 转换为0.0-1.0范围
    float normalizedVolume = std::max(0, std::min(100, volume)) / 100.0f;
    mOutputVolume = normalizedVolume;
}

void AAudioProcessor::setAmplificationFactor(float factor) {
    // 限制在0.1-100.0范围内（助听器应用需要非常大的放大倍数）
    mAmplification = std::max(0.1f, std::min(100.0f, factor));
}

void AAudioProcessor::setNoiseReduction(bool enabled) {
    mNoiseReduction = enabled;
}

void AAudioProcessor::setEqualizerBand(int band, int gain) {
    if (band < 0 || band >= kNumEqualizerBands) {
        LOGE("无效的均衡器频段: %d", band);
        return;
    }
    
    // 将均衡器增益从-15到15的范围映射到0.25到4的增益倍数
    // -15 -> 0.25 (衰减4倍), 0 -> 1.0 (无变化), +15 -> 4.0 (放大4倍)
    float gainFactor = std::pow(2.0f, gain / 5.0f);
    
    std::lock_guard<std::mutex> lock(mEqualizerMutex);
    mEqualizerGains[band] = gainFactor;
}

void AAudioProcessor::setWaveformCallback(AudioDataCallback inputCallback, AudioDataCallback outputCallback) {
    std::lock_guard<std::mutex> lock(mCallbackMutex);
    mInputWaveformCallback = inputCallback;
    mOutputWaveformCallback = outputCallback;
    LOGD("设置波形回调: 输入回调 %s, 输出回调 %s", 
         inputCallback ? "已设置" : "未设置", 
         outputCallback ? "已设置" : "未设置");
}

void AAudioProcessor::sendWaveformData(const float* data, size_t size, bool isInput) {
    std::lock_guard<std::mutex> lock(mCallbackMutex);
    
    if (isInput && mInputWaveformCallback) {
        LOGD("发送输入波形数据: %zu 个样本, 第一个样本值: %f", size, data[0]);
        mInputWaveformCallback(data, size, true);
    } else if (!isInput && mOutputWaveformCallback) {
        LOGD("发送输出波形数据: %zu 个样本, 第一个样本值: %f", size, data[0]);
        mOutputWaveformCallback(data, size, false);
    } else {
        // 检查回调是否为空
        if (isInput) {
            LOGD("输入波形回调为空，未能发送数据");
        } else {
            LOGD("输出波形回调为空，未能发送数据");
        }
    }
}

// 静态回调函数，将调用转发到类实例
aaudio_data_callback_result_t AAudioProcessor::dataCallback(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames) {
    return static_cast<AAudioProcessor*>(userData)->processAudioData(audioData, numFrames);
}

// 静态错误回调函数
void AAudioProcessor::errorCallback(
        AAudioStream *stream,
        void *userData,
        aaudio_result_t error) {
    LOGE("AAudio错误回调: %s", AAudio_convertResultToText(error));
    // 尝试重启流
    if (error == AAUDIO_ERROR_DISCONNECTED) {
        // 通知主线程进行重启
        // 在实际应用中，应该通过消息队列或其他方式通知主线程
        LOGD("AAudio流断开连接，应重新启动");
    }
}

// 音频数据处理
aaudio_data_callback_result_t AAudioProcessor::processAudioData(
        void *audioData,
        int32_t numFrames) {
    float *buffer = static_cast<float*>(audioData);
    
    // 同时处理所有声道
    int32_t channelCount = AAudioStream_getChannelCount(mInputStream);
    int totalSamples = numFrames * channelCount;
    
    // 创建输入数据的副本，因为原始数据会被修改
    std::vector<float> inputBuffer(totalSamples);
    std::copy(buffer, buffer + totalSamples, inputBuffer.begin());
    
    // 发送原始输入波形数据
    mWaveformCallbackCounter++;
    if (mWaveformCallbackCounter >= 2) { // 减少帧间隔，提高回调频率
        mWaveformCallbackCounter = 0;
        LOGD("处理音频数据: %d 帧, %d 总样本", numFrames, totalSamples);
        sendWaveformData(inputBuffer.data(), totalSamples, true);
    }
    
    // 第1步：应用输入音量
    for (int i = 0; i < totalSamples; i++) {
        buffer[i] *= mInputVolume;
    }
    
    // 第2步：应用放大
    for (int i = 0; i < totalSamples; i++) {
        buffer[i] *= mAmplification;
    }
    
    // 第3步：应用降噪（简单模拟）
    if (mNoiseReduction) {
        for (int i = 0; i < totalSamples; i++) {
            // 简单的噪声门限，抑制低于阈值的信号
            if (std::abs(buffer[i]) < 0.02f) { // 低于2%的幅度视为噪声
                buffer[i] *= 0.5f; // 衰减噪声
            }
        }
    }
    
    // 第4步：应用均衡器
    applyEqualizer(buffer, numFrames);
    
    // 第5步：应用输出音量并写入输出流
    for (int i = 0; i < totalSamples; i++) {
        // 对于助听器应用，我们需要允许更大的幅度
        // 使用改进的软限幅算法，适应更高的放大倍数
        if (buffer[i] > 1.0f) {
            // 软限幅：超过1.0的部分用对数压缩，系数增加以处理更大的放大倍数
            buffer[i] = 1.0f + log10f(1.0f + buffer[i]) * 0.5f;
        } else if (buffer[i] < -1.0f) {
            buffer[i] = -1.0f - log10f(1.0f - buffer[i]) * 0.5f;
        }
        
        // 应用输出音量
        buffer[i] *= mOutputVolume;
    }
    
    // 发送输出波形数据
    sendWaveformData(buffer, totalSamples, false);
    
    // 写入输出流
    if (mOutputStream) {
        aaudio_result_t result = AAudioStream_write(mOutputStream, buffer, numFrames, 0);
        if (result < 0) {
            LOGE("写入输出流失败: %s", AAudio_convertResultToText(result));
            return AAUDIO_CALLBACK_RESULT_STOP;
        }
    }
    
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// 均衡器处理
void AAudioProcessor::applyEqualizer(float *buffer, int32_t numFrames) {
    // 获取当前均衡器设置的副本，避免处理过程中更改
    std::vector<float> equalizerGains;
    {
        std::lock_guard<std::mutex> lock(mEqualizerMutex);
        equalizerGains = mEqualizerGains;
    }
    
    // 实际应用中，这里应该使用FFT进行频域处理，并应用均衡器增益
    // 为简化，这里使用一个简单模拟，直接应用均衡增益
    // 注：实际均衡器实现要比这复杂得多

    // 简单模拟：每个样本按一定比例应用不同频段的增益
    int32_t channelCount = AAudioStream_getChannelCount(mInputStream);
    for (int frame = 0; frame < numFrames; frame++) {
        for (int ch = 0; ch < channelCount; ch++) {
            int sampleIndex = frame * channelCount + ch;
            
            // 简单的模拟均衡效果
            float originalSample = buffer[sampleIndex];
            buffer[sampleIndex] = 0;
            
            // 简单地将信号分配到不同频段并应用增益
            for (int band = 0; band < kNumEqualizerBands; band++) {
                // 按频段权重分配信号（简化）
                float weight = 1.0f / kNumEqualizerBands;
                buffer[sampleIndex] += originalSample * weight * equalizerGains[band];
            }
        }
    }
} 