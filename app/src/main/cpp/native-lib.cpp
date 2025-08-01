#include <jni.h>
#include "AudioEngine.h"

// It's better to use a raw pointer and manage its lifecycle explicitly.
static AudioEngine *audioEngine = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_guitarpan_MainActivity_startAudioEngineNative(JNIEnv *env, jobject /* this */) {
if (audioEngine == nullptr) {
audioEngine = new AudioEngine();
}
audioEngine->start();
}

JNIEXPORT void JNICALL
Java_com_example_guitarpan_MainActivity_stopAudioEngine(JNIEnv *env, jobject /* this */) {
if (audioEngine) {
audioEngine->stop();
delete audioEngine;
audioEngine = nullptr;
}
}

JNIEXPORT void JNICALL
Java_com_example_guitarpan_MainActivity_playNote(JNIEnv *env, jobject /* this */, jint noteId) {
if (audioEngine) {
audioEngine->playNote(noteId);
}
}

} // extern "C"