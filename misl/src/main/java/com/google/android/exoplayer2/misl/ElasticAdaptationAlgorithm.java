package com.google.android.exoplayer2.misl;

import com.google.android.exoplayer2.source.TrackGroup;

/**
 * The MISL Elastic adaptation algorithm.
 */

public class ElasticAdaptationAlgorithm extends AdaptationAlgorithm {

    private static final int DEFAULT_AVERAGE_WINDOW = 5;
    private static final double DEFAULT_K_P = 0.01;
    private static final double DEFAULT_K_I = 0.001;

    private AlgorithmInfoProvider infoProvider;
    private int averageWindow;
    private double k_p;
    private double k_i;

    /**
     * Creates a new algorithm.
     *
     * @param group  The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks in group, in order of decreasing bandwidth.
     * @param infoProvider An object the algorithm can get necessary info from.
     */
    public ElasticAdaptationAlgorithm(TrackGroup group, int[] tracks, AlgorithmInfoProvider infoProvider) {
        this(group, tracks, infoProvider, DEFAULT_AVERAGE_WINDOW, DEFAULT_K_P, DEFAULT_K_I);
    }

    /**
     * Creates a new algorithm.
     *
     * @param group  The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks in group, in order of decreasing bandwidth.
     * @param infoProvider An object the algorithm can get necessary info from.
     * @param averageWindow The number of past samples to include in rate estimation.
     * @param k_p The first algorithm constant.
     * @param k_i The second algorithm constant.
     */
    public ElasticAdaptationAlgorithm(TrackGroup group, int[] tracks, AlgorithmInfoProvider infoProvider, int averageWindow,
                                      double k_p, double k_i) {
        super(group, tracks);
        this.infoProvider = infoProvider;
        this.averageWindow = averageWindow;
        this.k_p = k_p;
        this.k_i = k_i;
    }

    /**
     * Calculates the index of the TrackGroup track that should be used.
     *
     * @param bufferedDurationUs The duration of media currently buffered in microseconds.
     * @return The index of the TrackGroup track that should be used.
     */
    @Override
    public int determineIdealIndex(long bufferedDurationUs) {
        return 0;
    }

    @Override
    public int getSelectionReason() {
        return 0;
    }
}
