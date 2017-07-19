package com.google.android.exoplayer2.misl;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;

import static com.google.android.exoplayer2.upstream.BandwidthMeter.NO_ESTIMATE;

/**
 * A basic adaptation algorithm that picks the highest-bandwidth stream lower than
 * the current estimated bandwidth.
 */
public class BasicAdaptationAlgorithm extends AdaptationAlgorithm {

    public static final class Factory implements AdaptationAlgorithm.Factory {

        private final AlgorithmListener algorithmListener;

        public Factory(AlgorithmListener algorithmListener) {
            this.algorithmListener = algorithmListener;
        }

        /**
         * Creates a new algorithm.
         *
         * @param group  The {@link TrackGroup}.
         * @param tracks The indices of the selected tracks within the {@link TrackGroup}, in any order.
         * @return The created algorithm.
         */
        @Override
        public AdaptationAlgorithm createAlgorithm(TrackGroup group, int... tracks) {
            return new BasicAdaptationAlgorithm(group, tracks, algorithmListener);
        }
    }

    private static final String TAG = "BasicAlgorithm";

    private final AlgorithmListener algorithmListener;

    private int reason;

    public BasicAdaptationAlgorithm(TrackGroup group, int[] tracks, AlgorithmListener algorithmListener) {
        super(group, tracks);
        this.algorithmListener = algorithmListener;
    }

    public int determineIdealIndex(long bufferedDurationUs) {
        double bitrateEstimate = algorithmListener.lastDeliveryRate();
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
