package com.example.mislplayer.sampling;

import com.google.android.exoplayer2.source.chunk.MediaChunk;

/**
 * Stores downloaded chunks.
 */
public interface ChunkStore {
    /** Adds a new chunk to the store. */
    void add(MediaChunk lastChunk);

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
