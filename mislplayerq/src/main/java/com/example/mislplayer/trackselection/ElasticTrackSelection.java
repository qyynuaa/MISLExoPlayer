package com.example.mislplayer.trackselection;

import android.util.Log;

import com.example.mislplayer.sampling.SampleProcessor;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;

/**
 * Selects adaptive media tracks using the Elastic algorithm.
 */
public class ElasticTrackSelection extends AlgorithmTrackSelection {

    /**
     * Creates ElasticTrackSelection instances.
     */
    public static final class Factory implements TrackSelection.Factory {

        private final SampleProcessor algorithmListener;
        private final int elasticAverageWindow;
        private final double k_p;
        private final double k_i;

        /**
         * Creates an ElasticTrackSelection factory using default values.
         *
         * @param sampleProcessor Provides information about throughput
         *        samples to the algorithm.
         */
        public Factory(SampleProcessor sampleProcessor) {
            this(sampleProcessor, DEFAULT_ELASTIC_AVERAGE_WINDOW,
                    DEFAULT_K_P, DEFAULT_K_I);
        }

        /**
         * Creates an ElasticTrackSelection factory by specifying the algorithm parameters.
         *
         * @param sampleProcessor Provides information about throughput
         *        samples to the algorithm.
         * @param elasticAverageWindow The number of past throughput
         *        samples to consider.
         * @param k_p An algorithm constant.
         * @param k_i An algorithm constant.
         */
        public Factory(SampleProcessor sampleProcessor,
                       final int elasticAverageWindow, final double k_p,
                       final double k_i) {
            this.algorithmListener = sampleProcessor;
            this.elasticAverageWindow = elasticAverageWindow;
            this.k_p = k_p;
            this.k_i = k_i;
        }

        @Override
        public ElasticTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new ElasticTrackSelection(group, tracks, algorithmListener,
                    elasticAverageWindow, k_p, k_i);
        }

    }

    private static final int DEFAULT_ELASTIC_AVERAGE_WINDOW = 5;
    private static final double DEFAULT_K_P = 0.01;
    private static final double DEFAULT_K_I = 0.001;

    private static final String TAG = "Elastic";

    private final int elasticAverageWindow;
    private final double k_p;
    private final double k_i;

    private double staticAlgParameter = 0;

    private int lastChunkIndex;
    private int selectedIndex;
    private int selectionReason;


    /**
     * Creates a new ElasticTrackSelection.
     *
     * @param group The {@link TrackGroup}. Must not be null.
     * @param tracks The indices of the selected tracks within the
     *        {@link TrackGroup}. Must not be null or empty. May be in any order.
     * @param sampleProcessor Provides information about throughput
     *        samples to the algorithm.
     * @param elasticAverageWindow The number of past throughput
     *        samples to consider.
     * @param k_p An algorithm constant.
     * @param k_i An algorithm constant.
     */
    public ElasticTrackSelection(TrackGroup group, int[] tracks,
                                 SampleProcessor
                                         sampleProcessor,
                                 int elasticAverageWindow, double k_p,
                                 double k_i) {
        super(group, tracks, sampleProcessor);
        this.elasticAverageWindow = elasticAverageWindow;
        this.k_p = k_p;
        this.k_i = k_i;

        selectedIndex = lowestBitrateIndex();
        Log.d(TAG, String.format("Initial selected index = %d", selectedIndex));
        selectionReason = C.SELECTION_REASON_INITIAL;
    }

    @Override
    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public int getSelectionReason() {
        return selectionReason;
    }

    @Override
    public Object getSelectionData() {
        return null;
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
        if (sampleProcessor.dataNotAvailable()) {
            selectedIndex = lowestBitrateIndex();
        } else if (lastChunkIndex != sampleProcessor.lastChunkIndex()) {
            lastChunkIndex = sampleProcessor.lastChunkIndex();
            selectedIndex = calculateSelectedIndex(bufferedDurationUs);
            Log.d(TAG, String.format("Selected index = %d", selectedIndex));
        }
        selectionReason = C.SELECTION_REASON_ADAPTIVE;
    }

    /**
     * Calculates the index of the track that should be selected.
     *
     * @param bufferedDurationUs The duration of media currently buffered in microseconds.
     * @return The index of the track that should be selected.
     */
    private int calculateSelectedIndex(long bufferedDurationUs) {
        double averageRateEstimate = sampleProcessor.sampleHarmonicAverage(elasticAverageWindow);

        final double downloadTimeS = sampleProcessor.lastSampleDurationMs() / 1E3;
        final double maxBufferS = sampleProcessor.maxBufferMs() / 1E3;
        final double bufferedDurationS = bufferedDurationUs / 1E6;

        staticAlgParameter += downloadTimeS * (bufferedDurationS - maxBufferS);
        double targetRate = averageRateEstimate / (1 - k_p * bufferedDurationS - k_i * staticAlgParameter);

        if (targetRate <= 0) {
            targetRate = 0;
        }

        Log.d(TAG, String.format("targetRate = %f kbps", targetRate / 1000));

        return findBestRateIndex(targetRate);
    }
}
