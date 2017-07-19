package com.google.android.exoplayer2.misl;

import com.google.android.exoplayer2.source.TrackGroup;

/**
 * An algorithm that determines which track an {@link AlgorithmTrackSelection} should select.
 */
public abstract class AdaptationAlgorithm {

    public interface Factory {
        /**
         * Creates a new algorithm.
         *
         * @param group The {@link TrackGroup}.
         * @param tracks The indices of the selected tracks within the {@link TrackGroup}, in any order.
         * @return The created algorithm.
         */
        AdaptationAlgorithm createAlgorithm(TrackGroup group, int... tracks);
    }

    private final static String TAG = "AdaptationAlgorithm";
    private final TrackGroup group;
    private final int[] tracks;

    /**
     * Creates a new algorithm.
     *
     * @param group The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks in group, in order of decreasing bandwidth.
     */
    public AdaptationAlgorithm(TrackGroup group, int[] tracks) {
        this.group = group;
        this.tracks = tracks;
    }

    public TrackGroup getGroup() {
        return group;
    }

    public int[] getTracks() {
        return tracks;
    }

    /**
     * Calculates the index of the TrackGroup track that should be used.
     * @param bufferedDurationUs The duration of media currently buffered in microseconds.
     * @return The index of the TrackGroup track that should be used.
     */
    public abstract int determineIdealIndex(long bufferedDurationUs);

    public abstract int getSelectionReason();
}
