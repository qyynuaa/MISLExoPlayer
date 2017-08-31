package com.example.mislplayer;

/**
 * Base class for builders for different log representations.
 */
public abstract class LogBuilder {
    /**
     * Start a new entry in the log.
     */
    public void startEntry() {};

    /**
     * Finish an entry in the log.
     */
    public void finishEntry() {};

    /**
     * Finish the log.
     */
    public void finishLog() {};

    /**
     * Provide a chunk index value for the current entry.
     *
     * @param index The chunk index.
     */
    public void chunkIndex(int index) {};

    /**
     * Provide an arrival time value for the current entry.
     *
     * @param arrivalTimeMs The arrival time, in ms.
     */
    public void arrivalTime(long arrivalTimeMs) {};

    /**
     * Provide a delivery rate value for the current entry.
     *
     * @param deliveryRateKbps The delivery rate, in kbps.
     */
    public void deliveryRate(long deliveryRateKbps) {};

    /**
     * Provide a load duration value for the current entry.
     *
     * @param loadDurationMs The load duration, in ms.
     */
    public void loadDuration(long loadDurationMs) {};

    /**
     * Provide a stall duration value for the current entry.
     *
     * @param stallDurationMs The stall duration, in ms.
     */
    public void stallDuration(long stallDurationMs) {};

    /**
     * Provide a representation rate value for the current entry.
     *
     * @param repRateKbps The representation rate, in kbps.
     */
    public void representationRate(long repRateKbps) {};

    /**
     * Provide an actual rate value for the current entry.
     *
     * @param actualRateKbps The actual rate, in kbps.
     */
    public void actualRate(long actualRateKbps) {};

    /**
     * Provide a byte size value for the current entry.
     *
     * @param byteSize The byte size.
     */
    public void byteSize(long byteSize) {};

    /**
     * Provide a buffer level value for the current entry.
     *
     * @param bufferLevelMs The buffer level, in ms.
     */
    public void bufferLevel(long bufferLevelMs) {};

    /**
     * Provide a throughput value for the current entry.
     *
     * @param throughputKbps The throughput, in kbps.
     */
    public void throughput(long throughputKbps) {};
}
