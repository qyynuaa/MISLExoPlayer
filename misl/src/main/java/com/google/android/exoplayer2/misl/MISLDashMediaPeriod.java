package com.google.android.exoplayer2.misl;

import android.util.Pair;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.CompositeSequenceableLoader;
import com.google.android.exoplayer2.source.EmptySampleStream;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.chunk.DefaultChunkSampleStream;
import com.google.android.exoplayer2.source.dash.DashChunkSource;
import com.google.android.exoplayer2.source.dash.DashMediaPeriod;
import com.google.android.exoplayer2.source.dash.DefaultDashMediaPeriod;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.source.dash.manifest.SchemeValuePair;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.util.MimeTypes;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * An altered {@link DefaultDashMediaPeriod} which provides extra
 * information to a TrackSelection to be used by adaptation algorithms.
 */

public class MISLDashMediaPeriod implements DashMediaPeriod {

    /* package */ final int id;
    private final DashChunkSource.Factory chunkSourceFactory;
    private final int minLoadableRetryCount;
    private final AdaptiveMediaSourceEventListener.EventDispatcher eventDispatcher;
    private final long elapsedRealtimeOffset;
    private final LoaderErrorThrower manifestLoaderErrorThrower;
    private final Allocator allocator;
    private final TrackGroupArray trackGroups;
    private final EmbeddedTrackInfo[] embeddedTrackInfos;

    private Callback callback;
    private ChunkSampleStream<DashChunkSource>[] sampleStreams;
    private CompositeSequenceableLoader sequenceableLoader;
    private DashManifest manifest;
    private int periodIndex;
    private List<AdaptationSet> adaptationSets;
    private AlgorithmTrackSelection algorithmTrackSelection;

    public MISLDashMediaPeriod(int id, DashManifest manifest, int periodIndex,
                               DashChunkSource.Factory chunkSourceFactory, int minLoadableRetryCount,
                               AdaptiveMediaSourceEventListener.EventDispatcher eventDispatcher, long elapsedRealtimeOffset,
                               LoaderErrorThrower manifestLoaderErrorThrower, Allocator allocator,
                               AlgorithmTrackSelection algorithmTrackSelection) {
        this.id = id;
        this.manifest = manifest;
        this.periodIndex = periodIndex;
        this.chunkSourceFactory = chunkSourceFactory;
        this.minLoadableRetryCount = minLoadableRetryCount;
        this.eventDispatcher = eventDispatcher;
        this.elapsedRealtimeOffset = elapsedRealtimeOffset;
        this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
        this.allocator = allocator;
        this.algorithmTrackSelection = algorithmTrackSelection;
        sampleStreams = newSampleStreamArray(0);
        sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
        adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        Pair<TrackGroupArray, EmbeddedTrackInfo[]> result = buildTrackGroups(adaptationSets);
        trackGroups = result.first;
        embeddedTrackInfos = result.second;
    }

    @Override
    public int getID() {
        return id;
    }

    @Override
    public void updateManifest(DashManifest manifest, int periodIndex) {
        this.manifest = manifest;
        this.periodIndex = periodIndex;
        adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
        if (sampleStreams != null) {
            for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
                sampleStream.getChunkSource().updateManifest(manifest, periodIndex);
            }
            callback.onContinueLoadingRequested(this);
        }
    }

    @Override
    public void release() {
        for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            sampleStream.release();
        }
    }

    @Override
    public void prepare(Callback callback) {
        this.callback = callback;
        callback.onPrepared(this);
    }

    @Override
    public void maybeThrowPrepareError() throws IOException {
        manifestLoaderErrorThrower.maybeThrowError();
    }

    @Override
    public TrackGroupArray getTrackGroups() {
        return trackGroups;
    }

    @Override
    public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
                             SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
        int adaptationSetCount = adaptationSets.size();
        HashMap<Integer, ChunkSampleStream<DashChunkSource>> primarySampleStreams = new HashMap<>();
        // First pass for primary tracks.
        for (int i = 0; i < selections.length; i++) {
            if (streams[i] instanceof DefaultChunkSampleStream) {
                @SuppressWarnings("unchecked")
                ChunkSampleStream<DashChunkSource> stream = (ChunkSampleStream<DashChunkSource>) streams[i];
                if (selections[i] == null || !mayRetainStreamFlags[i]) {
                    stream.release();
                    streams[i] = null;
                } else {
                    int adaptationSetIndex = trackGroups.indexOf(selections[i].getTrackGroup());
                    primarySampleStreams.put(adaptationSetIndex, stream);
                }
            }
            if (streams[i] == null && selections[i] != null) {
                int trackGroupIndex = trackGroups.indexOf(selections[i].getTrackGroup());
                if (trackGroupIndex < adaptationSetCount) {
                    ChunkSampleStream<DashChunkSource> stream = buildSampleStream(trackGroupIndex,
                            selections[i], positionUs);
                    primarySampleStreams.put(trackGroupIndex, stream);
                    streams[i] = stream;
                    streamResetFlags[i] = true;
                }
            }
        }
        // Second pass for embedded tracks.
        for (int i = 0; i < selections.length; i++) {
            if ((streams[i] instanceof DefaultChunkSampleStream.EmbeddedSampleStream || streams[i] instanceof EmptySampleStream)
                    && (selections[i] == null || !mayRetainStreamFlags[i])) {
                // The stream is for an embedded track and is either no longer selected or needs replacing.
                releaseIfEmbeddedSampleStream(streams[i]);
                streams[i] = null;
            }
            // We need to consider replacing the stream even if it's non-null because the primary stream
            // may have been replaced, selected or deselected.
            if (selections[i] != null) {
                int trackGroupIndex = trackGroups.indexOf(selections[i].getTrackGroup());
                if (trackGroupIndex >= adaptationSetCount) {
                    int embeddedTrackIndex = trackGroupIndex - adaptationSetCount;
                    EmbeddedTrackInfo embeddedTrackInfo = embeddedTrackInfos[embeddedTrackIndex];
                    int adaptationSetIndex = embeddedTrackInfo.adaptationSetIndex;
                    ChunkSampleStream<?> primaryStream = primarySampleStreams.get(adaptationSetIndex);
                    SampleStream stream = streams[i];
                    boolean mayRetainStream = primaryStream == null ? stream instanceof EmptySampleStream
                            : (stream instanceof DefaultChunkSampleStream.EmbeddedSampleStream
                            && ((DefaultChunkSampleStream.EmbeddedSampleStream) stream).parent == primaryStream);
                    if (!mayRetainStream) {
                        releaseIfEmbeddedSampleStream(stream);
                        streams[i] = primaryStream == null ? new EmptySampleStream()
                                : primaryStream.selectEmbeddedTrack(positionUs, embeddedTrackInfo.trackType);
                        streamResetFlags[i] = true;
                    }
                }
            }
        }
        sampleStreams = newSampleStreamArray(primarySampleStreams.size());
        primarySampleStreams.values().toArray(sampleStreams);
        sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
        return positionUs;
    }

    @Override
    public void discardBuffer(long positionUs) {
        for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            sampleStream.discardUnselectedEmbeddedTracksTo(positionUs);
        }
    }

    @Override
    public boolean continueLoading(long positionUs) {
        return sequenceableLoader.continueLoading(positionUs);
    }

    @Override
    public long getNextLoadPositionUs() {
        return sequenceableLoader.getNextLoadPositionUs();
    }

    @Override
    public long readDiscontinuity() {
        return C.TIME_UNSET;
    }

    @Override
    public long getBufferedPositionUs() {
        long bufferedPositionUs = Long.MAX_VALUE;
        for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            long rendererBufferedPositionUs = sampleStream.getBufferedPositionUs();
            if (rendererBufferedPositionUs != C.TIME_END_OF_SOURCE) {
                bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
            }
        }
        return bufferedPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : bufferedPositionUs;
    }

    @Override
    public long seekToUs(long positionUs) {
        for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
            sampleStream.seekToUs(positionUs);
        }
        return positionUs;
    }

    // SequenceableLoader.Callback implementation.

    @Override
    public void onContinueLoadingRequested(ChunkSampleStream<DashChunkSource> sampleStream) {
        callback.onContinueLoadingRequested(this);
    }

    // Internal methods.

    private static Pair<TrackGroupArray, EmbeddedTrackInfo[]> buildTrackGroups(
            List<AdaptationSet> adaptationSets) {
        int adaptationSetCount = adaptationSets.size();
        int embeddedTrackCount = getEmbeddedTrackCount(adaptationSets);
        TrackGroup[] trackGroupArray = new TrackGroup[adaptationSetCount + embeddedTrackCount];
        EmbeddedTrackInfo[] embeddedTrackInfos = new EmbeddedTrackInfo[embeddedTrackCount];

        int embeddedTrackIndex = 0;
        for (int i = 0; i < adaptationSetCount; i++) {
            AdaptationSet adaptationSet = adaptationSets.get(i);
            List<Representation> representations = adaptationSet.representations;
            Format[] formats = new Format[representations.size()];
            for (int j = 0; j < formats.length; j++) {
                formats[j] = representations.get(j).format;
            }
            trackGroupArray[i] = new TrackGroup(formats);
            if (hasEventMessageTrack(adaptationSet)) {
                Format format = Format.createSampleFormat(adaptationSet.id + ":emsg",
                        MimeTypes.APPLICATION_EMSG, null, Format.NO_VALUE, null);
                trackGroupArray[adaptationSetCount + embeddedTrackIndex] = new TrackGroup(format);
                embeddedTrackInfos[embeddedTrackIndex++] = new EmbeddedTrackInfo(i, C.TRACK_TYPE_METADATA);
            }
            if (hasCea608Track(adaptationSet)) {
                Format format = Format.createTextSampleFormat(adaptationSet.id + ":cea608",
                        MimeTypes.APPLICATION_CEA608, null, Format.NO_VALUE, 0, null, null);
                trackGroupArray[adaptationSetCount + embeddedTrackIndex] = new TrackGroup(format);
                embeddedTrackInfos[embeddedTrackIndex++] = new EmbeddedTrackInfo(i, C.TRACK_TYPE_TEXT);
            }
        }

        return Pair.create(new TrackGroupArray(trackGroupArray), embeddedTrackInfos);
    }

    private ChunkSampleStream<DashChunkSource> buildSampleStream(int adaptationSetIndex,
                                                                 TrackSelection selection, long positionUs) {
        AdaptationSet adaptationSet = adaptationSets.get(adaptationSetIndex);
        int embeddedTrackCount = 0;
        int[] embeddedTrackTypes = new int[2];
        boolean enableEventMessageTrack = hasEventMessageTrack(adaptationSet);
        if (enableEventMessageTrack) {
            embeddedTrackTypes[embeddedTrackCount++] = C.TRACK_TYPE_METADATA;
        }
        boolean enableCea608Track = hasCea608Track(adaptationSet);
        if (enableCea608Track) {
            embeddedTrackTypes[embeddedTrackCount++] = C.TRACK_TYPE_TEXT;
        }
        if (embeddedTrackCount < embeddedTrackTypes.length) {
            embeddedTrackTypes = Arrays.copyOf(embeddedTrackTypes, embeddedTrackCount);
        }
        DashChunkSource chunkSource = chunkSourceFactory.createDashChunkSource(
                manifestLoaderErrorThrower, manifest, periodIndex, adaptationSetIndex, selection,
                elapsedRealtimeOffset, enableEventMessageTrack, enableCea608Track);
        ChunkSampleStream<DashChunkSource> stream = new MISLChunkSampleStream<>(adaptationSet.type,
                embeddedTrackTypes, chunkSource, this, allocator, positionUs, minLoadableRetryCount,
                eventDispatcher, algorithmTrackSelection);
        return stream;
    }

    private static int getEmbeddedTrackCount(List<AdaptationSet> adaptationSets) {
        int embeddedTrackCount = 0;
        for (int i = 0; i < adaptationSets.size(); i++) {
            AdaptationSet adaptationSet = adaptationSets.get(i);
            if (hasEventMessageTrack(adaptationSet)) {
                embeddedTrackCount++;
            }
            if (hasCea608Track(adaptationSet)) {
                embeddedTrackCount++;
            }
        }
        return embeddedTrackCount;
    }

    private static boolean hasEventMessageTrack(AdaptationSet adaptationSet) {
        List<Representation> representations = adaptationSet.representations;
        for (int i = 0; i < representations.size(); i++) {
            Representation representation = representations.get(i);
            if (!representation.inbandEventStreams.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCea608Track(AdaptationSet adaptationSet) {
        List<SchemeValuePair> descriptors = adaptationSet.accessibilityDescriptors;
        for (int i = 0; i < descriptors.size(); i++) {
            SchemeValuePair descriptor = descriptors.get(i);
            if ("urn:scte:dash:cc:cea-608:2015".equals(descriptor.schemeIdUri)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static ChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
        return new ChunkSampleStream[length];
    }

    private static void releaseIfEmbeddedSampleStream(SampleStream sampleStream) {
        if (sampleStream instanceof DefaultChunkSampleStream.EmbeddedSampleStream) {
            ((DefaultChunkSampleStream.EmbeddedSampleStream) sampleStream).release();
        }
    }
}

