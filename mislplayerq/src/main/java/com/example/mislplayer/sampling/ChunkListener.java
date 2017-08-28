package com.example.mislplayer.sampling;

import com.google.android.exoplayer2.source.chunk.MediaChunk;

/**
 * Receives {@link MediaChunk}s and passes the info to an AdaptationAlgorithm.
 */
public interface ChunkListener {
    /**
     * Gives the listener the last chunk that was downloaded, to be passed to the
     * adaptation algorithm.
     *
     * @param lastChunk The last chunk that was downloaded.
     */
    void giveLastChunk(MediaChunk lastChunk);
}
