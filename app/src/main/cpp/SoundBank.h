#pragma once

#include <vector>
#include <memory>
#include <mutex>
#include <cstdint>

struct SoundBuffer {
    std::vector<int16_t> data;   // interleaved PCM samples
    int channels = 1;
    int sampleRate = 44100;
    int numFrames = 0;           // number of frames (samples per channel)
};

class SoundBank {
public:
    // Load PCM data, returns soundId (0-based index)
    int load(const int16_t* data, int numFrames, int channels, int sampleRate);

    // Get a loaded sound buffer (nullptr if not found)
    const SoundBuffer* get(int soundId) const;

    // Remove a sound from the bank and hand its buffer to the caller. Freeing a large buffer
    // takes long enough to starve the audio callback, so callers drop it outside their locks.
    std::unique_ptr<SoundBuffer> take(int soundId);

    // Remove every sound; same ownership rule as take().
    std::vector<std::unique_ptr<SoundBuffer>> takeAll();

private:
    mutable std::mutex mutex_;
    std::vector<std::unique_ptr<SoundBuffer>> sounds_;
};
