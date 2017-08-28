package com.example.mislplayer.sampling;

import com.example.mislplayer.trackselection.AlgorithmTrackSelection;
import com.google.android.exoplayer2.source.chunk.MediaChunk;

import java.util.List;

/**
 * Receives throughput samples from a throughput sampler, and performs
 * calculations on those samples for {@link AlgorithmTrackSelection
 * adaptation track selections}.
 */
public interface SampleProcessor {

    /**
     * An interface for the sample processor to receive samples from other
     * components.
     */
    interface Receiver {
        /**
         * Send a new throughput sample to the receiver.
         *
         * @param elapsedRealtimeMs The value of SystemClock#elapsedRealtime()
         *                          when the sample finished.
         * @param bitsTransferred The number of bits transferred during
         *                        the sample period.
         * @param durationMs The duration of the time period the sample
         *                   covers, in ms.
         */
        void sendSample(long elapsedRealtimeMs, long bitsTransferred,
                       long durationMs);

        /** Give the sample processor the most-recently downloaded chunk. */
        void giveChunk(MediaChunk chunk);
    }

    /** A sample of the available throughput. */
    interface ThroughputSample {

        /** The arrival time for the sample, in ms. */
        long arrivalTimeMs();

        /** The number of bits transferred during the sample time period. */
        long bitsTransferred();

        /** The duration of the time period the sample covers, in ms. */
        long durationMs();

        /** The throughput for the sample time period in bps. */
        double bitsPerSecond();
    }

    /** Write to file a log of the samples recorded so far. */
    void writeSampleLog();

    /** Remove all existing samples from the store. */
    void clearSamples();

    /** The duration of the current mpd, in ms. */
    long mpdDuration();

    /** The current maximum buffer length the player is aiming for, in ms. */
    long maxBufferMs();

    /** Indicates data is unavailable. */
    boolean dataNotAvailable();

    /**
     * Whether the throughput is currently decreasing.
     *
     * @return true if the throughput is currently decreasing, false otherwise
     */
    boolean throughputIsDecreasing();

    /** The index of the most recently downloaded chunk. */
    int lastChunkIndex();

    /** The representation rate of the most recently downloaded chunk, in bps. */
    int lastRepLevel();

    /** The size of the most recently downloaded chunk, in bytes. */
    long lastByteSize();

    /** The duration of the most recently downloaded chunk, in ms. */
    long lastChunkDurationMs();

    /** The throughput value for the most recent sample, in bps. */
    double lastSampleThroughput();

    /**
     * The duration of the time period covered by the most recent
     * throughput sample, in ms.
     */
    long lastSampleDurationMs();

    /** The number of bytes transferred in the last throughput sample. */
    long lastSampleBytesTransferred();

    /**
     * The minimum of the most recent throughput samples.
     *
     * <p>If the required number of samples isn't available, the available
     * samples will be used.
     *
     * @param window The number of samples to consider.
     * @return The minimum sample in the window.
     */
    double minimumThroughputSample(int window);

    /**
     * The harmonic average of the most recent throughput samples.
     *
     * <p>If the required number of samples isn't available, the available
     * samples will be used.
     *
     * @param window The number of samples to use in the calculation.
     * @return The harmonic average of the window of samples.
     */
    double sampleHarmonicAverage(int window);


    /**
     * The coefficient of variation of the most recent throughput samples.
     *
     * <p>If the required number of samples isn't available, the available
     * samples will be used.
     *
     * @param window The number of samples to use in the calculation.
     * @return The coefficient of variation of the window of samples.
     */
    double sampleCV(int window);

    /**
     * The exponential average of the most recent throughput samples.
     *
     * <p>If the required number of samples isn't available, the available
     * samples will be used.
     *
     * @param window The number of samples to use in the calculation.
     * @param exponentialAverageRatio The ratio to use for the average.
     * @return The exponential average of the window of samples.
     */
    double sampleExponentialAverage(int window,
                                    double exponentialAverageRatio);

    /**
     * The exponential variance of the most recent throughput samples.
     *
     * <p>If the required number of samples isn't available, the available
     * samples will be used.
     *
     * @param sampleAverage The exponential average of the most recent
     *                      throughput samples.
     * @param window The number of samples to use in the calculation.
     * @param exponentialVarianceRatio The ratio to use for the variance.
     * @return The exponential variance of the window of samples.
     */
    double sampleExponentialVariance(double sampleAverage, int window,
                                     double exponentialVarianceRatio);

    /** Specific calculation for the OscarH adaptation algorithm. */
    double[] oscarKumarParEstimation(int estWindow, double expAvgRatio);
}
