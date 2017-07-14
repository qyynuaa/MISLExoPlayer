package com.google.android.exoplayer2.source.dash;

import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.trackselection.TrackSelection;

import java.io.IOException;

/**
 * A DASH {@link MediaPeriod}.
 */
public interface DashMediaPeriod extends MediaPeriod, SequenceableLoader.Callback<ChunkSampleStream<DashChunkSource>> {

    int getID();

    void updateManifest(DashManifest manifest, int periodIndex);

    void release();

    @Override
    void prepare(Callback callback);

    @Override
    void maybeThrowPrepareError() throws IOException;

    @Override
    TrackGroupArray getTrackGroups();

    @Override
    long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
                      SampleStream[] streams, boolean[] streamResetFlags, long positionUs);

    @Override
    void discardBuffer(long positionUs);

    @Override
    boolean continueLoading(long positionUs);

    @Override
    long getNextLoadPositionUs();

    @Override
    long readDiscontinuity();

    @Override
    long getBufferedPositionUs();

    @Override
    long seekToUs(long positionUs);

    @Override
    void onContinueLoadingRequested(ChunkSampleStream<DashChunkSource> sampleStream);

    public static final class EmbeddedTrackInfo {

      public final int adaptationSetIndex;
      public final int trackType;

      public EmbeddedTrackInfo(int adaptationSetIndex, int trackType) {
        this.adaptationSetIndex = adaptationSetIndex;
        this.trackType = trackType;
      }

    }
}
