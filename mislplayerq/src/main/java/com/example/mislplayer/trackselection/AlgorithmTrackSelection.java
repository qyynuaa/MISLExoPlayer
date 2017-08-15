package com.example.mislplayer.trackselection;

import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;

import static java.lang.Math.min;

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

    public int lowestBitrate() {
        return group.getFormat(tracks[tracks.length - 1]).bitrate;
    }

    public int highestBitrate() {
        return group.getFormat(tracks[0]).bitrate;
    }

    public int lowestBitrateIndex() {
        return tracks[tracks.length - 1];
    }

    public int highestBitrateIndex() {
        return tracks[0];
    }

    /**
     * Finds the index of the lowest representation level whose rate is above
     * a target rate.
     *
     * @param targetRate The target rate to find a representation level
     *                   above, in kbps.
     * @return The index of the lowest representation level above the target
     * rate, or the highest representation level available.
     */
    public int getNearestBitrateIndex(double targetRate){
        for (int i = tracks.length - 1; i >= 0; i--) {
            if (group.getFormat(tracks[i]).bitrate / 1000 >= targetRate) {
                return tracks[i];
            }
        }

        return tracks[0];
    }

    /**
     * Finds the index for the highest quality level below a target rate.
     *
     * @param targetRate The target rate.
     * @return The index of the highest suitable quality level.
     */
    public int findBestRateIndex(double targetRate) {
        for (int i = 0; i < length; i++) {
            if (getFormat(i).bitrate < targetRate) {
                return tracks[i];
            }
        }
        return length - 1;
    }

    /**
     * Finds the index of the track with a given bitrate.
     *
     * @param bitrate The bitrate of the desired track, in bps.
     * @return The index of the track with the given bitrate.
     */
    public int getRepIndex(int bitrate) {
        for (int i = 0; i < length; i++) {
            if (getFormat(i).bitrate == bitrate) {
                return i;
            }
        }
        throw new IllegalArgumentException(
                "No track exists with that bitrate");
    }
}
