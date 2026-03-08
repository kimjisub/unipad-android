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

    // Unload a specific sound
    void unload(int soundId);

    // Unload all sounds
    void unloadAll();

private:
    mutable std::mutex mutex_;
    std::vector<std::unique_ptr<SoundBuffer>> sounds_;
};
