cmake_minimum_required(VERSION 3.22.1)
project(unipad_audio)

# Oboe - Google's low-latency audio library
find_package(oboe REQUIRED CONFIG)

# Android libraries
find_library(log-lib log)
find_library(android-lib android)

add_library(unipad_audio SHARED
        AudioEngine.cpp
        SoundBank.cpp
        jni_bridge.cpp
)

target_include_directories(unipad_audio PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(unipad_audio
        oboe::oboe
        ${log-lib}
        ${android-lib}
        mediandk
        OpenSLES
)

target_compile_options(unipad_audio PRIVATE -std=c++17 -O2)
target_link_options(unipad_audio PRIVATE "-Wl,-z,max-page-size=16384")
