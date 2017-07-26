package com.example.mislplayer;

import android.util.Log;

import com.google.android.exoplayer2.upstream.BandwidthMeter;

/**
 * Created by Quentin L on 01/06/2017.
 */

public class BandwidthMeterEventListener implements BandwidthMeter.EventListener {

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrate){

    }
}