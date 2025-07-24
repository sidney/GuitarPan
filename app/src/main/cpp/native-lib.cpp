#include <jni.h>
#include "GuitarPanEngine.h"
static GuitarPanEngine engine;

extern "C" {
JNIEXPORT jboolean JNICALL
Java_com_example_guitarpan_MainActivity_nativeStartEngine(JNIEnv *, jobject) {
    return engine.start();
}
JNIEXPORT void JNICALL
Java_com_example_guitarpan_MainActivity_nativeStopEngine(JNIEnv *, jobject) {
    engine.stop();
}
JNIEXPORT void JNICALL
Java_com_example_guitarpan_MainActivity_nativePlayNote(JNIEnv *, jobject, jint note, jint velocity) {
    engine.playNote(note, static_cast<float>(velocity) / 127.0f);
}
}
