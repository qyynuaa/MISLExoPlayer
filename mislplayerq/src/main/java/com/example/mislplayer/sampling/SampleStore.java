package com.example.mislplayer.sampling;

/**
 * Stores and logs bandwidth samples.
 */
public interface SampleStore {
    /**
     * Adds a new throughput sample to the store.
     *
     * @param elapsedRealtimeMs The value of SystemClock#elapsedRealtime()
     *                          when the sample finished.
     * @param bitsTransferred The number of bits transferred during the sample.
     * @param durationMs The duration of the sample, in ms.
     */
    void addSample(long elapsedRealtimeMs, long bitsTransferred,
                   long durationMs);
}
