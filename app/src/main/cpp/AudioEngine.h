#ifndef GUITARPAN_AUDIOENGINE_H
#define GUITARPAN_AUDIOENGINE_H

#include <oboe/Oboe.h>
#include <vector>
#include <mutex>
#include <atomic>
#include "PanSynth.h"

#define MAX_POLYPHONY 10
#define TOTAL_MUSICAL_NOTES 20 // As defined in NoteLayout.kt

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    bool start();
    void stop();
    void playNote(int noteId);

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *oboeStream,
            void *audioData,
            int32_t numFrames) override;

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::mutex mLock;

    PanSynth mSynths[MAX_POLYPHONY];
    double mNoteFrequencies[TOTAL_MUSICAL_NOTES];
    std::atomic<uint64_t> mNextNoteGeneration{1}; // Atomic for thread safety if playNote can be called from multiple threads,
    // though mLock already provides some protection for this.
    // Initialized to 1. 0 can mean "not playing".
};

#endif //GUITARPAN_AUDIOENGINE_H