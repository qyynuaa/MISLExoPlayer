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

    // TransferListener implementation

    @Override
    public void onTransferStart(Object source, DataSpec dataSpec) {
        if (manifestRequestTime == 0) {
            manifestRequestTime = SystemClock.elapsedRealtime();
            Log.d(TAG, String.format("Updated manifest request time to %d.", manifestRequestTime));
            notifyListeners();
        }
    }

    @Override
    public void onBytesTransferred(Object source, int bytesTransferred) {}

    @Override
    public void onTransferEnd(Object source) {}

    /**
     * To be implemented by listeners who wish to receive the manifest
     * request time.
     */
    public interface ManifestRequestTimeReceiver {
        /** Gives the manifest request time to the listener. */
        void giveManifestRequestTime(long manifestRequestTime);
    }
}
