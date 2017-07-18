package com.google.android.exoplayer2.misl;

import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.chunk.ChunkSource;
import com.google.android.exoplayer2.source.chunk.DefaultChunkSampleStream;
import com.google.android.exoplayer2.source.chunk.DefaultChunkSampleStream.EmbeddedSampleStream;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.Loadable;

import java.io.IOException;

/**
 * A replacement for {@link DefaultChunkSampleStream},
 * that will pass extra information to an {@link AlgorithmTrackSelection}.
 */

public class MISLChunkSampleStream<T extends ChunkSource> extends DefaultChunkSampleStream {

    private final String TAG = "MISLChunkSampleStream";

    private AlgorithmTrackSelection algorithmTrackSelection;

    public MISLChunkSampleStream(int primaryTrackType, int[] embeddedTrackTypes, T chunkSource,
                                 Callback<ChunkSampleStream<T>> callback, Allocator allocator, long positionUs, int minLoadableRetryCount,
                                 AdaptiveMediaSourceEventListener.EventDispatcher eventDispatcher,
                                 AlgorithmTrackSelection algorithmTrackSelection) {
        super(primaryTrackType, embeddedTrackTypes, chunkSource, callback, allocator,
                positionUs, minLoadableRetryCount, eventDispatcher);
        this.algorithmTrackSelection = algorithmTrackSelection;
    }

    /**
     * Called when a load has completed.
     * <p>
     * Note: There is guaranteed to be a memory barrier between {@link Loadable#load()} exiting and
     * this callback being called.
     *
     * @param loadable          The loadable whose load has completed.
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the load ended.
     * @param loadDurationMs    The duration of the load.
     */
    @Override
    public void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs) {
        algorithmTrackSelection.giveLastChunkData(elapsedRealtimeMs, loadDurationMs);
        Log.d(TAG, "Last chunk data given");
        super.onLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }
}
