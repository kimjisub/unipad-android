#pragma once

#include <oboe/Oboe.h>
#include <memory>
#include <mutex>
#include <vector>
#include <atomic>
#include "SoundBank.h"

struct ActiveVoice {
    int soundId = -1;
    double position = 0.0;  // fractional position for resampling
    double playbackRate = 1.0; // sourceSampleRate / outputSampleRate
    float volumeL = 1.0f;
    float volumeR = 1.0f;
    int loopCount = 0;     // 0=one-shot, -1=infinite, >0=remaining loops
    bool active = false;
    int stopKey = 0;       // unique key for stop tracking
};

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    static constexpr int MAX_VOICES = 64;

    AudioEngine();
    ~AudioEngine();

    bool start();
    void stop();

    // Load a decoded PCM buffer, returns soundId
    int loadSound(const int16_t* data, int numFrames, int channels, int sampleRate);

    // Unload a sound by ID
    void unloadSound(int soundId);

    // Unload all sounds
    void unloadAll();

    // Play a sound, returns a stopKey for later stopping
    int play(int soundId, float volumeL, float volumeR, int loop);

    // Stop a specific voice by stopKey
    void stopVoice(int stopKey);

    // Oboe callback
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) override;

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex voiceMutex_;
    ActiveVoice voices_[MAX_VOICES];
    SoundBank soundBank_;
    std::atomic<int> nextStopKey_{1};
    int outputSampleRate_ = 48000;
};
