package com.example.mislplayer.algorithm;

import com.example.mislplayer.ChunkListener;
import com.example.mislplayer.sampling.ChunkBasedSampler;
import com.example.mislplayer.sampling.DefaultSampleProcessor;
import com.example.mislplayer.trackselection.ArbiterTrackSelection;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Implements the Arbiter video adaptation algorithm.
 */

public class ArbiterAlgorithm implements AdaptationAlgorithm {

    private DefaultSampleProcessor sampleProcessor;
    private ChunkBasedSampler chunkSampler;
    private TrackSelection.Factory trackSelectionFactory;

    public ArbiterAlgorithm(int maxBufferMs) {
        sampleProcessor = new DefaultSampleProcessor(maxBufferMs);
        chunkSampler = new ChunkBasedSampler(sampleProcessor, sampleProcessor);
        trackSelectionFactory = new ArbiterTrackSelection.Factory(sampleProcessor, sampleProcessor);
    }

    /**
     * Returns the TransferListener for the algorithm.
     */
    @Override
    public TransferListener<? super DataSource> transferListener() {
        return chunkSampler;
    }

    /**
     * Returns the ChunkListener for the algorithm.
     */
    @Override
    public ChunkListener chunkListener() {
        return chunkSampler;
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
        return trackSelectionFactory;
    }

    @Override
    public void writeLogsToFile() {
        sampleProcessor.writeLogsToFile();
    }

    @Override
    public void clearChunkInformation() {
        sampleProcessor.clearChunkInformation();
    }
}
