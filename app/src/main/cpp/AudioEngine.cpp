#include "AudioEngine.h"
#include <android/log.h>
#include <unistd.h> // For gettid()

// THIS MUST MATCH MusicalNote.count in Kotlin
#define TOTAL_MUSICAL_NOTES 20

#define APPNAME "GuitarPanAudioEngine" // Define a log tag

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
        mIsSuccessfullyStarted = true;
        return true; // Indicate success
    } else {
        mIsSuccessfullyStarted = false;
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

    std::lock_guard<std::mutex> lock(mLock);
    // Find an inactive synth to play the note
    for (auto &synth : mSynths) {
        if (!synth.isPlaying()) {
            synth.start(mNoteFrequencies[noteId]);
            return;
        }
    }
    // If all synths are busy, we could choose to steal one, but for now we do nothing.
    __android_log_print(ANDROID_LOG_WARN, "AudioEngine", "All synths busy, noteId %d not played.", noteId);
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

    std::lock_guard<std::mutex> lock(mLock);
    for (auto &synth : mSynths) {
        if (synth.isPlaying()) {
            synth.render(floatData, oboeStream->getChannelCount(), numFrames);
        }
    }

    return oboe::DataCallbackResult::Continue;
}