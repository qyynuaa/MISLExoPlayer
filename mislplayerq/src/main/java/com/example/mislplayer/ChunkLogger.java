package com.example.mislplayer;

import com.google.android.exoplayer2.source.chunk.MediaChunk;

/**
 * Logs information about a run, on a chunk-by-chunk basis.
 */
public interface ChunkLogger {

    /** Writes a log with details of all downloaded chunks. */
    void writeLogsToFile();

    /** Clears the store of downloaded chunks. */
    void clearChunkInformation();
}
