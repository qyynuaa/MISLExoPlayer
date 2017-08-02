package com.example.mislplayer.sampling;

/**
 * Stores and logs bandwidth samples.
 */
public interface SampleStore {
    /** Adds a new throughput sample to the store. */
    void addSample(long bitsTransferred, long durationMs);
}
