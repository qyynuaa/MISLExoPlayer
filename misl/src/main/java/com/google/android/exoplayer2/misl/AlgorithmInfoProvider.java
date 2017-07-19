package com.google.android.exoplayer2.misl;

import android.os.SystemClock;

/**
 * Provides necessary information to {@link AdaptationAlgorithm}s.
 */

public interface AlgorithmInfoProvider {
    /**
     * Used to indicate data is unavailable.
     */
    int DATA_NOT_AVAILABLE = -1;

    /**
     * Indicates whether any data is available.
     *
     * <p>Only reports false if <em>no</em> data is available.
     *
     * @return {@code true} if some data is available, {@code false}
     * otherwise.
     */
    boolean dataIsAvailable();

    /**
     * The index of the most recently downloaded chunk.
     *
     * @return The last chunk's index.
     */
    int lastChunkIndex();

    /**
     * The duration of the most recently downloaded chunk, in ms.
     *
     * @return The last chunk's duration in ms.
     */
    long lastChunkDurationMs();


    /**
     * The value of {@link SystemClock#elapsedRealtime()} when the
     * most recently downloaded chunk finished downloading.
     *
     * @return The last chunk's arrival time.
     */
    long lastArrivalTime();

    /**
     * The length of time it took to load the most recently downloaded
     * chunk, in ms.
     *
     * @return The last chunk's load duration in ms.
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
     * The representation rate of the most recently downloaded chunk,
     * in bits per second.
     *
     * @return The last chunk's representation rate in bits per second.
     */
    double lastRepresentationRate();

    /**
     * The delivery rate of the most recently downloaded chunk, in bits
     * per second.
     *
     * @return The last chunk's delivery rate in bits per second.
     */
    double lastDeliveryRate();

    /**
     * The actual data rate of the most recently downloaded chunk, in
     * bits per second.
     *
     * @return The last chunk's actual rate in bits per second.
     */
    double lastActualRate();

    /**
     * The size of the most recently downloaded chunk, in bytes.
     *
     * @return The last chunk's size in bytes.
     */
    long lastByteSize();
}
