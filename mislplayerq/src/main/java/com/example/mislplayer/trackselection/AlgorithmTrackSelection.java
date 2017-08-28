package com.example.mislplayer.trackselection;

import com.example.mislplayer.FutureSegmentInfos;
import com.example.mislplayer.PlayerActivity;
import com.example.mislplayer.sampling.SampleProcessor;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.BaseTrackSelection;

/**
 * A common superclass for track selections which implement an adaptation
 * algorithm.
 */
public abstract class AlgorithmTrackSelection extends BaseTrackSelection {

    private final String TAG = "AlgorithmTrackSelection";

    protected final SampleProcessor sampleProcessor;

    public AlgorithmTrackSelection(TrackGroup group, int[] tracks,
                                   SampleProcessor sampleProcessor) {
        super(group, tracks);
        this.sampleProcessor = sampleProcessor;
    }

    public int lowestBitrate() {
        return getFormat(lowestBitrateIndex()).bitrate;
    }

    public int highestBitrate() {
        return getFormat(highestBitrateIndex()).bitrate;
    }

    public int lowestBitrateIndex() {
        return length - 1;
    }

    public int highestBitrateIndex() {
        return 0;
    }

    /**
     * Finds the index for the highest quality level below a target rate.
     *
     * @param targetRate The target rate, in bps.
     * @return The index of the highest suitable quality level.
     */
    public int findBestRateIndex(double targetRate) {
        for (int i = 0; i < length; i++) {
            if (getFormat(i).bitrate < targetRate) {
                return i;
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
