package com.example.mislplayer;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Builds a whitespace-separated log and writes it to file.
 */
public class DefaultLogBuilder extends LogBuilder {

    private enum Column {
        CHUNK_INDEX ("Chunk_Index"),
        ARRIVAL_TIME ("Arrival_Time"),
        LOAD_DURATION("Delivery_Time"),
        STALL_DURATION("Stall_Duration"),
        REPRESENTATION_RATE("Representation_Rate"),
        DELIVERY_RATE("Delivery_Rate"),
        ACTUAL_RATE("Actual_Rate"),
        BYTE_SIZE("Byte_Size"),
        BUFFER_LEVEL("Buffer_Level");

        private final String title;
        private int width;

        Column(String columnTitle) {
            title = columnTitle;
            width = columnTitle.length();
        }

        private void widenTo(int length) {
            if (width < length) {
                width = length;
            }
        }
    }

    private static final String TAG = "DefaultLogBuilder";

    private static final String VALUE_SEPARATOR = "\t\t";
    private static final String ENTRY_SEPARATOR = "\n";

    private final File directory;
    private final File file;

    private StringBuilder body = new StringBuilder();
    private HashSet<Column> usedColumns = new HashSet<>();
    private HashMap<Column, String> entry;

    public DefaultLogBuilder(File directory, File file) {
        this.directory = directory;
        this.file = file;
    }

    @Override
    public void startEntry() {
        entry = new HashMap<>();
    }

    @Override
    public void chunkIndex(int index) {
        enterValue(Column.CHUNK_INDEX, Integer.toString(index));
    }

    @Override
    public void arrivalTime(long arrivalTimeMs) {
        enterValue(Column.ARRIVAL_TIME, Long.toString(arrivalTimeMs));
    }

    @Override
    public void deliveryRate(long deliveryRateKbps) {
        enterValue(Column.DELIVERY_RATE, Long.toString(deliveryRateKbps));
    }

    @Override
    public void loadDuration(long loadDurationMs) {
        enterValue(Column.LOAD_DURATION, Long.toString(loadDurationMs));
    }

    @Override
    public void stallDuration(long stallDurationMs) {
        enterValue(Column.STALL_DURATION, Long.toString(stallDurationMs));
    }

    @Override
    public void representationRate(long repRateKbps) {
        enterValue(Column.REPRESENTATION_RATE, Long.toString(repRateKbps));
    }

    @Override
    public void actualRate(long actualRateKbps) {
        enterValue(Column.ACTUAL_RATE, Long.toString(actualRateKbps));
    }

    @Override
    public void byteSize(long byteSize) {
        enterValue(Column.BYTE_SIZE, Long.toString(byteSize));
    }

    @Override
    public void bufferLevel(long bufferLevelMs) {
        enterValue(Column.BUFFER_LEVEL, Long.toString(bufferLevelMs));
    }

    @Override
    public void finishEntry() {
        boolean firstColumn = true;
        for (Column column : Column.values()) {
            if (usedColumns.contains(column)) {
                String lineString = "%" + column.width + "s";
                if (!firstColumn) {
                    body.append(VALUE_SEPARATOR);
                }
                body.append(String.format(lineString, entry.get(column)));
                firstColumn = false;
            }
        }
        body.append(ENTRY_SEPARATOR);
    }

    @Override
    public void finishLog() {
        StringBuilder header = makeHeader();
        writeLogToFile(header);
    }

    private void writeLogToFile(StringBuilder header) {
        try {
            if (!directory.exists()) {
                directory.mkdirs();
            }
            file.createNewFile();
            FileOutputStream stream = new FileOutputStream(file);
            String output = header.append(body).toString();
            stream.write(output.getBytes());
            stream.close();
        } catch (IOException e) {
            Log.e(TAG, "File write failed: " + e.toString());
        }
    }

    private StringBuilder makeHeader() {
        StringBuilder header = new StringBuilder();
        boolean firstColumn = true;
        for (Column column : Column.values()) {
            if (usedColumns.contains(column)) {
                String lineString = "%" + column.width + "s";
                if (!firstColumn) {
                    header.append(VALUE_SEPARATOR);
                }
                header.append(String.format(lineString, column.title));
                firstColumn = false;
            }
        }
        header.append(ENTRY_SEPARATOR);
        return header;
    }

    private void enterValue(Column column, String value) {
        entry.put(column, value);
        column.widenTo(value.length());
        if (!usedColumns.contains(column)) {
            usedColumns.add(column);
        }
    }
}
