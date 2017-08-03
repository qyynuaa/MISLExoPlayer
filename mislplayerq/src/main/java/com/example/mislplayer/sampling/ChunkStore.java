package com.example.mislplayer.sampling;

import com.google.android.exoplayer2.source.chunk.MediaChunk;

/**
 * Stores downloaded chunks.
 */
public interface ChunkStore {
    /** Adds a new chunk to the store. */
    void add(MediaChunk lastChunk);

    void giveMpdDuration(long durationMs);
}
