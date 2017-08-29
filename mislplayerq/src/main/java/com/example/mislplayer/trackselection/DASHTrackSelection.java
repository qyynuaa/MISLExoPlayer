package com.example.mislplayer.trackselection;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

import static com.google.android.exoplayer2.upstream.BandwidthMeter.NO_ESTIMATE;

/**
 * Selects adaptive media tracks using the conventional algorithm.
 */
public class DASHTrackSelection extends AlgorithmTrackSelection {

    /**
     * Creates DASHTrackSelection instances.
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
         *        that the selection should consider available for use.
         */
        public Factory(BandwidthMeter bandwidthMeter,
                       double bandwidthFraction) {
            this.bandwidthMeter = bandwidthMeter;
            this.bandwidthFraction = bandwidthFraction;
        }

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
     * Creates a new DASHTrackSelection.
     *
     * @param group The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *        empty. May be in any order.
     * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
     * @param bandwidthFraction The fraction of the available bandwidth
     *        that the selection should consider available for use.
     */
    public DASHTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter, double bandwidthFraction) {
        super(group, tracks, null);

        this.bandwidthMeter = bandwidthMeter;
        this.bandwidthFraction = bandwidthFraction;

        selectedIndex = lowestBitrateIndex();
        Log.d(TAG, String.format("Initial selected index = %d", selectedIndex));
        reason = C.SELECTION_REASON_INITIAL;
    }

    @Override
    public void updateSelectedTrack(long bufferedDurationUs) {
        int currentSelectedIndex = selectedIndex;
        selectedIndex = calculateSelectedIndex();
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


    /**
     * Uses a conventional algorithm to find which track should be
     * selected.
     *
     * @return The index of the track which should be selected.
     */
    private int calculateSelectedIndex() {
        long bitrateEstimate = bandwidthMeter.getBitrateEstimate();

        if (bitrateEstimate == NO_ESTIMATE) {
            return lowestBitrateIndex();
        }
        networkRate = 0.2 * bitrateEstimate + 0.8 * networkRate;
        double effectiveNetworkRate = bandwidthFraction * networkRate;

        return findBestRateIndex(effectiveNetworkRate);
    }
}
