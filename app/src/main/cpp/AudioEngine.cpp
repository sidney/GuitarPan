#include "AudioEngine.h"
#include <cmath> // Required for std::tanh
#include <algorithm> // Required for std::abs (though cmath might also provide it for floats)
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

    // 1. Zero out the buffer
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

    // 2. Render all active synths (they add their output)
    // std::lock_guard<std::mutex> lock(mLock); // Consider if needed based on synth.render safety
    for (auto &synth : mSynths) {
        if (synth.isPlaying()) {
            synth.render(floatData, oboeStream->getChannelCount(), numFrames);
        }
    }

    // 3. Apply Master Volume Boost (Makeup Gain BEFORE soft clipping)
    // Experiment with this value. Higher values will drive the soft clipper harder.
    // Start with a moderate value, e.g., 1.5 to 3.0.
    // If your individual synths are already outputting at -12dBFS to -18dBFS peaks
    // when summed, a makeupGain of 2.0 to 4.0 might be appropriate.
    const float makeupGain = 2.5f; // Example: boost by +~8dB if this was 2.5 (20*log10(2.5))

    for (int i = 0; i < numFrames * oboeStream->getChannelCount(); ++i) {
        floatData[i] *= makeupGain;
    }

    // 4. Apply a Soft Clipper using std::tanh
    // This will smoothly handle peaks that now exceed +/- 1.0 after the makeupGain.
    // The 'drive' parameter controls how quickly the signal saturates.
    // A drive of 1.0 is a good starting point. Higher values increase saturation.
    const float softClipDrive = 1.0f; // Adjust this to control saturation effect (e.g., 0.5 to 2.0 or higher)

    // For monitoring how much the soft clipper is working (optional logging)
    float maxSampleBeforeSoftClip = 0.0f;
    float maxSampleAfterSoftClip = 0.0f;
    bool softClippingOccurred = false;

    for (int i = 0; i < numFrames * oboeStream->getChannelCount(); ++i) {
        float currentSample = floatData[i];

        if (std::abs(currentSample) > maxSampleBeforeSoftClip) {
            maxSampleBeforeSoftClip = std::abs(currentSample);
        }

        // Apply tanh for soft clipping.
        // The result of tanh(x) is always in the range [-1, 1].
        // Multiplying by softClipDrive inside tanh makes the "knee" harder or softer.
        float clippedSample = std::tanh(currentSample * softClipDrive);

        // Optional: If you want to ensure the output of tanh doesn't lose perceived loudness
        // for signals that weren't originally far above 1.0, you might normalize.
        // However, a simple tanh is often used directly for its saturation character.
        // For a more transparent soft clipper, one might divide by tanh(softClipDrive)
        // if softClipDrive is also the intended max input before full saturation.
        // Example with normalization (can make the effect more subtle if drive is low):
        // if (softClipDrive > 0) { // Avoid division by zero if drive could be 0
        //    clippedSample = std::tanh(currentSample * softClipDrive) / std::tanh(softClipDrive);
        // } else {
        //    clippedSample = currentSample; // Or handle as no clipping if drive is 0
        // }
        // For now, let's use the simpler direct tanh output for its characteristic sound.

        floatData[i] = clippedSample;

        if (std::abs(currentSample) > 1.0f && std::abs(clippedSample) < std::abs(currentSample)) {
            // This crude check indicates the soft clipper had an effect on a sample that was over 1.0
            softClippingOccurred = true;
        }
        if (std::abs(clippedSample) > maxSampleAfterSoftClip) {
            maxSampleAfterSoftClip = std::abs(clippedSample);
        }
    }

    // 5. Logging (Optional - for debugging the soft clipper's effect)
    // This log can be conditional or removed once you're happy with the sound.
    // It will tell you the peak *before* soft clipping (to see how hard it was driven)
    // and if the soft clipper was engaged.
    if (maxSampleBeforeSoftClip > 1.0f || softClippingOccurred) { // Log if input was hot or clipper worked
        // Note: numFrames here is the actual frames in this callback
        __android_log_print(ANDROID_LOG_INFO, "AudioEngine",
                            "Soft Clipper engaged. Peak IN: %.2f, Peak OUT: %.2f, (numFrames: %d)",
                            maxSampleBeforeSoftClip, maxSampleAfterSoftClip, numFrames);
    }

    // The output is now soft-clipped and should be within [-1.0, 1.0]
    // (tanh theoretically approaches +/-1 but might not exactly reach it for finite inputs,
    // though practically it's very close for inputs > 3 or 4).
    // A final hard clip is usually not strictly necessary with tanh but can be added
    // as an absolute safety for extremely large, unexpected float values if you're paranoid.
    // for (int i = 0; i < numFrames * oboeStream->getChannelCount(); ++i) {
    //    if (floatData[i] > 1.0f) floatData[i] = 1.0f;
    //    else if (floatData[i] < -1.0f) floatData[i] = -1.0f;
    // }

    return oboe::DataCallbackResult::Continue;
}