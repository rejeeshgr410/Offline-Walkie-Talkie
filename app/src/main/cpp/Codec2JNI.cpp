#include <jni.h>
#include <cstdlib>
#include <cstring>
#include "codec2/codec2.h"

namespace Codec2Bridge {

    struct Context {
        struct CODEC2 *c2;
        short *buf;          // raw PCM audio frame
        unsigned char *bits; // encoded codec2 bytes
        int nsam;            // number of samples per frame (e.g. 160 or 320)
        int nbit;            // number of bits per frame
        int nbyte;           // size in bytes of one frame
    };

    static inline Context *getContext(jlong handle) {
        return reinterpret_cast<Context *>(handle);
    }

    static jlong nativeCreate(JNIEnv *env, jclass clazz, jint mode) {
        struct CODEC2 *c = codec2_create(mode);
        if (!c) return 0;
        
        Context *con = static_cast<Context *>(malloc(sizeof(Context)));
        if (!con) {
            codec2_destroy(c);
            return 0;
        }
        con->c2 = c;
        con->nsam = codec2_samples_per_frame(c);
        con->nbit = codec2_bits_per_frame(c);
        con->nbyte = (con->nbit + 7) / 8;
        con->buf = static_cast<short *>(malloc(con->nsam * sizeof(short)));
        con->bits = static_cast<unsigned char *>(malloc(con->nbyte * sizeof(unsigned char)));
        return reinterpret_cast<jlong>(con);
    }

    static jint nativeGetSamplesPerFrame(JNIEnv *env, jclass clazz, jlong handle) {
        Context *con = getContext(handle);
        return con ? con->nsam : 0;
    }

    static jint nativeGetBitsSize(JNIEnv *env, jclass clazz, jlong handle) {
        Context *con = getContext(handle);
        return con ? con->nbyte : 0;
    }

    static jint nativeDestroy(JNIEnv *env, jclass clazz, jlong handle) {
        Context *con = getContext(handle);
        if (con) {
            if (con->c2) codec2_destroy(con->c2);
            if (con->bits) free(con->bits);
            if (con->buf) free(con->buf);
            free(con);
        }
        return 0;
    }

    static jlong nativeEncode(JNIEnv *env, jclass clazz, jlong handle, jshortArray inPcm, jbyteArray outBits) {
        Context *con = getContext(handle);
        if (!con || !con->c2) return -1;

        jsize inLen = env->GetArrayLength(inPcm);
        jsize outLen = env->GetArrayLength(outBits);
        if (inLen < con->nsam || outLen < con->nbyte) return -2;

        env->GetShortArrayRegion(inPcm, 0, con->nsam, con->buf);
        codec2_encode(con->c2, con->bits, con->buf);
        env->SetByteArrayRegion(outBits, 0, con->nbyte, reinterpret_cast<jbyte *>(con->bits));
        return 0;
    }

    static jlong nativeDecode(JNIEnv *env, jclass clazz, jlong handle, jshortArray outPcm, jbyteArray inBits) {
        Context *con = getContext(handle);
        if (!con || !con->c2) return -1;

        jsize inLen = env->GetArrayLength(inBits);
        jsize outLen = env->GetArrayLength(outPcm);
        if (inLen < con->nbyte || outLen < con->nsam) return -2;

        env->GetByteArrayRegion(inBits, 0, con->nbyte, reinterpret_cast<jbyte *>(con->bits));
        codec2_decode_ber(con->c2, con->buf, con->bits, 0.0);
        env->SetShortArrayRegion(outPcm, 0, con->nsam, con->buf);
        return 0;
    }

    static const JNINativeMethod method_table[] = {
        {"create", "(I)J", reinterpret_cast<void *>(nativeCreate)},
        {"getSamplesPerFrame", "(J)I", reinterpret_cast<void *>(nativeGetSamplesPerFrame)},
        {"getBitsSize", "(J)I", reinterpret_cast<void *>(nativeGetBitsSize)},
        {"destroy", "(J)I", reinterpret_cast<void *>(nativeDestroy)},
        {"encode", "(J[S[B)J", reinterpret_cast<void *>(nativeEncode)},
        {"decode", "(J[S[B)J", reinterpret_cast<void *>(nativeDecode)}
    };

} // namespace Codec2Bridge

extern "C" jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("com/example/offlinewalkietalkie/voice/Codec2Native");
    if (!clazz) {
        return JNI_ERR;
    }

    jint ret = env->RegisterNatives(clazz, Codec2Bridge::method_table,
                                    sizeof(Codec2Bridge::method_table) / sizeof(Codec2Bridge::method_table[0]));
    env->DeleteLocalRef(clazz);
    return (ret == 0) ? JNI_VERSION_1_6 : JNI_ERR;
}
