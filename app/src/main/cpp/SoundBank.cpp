#include "SoundBank.h"

int SoundBank::load(const int16_t* data, int numFrames, int channels, int sampleRate) {
    auto buf = std::make_unique<SoundBuffer>();
    buf->numFrames = numFrames;
    buf->channels = channels;
    buf->sampleRate = sampleRate;
    buf->data.assign(data, data + (numFrames * channels));

    std::lock_guard<std::mutex> lock(mutex_);

    // Reuse a freed slot if available
    for (size_t i = 0; i < sounds_.size(); i++) {
        if (!sounds_[i]) {
            sounds_[i] = std::move(buf);
            return static_cast<int>(i);
        }
    }

    sounds_.push_back(std::move(buf));
    return static_cast<int>(sounds_.size() - 1);
}

const SoundBuffer* SoundBank::get(int soundId) const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (soundId < 0 || soundId >= static_cast<int>(sounds_.size()))
        return nullptr;
    return sounds_[soundId].get();
}

void SoundBank::unload(int soundId) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (soundId >= 0 && soundId < static_cast<int>(sounds_.size())) {
        sounds_[soundId].reset();
    }
}

void SoundBank::unloadAll() {
    std::lock_guard<std::mutex> lock(mutex_);
    sounds_.clear();
}
