package com.example.mislplayer.trackselection;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

import static com.google.android.exoplayer2.upstream.BandwidthMeter.NO_ESTIMATE;

/**
 * A track selection which implements a basic adaptation algorithm using
 * an estimate of the throughput.
 */
public class DASHTrackSelection extends AlgorithmTrackSelection {

    /**
     * Factory for DASHTrackSelection instances.
     */
    public static final class Factory implements TrackSelection.Factory {

        private final BandwidthMeter bandwidthMeter;
        private final double bandwidthFraction;

        /**
         * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
         */
        public Factory(BandwidthMeter bandwidthMeter) {
            this(bandwidthMeter, DEFAULT_BANDWIDTH_FRACTION);
        }

        /**
         * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
         * @param bandwidthFraction The fraction of the available bandwidth
         *                          that the selection should consider
         *                          available for use.
         */
        public Factory(BandwidthMeter bandwidthMeter,
                       double bandwidthFraction) {
            this.bandwidthMeter = bandwidthMeter;
            this.bandwidthFraction = bandwidthFraction;
        }

        /**
         * Creates a new DASHTrackSelection.
         * @param group The {@link TrackGroup}. Must not be null.
         * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
         *     null or empty. May be in any order.
         * @return A new DASHTrackSelection.
         */
        @Override
        public DASHTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new DASHTrackSelection(group, tracks, bandwidthMeter, bandwidthFraction);
        }
    }

    private static final String TAG = "DASHTrackSelection";
    private static final double DEFAULT_BANDWIDTH_FRACTION = 0.85;

    private final BandwidthMeter bandwidthMeter;
    private double networkRate;
    private double bandwidthFraction;
    private int selectedIndex;
    private int reason;

    /**
     * @param group The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     empty. May be in any order.
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     * @param bandwidthFraction The fraction of the available bandwidth
     *                          that the selection should consider
     *                          available for use.
     */
    public DASHTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter, double bandwidthFraction) {
        super(group, tracks);

        this.bandwidthMeter = bandwidthMeter;
        this.bandwidthFraction = bandwidthFraction;

        selectedIndex = lowestBitrateIndex();
        Log.d(TAG, String.format("Initial selected index = %d", selectedIndex));
        reason = C.SELECTION_REASON_INITIAL;
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
        int currentSelectedIndex = selectedIndex;
        selectedIndex = adaptiveAlgorithm();
        Log.d(TAG, String.format("Selected index = %d", selectedIndex));

        if (selectedIndex != currentSelectedIndex) {
            reason = C.SELECTION_REASON_ADAPTIVE;
        }
    }

    @Override
    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public int getSelectionReason() {
        return reason;
    }

    @Override
    public Object getSelectionData() {
        return null;
    }


    private int adaptiveAlgorithm() {
        long bitrateEstimate = bandwidthMeter.getBitrateEstimate();

        if (bitrateEstimate == NO_ESTIMATE) {
            return lowestBitrateIndex();
        }
        networkRate = 0.2 * bitrateEstimate + 0.8 * networkRate;
        double effectiveNetworkRate = bandwidthFraction * networkRate;

        return findBestRateIndex(effectiveNetworkRate);
    }
}
