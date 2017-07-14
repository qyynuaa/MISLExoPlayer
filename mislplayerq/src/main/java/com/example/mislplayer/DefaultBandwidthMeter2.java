package com.example.mislplayer;

import android.os.Handler;
import android.os.SystemClock;

import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.SlidingPercentile;

import java.util.ArrayList;

/**
 * Created by Quentin L on 29/05/2017.
 */

/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.os.Handler;
import android.os.SystemClock;

import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.SlidingPercentile;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.SlidingPercentile;

import java.util.ArrayList;

/**
 * Estimates bandwidth by listening to data transfers. The bandwidth estimate is calculated using
 * a {@link SlidingPercentile} and is updated each time a transfer ends.
 */
public final class DefaultBandwidthMeter2 implements BandwidthMeter, TransferListener<Object> {

    /**
     * The default maximum weight for the sliding window.
     */
    public static final int DEFAULT_MAX_WEIGHT = 2000;

    private static final int ELAPSED_MILLIS_FOR_ESTIMATE = 2000;
    private static final int BYTES_TRANSFERRED_FOR_ESTIMATE = 512 * 1024;

    private final Handler eventHandler;
    private final EventListener eventListener;
    private final SlidingPercentile slidingPercentile;
    private long [] sampleBytesCollected= new long[1000];
    private int streamCount;
    private int sampleCount=0;
    private long sampleStartTimeMs;
    private long sampleBytesTransferred;
    private ArrayList<Long> allValues = new ArrayList<>();
    private long totalElapsedTimeMs;
    private long totalBytesTransferred;
    private long totalBytesTransferred2;
    private long bitrateEstimate;
    private int r=0;

    public DefaultBandwidthMeter2() {
        this(null, null);
    }

    public DefaultBandwidthMeter2(Handler eventHandler, EventListener eventListener) {
        this(eventHandler, eventListener, DEFAULT_MAX_WEIGHT);
    }

    public DefaultBandwidthMeter2(Handler eventHandler, EventListener eventListener, int maxWeight) {
        this.eventHandler = eventHandler;
        this.eventListener = eventListener;
        this.slidingPercentile = new SlidingPercentile(maxWeight);
        bitrateEstimate = NO_ESTIMATE;
    }

    @Override
    public synchronized long getBitrateEstimate() {
        return bitrateEstimate;
    }

    @Override
    public synchronized void onTransferStart(Object source, DataSpec dataSpec) {
        if (streamCount == 0) {
            sampleStartTimeMs = SystemClock.elapsedRealtime();
        }
        streamCount++;
    }

    @Override
    public synchronized void onBytesTransferred(Object source, int bytes) {
        sampleBytesTransferred += bytes;
    }


    @Override
    public synchronized void onTransferEnd(Object source) {
        Assertions.checkState(streamCount > 0); // Check that stream Count is bigger than 0, means that onTransferStart has been called at least one time
        long nowMs = SystemClock.elapsedRealtime(); // Capture current elapsed time
        int sampleElapsedTimeMs = (int) (nowMs - sampleStartTimeMs);
        totalElapsedTimeMs += sampleElapsedTimeMs;
        totalBytesTransferred += sampleBytesTransferred;
        if (sampleElapsedTimeMs > 0) {
            float bitsPerSecond = (sampleBytesTransferred * 8000) / sampleElapsedTimeMs;
            slidingPercentile.addSample((int) Math.sqrt(sampleBytesTransferred), bitsPerSecond);
            if (totalElapsedTimeMs >= ELAPSED_MILLIS_FOR_ESTIMATE
                    || totalBytesTransferred >= BYTES_TRANSFERRED_FOR_ESTIMATE) {
                float bitrateEstimateFloat = slidingPercentile.getPercentile(0.5f);
                bitrateEstimate = Float.isNaN(bitrateEstimateFloat) ? NO_ESTIMATE
                        : (long) bitrateEstimateFloat;

            }
        }
        notifyBandwidthSample(sampleElapsedTimeMs, sampleBytesTransferred, bitrateEstimate);
        if (--streamCount > 0) {
            sampleStartTimeMs = nowMs;
        }
        sampleBytesCollected[r]=sampleBytesTransferred;
        r++;
        if(sampleBytesTransferred>1000) {
            totalBytesTransferred2+=sampleBytesTransferred;
            sampleCount++;
            allValues.add(sampleBytesTransferred);
        }
        sampleBytesTransferred = 0;
    }

    public double calculateMean(){
        double mean;
        mean=totalBytesTransferred2/sampleCount;
        return mean;
    }

    public double calculateVariance(){
        double temp=0;
        double variance;
        for(int i=0;i<allValues.size();i++) {
            temp = temp + (allValues.get(i)) * (allValues.get(i));
        }
        variance= (temp/(double)allValues.size())- (calculateMean()*calculateMean());
        return variance;
    }

    public long[] getSampleBytesCollected(){
        return sampleBytesCollected;
    }

    private void notifyBandwidthSample(final int elapsedMs, final long bytes, final long bitrate) {
        if (eventHandler != null && eventListener != null) {
            eventHandler.post(new Runnable()  {
                @Override
                public void run() {
                    eventListener.onBandwidthSample(elapsedMs, bytes, bitrate);
                }
            });
        }
    }








}
