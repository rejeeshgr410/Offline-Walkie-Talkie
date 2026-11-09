cmake_minimum_required(VERSION 3.22.1)
project(codec2_jni)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/codec2
)

set(CODEC2_SRCS
    stubs.c
    codec2/codec2.c
    codec2/dump.c
    codec2/lpc.c
    codec2/nlp.c
    codec2/postfilter.c
    codec2/sine.c
    codec2/codec2_fft.c
    codec2/kiss_fft.c
    codec2/kiss_fftr.c
    codec2/interp.c
    codec2/lsp.c
    codec2/phase.c
    codec2/quantise.c
    codec2/pack.c
    codec2/codebook.c
    codec2/codebookd.c
    codec2/codebookdt.c
    codec2/codebookge.c
    codec2/codebookjvm.c
    codec2/codebooknewamp1.c
    codec2/codebooknewamp1_energy.c
    codec2/codebookres.c
    codec2/codebookvq.c
    codec2/newamp1.c
    codec2/linreg.c
    codec2/mbest.c
    codec2/c2wideband.c
    codec2/dct2.c
)

add_library(codec2_jni SHARED
    Codec2JNI.cpp
    ${CODEC2_SRCS}
)

find_library(log-lib log)
find_library(m-lib m)

target_link_libraries(codec2_jni
    ${log-lib}
    ${m-lib}
)
