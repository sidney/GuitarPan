#include <jni.h>
#include "AudioEngine.h"

// It's better to use a raw pointer and manage its lifecycle explicitly.
static AudioEngine *audioEngine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_guitarpan_MainActivity_startAudioEngineNative([[maybe_unused]] JNIEnv *env, jobject /* this */) {
    if (audioEngine == nullptr) {
        audioEngine = new AudioEngine();
    }
    if (audioEngine->start()) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_com_example_guitarpan_MainActivity_stopAudioEngine([[maybe_unused]] JNIEnv *env, jobject /* this */) {
if (audioEngine) {
audioEngine->stop();
delete audioEngine;
audioEngine = nullptr;
}
}

JNIEXPORT void JNICALL
Java_com_example_guitarpan_MainActivity_playNote([[maybe_unused]] JNIEnv *env, jobject /* this */, jint noteId) {
if (audioEngine) {
audioEngine->playNote(noteId);
}
}

} // extern "C"