#include <jni.h>
#include <string>
#include <android/log.h>
#include "AAudioProcessor.h"

#define LOG_TAG "AudioProcJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 存储AAudioProcessor的指针
static AAudioProcessor *audioProcessor = nullptr;

// 全局引用，用于保存Java回调
static JavaVM *javaVM = nullptr;
static jobject inputWaveformCallbackObj = nullptr;
static jobject outputWaveformCallbackObj = nullptr;
static jmethodID inputWaveformMethodId = nullptr;
static jmethodID outputWaveformMethodId = nullptr;

// JNI环境获取辅助函数
static JNIEnv *getJNIEnv() {
    JNIEnv *env = nullptr;
    if (javaVM != nullptr) {
        jint result = javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            LOGD("线程未附加到JVM，尝试附加");
            JavaVMAttachArgs args;
            args.version = JNI_VERSION_1_6;
            args.name = "NativeWaveformCallback";
            args.group = nullptr;
            
            result = javaVM->AttachCurrentThread(&env, &args);
            if (result != JNI_OK) {
                LOGE("附加线程到JVM失败: %d", result);
                return nullptr;
            }
            LOGD("线程已成功附加到JVM");
        } else if (result != JNI_OK) {
            LOGE("获取JNI环境失败: %d", result);
            return nullptr;
        }
    } else {
        LOGE("JavaVM为空，无法获取JNI环境");
    }
    return env;
}

// 波形数据回调函数
static void onInputWaveformData(const float* data, size_t size, bool isInput) {
    JNIEnv *env = getJNIEnv();
    if (env == nullptr) {
        LOGE("无法获取JNI环境，无法发送波形数据");
        return;
    }
    
    if (inputWaveformCallbackObj == nullptr) {
        LOGE("输入波形回调对象为空");
        return;
    }
    
    if (inputWaveformMethodId == nullptr) {
        LOGE("输入波形回调方法ID为空");
        return;
    }

    // 创建浮点数组
    jfloatArray jArray = env->NewFloatArray(size);
    if (jArray == nullptr) {
        LOGE("创建浮点数组失败，大小: %zu", size);
        return;
    }

    // 复制数据
    env->SetFloatArrayRegion(jArray, 0, size, data);

    // 调用Java回调方法
    LOGD("调用Java输入波形回调方法，数据大小: %zu", size);
    env->CallVoidMethod(inputWaveformCallbackObj, inputWaveformMethodId, jArray);
    
    // 检查是否有异常
    if (env->ExceptionCheck()) {
        LOGE("调用Java输入波形回调时发生异常");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    // 释放局部引用
    env->DeleteLocalRef(jArray);
}

static void onOutputWaveformData(const float* data, size_t size, bool isInput) {
    JNIEnv *env = getJNIEnv();
    if (env == nullptr) {
        LOGE("无法获取JNI环境，无法发送波形数据");
        return;
    }
    
    if (outputWaveformCallbackObj == nullptr) {
        LOGE("输出波形回调对象为空");
        return;
    }
    
    if (outputWaveformMethodId == nullptr) {
        LOGE("输出波形回调方法ID为空");
        return;
    }

    // 创建浮点数组
    jfloatArray jArray = env->NewFloatArray(size);
    if (jArray == nullptr) {
        LOGE("创建浮点数组失败，大小: %zu", size);
        return;
    }

    // 复制数据
    env->SetFloatArrayRegion(jArray, 0, size, data);

    // 调用Java回调方法
    LOGD("调用Java输出波形回调方法，数据大小: %zu", size);
    env->CallVoidMethod(outputWaveformCallbackObj, outputWaveformMethodId, jArray);
    
    // 检查是否有异常
    if (env->ExceptionCheck()) {
        LOGE("调用Java输出波形回调时发生异常");
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    // 释放局部引用
    env->DeleteLocalRef(jArray);
}

extern "C" {

// 设置Java VM
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    LOGD("JNI_OnLoad被调用，设置JavaVM指针");
    javaVM = vm;
    return JNI_VERSION_1_6;
}

// 创建AAudioProcessor实例
JNIEXPORT jlong JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeCreateProcessor(JNIEnv *env, jobject thiz) {
    if (audioProcessor != nullptr) {
        LOGD("已存在AAudioProcessor实例，先删除");
        delete audioProcessor;
    }
    
    audioProcessor = new AAudioProcessor();
    LOGD("创建AAudioProcessor实例: %p", audioProcessor);
    return reinterpret_cast<jlong>(audioProcessor);
}

// 释放AAudioProcessor实例
JNIEXPORT void JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeReleaseProcessor(JNIEnv *env, jobject thiz, jlong handle) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor != nullptr) {
        processor->cleanup();
        delete processor;
        LOGD("释放AAudioProcessor实例: %p", processor);
    } else {
        LOGE("尝试释放空的AAudioProcessor实例");
    }
    
    if (processor == audioProcessor) {
        audioProcessor = nullptr;
    }
    
    // 清理全局引用
    if (inputWaveformCallbackObj != nullptr) {
        env->DeleteGlobalRef(inputWaveformCallbackObj);
        inputWaveformCallbackObj = nullptr;
        LOGD("删除输入波形回调全局引用");
    }
    if (outputWaveformCallbackObj != nullptr) {
        env->DeleteGlobalRef(outputWaveformCallbackObj);
        outputWaveformCallbackObj = nullptr;
        LOGD("删除输出波形回调全局引用");
    }
    
    // 重置方法ID
    inputWaveformMethodId = nullptr;
    outputWaveformMethodId = nullptr;
}

// 设置音频流
JNIEXPORT jboolean JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeSetupStreams(
        JNIEnv *env, jobject thiz, jlong handle,
        jint sample_rate, jint channel_count, jint format,
        jint input_device_id, jint output_device_id) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor == nullptr) {
        LOGE("AAudioProcessor实例为null");
        return JNI_FALSE;
    }
    
    bool result = processor->setupStreams(
            sample_rate, channel_count, format, input_device_id, output_device_id);
    LOGD("设置音频流: %s", result ? "成功" : "失败");
    return result ? JNI_TRUE : JNI_FALSE;
}

// 开始音频处理
JNIEXPORT jboolean JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeStart(JNIEnv *env, jobject thiz, jlong handle) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor == nullptr) {
        LOGE("AAudioProcessor实例为null");
        return JNI_FALSE;
    }
    
    bool result = processor->start();
    LOGD("开始音频处理: %s", result ? "成功" : "失败");
    return result ? JNI_TRUE : JNI_FALSE;
}

// 停止音频处理
JNIEXPORT void JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeStop(JNIEnv *env, jobject thiz, jlong handle) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor == nullptr) {
        LOGE("AAudioProcessor实例为null");
        return;
    }
    
    processor->stop();
    LOGD("停止音频处理");
}

// 设置输入音量
JNIEXPORT void JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeSetInputVolume(
        JNIEnv *env, jobject thiz, jlong handle, jint volume) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor == nullptr) {
        LOGE("AAudioProcessor实例为null");
        return;
    }
    
    processor->setInputVolume(volume);
}

// 设置输出音量
JNIEXPORT void JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeSetOutputVolume(
        JNIEnv *env, jobject thiz, jlong handle, jint volume) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor == nullptr) {
        LOGE("AAudioProcessor实例为null");
        return;
    }
    
    processor->setOutputVolume(volume);
}

// 设置放大倍数
JNIEXPORT void JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeSetAmplificationFactor(
        JNIEnv *env, jobject thiz, jlong handle, jfloat factor) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor == nullptr) {
        LOGE("AAudioProcessor实例为null");
        return;
    }
    
    processor->setAmplificationFactor(factor);
}

// 设置降噪
JNIEXPORT void JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeSetNoiseReduction(
        JNIEnv *env, jobject thiz, jlong handle, jboolean enabled) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor == nullptr) {
        LOGE("AAudioProcessor实例为null");
        return;
    }
    
    processor->setNoiseReduction(enabled);
}

// 设置均衡器频段
JNIEXPORT void JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeSetEqualizerBand(
        JNIEnv *env, jobject thiz, jlong handle, jint band, jint gain) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor == nullptr) {
        LOGE("AAudioProcessor实例为null");
        return;
    }
    
    processor->setEqualizerBand(band, gain);
}

// 设置波形数据回调
JNIEXPORT void JNICALL
Java_com_example_listenhelp6_audio_AAudioProcessorJNI_nativeSetWaveformCallback(
        JNIEnv *env, jobject thiz, jlong handle,
        jobject inputCallback, jobject outputCallback) {
    AAudioProcessor *processor = reinterpret_cast<AAudioProcessor*>(handle);
    if (processor == nullptr) {
        LOGE("AAudioProcessor实例为null");
        return;
    }

    // 清理旧的全局引用
    if (inputWaveformCallbackObj != nullptr) {
        env->DeleteGlobalRef(inputWaveformCallbackObj);
        inputWaveformCallbackObj = nullptr;
    }
    if (outputWaveformCallbackObj != nullptr) {
        env->DeleteGlobalRef(outputWaveformCallbackObj);
        outputWaveformCallbackObj = nullptr;
    }

    // 保存新的回调对象和方法
    if (inputCallback != nullptr) {
        inputWaveformCallbackObj = env->NewGlobalRef(inputCallback);
        jclass callbackClass = env->GetObjectClass(inputCallback);
        if (callbackClass != nullptr) {
            inputWaveformMethodId = env->GetMethodID(callbackClass, "onWaveformData", "([F)V");
            if (inputWaveformMethodId == nullptr) {
                LOGE("无法获取输入波形回调方法ID");
                env->ExceptionClear();
            } else {
                LOGD("成功获取输入波形回调方法ID");
            }
            env->DeleteLocalRef(callbackClass);
        } else {
            LOGE("无法获取输入波形回调类");
            env->ExceptionClear();
        }
    } else {
        LOGD("未提供输入波形回调");
    }

    if (outputCallback != nullptr) {
        outputWaveformCallbackObj = env->NewGlobalRef(outputCallback);
        jclass callbackClass = env->GetObjectClass(outputCallback);
        if (callbackClass != nullptr) {
            outputWaveformMethodId = env->GetMethodID(callbackClass, "onWaveformData", "([F)V");
            if (outputWaveformMethodId == nullptr) {
                LOGE("无法获取输出波形回调方法ID");
                env->ExceptionClear();
            } else {
                LOGD("成功获取输出波形回调方法ID");
            }
            env->DeleteLocalRef(callbackClass);
        } else {
            LOGE("无法获取输出波形回调类");
            env->ExceptionClear();
        }
    } else {
        LOGD("未提供输出波形回调");
    }

    // 设置C++回调
    bool haveValidCallbacks = (inputWaveformCallbackObj != nullptr && inputWaveformMethodId != nullptr) ||
                            (outputWaveformCallbackObj != nullptr && outputWaveformMethodId != nullptr);
    
    if (haveValidCallbacks) {
        processor->setWaveformCallback(onInputWaveformData, onOutputWaveformData);
        LOGD("设置波形回调成功");
    } else {
        LOGE("没有有效的回调对象或方法ID，不设置波形回调");
    }
}

} // extern "C" 