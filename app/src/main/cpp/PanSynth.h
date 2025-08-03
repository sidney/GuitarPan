#ifndef GUITARPAN_PANSYNTH_H
#define GUITARPAN_PANSYNTH_H

#include <atomic>
#include <cmath>

#define NUM_HARMONICS 3

class PanSynth {
public:
    PanSynth();
    void setSampleRate(double sampleRate);
    void start(double frequency, uint64_t generation);
    bool isPlaying() const;
    void render(float *audioData, int numChannels, int numFrames);

    uint64_t getGeneration() const;

private:
    std::atomic<bool> mIsPlaying {false};
    double mSampleRate = 48000.0;
//    double mFrequency = 0.0;

    double mPhase[NUM_HARMONICS];
    double mPhaseIncrement[NUM_HARMONICS];
    double mAmplitude[NUM_HARMONICS];
    double mDecay[NUM_HARMONICS];
    uint64_t mCurrentGeneration = 0;
};

#endif //GUITARPAN_PANSYNTH_H