package com.example.mislplayer;

import android.util.Log;

import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
import com.google.android.exoplayer2.source.chunk.DefaultChunkSampleStream;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.dash.DashChunkSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;

import java.io.IOException;
import java.util.List;

/**
 * A DashChunkSource which passes chunk information to a {@link ChunkListener} to assist in
 * adaptive track selection.}.
 */

public class MISLDashChunkSource implements DashChunkSource {

    public static final class Factory implements DashChunkSource.Factory {

        private static final int DEFAULT_MAX_SEGMENTS_PER_LOAD = 1;

        private final DataSource.Factory dataSourceFactory;
        private final int maxSegmentsPerLoad;
        private final ChunkListener chunkListener;

        // fields for transitional period
        private long mpdDuration;

        public Factory(DataSource.Factory dataSourceFactory, ChunkListener chunkListener) {
            this(dataSourceFactory, DEFAULT_MAX_SEGMENTS_PER_LOAD, chunkListener);
        }

        public Factory(DataSource.Factory dataSourceFactory, int maxSegmentsPerLoad, ChunkListener chunkListener) {
            this.dataSourceFactory = dataSourceFactory;
            this.maxSegmentsPerLoad = maxSegmentsPerLoad;
            this.chunkListener = chunkListener;
        }

        @Override
        public MISLDashChunkSource createDashChunkSource(LoaderErrorThrower manifestLoaderErrorThrower,
                                                     DashManifest manifest, int periodIndex, int adaptationSetIndex,
                                                     TrackSelection trackSelection, long elapsedRealtimeOffsetMs,
                                                     boolean enableEventMessageTrack, boolean enableCea608Track) {
            DataSource dataSource = dataSourceFactory.createDataSource();

            chunkListener.giveMpdDuration(manifest.duration);

            return new MISLDashChunkSource(manifestLoaderErrorThrower, manifest, periodIndex,
                    adaptationSetIndex, trackSelection, dataSource, elapsedRealtimeOffsetMs,
                    maxSegmentsPerLoad, enableEventMessageTrack, enableCea608Track, chunkListener);
        }
    }

    private final static String TAG = "MISLDashChunkSource";

    private DashChunkSource dashChunkSource;
    private ChunkListener chunkListener;

    public MISLDashChunkSource(LoaderErrorThrower manifestLoaderErrorThrower, DashManifest manifest,
                               int periodIndex, int adaptationSetIndex, TrackSelection trackSelection,
                               DataSource dataSource, long elapsedRealtimeOffsetMs, int maxSegmentsPerLoad,
                               boolean enableEventMessageTrack, boolean enableCea608Track, ChunkListener chunkListener) {
        this.dashChunkSource = new DefaultDashChunkSource(manifestLoaderErrorThrower, manifest, periodIndex,
                adaptationSetIndex, trackSelection, dataSource, elapsedRealtimeOffsetMs, maxSegmentsPerLoad,
                enableEventMessageTrack, enableCea608Track);
        this.chunkListener = chunkListener;
    }

    /** Delegates to the {@link DefaultDashChunkSource} */
    @Override
    public void updateManifest(DashManifest newManifest, int periodIndex) {
        chunkListener.giveMpdDuration(newManifest.duration);
        dashChunkSource.updateManifest(newManifest, periodIndex);
    }

    /**
     * If the source is currently having difficulty providing chunks, then this method throws the
     * underlying error. Otherwise does nothing.
     * <p>
     * This method should only be called after the source has been prepared.
     *
     * @throws IOException The underlying error.
     */
    @Override
    public void maybeThrowError() throws IOException {
        dashChunkSource.maybeThrowError();
    }

    /**
     * Evaluates whether {@link MediaChunk}s should be removed from the back of the queue.
     * <p>
     * Removing {@link MediaChunk}s from the back of the queue can be useful if they could be replaced
     * with chunks of a significantly higher quality (e.g. because the available bandwidth has
     * substantially increased).
     *
     * @param playbackPositionUs The current playback position.
     * @param queue              The queue of buffered {@link MediaChunk}s.
     * @return The preferred queue size.
     */
    @Override
    public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        return dashChunkSource.getPreferredQueueSize(playbackPositionUs, queue);
    }

    /**
     * Returns the next chunk to load.
     * <p>
     * If a chunk is available then {@link ChunkHolder#chunk} is set. If the end of the stream has
     * been reached then {@link ChunkHolder#endOfStream} is set. If a chunk is not available but the
     * end of the stream has not been reached, the {@link ChunkHolder} is not modified.
     *
     * @param previous           The most recently loaded media chunk.
     * @param playbackPositionUs The current playback position. If {@code previous} is null then this
     *                           parameter is the position from which playback is expected to start (or restart) and hence
     *                           should be interpreted as a seek position.
     * @param out                A holder to populate.
     */
    @Override
    public void getNextChunk(MediaChunk previous, long playbackPositionUs, ChunkHolder out) {
        chunkListener.giveLastChunk(previous);
        dashChunkSource.getNextChunk(previous, playbackPositionUs, out);
    }

    /**
     * Called when the {@link DefaultChunkSampleStream} has finished loading a chunk obtained from this
     * source.
     * <p>
     * This method should only be called when the source is enabled.
     *
     * @param chunk The chunk whose load has been completed.
     */
    @Override
    public void onChunkLoadCompleted(Chunk chunk) {
        dashChunkSource.onChunkLoadCompleted(chunk);
    }

    /**
     * Called when the {@link DefaultChunkSampleStream} encounters an error loading a chunk obtained from
     * this source.
     * <p>
     * This method should only be called when the source is enabled.
     *
     * @param chunk      The chunk whose load encountered the error.
     * @param cancelable Whether the load can be canceled.
     * @param e          The error.
     * @return Whether the load should be canceled.
     */
    @Override
    public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e) {
        return dashChunkSource.onChunkLoadError(chunk, cancelable, e);
    }
}
