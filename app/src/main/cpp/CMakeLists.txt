cmake_minimum_required(VERSION 3.22.1)
project(guitar_pan)

include(FetchContent)
FetchContent_Declare(
        oboe
        GIT_REPOSITORY https://github.com/google/oboe.git
        GIT_TAG        1.8.0   # or latest stable
)
FetchContent_MakeAvailable(oboe)

add_library(guitar_pan SHARED
    native-lib.cpp
    GuitarPanEngine.cpp
)
target_include_directories(guitar_pan PRIVATE
    ${OBOE_DIR}/include
)
target_link_libraries(guitar_pan oboe log android)
