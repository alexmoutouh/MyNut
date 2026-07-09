cmake_minimum_required(VERSION 3.22.1)
project("mynut_llm")

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)

set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_SRC} build-llama)

add_library(mynut_llm SHARED mynut_llm_jni.cpp)

target_include_directories(mynut_llm PRIVATE
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
)

target_link_libraries(mynut_llm
    llama
    android
    log
)
