package com.example.mislplayer.sampling;

import android.util.Log;

import com.example.mislplayer.ManifestListener;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.hls.HlsManifest;
import com.google.android.exoplayer2.source.smoothstreaming.manifest.SsManifest;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.example.mislplayer.PlayerActivity.LOG_DIRECTORY_PATH;
import static java.lang.Math.min;

/**
 * A default sample processor.
 */
public class DefaultSampleProcessor implements SampleProcessor, SampleProcessor.Receiver,
        ExoPlayer.EventListener, ManifestListener.ManifestRequestTimeReceiver {

    /** A default throughput sample implementation. */
    public static class DefaultThroughputSample implements ThroughputSample {

        private long arrivalTimeMs;
        private long bitsTransferred;
        private long durationMs;
        private double throughputBitsPerSecond;

        public DefaultThroughputSample(long arrivalTimeMs,
                                       long bitsTransferred, long durationMs) {
            this.arrivalTimeMs = arrivalTimeMs;
            this.bitsTransferred = bitsTransferred;
            this.durationMs = durationMs;
            this.throughputBitsPerSecond = (double)bitsTransferred * 1000 / durationMs;
        }

        @Override
        public long arrivalTimeMs() {
            return arrivalTimeMs;
        }

        @Override
        public long bitsTransferred() {
            return bitsTransferred;
        }

        @Override
        public long durationMs() {
            return durationMs;
        }

        @Override
        public double bitsPerSecond() {
            return throughputBitsPerSecond;
        }
    }

    private static final String TAG = "DefaultSampleProcessor";
    private static final int DATA_NOT_AVAILABLE = -1;

    private List<ThroughputSample> samples = new ArrayList<>();
    private int maxBufferMs;
    private long mpdDurationMs = DATA_NOT_AVAILABLE;
    private long manifestRequestTime;

    private MediaChunk lastChunk;

    public DefaultSampleProcessor(int maxBufferMs) {
        this.maxBufferMs = maxBufferMs;
    }

    @Override
    public void sendSample(long elapsedRealtimeMs, long bitsTransferred,
                           long durationMs) {
        long arrivalTime = elapsedRealtimeMs - manifestRequestTime;
        samples.add(
                new DefaultThroughputSample(arrivalTime, bitsTransferred,
                        durationMs)
        );
        Log.d(TAG,
                String.format("New sample (index: %d, bits: %d, duration (ms): %d, throughput (kbps): %g)",
                        samples.size() - 1, bitsTransferred, durationMs,
                        lastSampleThroughput() / 1000));
    }

    @Override
    public void writeSampleLog() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss");
        Date date = new Date();
        File directory = new File(LOG_DIRECTORY_PATH);
        File file = new File(directory, "/" + dateFormat.format(date) + "_Sample_Log.txt");

        try {
            if (!directory.exists()) {
                directory.mkdirs();
            }
            file.createNewFile();
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(("Arrival_Time\t\tBytes_Transferred\t\tDuration\t\tThroughput\n").getBytes());

            for (ThroughputSample sample : samples) {
                String logLine = String.format("%12d\t\t%17d\t\t%8d\t\t%10d\n",
                        sample.arrivalTimeMs(),
                        sample.bitsTransferred() * 8,
                        sample.durationMs(),
                        Math.round(sample.bitsPerSecond() / 1000));
                stream.write(logLine.getBytes());
            }
            stream.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    @Override
    public void clearSamples() {
        samples.clear();
    }

    @Override
    public void giveChunk(MediaChunk chunk) {
        this.lastChunk = chunk;
    }

    /** Returns the most recent throughput sample. */
    private ThroughputSample lastSample() {
        return samples.get(samples.size() - 1);
    }

    @Override
    public long mpdDuration() {
        return mpdDurationMs;
    }

    @Override
    public long maxBufferMs() {
        return maxBufferMs;
    }

    @Override
    public boolean dataNotAvailable() {return samples.size() == 0;}

    @Override
    public boolean throughputIsDecreasing() {
        if (samples.size() < 2) {
            return false;
        } else {
            List<Double> lastTwoSamples = throughputSamples(2);
            return lastTwoSamples.get(1) < lastTwoSamples.get(0);
        }
    }

    @Override
    public int lastChunkIndex(){
        return lastChunk.chunkIndex;
    }

    @Override
    public int lastRepLevel(){
        return lastChunk.trackFormat.bitrate;
    }

    @Override
    public long lastByteSize(){
        return lastChunk.bytesLoaded();
    }

    @Override
    public long lastChunkDurationMs(){
        return lastChunk.getDurationUs() / 1000;
    }

    @Override
    public double lastSampleThroughput() {
        return lastSample().bitsPerSecond();
    }

    @Override
    public long lastSampleDurationMs() {
        return lastSample().durationMs();
    }

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
    private int windowSize(int window) {
        return min(window, samples.size());
    }

    /**
     * Provides a number of recent throughput samples.
     *
     * <p>If the required number of throughput samples isn't available, the
     * available samples will be provided.
     *
     * @param window The number of throughput samples to provide.
     * @return The window of recent throughput samples.
     */
    private List<Double> throughputSamples(int window) {
        int workingWindow = windowSize(window);
        int lastIndex = samples.size();
        int firstIndex = lastIndex - workingWindow;
        List<ThroughputSample> sampleSublist = samples.subList(firstIndex, lastIndex);
        List<Double> rateSamples = new ArrayList<>(workingWindow);

        for (ThroughputSample sample : sampleSublist) {
            rateSamples.add(sample.bitsPerSecond());
        }

        return rateSamples;
    }

    @Override
    public double minimumThroughputSample(int window) {
        return Collections.min(throughputSamples(window));
    }

    @Override
    public double sampleHarmonicAverage(int window) {
        return harmonicAverage(throughputSamples(window));
    }

    @Override
    public double sampleCV(int window) {
        List<Double> throughputSamples = throughputSamples(window);
        return coefficientOfVariation(throughputSamples);
    }

    @Override
    public double sampleExponentialAverage(int window,
                                           double exponentialAverageRatio) {
        return exponentialAverage(throughputSamples(window),
                exponentialAverageRatio);
    }

    @Override
    public double sampleExponentialVariance(double sampleAverage,
                                            int window,
                                            double exponentialVarianceRatio) {
        return exponentialVariance(throughputSamples(window),
                sampleAverage, exponentialVarianceRatio);
    }

    @Override
    public double[] oscarKumarParEstimation(int estWindow, double expAvgRatio) {
        int window = windowSize(estWindow);
        List<Double> rateSamples = throughputSamples(window);

        double maxRate = Collections.max(rateSamples);

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
            durationWeight[i] = (double) lastSampleDurationMs() / 1000;
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

    // averages and variances

    /** Calculates the coefficient of variation of a list of values. */
    public static double coefficientOfVariation(List<Double> inputValues) {
        double average = arithmeticAverage(inputValues);
        double variance = arithmeticVariance(inputValues, average);

        return Math.sqrt(variance) / average;
    }

    /** Calculates the pythagorean arithmetic average of a list of values. */
    public static double arithmeticAverage(List<Double> values) {
        double subTotal = 0;
        for (double value : values) {
            subTotal += value;
        }
        return subTotal / values.size();
    }

    /** Calculates the arithmetic variance of a list of values. */
    public static double arithmeticVariance(List<Double> values,
                                             double inputAverage) {
        double totalDeviation = 0;
        double result = 0;

        for (double value : values) {
            totalDeviation += Math.pow(inputAverage - value, 2);
        }
        if (values.size() > 1) {
            result = totalDeviation / (values.size() - 1);
        }

        return result;
    }

    /** Calculates the harmonic average of a list of values. */
    public static double harmonicAverage(List<Double> values) {
        double subTotal = 0;
        for (double value : values) {
            subTotal += 1 / value;
        }
        return values.size() / subTotal;
    }

    /** Calculates the exponential average of a list of values. */
    public static double exponentialAverage(List<Double> values,
                                             double ratio) {
        double weightSum = (1 - Math.pow(1 - ratio, values.size()));
        double subTotal = 0;

        for (int i = 0; i < values.size(); i++) {
            double thisWeight = ratio * Math.pow(1 - ratio, i) / weightSum;
            subTotal += thisWeight * values.get(i);
        }
        return subTotal;
    }

    /** Calculates the exponential variance of a list of values. */
    public static double exponentialVariance(List<Double> values, double average,
                                       double ratio) {
        double weightSum = (1 - Math.pow(1 - ratio, values.size()));
        double totalDeviation = 0;

        for (int i = 0; i < values.size(); i++) {
            double thisWeight = (ratio) * Math.pow(1 - ratio, i) / weightSum;
            totalDeviation += thisWeight * Math.pow(average - values.get(i), 2);
        }
        return values.size() * totalDeviation / (values.size() - 1);
    }

    // ManifestRequestTimeReceiver implementation

    @Override
    public void giveManifestRequestTime(long manifestRequestTime) {
        this.manifestRequestTime = manifestRequestTime;
    }

    // ExoPlayer EventListener implementation

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {
        if (manifest != null) {
            if (manifest instanceof DashManifest) {
                DashManifest dashManifest = (DashManifest) manifest;
                mpdDurationMs = dashManifest.duration;
            } else if (manifest instanceof HlsManifest) {
                HlsManifest hlsManifest = (HlsManifest) manifest;
                mpdDurationMs = hlsManifest.mediaPlaylist.durationUs / 1000;
            } else if (manifest instanceof SsManifest) {
                SsManifest ssManifest = (SsManifest) manifest;
                mpdDurationMs = ssManifest.durationUs / 1000;
            }
        }
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

    @Override
    public void onLoadingChanged(boolean isLoading) {}

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {}

    @Override
    public void onPlayerError(ExoPlaybackException error) {}

    @Override
    public void onPositionDiscontinuity() {}

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}
}
