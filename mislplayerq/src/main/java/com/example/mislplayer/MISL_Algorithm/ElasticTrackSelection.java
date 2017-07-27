package com.example.mislplayer.MISL_Algorithm;

import android.util.Log;

import com.example.mislplayer.TransitionalAlgorithmListener;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;

/**
 * Uses the MISL Elastic adaptation algorithm to select tracks.
 */

public class ElasticTrackSelection extends AlgorithmTrackSelection {

    /**
     * Creates ElasticTrackSelection instances.
     */
    public static final class Factory implements TrackSelection.Factory {

        private final TransitionalAlgorithmListener algorithmListener;
        private final int elasticAverageWindow;
        private final double k_p;
        private final double k_i;

        /**
         * Creates an ElasticTrackSelection factory using default values.
         */
        public Factory(TransitionalAlgorithmListener algorithmListener) {
            this(algorithmListener, DEFAULT_ELASTIC_AVERAGE_WINDOW,
                    DEFAULT_K_P, DEFAULT_K_I);
        }

        /**
         * Creates an ElasticTrackSelection factory by specifying the algorithm parameters.
         *
         * @param algorithmListener
         * @param elasticAverageWindow The number of previous rate samples to consider.
         * @param k_p
         * @param k_i
         */
        public Factory(TransitionalAlgorithmListener algorithmListener,
                       final int elasticAverageWindow, final double k_p,
                       final double k_i) {
            this.algorithmListener = algorithmListener;
            this.elasticAverageWindow = elasticAverageWindow;
            this.k_p = k_p;
            this.k_i = k_i;
        }

        /**
         * Creates a new ElasticTrackSelection.
         *
         * @param group The {@link TrackGroup}. Must not be null.
         * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
         *     null or empty. May be in any order.
         * @return A new ElasticTrackSelection.
         */
        @Override
        public ElasticTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new ElasticTrackSelection(group, tracks, algorithmListener,
                    elasticAverageWindow, k_p, k_i);
        }

    }

    private static final int DEFAULT_ELASTIC_AVERAGE_WINDOW = 5;
    private static final double DEFAULT_K_P = 0.01;
    private static final double DEFAULT_K_I = 0.001;

    private static final String TAG = "ElasticTrackSelection";

    private TransitionalAlgorithmListener algorithmListener;

    private final int elasticAverageWindow;
    private final double k_p;
    private final double k_i;

    private double staticAlgParameter = 0;

    private int selectedIndex;


    /**
     * Creates a new ElasticTrackSelection.
     *
     * @param group The {@link TrackGroup}. Must not be null.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     null or empty. May be in any order.
     * @param algorithmListener Provides necessary information to the
     *                          algorithm.
     * @param elasticAverageWindow
     * @param k_p
     * @param k_i
     */
    public ElasticTrackSelection(TrackGroup group, int[] tracks,
                                 TransitionalAlgorithmListener
                                         algorithmListener,
                                 int elasticAverageWindow, double k_p,
                                 double k_i) {
        super(group, tracks);
        this.algorithmListener = algorithmListener;
        this.elasticAverageWindow = elasticAverageWindow;
        this.k_p = k_p;
        this.k_i = k_i;
    }

    /**
     * Returns the index of the selected track.
     */
    @Override
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Returns the reason for the current track selection.
     */
    @Override
    public int getSelectionReason() {
        return 0;
    }

    /**
     * Returns optional data associated with the current track selection.
     */
    @Override
    public Object getSelectionData() {
        return null;
    }

    /**
     * Updates the selected track.
     *
     * @param bufferedDurationUs The duration of media currently buffered in microseconds.
     */
    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
        selectedIndex = doRateAdaptation(bufferedDurationUs);
        Log.d(TAG, String.format("Selected index = %d", selectedIndex));
    }

    // internal

    /**
     * Applies the adaptation algorithm.
     *
     * @param bufferedDurationUs The duration of media currently buffered in microseconds.
     * @return The index (in sorted order) of the track to switch to.
     */
    private int doRateAdaptation(long bufferedDurationUs) {
        /*
                Brief explanation of used variables:
                total_size - size (in bytes) of the last downloaded segment
                time - time needed to download the last segment
                rep->bandwidth - average (nominal) representation rate (bps)
                bitrate - actual representation rate (kbps)
                bytes_per_sec - throughput for the last segment
                group->current_downloaded_segment_duration - segment duration (in ms)
                group->max_cached_segments - max number of cached segments - total amount of buffer is twice this size (because there is also a playback buffer: total = play + cache)
                group->max_buffer_segments - same as above
                group->active_rep_index - current index of downloaded segment
                group->download_segment_index + 1 - number of downloaded segments
        */

        if (algorithmListener.chunkDataNotAvailable()) {
            return lowestBitrateIndex();
        }


        final int segmentIndex = algorithmListener.lastChunkIndex();

        final double totalSizeBytes = algorithmListener.lastByteSize();
        final double deliveryRateKbps = algorithmListener.lastDeliveryRateKbps();
        final double downloadTimeS = algorithmListener.lastLoadDurationMs() / 1E3;
        final double lastSegmentDurationS = algorithmListener.lastChunkDurationMs() / 1E3;

        if (totalSizeBytes == 0 || deliveryRateKbps == 0 || lastSegmentDurationS == 0) {
            // skipping rate adaptation – log details and return
            return -1;
        } else {
            final double elasticTargetQueueS = algorithmListener.getMaxBufferMs() / 1E3;

            final double queueLengthS = bufferedDurationUs / 1E6;

            int averageWindow = algorithmListener.getWindowSize(elasticAverageWindow);
            double[] rateSamples = algorithmListener.getThroughputSamples(averageWindow);

            double averageRateEstimate = new HarmonicAverage(averageWindow, rateSamples).value();

            staticAlgParameter += downloadTimeS * (queueLengthS - elasticTargetQueueS);

            double targetRate = averageRateEstimate / (1 - k_p * queueLengthS - k_i * staticAlgParameter);

            if (targetRate <= 0) {
                targetRate = 0;
            }

            Log.d(TAG, String.format("targetRate = %f kbps", targetRate));

            return findBestRateIndex(targetRate * 1000);
        }
    }
}
