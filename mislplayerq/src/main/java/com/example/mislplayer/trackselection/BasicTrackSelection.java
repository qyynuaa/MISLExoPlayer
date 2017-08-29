package com.example.mislplayer.trackselection;

import android.util.Log;

import com.example.mislplayer.sampling.SampleProcessor;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;

/**
 * Uses the single most recent throughput sample to choose an ideal track.
 */

public class BasicTrackSelection extends AlgorithmTrackSelection {

    public static final class Factory implements TrackSelection.Factory {

        private SampleProcessor sampleProcessor;

        public Factory(SampleProcessor sampleProcessor) {
            this.sampleProcessor = sampleProcessor;
        }

        /**
         * Creates a new selection.
         *
         * @param group  The {@link TrackGroup}. Must not be null.
         * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
         *               null or empty. May be in any order.
         * @return The created selection.
         */
        @Override
        public TrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new BasicTrackSelection(group, tracks, sampleProcessor);
        }
    }

    private static final String TAG = "BasicTrackSelection";

    private int selectedIndex = lowestBitrateIndex();
    private int selectionReason = C.SELECTION_REASON_INITIAL;

    /**
     * @param group  The {@link TrackGroup}. Must not be null.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     */
    public BasicTrackSelection(TrackGroup group, int[] tracks, SampleProcessor sampleProcessor) {
        super(group, tracks, sampleProcessor);
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
        return selectionReason;
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
