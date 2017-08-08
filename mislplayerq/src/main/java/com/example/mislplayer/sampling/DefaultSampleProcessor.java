package com.example.mislplayer.sampling;

import android.os.Environment;
import android.util.Log;

import com.google.android.exoplayer2.source.chunk.MediaChunk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.lang.Math.min;

/**
 * A default sample processor.
 */
public class DefaultSampleProcessor implements SampleProcessor, SampleStore {

    /** A default throughput sample implementation. */
    public static class DefaultThroughputSample implements ThroughputSample {

        private long bitsTransferred;
        private long durationMs;
        private double throughputBitsPerSecond;

        public DefaultThroughputSample(long bitsTransferred, long durationMs) {
            this.bitsTransferred = bitsTransferred;
            this.durationMs = durationMs;
            this.throughputBitsPerSecond = (double)bitsTransferred * 1000 / durationMs;
        }

        /**
         * The number of bits transferred during the sample time period.
         */
        @Override
        public long bitsTransferred() {
            return bitsTransferred;
        }

        /**
         * The length of the sample time period in ms.
         */
        @Override
        public long durationMs() {
            return durationMs;
        }

        /**
         * The throughput for the sample time period in bps.
         */
        @Override
        public double bitsPerSecond() {
            return throughputBitsPerSecond;
        }
    }

    private static final String TAG = "DefaultSampleProcessor";

    private List<ThroughputSample> samples = new ArrayList<>();
    private int maxBufferMs;
    private long mpdDurationMs;

    private MediaChunk lastChunk;

    public DefaultSampleProcessor(int maxBufferMs) {
        this.maxBufferMs = maxBufferMs;
    }

    /** Adds a new throughput sample to the store. */
    @Override
    public void addSample(long bitsTransferred, long durationMs) {
        samples.add(new DefaultThroughputSample(bitsTransferred,
                durationMs));
        Log.d(TAG,
                String.format("New sample (index: %d, bits: %d, duration (ms): %d, throughput (kbps): %g)",
                        samples.size() - 1, bitsTransferred, durationMs,
                        lastSampleThroughputKbps()));
    }

    @Override
    public void giveChunk(MediaChunk chunk) {
        this.lastChunk = chunk;
    }

    @Override
    public void giveMpdDuration(long durationMs) {
        mpdDurationMs = durationMs;
    }

    /** Returns the most recent throughput sample. */
    private ThroughputSample lastSample() {
        return samples.get(samples.size() - 1);
    }

    /** Provides the duration of the current mpd. */
    @Override
    public long mpdDuration() {
        return mpdDurationMs;
    }

    /** Gives the current maximum buffer length the player is aiming for. */
    @Override
    public long maxBufferMs() {
        return maxBufferMs;
    }

    /**
     * Indicates that data on previous chunks is not available.
     *
     * @return true if data on previous chunks is not available, false
     * otherwise.
     */
    @Override
    public boolean dataNotAvailable() {return samples.size() == 0;}

    /** Returns the index of the most recently downloaded chunk. */
    @Override
    public int lastChunkIndex(){
        return lastChunk.chunkIndex;
    }

    /** Returns the representation level of the most recently downloaded chunk, in kbps. */
    @Override
    public int lastRepLevelKbps(){
        return lastChunk.trackFormat.bitrate / 1000;
    }

    /** Returns the size of the most recently downloaded chunk, in bytes. */
    @Override
    public long lastByteSize(){
        return lastChunk.bytesLoaded();
    }

    /** Returns the duration of the most recently downloaded chunk, in ms. */
    @Override
    public long lastChunkDurationMs(){
        return lastChunk.getDurationUs() / 1000;
    }

    /**
     * Returns the most recent throughput sample in kbps.
     */
    @Override
    public double lastSampleThroughputKbps() {
        return lastSample().bitsPerSecond() / 1000;
    }

    /**
     * Returns the duration of the most recent throughput sample, in ms.
     */
    @Override
    public long lastSampleDurationMs() {
        return lastSample().durationMs();
    }

    /**
     * Returns the number of bytes transferred in the last throughput
     * sample.
     */
    @Override
    public long lastSampleBytesTransferred() {
        return lastSample().bitsTransferred() / 8;
    }

    /**
     * Calculates an appropriate window size, based on the number of
     * downloaded chunks available.
     *
     * @param window The ideal window size.
     * @return The appropriate window size.
     */
    private int getWindowSize(int window) {
        return min(window, samples.size());
    }

    /**
     * Provides a number of recent throughput samples.
     *
     * @param window The number of throughput samples to provide.
     * @return If the number of throughput samples specified by `window` are
     * available, then that number of samples. If not, then as many as are
     * available.
     */
    private List<Double> getThroughputSamples(int window) {
        int workingWindow = getWindowSize(window);
        int lastIndex = samples.size();
        int firstIndex = lastIndex - workingWindow;
        List<ThroughputSample> sampleSublist = samples.subList(firstIndex, lastIndex);
        List<Double> rateSamples = new ArrayList<>(workingWindow);

        for (ThroughputSample sample : sampleSublist) {
            rateSamples.add(sample.bitsPerSecond());
        }

        return rateSamples;
    }

    /**
     * Finds the minimum of the available throughput samples.
     *
     * @param maxWindow The maximum number of most recent samples to consider.
     * @return The minimum sample in the window.
     */
    @Override
    public double getMinimumThroughputSample(int maxWindow) {
        double minimumSample = 0;

        for (double thisSample: getThroughputSamples(maxWindow)) {
            if (thisSample < minimumSample) {
                minimumSample = thisSample;
            }
        }

        return minimumSample;
    }

    /**
     * Calculates a harmonic average of the available throughput samples.
     *
     * @param preferredWindow The number of samples that should be used in
     *                        the calculation, if available.
     * @return If there are the `preferredWindow` number of samples available,
     * the harmonic average of those samples. If not, the harmonic average of
     * the samples that are available.
     */
    @Override
    public double getSampleHarmonicAverage(int preferredWindow) {
        return new HarmonicAverage(getThroughputSamples(preferredWindow)).value();
    }

    /**
     * Calculates an exponential average of the most recent throughput
     * samples.
     *
     * @param maxWindow The maximum number of recent samples to use in the
     *                  calculation.
     * @param exponentialAverageRatio The ratio to use for the exponential
     *                                average.
     * @return The exponential average of the {@code maxWindow} most
     * recent samples, if that many are available. Otherwise, the
     * exponential average of the available samples.
     */
    @Override
    public double getSampleExponentialAverage(int maxWindow,
                                              double exponentialAverageRatio) {
        return new ExponentialAverage(getThroughputSamples(maxWindow),
                exponentialAverageRatio).value();
    }

    /**
     * Calculates an exponential variance of the most recent throughput
     * samples.
     *
     * @param sampleAverage The exponential average of the most recent
     *                      throughput samples.
     * @param maxWindow The maximum number of recent samples to use in the
     *                  calculation.
     * @param exponentialVarianceRatio The ratio to use for the exponential
     *                                variance.
     * @return The exponential variance of the {@code maxWindow} most
     * recent samples, if that many are available. Otherwise, the
     * exponential variance of the available samples.
     */
    @Override
    public double getSampleExponentialVariance(double sampleAverage,
                                               int maxWindow,
                                               double exponentialVarianceRatio) {
        return new ExponentialVariance(sampleAverage,
                getThroughputSamples(maxWindow),
                exponentialVarianceRatio).value();
    }

    @Override
    public double[] oscarKumarParEstimation(int estWindow, double expAvgRatio) {
        int window = getWindowSize(estWindow);
        List<Double> rateSamples = getThroughputSamples(window);
        double maxRate=rateSamples.get(0);
        for (int i = 1; i < window; i++) {
            if (rateSamples.get(i) > maxRate)
                maxRate = rateSamples.get(i);
        }
        double normalizedSamples[] = new double[window];
        for (int i = 0; i < window; i++) {
            normalizedSamples[i] = rateSamples.get(i) / maxRate / 1.01;

        }
        // estimate the weights of different samples
        double weights[] = new double[window];
        double freshWeights[] = new double[window];
        double durationWeight[] = new double[window];
        double weightSum = (1 - Math.pow(1 - expAvgRatio, window));
        double totalDuration = 0;
        double relWght = 1;
        for (int i = 0; i < window; i++) {
            freshWeights[i] = (expAvgRatio) * Math.pow(1 - expAvgRatio, i) / weightSum;
            durationWeight[i] = lastSampleDurationMs();
            //(dash->transmittedSegmentsData[group->download_segment_index -i].receiveTime - dash->transmittedSegmentsData[group->download_segment_index -i].requestTime);
            totalDuration += durationWeight[i];
        }
        for (int i = 0; i < window; i++) {
            durationWeight[i] = durationWeight[i] / totalDuration;
            weights[i] = relWght * freshWeights[i] + (1 - relWght) * durationWeight[i];
        }
        // Calculate the average and variance
        double avgRate = 0;
        for (int i = 0; i < window; i++) {
            avgRate += weights[i] * normalizedSamples[i];
        }
        // avgRate =  OSCAR_KUM_REDUCTION * avgRate;
        double rateVar = 0;
        for (int i = 0; i < window; i++) {
            rateVar += weights[i] * Math.pow(normalizedSamples[i] - avgRate, 2);
        }
        rateVar = window * rateVar / (window - 1);

        double checkTerm = avgRate * (1 - avgRate) / rateVar;

        // estimate beta parameters for the initial guess
        double beta1 = 0;
        //double beta2 = 0;
        if (checkTerm > 1) {
            beta1 = avgRate * (checkTerm - 1);

        } else {
            beta1 = 1;

        }
        double kum1Min = beta1;
        double smin = computeS(window, normalizedSamples, weights, kum1Min);
        while (smin < 0 && kum1Min > 0.0001) {
            kum1Min = kum1Min / 2;
            smin = computeS(window, normalizedSamples, weights, kum1Min);
        }
        double kum1Max = beta1;
        double smax = computeS(window, normalizedSamples, weights, kum1Max);
        while (smax > 0 && kum1Max < 2000) {
            kum1Max = kum1Max * 2;
            smax = computeS(window, normalizedSamples, weights, kum1Max);
        }

        double kum1 = (kum1Max + kum1Min) / 2;
        double smid = 0;
        while (kum1Max - kum1Min > 0.001) {
            kum1 = (kum1Max + kum1Min) / 2;
            smid = computeS(window, normalizedSamples, weights, kum1);
            if (smid > 0)
                kum1Min = kum1;
            else
                kum1Max = kum1;
        }

        double kum2 = 0;
        for (int i = 0; i < window; i++) {
            kum2 += weights[i] * Math.log(1 - Math.pow(normalizedSamples[i], kum1));
        }

        if (kum2 == 0)
            kum2 = 1e-5;
        else {
            kum2 = -1 / kum2;
            kum2 = kum2 > 1e5 ? 1e5 : kum2;
        }
        return new double[] {kum1, kum2, 0, maxRate};
    }

    private double computeS(int window, double[] samples, double[] weights, double kum1) {
        double T1 = 0;
        double T2 = 0;
        double T3 = 0;
        double y, yCompl;

        for (int i = 0; i < window; i++) {

            y = Math.pow(samples[i], kum1);
            yCompl = 1 - y;
            T1 += weights[i] * Math.log(y) / (yCompl);
            T2 += weights[i] * Math.log(y) / (yCompl) * y;
            T3 += weights[i] * Math.log(yCompl);
        }

        return (1 + T1 + T2 / T3);
    }
}
