package com.google.android.exoplayer2.misl;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.upstream.BandwidthMeter;

import static com.google.android.exoplayer2.upstream.BandwidthMeter.NO_ESTIMATE;

/**
 * A basic adaptation algorithm that picks the highest-bandwidth stream lower than
 * the current estimated bandwidth.
 */
public class BasicAdaptationAlgorithm extends AdaptationAlgorithm {

    private static final String TAG = "BasicAlgorithm";

    private final BandwidthMeter bandwidthMeter;

    private int reason;

    public BasicAdaptationAlgorithm(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter) {
        super(group, tracks);
        this.bandwidthMeter = bandwidthMeter;
    }

    public int determineIdealIndex(long bufferedDurationUs) {
        long bitrateEstimate = bandwidthMeter.getBitrateEstimate();
        Log.d(TAG, "Bitrate estimate = " + bitrateEstimate);
        int selectedIndex = getTracks().length - 1;

        if (bitrateEstimate == NO_ESTIMATE) {
            Log.d(TAG, "No bitrate estimate available; choosing lowest-bandwidth stream.");
            setReason(C.SELECTION_REASON_INITIAL);
            return selectedIndex;
        } else {
            for (int i = 0; i < getTracks().length; i++) {
                int thisBitrate = getGroup().getFormat(getTracks()[i]).bitrate;

                if (thisBitrate < bitrateEstimate || i == getTracks().length - 1) {
                    Log.d(TAG, "Selecting bitrate: " + thisBitrate);
                    selectedIndex = i;
                    break;
                }
            }
            setReason(C.SELECTION_REASON_ADAPTIVE);
            return selectedIndex;
        }
    }

    @Override
    public int getSelectionReason() {
        return getReason();
    }

    private int getReason() {
        return reason;
    }

    private void setReason(int newReason) {
        reason = newReason;
    }
}
