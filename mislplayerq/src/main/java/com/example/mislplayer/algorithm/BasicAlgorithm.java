package com.example.mislplayer.algorithm;

import com.example.mislplayer.ChunkListener;
import com.example.mislplayer.trackselection.DASHTrackSelection;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Implements a basic video adaptation algorithm based on a throughput
 * estimate.
 */
public class BasicAlgorithm implements AdaptationAlgorithm {

    private DefaultBandwidthMeter bandwidthMeter;

    public BasicAlgorithm() {
        this.bandwidthMeter = new DefaultBandwidthMeter();
    }

    /**
     * Returns the TransferListener for the algorithm.
     */
    @Override
    public TransferListener<? super DataSource> transferListener() {
        return bandwidthMeter;
    }

    /**
     * Returns the ChunkListener for the algorithm.
     */
    @Override
    public ChunkListener chunkListener() {
        return null;
    }

    /**
     * Returns the ExoPlayer EventListener for the algorithm.
     */
    @Override
    public ExoPlayer.EventListener playerListener() {
        return null;
    }

    /**
     * Returns the factory which can be used to make track selections
     * according to the adaptation algorithm.
     *
     * @return The TrackSelection Factory for the algorithm.
     */
    @Override
    public TrackSelection.Factory trackSelectionFactory() {
        return new DASHTrackSelection.Factory(bandwidthMeter);
    }

    @Override
    public void writeLogsToFile() {}

    @Override
    public void clearChunkInformation() {}
}
