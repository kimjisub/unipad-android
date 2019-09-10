# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Werror")

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add.library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.


### INCLUDE OBOE LIBRARY ###

# Set the path to the Oboe library directory
set (OBOE_DIR /Users/kimjisub/Develop/oboe)

# Add the Oboe library as a subproject. Since Oboe is an out-of-tree source library we must also
# specify a binary directory
add_subdirectory(${OBOE_DIR} ./oboe-bin)

# Include the Oboe headers
include_directories(${OBOE_DIR}/include)

### END OBOE INCLUDE SECTION ###

# Debug utilities
set (DEBUG_UTILS_PATH "../debug-utils")
set (DEBUG_UTILS_SOURCES ${DEBUG_UTILS_PATH}/trace.cpp)
include_directories(${DEBUG_UTILS_PATH})

# App specific sources
set (APP_DIR src/main/cpp)
file (GLOB_RECURSE APP_SOURCES
		${APP_DIR}/jni_bridge.cpp
		${APP_DIR}/PlayAudioEngine.cpp
		${APP_DIR}/SineGenerator.cpp
		)

# Build the libhello-oboe library
add_library(hello-oboe SHARED
		${DEBUG_UTILS_SOURCES}
		${APP_SOURCES}
		)

# Specify the libraries needed for hello-oboe
target_link_libraries(hello-oboe android log oboe)