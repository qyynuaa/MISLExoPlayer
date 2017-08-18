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

    /**
     * Whether the throughput is currently decreasing.
     *
     * @return true if the throughput is currently decreasing, false otherwise
     */
    boolean throughputIsDecreasing();

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
     * Calculates the coefficient of variation of the most recent
     * throughput samples.
     *
     * <p>If the required number of samples isn't available, the available
     * samples will be used.
     *
     * @param preferredWindow The maximum number of samples to use in the
     *                        calculation
     * @return The coefficient of variation of the window of samples.
     */
    double getSampleCV(int preferredWindow);

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
}
