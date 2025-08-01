cmake_minimum_required(VERSION 3.22.1)

project("guitarpan" CXX)

include(FetchContent)
FetchContent_Declare(
        oboe
        GIT_REPOSITORY https://github.com/google/oboe.git
        GIT_TAG        1.9.3   # or latest stable
)
FetchContent_MakeAvailable(oboe)

add_library(guitarpan SHARED
        native-lib.cpp
        AudioEngine.cpp
        PanSynth.cpp)

target_link_libraries(guitarpan
        oboe
        android
        log)