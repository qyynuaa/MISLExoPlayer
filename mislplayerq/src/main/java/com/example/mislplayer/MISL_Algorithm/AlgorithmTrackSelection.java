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

    public double getVar(int mode, int window, double avgRate, double[] rates,
                         double expAvgRatio) {
        double retVal = 0;
        double totDeviation = 0;
        int i;
        switch (mode) {
            case 1:  // moving average : MOVING_AVG_ID
            {
                for (i = 0; i < window; i++) {
                    totDeviation += Math.pow(avgRate - rates[i], 2);
                }
                if (window > 1) {
                    retVal = totDeviation / (window - 1);
                } else {
                    Log.d(TAG, "Call rate variance estimator with uniary window");
                }
                break;
            }
            case 2: // harmonic average id : HARMONIC_AVG_ID
            {
                double rateRcp[] = new double[window];
                double reducedAvg[] = new double[window];
                double rateRcpSum = 0;
                double reducedAvged = 0;
                for (i = 0; i < window; i++) {
                    rateRcp[i] += 1 / rates[i];
                    rateRcpSum += rateRcp[i];
                }
                for (i = 0; i < window; i++) {
                    reducedAvg[i] = (window - 1) / (rateRcpSum - rateRcp[i]);
                    reducedAvged += reducedAvg[i];
                }
                reducedAvged = reducedAvged / window;
                for (i = 0; i < window; i++) {
                    totDeviation += Math.pow(reducedAvged - reducedAvg[i], 2);
                }
                retVal = (1 - 1 / window) * totDeviation;
                break;
            }
            case 3: // EXP_AVG_ID
            {

                double weights[] = new double[window];
                for (i = 0; i < window; i++) {
                    if (i == window - 1)
                        weights[i] = Math.pow(1 - expAvgRatio, i);
                    else
                        weights[i] = (expAvgRatio) * Math.pow(1 - expAvgRatio, i);
                    totDeviation += weights[i] * Math.pow(avgRate - rates[i], 2);
                }
                retVal = window * totDeviation / (window - 1);
                break;
            }
            default: {
                Log.d(TAG, "Invalid averaging mode for variance estimator");
                break;

            }
        }
        return retVal;
    }

    double getAvgRate(int mode, int window, double[] rates,
                      double expAvgRatio) {
        double retVal = 0;
        int i;
        switch (mode) {
            case 1:  // moving average
            {
                for (i = 0; i < window; i++) {
                    retVal += rates[i];
                }
                retVal = retVal / window;
                break;
            }
            case 2: {
                for (i = 0; i < window; i++) {
                    retVal += 1 / rates[i];
                }
                retVal = window / retVal;
                break;
            }
            case 3: {
                double weights[] = new double[window];
                for (i = 0; i < window; i++) {
                    if (i == window - 1)
                        weights[i] = Math.pow(1 - expAvgRatio, i);
                    else
                        weights[i] = (expAvgRatio) * Math.pow(1 - expAvgRatio, i);
                    retVal += weights[i] * rates[i];
                }
                break;
            }
            default: {
                Log.d(TAG, "Invalid averaging mode for rate estimator");

                break;
            }
        }
        return retVal;
    }
}
