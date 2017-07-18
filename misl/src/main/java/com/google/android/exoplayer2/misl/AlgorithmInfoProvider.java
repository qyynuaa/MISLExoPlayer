package com.google.android.exoplayer2.misl;

import android.os.SystemClock;

/**
 * Provides necessary information to {@link AdaptationAlgorithm}s.
 */

public interface AlgorithmInfoProvider {
    /**
     * The index of the last segment in the stream.
     *
     * @return The last segment's index.
     */
    int lastSegmentNumber();

    /**
     * The value of {@link SystemClock#elapsedRealtime()} when the
     * last segment finished downloading.
     *
     * @return The last downloaded segment's arrival time.
     */
    long lastArrivalTime();

    /**
     * The length of time it took to load the last segment, in ms.
     *
     * @return The last segment's load duration in ms.
     */
    long lastLoadDurationMs();

    /**
     * The length of time the player has stalled for since starting the
     * video, in ms.
     *
     * @return The player's stall duration in ms.
     */
    long stallDurationMs();

    /**
     * The representation rate of the last segment, in bits per second.
     *
     * @return The last segment's representation rate in bits per second.
     */
    double lastRepresentationRate();

    /**
     * The delivery rate of the last segment, in bits per second.
     *
     * @return The last segment's delivery rate in bits per second.
     */
    double lastDeliveryRate();

    /**
     * The actual data rate of the last segment, in bits per second.
     *
     * @return The last segment's actual rate in bits per second.
     */
    double lastActualRate();

    /**
     * The size of the last segment, in bytes.
     *
     * @return The last segment's size in bytes.
     */
    long lastByteSize();

    /**
     * The amount of content currently in the buffer, in microseconds.
     *
     * @return The current buffer level in microseconds.
     */
    long currentBufferLevelUs();
}
