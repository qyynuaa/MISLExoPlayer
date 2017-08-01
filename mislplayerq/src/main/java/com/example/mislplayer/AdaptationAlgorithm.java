package com.example.mislplayer;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Controls how video quality adaptation is done.
 */
public interface AdaptationAlgorithm {
    /** Returns the TransferListener for the algorithm. */
    TransferListener<? super DataSource> transferListener();

    /** Returns the ChunkListener for the algorithm. */
    ChunkListener chunkListener();

    /** Returns the ExoPlayer EventListener for the algorithm. */
    ExoPlayer.EventListener playerListener();

    /**
     * Returns the factory which can be used to make track selections
     * according to the adaptation algorithm.
     *
     * @return The TrackSelection Factory for the algorithm.
     */
    TrackSelection.Factory trackSelectionFactory();
}
