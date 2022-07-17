package com.example.camera30;

public class WaveSample {
    private long time;
    private long amplitude;

    public WaveSample(long time, int amplitude) {
        this.time = time;
        this.amplitude = amplitude;
    }

    public long getAmplitude() {
        return amplitude;
    }

}
