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

    /**
     * A pythagorean arithmetic average.
     */
    public class ArithmeticAverage {

        private int window;
        private double[] rates;

        public ArithmeticAverage(int window, double[] rates) {
            this.window = window;
            this.rates = rates;
        }

        public double value() {
            double subTotal = 0;
            for (int i = 0; i < window; i++) {
                subTotal += rates[i];
            }
            return subTotal / window;
        }
    }

    public class ArithmeticVariance {

        private int window;
        private double averageRate;
        private double[] rates;

        public ArithmeticVariance(int window, double averageRate, double[] rates) {
            this.window = window;
            this.averageRate = averageRate;
            this.rates = rates;
        }

        public double value() {
            double totalDeviation = 0;
            double result = 0;

            for (int i = 0; i < window; i++) {
                totalDeviation += Math.pow(averageRate - rates[i], 2);
            }
            if (window > 1) {
                result = totalDeviation / (window - 1);
            } else {
                Log.d(TAG, "Call rate variance estimator with uniary window");
            }

            return result;
        }
    }

    /**
     * A pythagorean harmonic average.
     */
    public class HarmonicAverage {

        private int window;
        private double[] rates;

        public HarmonicAverage(int window, double[] rates) {
            this.window = window;
            this.rates = rates;
        }

        public double value() {
            double subTotal = 0;
            for (int i = 0; i < window; i++) {
                subTotal += 1 / rates[i];
            }
            return window / subTotal;
        }
    }

    public class HarmonicVariance {

        private int window;
        private double[] rates;

        public HarmonicVariance(int window, double[] rates) {
            this.window = window;
            this.rates = rates;
        }

        public double value() {
            double rateRcp[] = new double[window];
            double reducedAvg[] = new double[window];
            double rateRcpSum = 0;
            double reducedAvged = 0;
            double totalDeviation = 0;

            for (int i = 0; i < window; i++) {
                rateRcp[i] += 1 / rates[i];
                rateRcpSum += rateRcp[i];
            }
            for (int i = 0; i < window; i++) {
                reducedAvg[i] = (window - 1) / (rateRcpSum - rateRcp[i]);
                reducedAvged += reducedAvg[i];
            }
            reducedAvged = reducedAvged / window;
            for (int i = 0; i < window; i++) {
                totalDeviation += Math.pow(reducedAvged - reducedAvg[i], 2);
            }
            return (1 - 1 / window) * totalDeviation;
        }
    }

    /**
     * An exponential average.
     */
    public class ExponentialAverage {

        private int window;
        private double[] rates;
        private double ratio;

        public ExponentialAverage(int window, double[] rates, double ratio) {
            this.window = window;
            this.rates = rates;
            this.ratio = ratio;
        }

        public double value() {
            double weights[] = new double[window];
            double subTotal = 0;

            for (int i = 0; i < window; i++) {
                if (i == window - 1) {
                    weights[i] = Math.pow(1 - ratio, i);
                }
                else {
                    weights[i] = (ratio) * Math.pow(1 - ratio, i);
                }
                subTotal += weights[i] * rates[i];
            }
            return subTotal;
        }
    }

    public class ExponentialVariance {

        private int window;
        private double averageRate;
        private double[] rates;
        private double ratio;

        public ExponentialVariance(int window, double averageRate, double[] rates, double ratio) {
            this.window = window;
            this.averageRate = averageRate;
            this.rates = rates;
            this.ratio = ratio;
        }

        public double value() {
            double weights[] = new double[window];
            double totalDeviation = 0;

            for (int i = 0; i < window; i++) {
                if (i == window - 1)
                    weights[i] = Math.pow(1 - ratio, i);
                else
                    weights[i] = (ratio) * Math.pow(1 - ratio, i);
                totalDeviation += weights[i] * Math.pow(averageRate - rates[i], 2);
            }
            return window * totalDeviation / (window - 1);
        }
    }
}
