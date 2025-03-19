cmake_minimum_required(VERSION 3.22.1)
project(listenhelp6_audio)

# 设置C++标准
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加源文件
add_library(audioproc SHARED
        audioproc.cpp
        AAudioProcessor.cpp
        AAudioProcessor.h
)

# 查找并链接AAudio库
find_library(log-lib log)
find_library(aaudio-lib aaudio)

# 链接库
target_link_libraries(audioproc
        ${log-lib}
        ${aaudio-lib}
) 