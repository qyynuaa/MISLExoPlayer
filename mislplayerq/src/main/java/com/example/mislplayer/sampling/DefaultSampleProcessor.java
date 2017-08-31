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

import static com.example.mislplayer.PlayerActivity.DEFAULT_LOG_DIRECTORY;
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
        File directory = DEFAULT_LOG_DIRECTORY;
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

    @Override
    public int windowSize(int window) {
        return min(window, samples.size());
    }

    @Override
    public List<Double> throughputSamples(int window) {
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
