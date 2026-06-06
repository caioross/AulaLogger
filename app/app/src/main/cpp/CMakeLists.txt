cmake_minimum_required(VERSION 3.22.1)
project("whisperjni")

# Configuração do whisper.cpp
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
# OpenMP traz ganho significativo nas multiplicações de matriz (~20-40%).
# NDK r25 inclui libomp para Android.
set(GGML_OPENMP            ON  CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)

# Otimizações ARM (sem -ffast-math: ggml exige non-finite math)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    # -march=armv8.2-a+dotprod em TODAS as fontes nativas arm64: liga os kernels
    # de matmul quantizada (vdotq_s32) do ggml para os modelos q5_*. Sem isto, o
    # ggml compila no baseline armv8-a (cross-compile, GGML_NATIVE off) e a
    # inferência fica ~1.5-2x mais lenta. Cobre ~99% dos aparelhos 64-bit de
    # 2018+. (i8mm fica de fora de propósito: exige ARMv8.6 / ~2021+ e dispararia
    # SIGILL em aparelhos mais antigos.)
    #
    # Aplicado via add_compile_options (e NÃO GGML_CPU_ARM_ARCH) de propósito: o
    # caminho GGML_CPU_ARM_ARCH dispara um probe de features que roda o clang sem
    # --target e cospe um "error: unknown target CPU" cosmético no host Windows.
    add_compile_options(-O3 -fno-finite-math-only -march=armv8.2-a+dotprod)
elseif (ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-O3 -fno-finite-math-only -mfpu=neon -mfloat-abi=softfp)
endif()

add_subdirectory(whisper.cpp)

add_library(whisperjni SHARED whisper-jni.cpp)

target_include_directories(whisperjni PRIVATE
    ${CMAKE_SOURCE_DIR}/whisper.cpp/include
    ${CMAKE_SOURCE_DIR}/whisper.cpp/ggml/include
)

target_link_libraries(whisperjni
    whisper
    ggml
    log
    android
)
