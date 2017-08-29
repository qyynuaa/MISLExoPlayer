package com.example.mislplayer.sampling;

import com.google.android.exoplayer2.source.chunk.MediaChunk;

/**
 * Receives {@link MediaChunk}s.
 */
public interface ChunkListener {
    /**
     * Give the listener the last chunk that was downloaded.
     *
     * @param lastChunk The last chunk that was downloaded.
     */
    void giveLastChunk(MediaChunk lastChunk);
}
