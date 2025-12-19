# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("panoramapro")

# 导入opencv
set(OpenCV_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../opencv/sdk/native/jni)
find_package(OpenCV REQUIRED)
message(STATUS "OpenCV found: ${OpenCV_LIBS}")

# 导入eigen
include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../eigen
)

include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

file(GLOB_RECURSE ALL_SOURCE_FILES "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp")
file(GLOB_RECURSE ALL_HEADER_FILES "${CMAKE_CURRENT_SOURCE_DIR}/include/*.h")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # JNI 入口文件 (它在根目录，需要单独加)
        native-lib.cpp

        # 自动扫描到的所有 src 下的 cpp 文件
        ${ALL_SOURCE_FILES}

        # 把头文件加在这里只是为了让 IDE 显示它们，不影响编译
        ${ALL_HEADER_FILES}
)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        ${OpenCV_LIBS}
        android
        log
        jnigraphics)