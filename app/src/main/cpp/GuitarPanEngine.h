#pragma once
#include <oboe/Oboe.h>
#include <array>

class GuitarPanEngine : public oboe::AudioStreamDataCallback {
public:
    bool start();
    void stop();
    void playNote(int noteIndex, float velocity);

    // AudioStreamDataCallback
    oboe::DataCallbackResult
    onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override;

private:
    static constexpr int kSampleRate = 48000;
    static constexpr int kMaxVoices  = 8;

    struct Voice {
        float phases[3]  = {0, 0, 0};
        float amps[3]    = {0, 0, 0};
        int   age        = 0;
        bool  active     = false;
    };

    std::array<Voice, kMaxVoices> mVoices;
    std::shared_ptr<oboe::AudioStream> mStream;
};
