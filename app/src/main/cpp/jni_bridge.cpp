#include <jni.h>
#include <android/log.h>
#include <memory>
#include "AudioEngine.h"

#define LOG_TAG "UniPadAudioJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::unique_ptr<AudioEngine> sEngine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeStart(JNIEnv*, jobject) {
    if (sEngine) {
        sEngine->stop();
    }
    sEngine = std::make_unique<AudioEngine>();
    return sEngine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeStop(JNIEnv*, jobject) {
    if (sEngine) {
        sEngine->stop();
        sEngine.reset();
    }
}

JNIEXPORT jint JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeLoadSound(
        JNIEnv* env, jobject, jshortArray pcmData, jint numFrames, jint channels, jint sampleRate) {
    if (!sEngine) return -1;

    jshort* data = env->GetShortArrayElements(pcmData, nullptr);
    if (!data) return -1;

    int soundId = sEngine->loadSound(data, numFrames, channels, sampleRate);
    env->ReleaseShortArrayElements(pcmData, data, JNI_ABORT);
    return soundId;
}

JNIEXPORT void JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeUnloadSound(
        JNIEnv*, jobject, jint soundId) {
    if (sEngine) sEngine->unloadSound(soundId);
}

JNIEXPORT void JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeUnloadAll(JNIEnv*, jobject) {
    if (sEngine) sEngine->unloadAll();
}

JNIEXPORT jint JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativePlay(
        JNIEnv*, jobject, jint soundId, jfloat volumeL, jfloat volumeR, jint loop) {
    if (!sEngine) return 0;
    return sEngine->play(soundId, volumeL, volumeR, loop);
}

JNIEXPORT void JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeStopVoice(
        JNIEnv*, jobject, jint stopKey) {
    if (sEngine) sEngine->stopVoice(stopKey);
}

} // extern "C"
