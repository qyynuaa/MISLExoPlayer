package com.example.mislplayer;

import android.util.Log;

import com.example.mislplayer.Algorithm_Parameters.DashParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * Created by Quentin L on 31/05/2017.
 */

public class LogSegment {
    private int segNumber;
    private long arrivalTime;
    private long deliveryTime;
    private long stallDuration;
    private int repLevel;
    private long actionRate;
    private long byteSize;
    private long bufferLevel;
    private long deliveryRate;
    private long segmentDuration;
    private DashParameters dashParameters;
    public static String path = "/sdcard/Logs_Exoplayer";

    public LogSegment(int segNumber,long arrivalTime,long deliveryTime,long stallDuration,int repLevel,long deliveryRate,long actionRate,long byteSize, long bufferLevel,long segmentDuration,DashParameters dashParameters){
        this.segNumber=segNumber;
        this.arrivalTime=arrivalTime;
        this.deliveryTime=deliveryTime;
        this.stallDuration=stallDuration;
        this.repLevel=repLevel;
        this.deliveryRate=deliveryRate;
        this.actionRate=actionRate;
        this.byteSize=byteSize;
        this.bufferLevel=bufferLevel;
        this.segmentDuration=segmentDuration;
        this.dashParameters=dashParameters;
    }

    public int getSegNumber(){
        return segNumber;
    }
    public long getArrivalTime(){
        return arrivalTime;
    }
    public long getDeliveryTime(){
        return deliveryTime;
    }
    public long getStallDuration(){
        return stallDuration;
    }
    public int getRepLevel(){
        return repLevel;
    }
    public long getDeliveryRate() {return deliveryRate;}
    public long getActionRate(){
        return actionRate;
    }
    public long getByteSize(){
        return byteSize;
    }

    public long getSegmentDuration(){
        return segmentDuration;
    }

    public DashParameters getDashParameters(){
        return dashParameters;
    }

    public void setByteSize(long byteSize){this.byteSize=byteSize;}
    public void setDeliveryRate(long deliveryRate){this.deliveryRate=deliveryRate;}
    public void setBufferLevel(long bufferLevel){this.bufferLevel=bufferLevel;}
    public void setRepLevel(int repLevel){this.repLevel=repLevel;}
    public void setActionRate(long actionRate){this.actionRate=actionRate;}
    public void setDashParameters(DashParameters dashParameters){this.dashParameters=dashParameters;}

    @Override
    public String toString(){
        String segNum= (getSegNumber())+"";
        String arrivalTime = getArrivalTime()+"";
        String deliveryTime = getDeliveryTime()+"";
        String stallDuration = getStallDuration()+"";
        String repLevel = getRepLevel()+"";
        String deliveryRate = getDeliveryRate()+"";
        String actionRate = getActionRate()+"";
        String byteSize = getByteSize()+"";
        while (segNum.length()!=5){
            segNum = " "+segNum;
        }
        while(arrivalTime.length()!=8){
            arrivalTime = " "+arrivalTime;
        }
        while(deliveryTime.length()!=9){
            deliveryTime = " "+deliveryTime;
        }
        while(stallDuration.length()!=10){
            stallDuration = " "+stallDuration;
        }
        while(repLevel.length()!=10){
            repLevel = " "+repLevel;
        }
        while(deliveryRate.length()!=9){
            deliveryRate = " "+deliveryRate;
        }
        while(actionRate.length()!=9){
            actionRate = " "+actionRate;
        }
        while(byteSize.length()!=10){
            byteSize = " "+byteSize;
        }
        return segNum+" "+arrivalTime+"\t"+deliveryTime+"\t"+stallDuration+"\t"+repLevel+"\t"+deliveryRate+"\t"+actionRate+"\t"+byteSize+"\t"+bufferLevel;
    }


    public static void writeLogSegInFile(ArrayList<LogSegment> segmentsInfos, long[] byteSizes) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH:mm:ss");
        Date date = new Date();
        File file = new File(path, "/Log_Segments_ExoPlayer_" + dateFormat.format(date) + ".txt");
        try {
            FileOutputStream stream = new FileOutputStream(file);
            stream.write("Seg_#\t\tArr_time\t\tDel_Time\t\tStall_Dur\t\tRep_Level\t\tDel_Rate\t\tAct_Rate\t\tByte_Size\t\tBuff_Level\n".getBytes());
            int index;
            for (index = 0; index < segmentsInfos.size(); index++) {
                if (segmentsInfos.get(index) != null) {
                    stream.write(segmentsInfos.get(index).toString().getBytes());
                    stream.write("\n".getBytes());
                }

            }
            stream.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }


    }




}
