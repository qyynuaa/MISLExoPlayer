package com.google.android.exoplayer2.misl;

import android.os.SystemClock;

import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.trackselection.TrackSelection;

/**
 * Uses video adaptation algorithms to make track selection decisions.
 */

interface AlgorithmTrackSelection extends TrackSelection {
    /**
     * Gives the TrackSelection the last chunk that was downloaded, so decisions can be made
     * based on it.
     * @param lastChunk The last chunk that was downloaded.
     */
    void giveLastChunk(MediaChunk lastChunk);

    /**
     * Gives the TrackSelection information about the last chunk that was downloaded.
     *
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the load ended.
     * @param loadDurationMs The duration of the load.
     */
    void giveLastChunkData(long elapsedRealtimeMs, long loadDurationMs);
}
