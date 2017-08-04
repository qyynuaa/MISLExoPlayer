package com.example.mislplayer.sampling;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;

/**
 * Stores downloaded chunks.
 */
public interface ChunkStore extends AdaptiveMediaSourceEventListener, ExoPlayer.EventListener {

    /** Writes a log with details of all downloaded chunks. */
    void writeLogsToFile();

    /** Clears the store of downloaded chunks. */
    void clearChunkInformation();

    /** Informs the chunk store of the current buffer estimate. */
    void updateBufferLevel(long bufferedDurationUs);
}
