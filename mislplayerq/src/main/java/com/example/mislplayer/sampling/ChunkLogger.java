package com.example.mislplayer.sampling;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;

/**
 * Logs information about a run, on a chunk-by-chunk basis.
 */
public interface ChunkLogger extends AdaptiveMediaSourceEventListener, ExoPlayer.EventListener {

    /** Writes a log with details of all downloaded chunks. */
    void writeLogsToFile();

    /** Clears the store of downloaded chunks. */
    void clearChunkInformation();

    /** Gives the logger a reference to the current player instance. */
    void setPlayer(ExoPlayer player);
}
