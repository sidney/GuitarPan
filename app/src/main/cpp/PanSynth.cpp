#include "PanSynth.h"
#include <cstring> // For memset

constexpr double TWO_PI = 2.0 * M_PI;

PanSynth::PanSynth() {
    memset(mPhase, 0, sizeof(mPhase));
    memset(mPhaseIncrement, 0, sizeof(mPhaseIncrement));
    memset(mAmplitude, 0, sizeof(mAmplitude));
    memset(mDecay, 0, sizeof(mDecay));
}

void PanSynth::setSampleRate(double sampleRate) {
    mSampleRate = sampleRate;
}

void PanSynth::start(double frequency, uint64_t generation) {
    // Simplified acoustic model based on common steelpan characteristics
    // Harmonic 1: Fundamental
    mPhase[0] = 0.0;
    mPhaseIncrement[0] = (TWO_PI * frequency) / mSampleRate;
    mAmplitude[0] = 1.0;
    mDecay[0] = 0.99995;

    // Harmonic 2: An octave or other prominent partial
    mPhase[1] = 0.0;
    mPhaseIncrement[1] = (TWO_PI * frequency * 2.001) / mSampleRate; // Slightly detuned
    mAmplitude[1] = 0.6;
    mDecay[1] = 0.99992;

    // Harmonic 3: Another inharmonic partial
    mPhase[2] = 0.0;
    mPhaseIncrement[2] = (TWO_PI * frequency * 3.5) / mSampleRate;
    mAmplitude[2] = 0.4;
    mDecay[2] = 0.99985;

    mIsPlaying.store(true);
    mCurrentGeneration = generation;
}

bool PanSynth::isPlaying() const {
    return mIsPlaying.load();
}

uint64_t PanSynth::getGeneration() const {
    return mCurrentGeneration;
}

void PanSynth::render(float *audioData, int numChannels, int numFrames) {
    if (!isPlaying()) return;

    for (int i = 0; i < numFrames; ++i) {
        float sample = 0.0f;

        for (int j = 0; j < NUM_HARMONICS; ++j) {
            sample += sin(mPhase[j]) * mAmplitude[j];
            mPhase[j] += mPhaseIncrement[j];
            if (mPhase[j] >= TWO_PI) mPhase[j] -= TWO_PI;
            mAmplitude[j] *= mDecay[j];
        }

        // Apply to all channels (e.g., stereo)
        for (int k = 0; k < numChannels; ++k) {
            audioData[i * numChannels + k] += sample;
        }
    }

    // Stop playing if amplitude is negligible
    if (mAmplitude[0] < 0.001) {
        mIsPlaying.store(false);
    }
}