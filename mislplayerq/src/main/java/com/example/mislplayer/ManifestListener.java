package com.example.mislplayer;

import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Listens to manifest data transfers and notifies components of the
 * manifest request time so they can synchronise times.
 */
public class ManifestListener implements TransferListener<Object> {

    private static final String TAG = "ManifestListener";

    private List<ManifestRequestTimeReceiver> listeners = new ArrayList<>();

    private long manifestRequestTime;

    public void addListener(ManifestRequestTimeReceiver listener) {
        listeners.add(listener);
    }

    private void notifyListeners() {
        for (ManifestRequestTimeReceiver listener : listeners) {
            listener.giveManifestRequestTime(manifestRequestTime);
        }
    }

    /**
     * Called when a transfer starts.
     *
     * @param source   The source performing the transfer.
     * @param dataSpec Describes the data being transferred.
     */
    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        if (manifestRequestTime == 0) {
            manifestRequestTime = SystemClock.elapsedRealtime();
            Log.d(TAG, String.format("Updated manifest request time to %d.", manifestRequestTime));
            notifyListeners();
        }
    }

    /**
     * Called incrementally during a transfer.
     *
     * @param source           The source performing the transfer.
     * @param bytesTransferred The number of bytes transferred since the previous call to this
     */
    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {

    }

    /**
     * Called when a transfer ends.
     *
     * @param source The source performing the transfer.
     */
    @Override
    public void onTransferEnd(Object source) {

    }

    public interface ManifestRequestTimeReceiver {
        void giveManifestRequestTime(long manifestRequestTime);
    }
}
