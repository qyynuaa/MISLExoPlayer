package com.google.android.exoplayer2.source.dash;

import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A DASH {@link MediaSource}.
 */

public interface DashMediaSource extends MediaSource {
    /**
     * Manually replaces the manifest {@link Uri}.
     *
     * @param manifestUri The replacement manifest {@link Uri}.
     */
    void replaceManifestUri(Uri manifestUri);

    @Override
    void prepareSource(ExoPlayer player, boolean isTopLevelSource, Listener listener);

    @Override
    void maybeThrowSourceInfoRefreshError() throws IOException;

    @Override
    MediaPeriod createPeriod(int periodIndex, Allocator allocator, long positionUs);

    @Override
    void releasePeriod(MediaPeriod mediaPeriod);

    @Override
    void releaseSource();

    public static final class PeriodSeekInfo {

      public static PeriodSeekInfo createPeriodSeekInfo(
          com.google.android.exoplayer2.source.dash.manifest.Period period, long durationUs) {
        int adaptationSetCount = period.adaptationSets.size();
        long availableStartTimeUs = 0;
        long availableEndTimeUs = Long.MAX_VALUE;
        boolean isIndexExplicit = false;
        boolean seenEmptyIndex = false;
        for (int i = 0; i < adaptationSetCount; i++) {
          DashSegmentIndex index = period.adaptationSets.get(i).representations.get(0).getIndex();
          if (index == null) {
            return new PeriodSeekInfo(true, 0, durationUs);
          }
          isIndexExplicit |= index.isExplicit();
          int segmentCount = index.getSegmentCount(durationUs);
          if (segmentCount == 0) {
            seenEmptyIndex = true;
            availableStartTimeUs = 0;
            availableEndTimeUs = 0;
          } else if (!seenEmptyIndex) {
            int firstSegmentNum = index.getFirstSegmentNum();
            long adaptationSetAvailableStartTimeUs = index.getTimeUs(firstSegmentNum);
            availableStartTimeUs = Math.max(availableStartTimeUs, adaptationSetAvailableStartTimeUs);
            if (segmentCount != DashSegmentIndex.INDEX_UNBOUNDED) {
              int lastSegmentNum = firstSegmentNum + segmentCount - 1;
              long adaptationSetAvailableEndTimeUs = index.getTimeUs(lastSegmentNum)
                  + index.getDurationUs(lastSegmentNum, durationUs);
              availableEndTimeUs = Math.min(availableEndTimeUs, adaptationSetAvailableEndTimeUs);
            }
          }
        }
        return new PeriodSeekInfo(isIndexExplicit, availableStartTimeUs, availableEndTimeUs);
      }

      public final boolean isIndexExplicit;
      public final long availableStartTimeUs;
      public final long availableEndTimeUs;

      private PeriodSeekInfo(boolean isIndexExplicit, long availableStartTimeUs,
          long availableEndTimeUs) {
        this.isIndexExplicit = isIndexExplicit;
        this.availableStartTimeUs = availableStartTimeUs;
        this.availableEndTimeUs = availableEndTimeUs;
      }

    }

    public static final class DashTimeline extends Timeline {

      private final long presentationStartTimeMs;
      private final long windowStartTimeMs;

      private final int firstPeriodId;
      private final long offsetInFirstPeriodUs;
      private final long windowDurationUs;
      private final long windowDefaultStartPositionUs;
      private final DashManifest manifest;

      public DashTimeline(long presentationStartTimeMs, long windowStartTimeMs,
          int firstPeriodId, long offsetInFirstPeriodUs, long windowDurationUs,
          long windowDefaultStartPositionUs, DashManifest manifest) {
        this.presentationStartTimeMs = presentationStartTimeMs;
        this.windowStartTimeMs = windowStartTimeMs;
        this.firstPeriodId = firstPeriodId;
        this.offsetInFirstPeriodUs = offsetInFirstPeriodUs;
        this.windowDurationUs = windowDurationUs;
        this.windowDefaultStartPositionUs = windowDefaultStartPositionUs;
        this.manifest = manifest;
      }

      @Override
      public int getPeriodCount() {
        return manifest.getPeriodCount();
      }

      @Override
      public Period getPeriod(int periodIndex, Period period, boolean setIdentifiers) {
        Assertions.checkIndex(periodIndex, 0, manifest.getPeriodCount());
        Object id = setIdentifiers ? manifest.getPeriod(periodIndex).id : null;
        Object uid = setIdentifiers ? firstPeriodId
            + Assertions.checkIndex(periodIndex, 0, manifest.getPeriodCount()) : null;
        return period.set(id, uid, 0, manifest.getPeriodDurationUs(periodIndex),
            C.msToUs(manifest.getPeriod(periodIndex).startMs - manifest.getPeriod(0).startMs)
                - offsetInFirstPeriodUs, false);
      }

      @Override
      public int getWindowCount() {
        return 1;
      }

      @Override
      public Window getWindow(int windowIndex, Window window, boolean setIdentifier,
          long defaultPositionProjectionUs) {
        Assertions.checkIndex(windowIndex, 0, 1);
        long windowDefaultStartPositionUs = getAdjustedWindowDefaultStartPositionUs(
            defaultPositionProjectionUs);
        return window.set(null, presentationStartTimeMs, windowStartTimeMs, true /* isSeekable */,
            manifest.dynamic, windowDefaultStartPositionUs, windowDurationUs, 0,
            manifest.getPeriodCount() - 1, offsetInFirstPeriodUs);
      }

      @Override
      public int getIndexOfPeriod(Object uid) {
        if (!(uid instanceof Integer)) {
          return C.INDEX_UNSET;
        }
        int periodId = (int) uid;
        return periodId < firstPeriodId || periodId >= firstPeriodId + getPeriodCount()
            ? C.INDEX_UNSET : (periodId - firstPeriodId);
      }

      private long getAdjustedWindowDefaultStartPositionUs(long defaultPositionProjectionUs) {
        long windowDefaultStartPositionUs = this.windowDefaultStartPositionUs;
        if (!manifest.dynamic) {
          return windowDefaultStartPositionUs;
        }
        if (defaultPositionProjectionUs > 0) {
          windowDefaultStartPositionUs += defaultPositionProjectionUs;
          if (windowDefaultStartPositionUs > windowDurationUs) {
            // The projection takes us beyond the end of the live window.
            return C.TIME_UNSET;
          }
        }
        // Attempt to snap to the start of the corresponding video segment.
        int periodIndex = 0;
        long defaultStartPositionInPeriodUs = offsetInFirstPeriodUs + windowDefaultStartPositionUs;
        long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
        while (periodIndex < manifest.getPeriodCount() - 1
            && defaultStartPositionInPeriodUs >= periodDurationUs) {
          defaultStartPositionInPeriodUs -= periodDurationUs;
          periodIndex++;
          periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
        }
        com.google.android.exoplayer2.source.dash.manifest.Period period =
            manifest.getPeriod(periodIndex);
        int videoAdaptationSetIndex = period.getAdaptationSetIndex(C.TRACK_TYPE_VIDEO);
        if (videoAdaptationSetIndex == C.INDEX_UNSET) {
          // No video adaptation set for snapping.
          return windowDefaultStartPositionUs;
        }
        // If there are multiple video adaptation sets with unaligned segments, the initial time may
        // not correspond to the start of a segment in both, but this is an edge case.
        DashSegmentIndex snapIndex = period.adaptationSets.get(videoAdaptationSetIndex)
            .representations.get(0).getIndex();
        if (snapIndex == null || snapIndex.getSegmentCount(periodDurationUs) == 0) {
          // Video adaptation set does not include a non-empty index for snapping.
          return windowDefaultStartPositionUs;
        }
        int segmentNum = snapIndex.getSegmentNum(defaultStartPositionInPeriodUs, periodDurationUs);
        return windowDefaultStartPositionUs + snapIndex.getTimeUs(segmentNum)
            - defaultStartPositionInPeriodUs;
      }

    }

    public static final class XsDateTimeParser implements ParsingLoadable.Parser<Long> {

      @Override
      public Long parse(Uri uri, InputStream inputStream) throws IOException {
        String firstLine = new BufferedReader(new InputStreamReader(inputStream)).readLine();
        return Util.parseXsDateTime(firstLine);
      }

    }

    public static final class Iso8601Parser implements ParsingLoadable.Parser<Long> {

      @Override
      public Long parse(Uri uri, InputStream inputStream) throws IOException {
        String firstLine = new BufferedReader(new InputStreamReader(inputStream)).readLine();
        try {
          // TODO: It may be necessary to handle timestamp offsets from UTC.
          SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
          format.setTimeZone(TimeZone.getTimeZone("UTC"));
          return format.parse(firstLine).getTime();
        } catch (ParseException e) {
          throw new ParserException(e);
        }
      }

    }
}
