package com.google.android.exoplayer2.source.chunk;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.upstream.Loader;

import java.io.IOException;

/**
 * A {@link SampleStream} that loads media in {@link Chunk}s, obtained from a {@link ChunkSource}.
 * May also be configured to expose additional embedded {@link SampleStream}s.
 */

public interface ChunkSampleStream<T extends ChunkSource> extends SampleStream, SequenceableLoader, Loader.Callback<Chunk> {
    /**
     * Discards buffered media for embedded tracks that are not currently selected, up to the
     * specified position.
     *
     * @param positionUs The position to discard up to, in microseconds.
     */
    void discardUnselectedEmbeddedTracksTo(long positionUs);

    /**
     * Selects the embedded track, returning a new {@link DefaultChunkSampleStream.EmbeddedSampleStream} from which the track's
     * samples can be consumed. {@link DefaultChunkSampleStream.EmbeddedSampleStream#release()} must be called on the returned
     * stream when the track is no longer required, and before calling this method again to obtain
     * another stream for the same track.
     *
     * @param positionUs The current playback position in microseconds.
     * @param trackType The type of the embedded track to enable.
     * @return The {@link DefaultChunkSampleStream.EmbeddedSampleStream} for the embedded track.
     */
    DefaultChunkSampleStream.EmbeddedSampleStream selectEmbeddedTrack(long positionUs, int trackType);

    /**
     * Returns the {@link ChunkSource} used by this stream.
     */
    T getChunkSource();

    /**
     * Returns an estimate of the position up to which data is buffered.
     *
     * @return An estimate of the absolute position in microseconds up to which data is buffered, or
     *     {@link C#TIME_END_OF_SOURCE} if the track is fully buffered.
     */
    long getBufferedPositionUs();

    /**
     * Seeks to the specified position in microseconds.
     *
     * @param positionUs The seek position in microseconds.
     */
    void seekToUs(long positionUs);

    /**
     * Releases the stream.
     * <p>
     * This method should be called when the stream is no longer required.
     */
    void release();

    @Override
    boolean isReady();

    @Override
    void maybeThrowError() throws IOException;

    @Override
    int readData(FormatHolder formatHolder, DecoderInputBuffer buffer,
                 boolean formatRequired);

    @Override
    void skipData(long positionUs);

    @Override
    void onLoadCompleted(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs);

    @Override
    void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
                        boolean released);

    @Override
    int onLoadError(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs,
                    IOException error);

    @Override
    boolean continueLoading(long positionUs);

    @Override
    long getNextLoadPositionUs();
}
