// Run on the host: clang++ -std=c++17 app/src/test/cpp/SoundBankTest.cpp app/src/main/cpp/SoundBank.cpp -o sound-bank-test
#include "../../main/cpp/SoundBank.h"
#include <cstdlib>
#include <new>
#include <iostream>

static bool failAllocation = false;
void* operator new(std::size_t size) {
    if (failAllocation) { failAllocation = false; throw std::bad_alloc(); }
    if (void* ptr = std::malloc(size)) return ptr;
    throw std::bad_alloc();
}
void operator delete(void* ptr) noexcept { std::free(ptr); }

int main() {
    SoundBank bank;
    std::vector<int16_t> pcm = {1, 2};
    failAllocation = true;
    try {
        const int result = bank.load(std::move(pcm), 1, 2, 48000);
        if (result != -2) { std::cerr << "allocation failure must return -2, got " << result << '\n'; return 1; }
    } catch (const std::bad_alloc&) {
        std::cerr << "allocation failure escaped SoundBank::load\n";
        return 1;
    }
    std::vector<int16_t> recovered = {1, 2};
    const int16_t* owned = recovered.data();
    const int id = bank.load(std::move(recovered), 1, 2, 48000);
    if (id < 0 || !bank.get(id) || bank.get(id)->data[1] != 2) return 2;
    if (bank.get(id)->data.data() != owned) { std::cerr << "PCM was copied instead of adopted\n"; return 4; }
    if (bank.load(std::move(pcm), 1, 9, 48000) >= 0) { std::cerr << "unsupported channel count accepted\n"; return 3; }
    std::cout << "SoundBank allocation recovery and PCM preservation passed\n";
}
