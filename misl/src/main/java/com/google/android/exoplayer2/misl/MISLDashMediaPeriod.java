package com.google.android.exoplayer2.misl;

import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.dash.DashChunkSource;
import com.google.android.exoplayer2.source.dash.DashMediaPeriod;
import com.google.android.exoplayer2.source.dash.DefaultDashMediaPeriod;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;

import java.io.IOException;

/**
 * A DASH {@link MediaPeriod}.
 */

public class MISLDashMediaPeriod implements DashMediaPeriod {

    private DashMediaPeriod dashMediaPeriod;

    public MISLDashMediaPeriod(int id, DashManifest manifest, int periodIndex,
                               DashChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
                               AdaptiveMediaSourceEventListener.EventDispatcher eventDispatcher, long elapsedRealtimeOffset,
                               LoaderErrorThrower manifestLoaderErrorThrower, Allocator allocator) {
        this.dashMediaPeriod = new DefaultDashMediaPeriod(id, manifest, periodIndex, chunkSourceFactory, minLoadableRetryCount,
                eventDispatcher, elapsedRealtimeOffset, manifestLoaderErrorThrower, allocator);
    }

    @Override
    public int getID() {
        return 0;
    }

    @Override
    public void updateManifest(DashManifest manifest, int periodIndex) {

    }

    @Override
    public void release() {

    }

    @Override
    public void prepare(Callback callback) {

    }

    @Override
    public void maybeThrowPrepareError() throws IOException {

    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return null;
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        return 0;
    }

    @Override
    public void discardBuffer(long positionUs) {

    }

    @Override
    public boolean continueLoading(long positionUs) {
        return false;
    }

    @Override
    public long getNextLoadPositionUs() {
        return 0;
    }

    @Override
    public long readDiscontinuity() {
        return 0;
    }

    @Override
    public long getBufferedPositionUs() {
        return 0;
    }

    @Override
    public long seekToUs(long positionUs) {
        return 0;
    }

    @Override
    public void onContinueLoadingRequested(ChunkSampleStream<DashChunkSource> sampleStream) {

    }
}
