#include "SoundBank.h"
#include <utility>
#include <limits>
#include <new>

namespace {
bool validLayout(int frames, int channels, int rate) {
    return frames > 0 && channels >= 1 && channels <= 8 && rate >= 8000 && rate <= 384000 &&
           frames <= std::numeric_limits<int>::max() / channels;
}
}

int SoundBank::load(std::vector<int16_t>&& data, int numFrames, int channels, int sampleRate) {
    if (!validLayout(numFrames, channels, sampleRate) ||
        data.size() != static_cast<size_t>(numFrames) * channels) return -1;
    try {
        auto buf = std::make_unique<SoundBuffer>();
        buf->numFrames = numFrames;
        buf->channels = channels;
        buf->sampleRate = sampleRate;
        buf->data = std::move(data);
        std::lock_guard<std::mutex> lock(mutex_);
        for (size_t i = 0; i < sounds_.size(); i++) {
            if (!sounds_[i]) {
                sounds_[i] = std::move(buf);
                return static_cast<int>(i);
            }
        }
        sounds_.push_back(std::move(buf));
        return static_cast<int>(sounds_.size() - 1);
    } catch (const std::bad_alloc&) {
        return LOAD_MEMORY_FAILURE;
    }
}

const SoundBuffer* SoundBank::get(int soundId) const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (soundId < 0 || soundId >= static_cast<int>(sounds_.size()))
        return nullptr;
    return sounds_[soundId].get();
}

std::unique_ptr<SoundBuffer> SoundBank::take(int soundId) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (soundId < 0 || soundId >= static_cast<int>(sounds_.size())) return nullptr;
    return std::move(sounds_[soundId]);
}

std::vector<std::unique_ptr<SoundBuffer>> SoundBank::takeAll() {
    std::lock_guard<std::mutex> lock(mutex_);
    return std::exchange(sounds_, {});
}
