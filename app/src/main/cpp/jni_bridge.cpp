#include <jni.h>
#include <android/log.h>
#include <memory>
#include <mutex>
#include "AudioEngine.h"

#define LOG_TAG "UniPadAudioJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// One engine for the life of the process. It used to be destroyed on every stop and
// re-created on start; a callback still in flight on some low-end devices then hit a
// destroyed mutex and the process aborted (Play vitals: SIGABRT in libc++ under liboboe,
// unipad-android #37). Streams are opened and closed, the engine object stays.
static std::unique_ptr<AudioEngine> sEngine;
static std::once_flag sEngineOnce;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeStart(JNIEnv*, jobject) {
    // Two runners can start at once (leaving and reopening a pack quickly); both must see the
    // same engine rather than each assigning its own.
    std::call_once(sEngineOnce, [] { sEngine = std::make_unique<AudioEngine>(); });
    return sEngine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeStop(JNIEnv*, jobject) {
    if (sEngine) {
        sEngine->stop();
    }
}

JNIEXPORT jint JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeLoadSound(
        JNIEnv* env, jobject, jshortArray pcmData, jint numFrames, jint channels, jint sampleRate) {
    if (!sEngine || !pcmData || numFrames <= 0 || channels < 1 || channels > 8 ||
        sampleRate < 8000 || sampleRate > 384000) return -1;
    const jlong samples = static_cast<jlong>(numFrames) * channels;
    if (samples > env->GetArrayLength(pcmData)) return -1;
    try {
        std::vector<int16_t> data(static_cast<size_t>(samples));
        env->GetShortArrayRegion(pcmData, 0, static_cast<jsize>(samples), data.data());
        if (env->ExceptionCheck()) return -1;
        return sEngine->loadSound(std::move(data), numFrames, channels, sampleRate);
    } catch (const std::bad_alloc&) {
        return SoundBank::LOAD_MEMORY_FAILURE;
    }

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

JNIEXPORT void JNICALL
Java_com_kimjisub_launchpad_audio_OboeAudioEngine_nativeStopAllVoices(JNIEnv*, jobject) {
    if (sEngine) sEngine->stopAllVoices();
}

} // extern "C"
