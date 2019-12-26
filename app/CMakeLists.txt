cmake_minimum_required(VERSION 3.4.1)

find_library( log-lib
              log )
file(GLOB my_source src/main/cpp/*.cpp src/main/cpp/*.c)
set(distribution_DIR ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI})

add_library( avutil-55
             SHARED
             IMPORTED )
set_target_properties( avutil-55
                       PROPERTIES IMPORTED_LOCATION
                       ${distribution_DIR}/libavutil-55.so)

add_library( swresample-2
             SHARED
             IMPORTED )
set_target_properties( swresample-2
                       PROPERTIES IMPORTED_LOCATION
                       ${distribution_DIR}/libswresample-2.so)

add_library( avcodec-57
             SHARED
             IMPORTED )
set_target_properties( avcodec-57
                       PROPERTIES IMPORTED_LOCATION
                       ${distribution_DIR}/libavcodec-57.so)

add_library( avfilter-6
             SHARED
             IMPORTED )
set_target_properties( avfilter-6
                       PROPERTIES IMPORTED_LOCATION
                       ${distribution_DIR}/libavfilter-6.so)

add_library( swscale-4
             SHARED
             IMPORTED )
set_target_properties( swscale-4
                       PROPERTIES IMPORTED_LOCATION
                       ${distribution_DIR}/libswscale-4.so)

add_library( avdevice-57
             SHARED
             IMPORTED)
set_target_properties( avdevice-57
                       PROPERTIES IMPORTED_LOCATION
                       ${distribution_DIR}/libavdevice-57.so )

add_library( avformat-57
             SHARED
             IMPORTED )
set_target_properties( avformat-57
                       PROPERTIES IMPORTED_LOCATION
                       ${distribution_DIR}/libavformat-57.so)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

add_library( native-lib
             SHARED
             ${my_source})

include_directories(src/main/jniLibs/include)

target_link_libraries(native-lib
        swresample-2
        avcodec-57
        avfilter-6
        swscale-4
        avdevice-57
        avformat-57
        avutil-55
        log
        android
        OpenSLES
        ${log-lib} )