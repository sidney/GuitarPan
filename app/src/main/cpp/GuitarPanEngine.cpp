#include "GuitarPanEngine.h"
#include <cmath>
#include <android/log.h>

static constexpr float kTwoPi = 6.283185307179586f;

// 20 notes × 3 modes
struct NoteParam {
    float freq[3];
    float damp[3];
};
static const NoteParam kNotes[20] = {
    /* 0  D3  */ {{146.83f, 293.66f, 440.49f}, {1.85f, 3.29f, 4.83f}},
    /* 1  G#3 */ {{207.65f, 415.30f, 622.95f}, {2.22f, 3.94f, 5.79f}},
    /* 2  E3  */ {{164.81f, 329.63f, 494.44f}, {1.97f, 3.49f, 5.13f}},
    /* 3  E4  */ {{329.63f, 659.26f, 988.89f}, {2.80f, 4.97f, 7.30f}},
    /* 4  C4  */ {{261.63f, 523.25f, 784.88f}, {2.49f, 4.42f, 6.50f}},
    /* 5  F#3 */ {{185.00f, 370.00f, 555.00f}, {2.09f, 3.71f, 5.45f}},
    /* 6  Bb3 */ {{233.08f, 466.16f, 699.24f}, {2.35f, 4.17f, 6.13f}},
    /* 7  D4  */ {{293.66f, 587.33f, 880.99f}, {2.64f, 4.68f, 6.88f}},
    /* 8  G#4 */ {{415.30f, 830.61f, 1245.91f}, {3.15f, 5.59f, 8.21f}},
    /* 9  F#4 */ {{369.99f, 739.99f, 1109.98f}, {2.97f, 5.27f, 7.74f}},
    /*10 C#3 */ {{138.59f, 277.18f, 415.77f}, {1.80f, 3.19f, 4.68f}},
    /*11 G3  */ {{196.00f, 392.00f, 588.00f}, {2.15f, 3.82f, 5.61f}},
    /*12 Eb3 */ {{155.56f, 311.13f, 466.69f}, {1.91f, 3.39f, 4.98f}},
    /*13 Eb4 */ {{311.13f, 622.25f, 933.38f}, {2.72f, 4.83f, 7.09f}},
    /*14 B3  */ {{246.94f, 493.88f, 740.82f}, {2.42f, 4.30f, 6.31f}},
    /*15 F3  */ {{174.61f, 349.23f, 523.84f}, {2.03f, 3.61f, 5.30f}},
    /*16 A3  */ {{220.00f, 440.00f, 660.00f}, {2.28f, 4.05f, 5.95f}},
    /*17 C#4*/ {{277.18f, 554.37f, 831.55f}, {2.57f, 4.56f, 6.70f}},
    /*18 G4  */ {{392.00f, 784.00f, 1176.0f}, {3.06f, 5.43f, 7.98f}},
    /*19 F4  */ {{349.23f, 698.46f, 1047.7f}, {2.88f, 5.12f, 7.52f}},
};

bool GuitarPanEngine::start() {
    oboe::AudioStreamBuilder b;
    b.setPerformanceMode(oboe::PerformanceMode::LowLatency)
     ->setSharingMode(oboe::SharingMode::Exclusive)
     ->setFormat(oboe::AudioFormat::Float)
     ->setChannelCount(oboe::ChannelCount::Mono)
     ->setSampleRate(kSampleRate)
     ->setDataCallback(this);
    oboe::Result r = b.openStream(mStream);
    if (r != oboe::Result::OK) return false;
    return mStream->requestStart() == oboe::Result::OK;
}

void GuitarPanEngine::stop() {
    if (mStream) { mStream->close(); mStream = nullptr; }
}

void GuitarPanEngine::playNote(int idx, float velocity) {
    if (idx < 0 || idx >= 20) return;
    Voice *v = nullptr;
    for (auto &voice : mVoices) if (!voice.active) { v = &voice; break; }
    if (!v) return;  // no free voice
    v->noteIdx = idx;
    v->active = true;
    v->age = 0;
    for (int k = 0; k < 3; ++k) {
        v->phases[k] = 0.f;
        v->amps[k]   = velocity * 0.25f;   // scaling factor
    }
}

oboe::DataCallbackResult
GuitarPanEngine::onAudioReady(oboe::AudioStream *, void *audioData, int32_t numFrames) {
    auto out = static_cast<float*>(audioData);
    std::fill(out, out + numFrames, 0.f);
// inside onAudioReady(...)
    for (auto &v : mVoices) {
        if (!v.active) continue;
        const auto &p = kNotes[v.noteIdx];
        for (int k = 0; k < 3; ++k) {
            float w  = kTwoPi * p.freq[k] / kSampleRate;
            float g  = exp(-p.damp[k] / kSampleRate);
            for (int n = 0; n < numFrames; ++n) {
                out[n] += v.amps[k] * sin(v.phases[k]);
                v.phases[k] += w;
                v.amps[k]   *= g;
            }
        }
        v.age += numFrames;
        if (v.amps[0] < 1e-4f) v.active = false;
    }    return oboe::DataCallbackResult::Continue;
}
