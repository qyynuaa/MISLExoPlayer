package com.example.mislplayer.MISL_Algorithm;

import android.util.Log;

import com.example.mislplayer.DashMediaSourceListener;
import com.example.mislplayer.PlayerActivity;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;

import static java.lang.Math.min;

/**
 * Uses the MISL Elastic adaptation algorithm to select tracks.
 */

public class ElasticTrackSelection extends BaseTrackSelection {

    public static final class Factory implements TrackSelection.Factory {

        private final int elasticAverageWindow;
        private final double k_p;
        private final double k_i;

        /**
         * Sets parameters for an ElasticTrackSelection, which can be created later.
         *
         * @param elasticAverageWindow The number of previous rate samples to consider.
         * @param k_p
         * @param k_i
         */
        public Factory(final int elasticAverageWindow, final double k_p, final double k_i) {
            this.elasticAverageWindow = elasticAverageWindow;
            this.k_p = k_p;
            this.k_i = k_i;
        }

        @Override
        public ElasticTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new ElasticTrackSelection(group, tracks, elasticAverageWindow, k_p, k_i);
        }

    }

    private static final String TAG = "ElasticTrackSelection";

    private final int elasticAverageWindow;
    private final double k_p;
    private final double k_i;

    private double staticAlgParameter = 0;

    private int selectedIndex;


    public ElasticTrackSelection(TrackGroup group, int[] tracks, int elasticAverageWindow, double k_p, double k_i) {
        super(group, tracks);
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
        Log.d(TAG, String.format("selectedIndex = %d", selectedIndex));
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

        if (DashMediaSourceListener.logSegment == null) {
            // select lowest rate
            return length - 1;
        }


        final int segmentIndex = DashMediaSourceListener.logSegment.getSegNumber();

        final double totalSizeBytes = DashMediaSourceListener.logSegment.getByteSize();
        final double bytesPerSecond = DashMediaSourceListener.logSegment.getDeliveryRate();
        final double downloadTimeS = DashMediaSourceListener.logSegment.getDeliveryTime() / 1E3;
        final double lastSegmentDurationS = DashMediaSourceListener.logSegment.getSegmentDuration() / 1E3;

        if (totalSizeBytes == 0 || bytesPerSecond == 0 || lastSegmentDurationS == 0) {
            // skipping rate adaptation – log details and return
            return -1;
        } else {
            final double elasticTargetQueueS = PlayerActivity.loadControl.getMaxBufferUs() / 1E6 * lastSegmentDurationS;

            final double queueLengthS = bufferedDurationUs / 1E6;

            int averageWindow = min(segmentIndex, elasticAverageWindow);
            Log.d(TAG, String.format("averageWindow = %d", averageWindow));

            double[] rateSamples = new double[averageWindow];

            for (int i = 1; i <= averageWindow; i++) {
                Log.d(TAG, String.format("Index = %d", i));

                // rateSamples is in bits/second
                rateSamples[i - 1] = PlayerActivity.dMSL.getSegInfos().get(segmentIndex - i).getDeliveryRate() * 1E3;
            }

            double averageRateEstimate = getAverageRate(rateSamples);

            Log.d(TAG, String.format("averageRateEstimate = %f", averageRateEstimate));

            staticAlgParameter += downloadTimeS * (queueLengthS - elasticTargetQueueS);

            Log.d(TAG, String.format("staticAlgParameter = %f", staticAlgParameter));

            double targetRate = averageRateEstimate / (1 - k_p * queueLengthS - k_i * staticAlgParameter);

            if (targetRate <= 0) {
                targetRate = 0;
            }

            Log.d(TAG, String.format("targetRate = %f", targetRate));

            return findRateIndex(targetRate);
        }
    }

    /**
     * Finds the index for the highest quality level below a target rate.
     *
     * @param targetRate The target rate.
     * @return The index of the highest suitable quality level.
     */
    private int findRateIndex(double targetRate) {
        for (int i = 0; i < length; i++) {
            if (getFormat(i).bitrate < targetRate) {
                return tracks[i];
            }
        }
        return length - 1;
    }

    /**
     * Calculates the average of an array of samples.
     *
     * @param rateSamples The array of samples to find the average of.
     * @return The average of the array of samples.
     */
    private double getAverageRate(double[] rateSamples) {
        double average = 0;
        for (int i = 0; i < rateSamples.length; i++) {
            average += 1 / rateSamples[i];
        }
        return rateSamples.length / average;
    }
}
