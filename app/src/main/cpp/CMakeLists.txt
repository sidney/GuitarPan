cmake_minimum_required(VERSION 3.22.1)
project(guitar_pan)

set(OBOE_DIR ${CMAKE_SOURCE_DIR}/../../../../oboe)   # or FetchContent
add_subdirectory(${OBOE_DIR} ./oboe)

add_library(guitar_pan SHARED
    native-lib.cpp
    GuitarPanEngine.cpp
)
target_include_directories(guitar_pan PRIVATE
    ${OBOE_DIR}/include
)
target_link_libraries(guitar_pan oboe log android)
