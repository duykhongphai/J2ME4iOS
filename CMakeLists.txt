cmake_minimum_required(VERSION 3.16)
project(J2ME4iOS C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS ON)
if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
  set(CMAKE_BUILD_TYPE Release)
endif()

# ---------------- VM core (portable C) ----------------
file(GLOB J4I_CORE_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/core/*.c)
add_library(j4icore STATIC ${J4I_CORE_SOURCES})
target_include_directories(j4icore PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/core)
if(MSVC)
  target_compile_options(j4icore PRIVATE /W3 /O2)
  target_compile_definitions(j4icore PRIVATE _CRT_SECURE_NO_WARNINGS)
else()
  target_compile_options(j4icore PRIVATE -O2 -Wall -Wno-unused-parameter -fno-strict-aliasing)
  target_link_libraries(j4icore PUBLIC m)
endif()

# ---------------- runtime classes (CLDC 1.1 / MIDP 2.0) ----------------
find_package(Java COMPONENTS Development)
if(Java_FOUND)
  file(GLOB_RECURSE J4I_RUNTIME_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/runtime/src/*.java)
  set(J4I_RT_CLASSES ${CMAKE_CURRENT_BINARY_DIR}/runtime-classes)
  set(J4I_RT_JAR ${CMAKE_CURRENT_BINARY_DIR}/j4i-runtime.jar)
  set(J4I_RT_ARGS ${CMAKE_CURRENT_BINARY_DIR}/runtime-sources.txt)
  set(_list "")
  foreach(f ${J4I_RUNTIME_SOURCES})
    string(APPEND _list "\"${f}\"\n")
  endforeach()
  file(WRITE ${J4I_RT_ARGS} "${_list}")
  add_custom_command(
    OUTPUT ${J4I_RT_JAR}
    COMMAND ${CMAKE_COMMAND} -E rm -rf ${J4I_RT_CLASSES}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${J4I_RT_CLASSES}
    COMMAND ${Java_JAVAC_EXECUTABLE} -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8
            -bootclasspath ${J4I_RT_CLASSES} -d ${J4I_RT_CLASSES} @${J4I_RT_ARGS}
    COMMAND ${Java_JAR_EXECUTABLE} cf0 ${J4I_RT_JAR} -C ${J4I_RT_CLASSES} .
    DEPENDS ${J4I_RUNTIME_SOURCES}
    COMMENT "Building j4i-runtime.jar")
  add_custom_target(runtime ALL DEPENDS ${J4I_RT_JAR})
endif()

# ---------------- headless runner (all platforms, used by CI) ----------------
add_executable(j4i-headless platform/headless/main.c)
target_link_libraries(j4i-headless PRIVATE j4icore)
if(NOT WIN32)
  find_package(Threads REQUIRED)
  target_link_libraries(j4i-headless PRIVATE Threads::Threads)
endif()

# ---------------- desktop runner (Windows) ----------------
if(WIN32)
  add_executable(j4i platform/desktop/main_win.c)
  target_link_libraries(j4i PRIVATE j4icore gdi32 user32 winmm)
  if(MSVC)
    target_compile_definitions(j4i PRIVATE _CRT_SECURE_NO_WARNINGS)
  endif()
endif()
