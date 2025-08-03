#include "AudioEngine.h"
#include <android/log.h>
#include <unistd.h> // For gettid()
#include <limits> // Required for std::numeric_limits

// THIS MUST MATCH MusicalNote.count in Kotlin
#define TOTAL_MUSICAL_NOTES 20

AudioEngine::AudioEngine() {
    // Frequencies for C#3 to G#4. Order MUST MATCH MusicalNote enum in Kotlin.

    mNoteFrequencies[0] = 138.59;  // CS3 ("C#3")
    mNoteFrequencies[1] = 146.83;  // D3 ("D3")
    mNoteFrequencies[2] = 155.56;  // DS3 ("D#3", "Eb3")
    mNoteFrequencies[3] = 164.81;  // E3 ("E3")
    mNoteFrequencies[4] = 174.61;  // F3 ("F3")
    mNoteFrequencies[5] = 185.00;  // FS3 ("F#3")
    mNoteFrequencies[6] = 196.00;  // G3 ("G3")
    mNoteFrequencies[7] = 207.65;  // GS3 ("G#3", "Ab3")
    mNoteFrequencies[8] = 220.00;  // A3 ("A3")
    mNoteFrequencies[9] = 233.08;  // AS3 ("A#3", "Bb3")
    mNoteFrequencies[10] = 246.94; // B3 ("B3")
    mNoteFrequencies[11] = 261.63; // C4 ("C4")
    mNoteFrequencies[12] = 277.18; // CS4 ("C#4")
    mNoteFrequencies[13] = 293.66; // D4 ("D4")
    mNoteFrequencies[14] = 311.13; // DS4 ("D#4", "Eb4")
    mNoteFrequencies[15] = 329.63; // E4 ("E4")
    mNoteFrequencies[16] = 349.23; // F4 ("F4")
    mNoteFrequencies[17] = 369.99; // FS4 ("F#4")
    mNoteFrequencies[18] = 392.00; // G4 ("G4")
    mNoteFrequencies[19] = 415.30; // GS4 ("G#4", "Ab4")

    // ... rest of constructor, start();
    //start();  not called here, is called in the JNI native start function
}

AudioEngine::~AudioEngine() {
    stop();
}

bool AudioEngine::start() {
    std::lock_guard<std::mutex> lock(mLock);
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setDataCallback(this)
            ->openStream(mStream);

    if (mStream) {
        mStream->requestStart();
        int32_t sampleRate = mStream->getSampleRate();
        for (auto &synth : mSynths) {
            synth.setSampleRate(sampleRate);
        }
        return true; // Indicate success
    } else {
        return false; // Indicate failure
    }
}

void AudioEngine::stop() {
    if (mStream) {
        mStream->stop();
        mStream->close();
        mStream.reset();
    }
}

void AudioEngine::playNote(int noteId) {
    if (noteId < 0 || noteId >= TOTAL_MUSICAL_NOTES) {
        __android_log_print(ANDROID_LOG_ERROR, "AudioEngine", "Invalid noteId: %d", noteId);
        return;
    }

    if (!mStream) {
        __android_log_print(ANDROID_LOG_ERROR, "AudioEngine", "Audio stream is not open!");
        return;
    }

    std::lock_guard<std::mutex> lock(mLock); // Protect access to mSynths and mNextNoteGeneration

    // 1. Find an inactive synth
    for (auto &synth : mSynths) {
    if (!synth.isPlaying()) {
        uint64_t currentGeneration = mNextNoteGeneration++;
        synth.start(mNoteFrequencies[noteId], currentGeneration);
        __android_log_print(ANDROID_LOG_INFO, "AudioEngine", "Played noteId %d on new synth, gen %llu", noteId, currentGeneration);
        return;
    }
}

// 2. If all synths are busy, find the oldest one to steal
    __android_log_print(ANDROID_LOG_WARN, "AudioEngine", "All synths busy. Attempting to steal oldest voice for noteId %d.", noteId);

    PanSynth* oldestSynth = nullptr;
    uint64_t oldestGeneration = std::numeric_limits<uint64_t>::max();

    for (auto &synth : mSynths) {
        if (synth.getGeneration() < oldestGeneration) { // Find synth with the smallest generation number
            oldestGeneration = synth.getGeneration();
            oldestSynth = &synth;
        }
}

    if (oldestSynth != nullptr) {
        uint64_t currentGeneration = mNextNoteGeneration++;
        __android_log_print(ANDROID_LOG_INFO, "AudioEngine", "Stealing synth (gen %llu) for noteId %d (new gen %llu)", oldestGeneration, noteId, currentGeneration);
        // It's good practice for a synth's start() to reset its state (phase, envelope, etc.)
        oldestSynth->start(mNoteFrequencies[noteId], currentGeneration);
    } else {
        // Should not happen if MAX_POLYPHONY > 0, but as a fallback:
        __android_log_print(ANDROID_LOG_ERROR, "AudioEngine", "Could not find an oldest synth to steal. This is unexpected.");
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) {

    auto *floatData = static_cast<float *>(audioData);

    // Zero out the buffer
    for (int i = 0; i < numFrames * oboeStream->getChannelCount(); ++i) {
        floatData[i] = 0.0f;
    }

    // No lock needed here if synth.render() and synth.isPlaying() are thread-safe
    // or if playNote is the only function modifying synth states and is locked.
    // However, if synth.stop() can be called from render (e.g. envelope finished),
    // and playNote modifies mSynths, mLock in playNote is good.
    // The current mLock in playNote protects mSynths during iteration and modification.
    // onAudioReady reads from mSynths. A lock here could cause priority inversion
    // if it blocks the audio thread waiting for playNote.
    // A lock-free approach or a try_lock would be better if contention is an issue.
    // For now, assuming mSynths array itself doesn't change, and synth methods are safe.
    // The main protection provided by mLock in playNote is for selecting/starting a synth.
    // std::lock_guard<std::mutex> lock(mLock);
    for (auto &synth : mSynths) {
        if (synth.isPlaying()) {
            synth.render(floatData, oboeStream->getChannelCount(), numFrames);
        }
    }

    bool clippingDetectedThisCallback = false;
    for (int i = 0; i < numFrames * oboeStream->getChannelCount(); ++i) {
        if (floatData[i] > 1.0f || floatData[i] < -1.0f) {
            clippingDetectedThisCallback = true;
            // You can apply clipping here immediately if desired
            // if (floatData[i] > 1.0f) floatData[i] = 1.0f;
            // else if (floatData[i] < -1.0f) floatData[i] = -1.0f;
            break; // Found clipping, no need to check further in this callback for logging purposes
        }
    }

    if (clippingDetectedThisCallback) {
        //__android_log_print(ANDROID_LOG_WARN, "AudioEngine", "Clipping detected in onAudioReady callback!");
        __android_log_print(ANDROID_LOG_WARN, "AudioEngine", "Clipping detected in onAudioReady callback! numFrames: %d", numFrames);
    }

    // Then, if you haven't applied clipping yet, apply it now (or your limiter)
    for (int i = 0; i < numFrames * oboeStream->getChannelCount(); ++i) {
        if (floatData[i] > 1.0f) {
            floatData[i] = 1.0f;
        } else if (floatData[i] < -1.0f) {
            floatData[i] = -1.0f;
        }
    }

    return oboe::DataCallbackResult::Continue;
}