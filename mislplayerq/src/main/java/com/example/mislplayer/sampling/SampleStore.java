package com.example.mislplayer.sampling;

/**
 * Stores and logs bandwidth samples.
 */
public interface SampleStore {
    void addSample(long bitsTransferred, long durationMs);
}
