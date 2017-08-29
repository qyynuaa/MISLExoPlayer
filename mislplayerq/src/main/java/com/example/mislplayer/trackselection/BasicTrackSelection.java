package com.example.mislplayer.trackselection;

import android.util.Log;

import com.example.mislplayer.sampling.SampleProcessor;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;

/**
 * Selects adaptive media tracks by using the most recent throughput
 * sample as a bandwidth estimate.
 */
public class BasicTrackSelection extends AlgorithmTrackSelection {

    /**
     * Creates BasicTrackSelection instances.
     */
    public static final class Factory implements TrackSelection.Factory {

        private SampleProcessor sampleProcessor;

        /**
         * Creates a new BasicTrackSelection factory.
         *
         * @param sampleProcessor Provides information about throughput
         *        samples to the algorithm.
         */
        public Factory(SampleProcessor sampleProcessor) {
            this.sampleProcessor = sampleProcessor;
        }

        @Override
        public TrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new BasicTrackSelection(group, tracks, sampleProcessor);
        }
    }

    private static final String TAG = "BasicTrackSelection";

    private int selectedIndex = lowestBitrateIndex();
    private int selectionReason = C.SELECTION_REASON_INITIAL;

    /**
     * Creates a BasicTrackSelection.
     *
     * @param group  The {@link TrackGroup}. Must not be null.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}.
     * @param sampleProcessor Provides information about throughput
     *        samples to the algorithm.
     */
    public BasicTrackSelection(TrackGroup group, int[] tracks, SampleProcessor sampleProcessor) {
        super(group, tracks, sampleProcessor);
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
            Log.d(TAG, "No data available.");
        } else {
            double throughputSample = sampleProcessor.lastSampleThroughput();
            Log.d(TAG, String.format("Throughput sample (kbps): %g", throughputSample / 1000));
            selectedIndex = findBestRateIndex(throughputSample);
            Log.d(TAG, String.format("Changed selected index to: %d", selectedIndex));
        }
    }
}
