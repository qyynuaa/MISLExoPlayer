package com.example.mislplayer.algorithm;

import com.example.mislplayer.ChunkListener;
import com.example.mislplayer.TransitionalAlgorithmListener;
import com.example.mislplayer.trackselection.BBA2TrackSelection;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Implements the BBA2 video adaptation algorithm.
 */
public class BBA2Algorithm implements AdaptationAlgorithm {

    private TransitionalAlgorithmListener algorithmListener;
    private TrackSelection.Factory trackSelectionFactory;

    public BBA2Algorithm(int maxBufferMs) {
        algorithmListener = new TransitionalAlgorithmListener(maxBufferMs);
        trackSelectionFactory = new BBA2TrackSelection.Factory(algorithmListener);
    }

    /**
     * Returns the TransferListener for the algorithm.
     */
    @Override
    public TransferListener<? super DataSource> transferListener() {
        return algorithmListener;
    }

    /**
     * Returns the ChunkListener for the algorithm.
     */
    @Override
    public ChunkListener chunkListener() {
        return algorithmListener;
    }

    /**
     * Returns the ExoPlayer EventListener for the algorithm.
     */
    @Override
    public ExoPlayer.EventListener playerListener() {
        return algorithmListener;
    }

    /**
     * Returns the factory which can be used to make track selections
     * according to the adaptation algorithm.
     *
     * @return The TrackSelection Factory for the algorithm.
     */
    @Override
    public TrackSelection.Factory trackSelectionFactory() {
        return trackSelectionFactory;
    }

    @Override
    public void writeLogsToFile() {
        algorithmListener.writeLogsToFile();
    }

    @Override
    public void clearChunkInformation() {
        algorithmListener.clearChunkInformation();
    }
}
