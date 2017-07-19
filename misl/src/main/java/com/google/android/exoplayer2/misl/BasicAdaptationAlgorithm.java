package com.google.android.exoplayer2.misl;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;

import static com.google.android.exoplayer2.misl.AlgorithmListener.DATA_NOT_AVAILABLE;
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
    public static final int INITIAL_AVERAGE_RATE = -1;

    private final AlgorithmListener algorithmListener;

    private int reason;
    private double averageRate = INITIAL_AVERAGE_RATE;

    public BasicAdaptationAlgorithm(TrackGroup group, int[] tracks, AlgorithmListener algorithmListener) {
        super(group, tracks);
        this.algorithmListener = algorithmListener;
    }

    public int determineIdealIndex(long bufferedDurationUs) {
        double deliveryRate = algorithmListener.lastDeliveryRate();
        int selectedIndex = getTracks().length - 1;

        if (deliveryRate != DATA_NOT_AVAILABLE) {
            updateAverage(deliveryRate);
        }

        if (averageRate == INITIAL_AVERAGE_RATE) {
            Log.d(TAG, "No bitrate estimate available; choosing lowest-bandwidth stream.");
            setReason(C.SELECTION_REASON_INITIAL);
            return selectedIndex;
        } else {
            Log.d(TAG, String.format("New bitrate sample = %e", deliveryRate));
            Log.d(TAG, String.format("Bitrate estimate = %e", averageRate));
            Log.d(TAG, String.format("Last chunk index is %d", algorithmListener.lastSegmentNumber()));
            for (int i = 0; i < getTracks().length; i++) {
                int thisBitrate = getGroup().getFormat(getTracks()[i]).bitrate;

                if (thisBitrate < averageRate || i == getTracks().length - 1) {
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

    private void updateAverage(double newSegmentRate) {
        if (averageRate == INITIAL_AVERAGE_RATE) {
            averageRate = newSegmentRate;
        } else {
            averageRate = 0.8 * averageRate + 0.2 * newSegmentRate;
        }
    }
}
