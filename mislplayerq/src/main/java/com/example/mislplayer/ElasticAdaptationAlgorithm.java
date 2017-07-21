package com.example.mislplayer;

import android.util.Log;

import com.google.android.exoplayer2.source.TrackGroup;

import static com.google.android.exoplayer2.misl.AlgorithmInfoProvider.DATA_NOT_AVAILABLE;
import static java.lang.Math.min;

/**
 * The MISL Elastic adaptation algorithm.
 */

public class ElasticAdaptationAlgorithm extends AdaptationAlgorithm {

    public static final class Factory implements AdaptationAlgorithm.Factory {

        private static final int DEFAULT_AVERAGE_WINDOW = 5;
        private static final double DEFAULT_K_P = 0.01;
        private static final double DEFAULT_K_I = 0.001;

        private final int averageWindow;
        private final double k_p;
        private final double k_i;

        private final AlgorithmInfoProvider infoProvider;

        public Factory(AlgorithmInfoProvider infoProvider) {
            this(infoProvider, DEFAULT_AVERAGE_WINDOW, DEFAULT_K_P,
                DEFAULT_K_I);
        }

        /**
         *
         * @param infoProvider An object the algorithm can get necessary info from.
         * @param averageWindow The number of past samples to include in rate estimation.
         * @param k_p The first algorithm constant.
         * @param k_i The second algorithm constant.
         */
        public Factory(AlgorithmInfoProvider infoProvider, int averageWindow, double k_p, double k_i) {
            this.infoProvider = infoProvider;
            this.averageWindow = averageWindow;
            this.k_p = k_p;
            this.k_i = k_i;
        }

        /**
         * Creates a new algorithm.
         *
         * @param group  The {@link TrackGroup}.
         * @param tracks The indices of the selected tracks within the {@link TrackGroup}, in any order.
         * @return The created algorithm.
         */
        @Override
        public AdaptationAlgorithm createAlgorithm(TrackGroup group, int... tracks) {
            return new ElasticAdaptationAlgorithm(group, tracks, infoProvider,
                    averageWindow, k_p, k_i);
        }
    }

    private static final String TAG = "Elastic";

    private AlgorithmInfoProvider infoProvider;
    private int averageWindow;
    private double k_p;
    private double k_i;
    private double staticAlgParameter;

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
        final int lastChunkIndex = infoProvider.lastChunkIndex();
        if (lastChunkIndex == DATA_NOT_AVAILABLE) {
            // choose lowest-bitrate stream
            return getGroup().length - 1;
        } else {
            final int numberOfChunks = lastChunkIndex + 1;
            final double downloadTimeS = infoProvider.loadDurationMs(lastChunkIndex) / 1E3;
            final double lastChunkDurationS = infoProvider.chunkDurationMs(lastChunkIndex) / 1E3;

            final double elasticTargetQueueS = infoProvider.maxBufferMs() / 1E6 * lastChunkDurationS;

            final double queueLengthS = bufferedDurationUs / 1E6;

            int workingAverageWindow = min(numberOfChunks, averageWindow);
            Log.d(TAG, String.format("averageWindow = %d", workingAverageWindow));

            double[] rateSamples = new double[workingAverageWindow];

            for (int i = 0; i < workingAverageWindow; i++) {
                rateSamples[i] = infoProvider.deliveryRate(lastChunkIndex - i);
            }

            Log.d(TAG, String.format("rateSamples length is %d", rateSamples.length));

            double averageRateEstimate = new Average(Average.Pythagorean.HARMONIC, rateSamples).value();

            Log.d(TAG, String.format("averageRateEstimate = %f", averageRateEstimate));

            staticAlgParameter += downloadTimeS * (queueLengthS - elasticTargetQueueS);

            Log.d(TAG, String.format("staticAlgParameter = %f", staticAlgParameter));

            double targetRate = averageRateEstimate / (1 - k_p * queueLengthS - k_i * staticAlgParameter);

            if (targetRate <= 0) {
                targetRate = 0;
            }

            Log.d(TAG, String.format("targetRate = %f", targetRate));

            return findRateIndex((long)targetRate);
        }
    }

    @Override
    public int getSelectionReason() {
        return 0;
    }
}
