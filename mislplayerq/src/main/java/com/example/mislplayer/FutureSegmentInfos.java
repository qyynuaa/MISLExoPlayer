package com.example.mislplayer;

import android.util.Log;

import java.util.ArrayList;

/**
 * Created by Quentin L on 19/06/2017.
 */

public class FutureSegmentInfos {

    private int segmentNumber;
    private int representationLevel;
    private int byteSize;


    public FutureSegmentInfos(int segmentNumber,int representationLevel,int byteSize){
        this.segmentNumber=segmentNumber;
        this.representationLevel=representationLevel;
        this.byteSize=byteSize;
    }

    public static int getByteSize(ArrayList<FutureSegmentInfos> segInfos, int segmentNumber, int representationLevel){
        for(int i=0;i<segInfos.size();i++){
            Log.d("RPL",segInfos.get(i).representationLevel+"");
            if(segInfos.get(i).representationLevel==representationLevel && segInfos.get(i).segmentNumber==segmentNumber){
                return segInfos.get(i).byteSize;
            }
        }
        return -1;
    }

}
