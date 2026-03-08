#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <cstring>

#define LOG_TAG "UniPadAudio"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine() {
    std::memset(voices_, 0, sizeof(voices_));
}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::I16)
           ->setChannelCount(oboe::ChannelCount::Stereo)
           ->setUsage(oboe::Usage::Game)
           ->setContentType(oboe::ContentType::Sonification)
           ->setDataCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open stream: %s", oboe::convertToText(result));
        return false;
    }

    outputSampleRate_ = stream_->getSampleRate();
    LOGI("Opened stream: sampleRate=%d, framesPerBurst=%d",
         outputSampleRate_, stream_->getFramesPerBurst());

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start stream: %s", oboe::convertToText(result));
        stream_->close();
        return false;
    }

    LOGI("Audio engine started successfully");
    return true;
}

void AudioEngine::stop() {
    if (stream_) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
    }
}

int AudioEngine::loadSound(const int16_t* data, int numFrames, int channels, int sampleRate) {
    return soundBank_.load(data, numFrames, channels, sampleRate);
}

void AudioEngine::unloadSound(int soundId) {
    // Stop any voices playing this sound
    std::lock_guard<std::mutex> lock(voiceMutex_);
    for (auto& v : voices_) {
        if (v.active && v.soundId == soundId) {
            v.active = false;
        }
    }
    soundBank_.unload(soundId);
}

void AudioEngine::unloadAll() {
    std::lock_guard<std::mutex> lock(voiceMutex_);
    for (auto& v : voices_) {
        v.active = false;
    }
    soundBank_.unloadAll();
}

int AudioEngine::play(int soundId, float volumeL, float volumeR, int loop) {
    const SoundBuffer* buf = soundBank_.get(soundId);
    if (!buf) return 0;

    double rate = static_cast<double>(buf->sampleRate) / outputSampleRate_;
    int key = nextStopKey_.fetch_add(1);

    std::lock_guard<std::mutex> lock(voiceMutex_);

    // Find a free voice slot
    for (auto& v : voices_) {
        if (!v.active) {
            v.soundId = soundId;
            v.position = 0.0;
            v.playbackRate = rate;
            v.volumeL = volumeL;
            v.volumeR = volumeR;
            v.loopCount = loop;
            v.active = true;
            v.stopKey = key;
            return key;
        }
    }

    // All voices busy - steal the oldest one (first in array)
    auto& v = voices_[0];
    v.soundId = soundId;
    v.position = 0.0;
    v.playbackRate = rate;
    v.volumeL = volumeL;
    v.volumeR = volumeR;
    v.loopCount = loop;
    v.active = true;
    v.stopKey = key;
    return key;
}

void AudioEngine::stopVoice(int stopKey) {
    if (stopKey == 0) return;
    std::lock_guard<std::mutex> lock(voiceMutex_);
    for (auto& v : voices_) {
        if (v.active && v.stopKey == stopKey) {
            v.active = false;
            return;
        }
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames) {

    auto* output = static_cast<int16_t*>(audioData);
    std::memset(output, 0, numFrames * 2 * sizeof(int16_t)); // stereo

    std::lock_guard<std::mutex> lock(voiceMutex_);

    for (auto& v : voices_) {
        if (!v.active) continue;

        const SoundBuffer* buf = soundBank_.get(v.soundId);
        if (!buf) {
            v.active = false;
            continue;
        }

        for (int frame = 0; frame < numFrames; frame++) {
            int idx = static_cast<int>(v.position);

            if (idx >= buf->numFrames) {
                // Handle looping
                if (v.loopCount == -1) {
                    v.position -= buf->numFrames;
                    idx = static_cast<int>(v.position);
                } else if (v.loopCount > 0) {
                    v.loopCount--;
                    v.position -= buf->numFrames;
                    idx = static_cast<int>(v.position);
                } else {
                    v.active = false;
                    break;
                }
            }

            // Linear interpolation between samples
            float frac = static_cast<float>(v.position - idx);
            int nextIdx = idx + 1;
            if (nextIdx >= buf->numFrames) nextIdx = 0;

            float sampleL, sampleR;
            if (buf->channels == 2) {
                sampleL = buf->data[idx * 2] * (1.0f - frac) + buf->data[nextIdx * 2] * frac;
                sampleR = buf->data[idx * 2 + 1] * (1.0f - frac) + buf->data[nextIdx * 2 + 1] * frac;
            } else {
                float s = buf->data[idx] * (1.0f - frac) + buf->data[nextIdx] * frac;
                sampleL = sampleR = s;
            }

            // Mix with clamping
            int32_t mixL = output[frame * 2] + static_cast<int32_t>(sampleL * v.volumeL);
            int32_t mixR = output[frame * 2 + 1] + static_cast<int32_t>(sampleR * v.volumeR);

            output[frame * 2] = static_cast<int16_t>(
                std::clamp(mixL, (int32_t)-32768, (int32_t)32767));
            output[frame * 2 + 1] = static_cast<int16_t>(
                std::clamp(mixR, (int32_t)-32768, (int32_t)32767));

            v.position += v.playbackRate;
        }
    }

    return oboe::DataCallbackResult::Continue;
}
