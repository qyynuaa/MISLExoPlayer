package com.example.mislplayer.MISL_Algorithm;

import android.util.Log;

import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;

/**
 * A common superclass for track selections which implement an adaptation
 * algorithm.
 */
public abstract class AlgorithmTrackSelection extends BaseTrackSelection {

    private final String TAG = "AlgorithmTrackSelection";

    /**
     * @param group The {@link TrackGroup}. Must not be null.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     null or empty. May be in any order.
     */
    public AlgorithmTrackSelection(TrackGroup group, int... tracks) {
        super(group, tracks);
    }

}
