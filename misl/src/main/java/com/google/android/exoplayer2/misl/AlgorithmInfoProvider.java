package com.google.android.exoplayer2.misl;

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
     * The duration of a downloaded chunk, in ms.
     *
     * @param chunkIndex The index of the chunk.
     * @return The duration of the chunk in ms.
     */
    long chunkDurationMs(int chunkIndex);

    /**
     * The arrival time of a downloaded chunk, in ms.
     *
     * @param chunkIndex The index of the chunk.
     * @return The arrival time of the chunk in ms.
     */
    long arrivalTimeMs(int chunkIndex);

    /**
     * The load duration of a downloaded chunk, in ms.
     *
     * @param chunkIndex The index of the chunk.
     * @return The load duration of the chunk in ms.
     */
    long loadDurationMs(int chunkIndex);

    /**
     * The length of time the player has stalled for since starting the
     * video, in ms.
     *
     * @return The player's stall duration in ms.
     */
    long stallDurationMs();

    /**
     * The representation rate of a downloaded chunk, in bits per second.
     *
     * @param chunkIndex The index of the chunk.
     * @return The representation rate of the chunk in bits per second.
     */
    double representationRate(int chunkIndex);

    /**
     * The delivery rate of a downloaded chunk, in bits per second.
     *
     * @param chunkIndex The index of the chunk.
     * @return The delivery rate of the chunk in bits per second.
     */
    double deliveryRate(int chunkIndex);

    /**
     * The actual data rate of a downloaded chunk, in bits per second.
     *
     * @param chunkIndex The index of the chunk.
     * @return The actual data rate of the chunk in bits per second.
     */
    double actualDataRate(int chunkIndex);

    /**
     * The size of a downloaded chunk, in bits.
     *
     * @param chunkIndex The index of the chunk.
     * @return The size of the chunk in bits.
     */
    double size(int chunkIndex);

    /**
     * The maximum duration of media that the player will attempt to
     * buffer, in ms.
     *
     * @return The player's maximum buffer length in ms.
     */
    double maxBufferMs();
}
