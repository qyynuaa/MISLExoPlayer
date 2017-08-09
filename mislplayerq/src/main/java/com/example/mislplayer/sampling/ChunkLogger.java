package com.example.mislplayer.sampling;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.chunk.MediaChunk;

/**
 * Logs information about a run, on a chunk-by-chunk basis.
 */
public interface ChunkLogger extends AdaptiveMediaSourceEventListener, ExoPlayer.EventListener {

    /** Writes a log with details of all downloaded chunks. */
    void writeLogsToFile();

    /** Clears the store of downloaded chunks. */
    void clearChunkInformation();

    /** Informs the logger of the current buffer estimate. */
    void updateBufferLevel(MediaChunk previous, long bufferedDurationUs);
}
