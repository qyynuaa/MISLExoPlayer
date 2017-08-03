package com.example.mislplayer.sampling;

import com.google.android.exoplayer2.source.chunk.MediaChunk;

/**
 * Stores downloaded chunks.
 */
public interface ChunkStore {
    /**
     * Adds a new chunk to the store.
     *
     * @param chunk A chunk that has been downloaded.
     * @param arrivalTimeMs The time at which the chunk finished downloading.
     * @param loadDurationMs The length of time the chunk took to download,
     *                       in ms.
     */
    void add(MediaChunk chunk, long arrivalTimeMs, long loadDurationMs);

    void giveMpdDuration(long durationMs);

    /** Writes a log with details of all downloaded chunks. */
    void writeLogsToFile();

    /** Clears the store of downloaded chunks. */
    void clearChunkInformation();

    /** Informs the chunk store of a new stall. */
    void newStall(long stallDurationMs);

    /** Informs the chunk store of the current buffer estimate. */
    void updateBufferLevel(long bufferedDurationUs);
}
