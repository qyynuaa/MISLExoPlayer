package com.google.android.exoplayer2.misl;

import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;


public class DefaultAlgorithmTrackSelection extends BaseTrackSelection
        implements AlgorithmTrackSelection {

    public static final class Factory implements TrackSelection.Factory {

        private AdaptationAlgorithm.Factory adaptationAlgorithmFactory;

        public Factory(AdaptationAlgorithm.Factory adaptationAlgorithmFactory) {
            this.adaptationAlgorithmFactory = adaptationAlgorithmFactory;
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
        public DefaultAlgorithmTrackSelection createTrackSelection(TrackGroup group, int... tracks) {
            return new DefaultAlgorithmTrackSelection(adaptationAlgorithmFactory, group, tracks);
        }
    }

    private static final String TAG = "DefaultATS";

    private AdaptationAlgorithm algorithm;
    private int selectedIndex;
    private int reason;

    public DefaultAlgorithmTrackSelection(AdaptationAlgorithm.Factory adaptationAlgorithmFactory,
                                          TrackGroup group, int[] tracks) {
        super(group, tracks);
        this.algorithm = adaptationAlgorithmFactory.createAlgorithm(this.group, this.tracks);
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
        return reason;
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
        selectedIndex = algorithm.determineIdealIndex(bufferedDurationUs);
        reason = algorithm.getSelectionReason();
    }
}
