package com.example.mislplayer.sampling;

import com.example.mislplayer.trackselection.AlgorithmTrackSelection;
import com.google.android.exoplayer2.source.chunk.MediaChunk;

import java.util.List;

/**
 * Provides necessary information to
 * {@link AlgorithmTrackSelection algorithm track selections}.
 */
public interface SampleProcessor {

    /** A sample of the available throughput. */
    interface ThroughputSample {

        /** The arrival time for the sample, in ms. */
        long arrivalTimeMs();

        /** The number of bits transferred during the sample time period. */
        long bitsTransferred();

        /** The length of the sample time period in ms. */
        long durationMs();

        /** The throughput for the sample time period in bps. */
        double bitsPerSecond();
    }

    /** Writes a log of the samples so far to file. */
    void writeSampleLog();

    /** Removes all existing samples. */
    void clearSamples();

    void giveChunk(MediaChunk chunk);

    /** Provides the duration of the current mpd. */
    long mpdDuration();

    /** Gives the current maximum buffer length the player is aiming for. */
    long maxBufferMs();

    /** Indicates data is unavailable. */
    boolean dataNotAvailable();

    /** Returns the index of the most recently downloaded chunk. */
    int lastChunkIndex();

    /** Returns the representation level of the most recently downloaded chunk, in bps. */
    int lastChunkRepLevel();

    /** Returns the size of the most recently downloaded chunk, in bytes. */
    long lastByteSize();

    /** Returns the duration of the most recently downloaded chunk, in ms. */
    long lastChunkDurationMs();

    /** Returns the most recent throughput sample in bps. */
    double lastSampleThroughput();

    /** Returns the duration of the most recent throughput sample, in ms. */
    long lastSampleDurationMs();

    /**
     * Returns the number of bytes transferred in the last throughput
     * sample.
     */
    long lastSampleBytesTransferred();

    /**
     * Finds the minimum of the most recent throughput samples.
     *
     * @param maxWindow The maximum number of samples to consider.
     * @return The minimum sample in the window.
     */
    double getMinimumThroughputSample(int maxWindow);

    /**
     * Calculates a harmonic average of the most recent throughput samples.
     *
     * <p>If the required number of samples isn't available, the average
     * will be of the available samples.
     *
     * @param preferredWindow The maximum number of samples to use in the
     *                        calculation.
     * @return The harmonic average of the window of samples.
     */
    double getSampleHarmonicAverage(int preferredWindow);

    /**
     * Calculates an exponential average of the most recent throughput
     * samples.
     *
     * <p>If the required number of samples isn't available, the average
     * will be of the available samples.
     *
     * @param maxWindow The maximum number of recent samples to use in the
     *                  calculation.
     * @param exponentialAverageRatio The ratio to use for the exponential
     *                                average.
     * @return The exponential average of the window of samples.
     */
    double getSampleExponentialAverage(int maxWindow,
                                              double exponentialAverageRatio);

    /**
     * Calculates an exponential variance of the most recent throughput
     * samples.
     *
     * <p>If the required number of samples isn't available, the variance
     * will be of the available samples.
     *
     * @param sampleAverage The exponential average of the most recent
     *                      throughput samples.
     * @param maxWindow The maximum number of recent samples to use in the
     *                  calculation.
     * @param exponentialVarianceRatio The ratio to use for the exponential
     *                                variance.
     * @return The exponential variance of the window of samples.
     */
    double getSampleExponentialVariance(double sampleAverage,
                                               int maxWindow,
                                               double exponentialVarianceRatio);

    double[] oscarKumarParEstimation(int estWindow, double expAvgRatio);

    /**
     * A pythagorean arithmetic average.
     */
    class ArithmeticAverage {

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

    class ArithmeticVariance {

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
            }

            return result;
        }
    }

    /**
     * A pythagorean harmonic average.
     */
    class HarmonicAverage {

        private int window;
        private List<Double> rates;

        public HarmonicAverage(List<Double> samples) {
            this(samples.size(), samples);
        }

        public HarmonicAverage(int window, List<Double> rates) {
            this.window = window;
            this.rates = rates;
        }

        public double value() {
            double subTotal = 0;
            for (int i = 0; i < window; i++) {
                subTotal += 1 / rates.get(i);
            }
            return window / subTotal;
        }
    }

    class HarmonicVariance {

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
    class ExponentialAverage {

        private List<Double> rates;
        private double ratio;

        public ExponentialAverage(List<Double> rates, double ratio) {
            this.rates = rates;
            this.ratio = ratio;
        }

        public double value() {
            double weightSum = (1 - Math.pow(1 - ratio, rates.size()));
            double subTotal = 0;

            for (int i = 0; i < rates.size(); i++) {
                double thisWeight = ratio * Math.pow(1 - ratio, i) / weightSum;
                subTotal += thisWeight * rates.get(i);
            }
            return subTotal;
        }
    }

    class ExponentialVariance {

        private double averageRate;
        private List<Double> rates;
        private double ratio;

        public ExponentialVariance(double averageRate, List<Double> rates, double ratio) {
            this.averageRate = averageRate;
            this.rates = rates;
            this.ratio = ratio;
        }

        public double value() {
            double weightSum = (1 - Math.pow(1 - ratio, rates.size()));
            double totalDeviation = 0;

            for (int i = 0; i < rates.size(); i++) {
                double thisWeight = (ratio) * Math.pow(1 - ratio, i) / weightSum;
                totalDeviation += thisWeight * Math.pow(averageRate - rates.get(i), 2);
            }
            return rates.size() * totalDeviation / (rates.size() - 1);
        }
    }
}
