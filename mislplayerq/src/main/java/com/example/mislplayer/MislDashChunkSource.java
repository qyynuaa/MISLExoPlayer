package com.example.mislplayer;

import com.example.mislplayer.sampling.ChunkListener;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.ChunkHolder;
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
 * A replacement for {@link DefaultDashChunkSource}.
 *
 * <p>Passes chunks to a ChunkListener for chunk-based throughput sampling,
 * and otherwise behaves identically to DefaultDashChunkSource.
 */

public class MislDashChunkSource implements DashChunkSource {

    public static class Factory implements DashChunkSource.Factory {

        private static final int DEFAULT_MAX_SEGMENTS_PER_LOAD = 1;

        private final DataSource.Factory dataSourceFactory;
        private final int maxSegmentsPerLoad;
        private final ChunkListener chunkListener;

        /**
         * Creates a MislDashChunkSource factory with default values.
         *
         * @param dataSourceFactory
         * @param chunkListener Can be given chunks for chunk-based
         *                      throughput sampling.
         */
        public Factory(DataSource.Factory dataSourceFactory,
                       ChunkListener chunkListener) {
            this(dataSourceFactory, DEFAULT_MAX_SEGMENTS_PER_LOAD,
                    chunkListener);
        }

        public Factory(DataSource.Factory dataSourceFactory,
                       int maxSegmentsPerLoad, ChunkListener chunkListener) {
            this.dataSourceFactory = dataSourceFactory;
            this.maxSegmentsPerLoad = maxSegmentsPerLoad;
            this.chunkListener = chunkListener;
        }

        @Override
        public MislDashChunkSource createDashChunkSource(LoaderErrorThrower manifestLoaderErrorThrower,
                                                     DashManifest manifest, int periodIndex, int adaptationSetIndex,
                                                     TrackSelection trackSelection, long elapsedRealtimeOffsetMs,
                                                     boolean enableEventMessageTrack, boolean enableCea608Track) {
            DataSource dataSource = dataSourceFactory.createDataSource();

            return new MislDashChunkSource(manifestLoaderErrorThrower, manifest, periodIndex,
                    adaptationSetIndex, trackSelection, dataSource, elapsedRealtimeOffsetMs,
                    maxSegmentsPerLoad, enableEventMessageTrack,
                    enableCea608Track, chunkListener);
        }
    }

    private static final String TAG = "MislDashChunkSource";

    private DashChunkSource dashChunkSource;
    private ChunkListener chunkListener;

    public MislDashChunkSource(LoaderErrorThrower manifestLoaderErrorThrower, DashManifest manifest,
                               int periodIndex, int adaptationSetIndex, TrackSelection trackSelection,
                               DataSource dataSource, long elapsedRealtimeOffsetMs, int maxSegmentsPerLoad,
                               boolean enableEventMessageTrack, boolean enableCea608Track, ChunkListener chunkListener) {
        this.dashChunkSource = new DefaultDashChunkSource(manifestLoaderErrorThrower, manifest, periodIndex,
                adaptationSetIndex, trackSelection, dataSource, elapsedRealtimeOffsetMs, maxSegmentsPerLoad,
                enableEventMessageTrack, enableCea608Track);
        this.chunkListener = chunkListener;
    }

    @Override
    public void updateManifest(DashManifest newManifest, int periodIndex) {
        dashChunkSource.updateManifest(newManifest, periodIndex);
    }

    @Override
    public void maybeThrowError() throws IOException {
        dashChunkSource.maybeThrowError();
    }

    @Override
    public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
        return dashChunkSource.getPreferredQueueSize(playbackPositionUs, queue);
    }

    @Override
    public void getNextChunk(MediaChunk previous, long playbackPositionUs, ChunkHolder out) {
        if (chunkListener != null) {
            chunkListener.giveLastChunk(previous);
        }

        dashChunkSource.getNextChunk(previous, playbackPositionUs, out);
    }

    @Override
    public void onChunkLoadCompleted(Chunk chunk) {
        dashChunkSource.onChunkLoadCompleted(chunk);
    }

    @Override
    public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e) {
        return dashChunkSource.onChunkLoadError(chunk, cancelable, e);
    }
}
