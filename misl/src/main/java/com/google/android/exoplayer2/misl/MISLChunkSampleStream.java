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

public class MISLChunkSampleStream<T extends ChunkSource> implements ChunkSampleStream<T> {

    private final String TAG = "MISLChunkSampleStream";

    private ChunkSampleStream<T> chunkSampleStream;
    private AlgorithmTrackSelection algorithmTrackSelection;

    public MISLChunkSampleStream(int primaryTrackType, int[] embeddedTrackTypes, T chunkSource,
                                 Callback<ChunkSampleStream<T>> callback, Allocator allocator, long positionUs, int minLoadableRetryCount,
                                 AdaptiveMediaSourceEventListener.EventDispatcher eventDispatcher,
                                 AlgorithmTrackSelection algorithmTrackSelection) {
        chunkSampleStream = new DefaultChunkSampleStream<T>(primaryTrackType, embeddedTrackTypes, chunkSource, callback, allocator,
                positionUs, minLoadableRetryCount, eventDispatcher);
        this.algorithmTrackSelection = algorithmTrackSelection;
    }

    /**
     * Discards buffered media for embedded tracks that are not currently selected, up to the
     * specified position.
     *
     * @param positionUs The position to discard up to, in microseconds.
     */
    @Override
    public void discardUnselectedEmbeddedTracksTo(long positionUs) {
        chunkSampleStream.discardUnselectedEmbeddedTracksTo(positionUs);
    }

    /**
     * Selects the embedded track, returning a new {@link EmbeddedSampleStream} from which the track's
     * samples can be consumed. {@link EmbeddedSampleStream#release()} must be called on the returned
     * stream when the track is no longer required, and before calling this method again to obtain
     * another stream for the same track.
     *
     * @param positionUs The current playback position in microseconds.
     * @param trackType  The type of the embedded track to enable.
     * @return The {@link EmbeddedSampleStream} for the embedded track.
     */
    @Override
    public DefaultChunkSampleStream.EmbeddedSampleStream selectEmbeddedTrack(long positionUs, int trackType) {
        return chunkSampleStream.selectEmbeddedTrack(positionUs, trackType);
    }

    /**
     * Returns the {@link ChunkSource} used by this stream.
     */
    @Override
    public T getChunkSource() {
        return chunkSampleStream.getChunkSource();
    }

    /**
     * Returns an estimate of the position up to which data is buffered.
     *
     * @return An estimate of the absolute position in microseconds up to which data is buffered, or
     * {@link C#TIME_END_OF_SOURCE} if the track is fully buffered.
     */
    @Override
    public long getBufferedPositionUs() {
        return chunkSampleStream.getBufferedPositionUs();
    }

    /**
     * Seeks to the specified position in microseconds.
     *
     * @param positionUs The seek position in microseconds.
     */
    @Override
    public void seekToUs(long positionUs) {
        chunkSampleStream.seekToUs(positionUs);
    }

    /**
     * Releases the stream.
     * <p>
     * This method should be called when the stream is no longer required.
     */
    @Override
    public void release() {
        chunkSampleStream.release();
    }

    /**
     * Returns whether data is available to be read.
     * <p>
     * Note: If the stream has ended then a buffer with the end of stream flag can always be read from
     * {@link #readData(FormatHolder, DecoderInputBuffer, boolean)}. Hence an ended stream is always
     * ready.
     *
     * @return Whether data is available to be read.
     */
    @Override
    public boolean isReady() {
        return chunkSampleStream.isReady();
    }

    /**
     * Throws an error that's preventing data from being read. Does nothing if no such error exists.
     *
     * @throws IOException The underlying error.
     */
    @Override
    public void maybeThrowError() throws IOException {
        chunkSampleStream.maybeThrowError();
    }

    /**
     * Attempts to read from the stream.
     * <p>
     * If the stream has ended then {@link C#BUFFER_FLAG_END_OF_STREAM} flag is set on {@code buffer}
     * and {@link C#RESULT_BUFFER_READ} is returned. Else if no data is available then
     * {@link C#RESULT_NOTHING_READ} is returned. Else if the format of the media is changing or if
     * {@code formatRequired} is set then {@code formatHolder} is populated and
     * {@link C#RESULT_FORMAT_READ} is returned. Else {@code buffer} is populated and
     * {@link C#RESULT_BUFFER_READ} is returned.
     *
     * @param formatHolder   A {@link FormatHolder} to populate in the case of reading a format.
     * @param buffer         A {@link DecoderInputBuffer} to populate in the case of reading a sample or the
     *                       end of the stream. If the end of the stream has been reached, the
     *                       {@link C#BUFFER_FLAG_END_OF_STREAM} flag will be set on the buffer.
     * @param formatRequired Whether the caller requires that the format of the stream be read even if
     *                       it's not changing. A sample will never be read if set to true, however it is still possible
     *                       for the end of stream or nothing to be read.
     * @return The result, which can be {@link C#RESULT_NOTHING_READ}, {@link C#RESULT_FORMAT_READ} or
     * {@link C#RESULT_BUFFER_READ}.
     */
    @Override
    public int readData(FormatHolder formatHolder, DecoderInputBuffer buffer, boolean formatRequired) {
        return chunkSampleStream.readData(formatHolder, buffer, formatRequired);
    }

    /**
     * Attempts to skip to the keyframe before the specified position, or to the end of the stream if
     * {@code positionUs} is beyond it.
     *
     * @param positionUs The specified time.
     */
    @Override
    public void skipData(long positionUs) {
        chunkSampleStream.skipData(positionUs);
    }

    /**
     * Returns the next load time, or {@link C#TIME_END_OF_SOURCE} if loading has finished.
     */
    @Override
    public long getNextLoadPositionUs() {
        return chunkSampleStream.getNextLoadPositionUs();
    }

    /**
     * Attempts to continue loading.
     *
     * @param positionUs The current playback position.
     * @return True if progress was made, meaning that {@link #getNextLoadPositionUs()} will return
     * a different value than prior to the call. False otherwise.
     */
    @Override
    public boolean continueLoading(long positionUs) {
        return chunkSampleStream.continueLoading(positionUs);
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
        chunkSampleStream.onLoadCompleted(loadable, elapsedRealtimeMs, loadDurationMs);
    }

    /**
     * Called when a load has been canceled.
     * <p>
     * Note: If the {@link Loader} has not been released then there is guaranteed to be a memory
     * barrier between {@link Loadable#load()} exiting and this callback being called. If the
     * {@link Loader} has been released then this callback may be called before
     * {@link Loadable#load()} exits.
     *
     * @param loadable          The loadable whose load has been canceled.
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the load was canceled.
     * @param loadDurationMs    The duration of the load up to the point at which it was canceled.
     * @param released          True if the load was canceled because the {@link Loader} was released. False
     */
    @Override
    public void onLoadCanceled(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs, boolean released) {
        chunkSampleStream.onLoadCanceled(loadable, elapsedRealtimeMs, loadDurationMs, released);
    }

    /**
     * Called when a load encounters an error.
     * <p>
     * Note: There is guaranteed to be a memory barrier between {@link Loadable#load()} exiting and
     * this callback being called.
     *
     * @param loadable          The loadable whose load has encountered an error.
     * @param elapsedRealtimeMs {@link SystemClock#elapsedRealtime} when the error occurred.
     * @param loadDurationMs    The duration of the load up to the point at which the error occurred.
     * @param error             The load error.
     * @return The desired retry action. One of {@link Loader#RETRY},
     * {@link Loader#RETRY_RESET_ERROR_COUNT}, {@link Loader#DONT_RETRY} and
     * {@link Loader#DONT_RETRY_FATAL}.
     */
    @Override
    public int onLoadError(Chunk loadable, long elapsedRealtimeMs, long loadDurationMs, IOException error) {
        return chunkSampleStream.onLoadError(loadable, elapsedRealtimeMs, loadDurationMs, error);
    }
}
