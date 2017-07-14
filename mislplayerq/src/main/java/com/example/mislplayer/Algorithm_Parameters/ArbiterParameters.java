package com.example.mislplayer.Algorithm_Parameters;

//Created by Quentin L on 05/07/2017.

import com.example.mislplayer.Algorithm_Parameters.DashParameters;

public class ArbiterParameters implements DashParameters {

    public int estWindow;
    public double EXP_AVG_RATIO;
    public double bwSafetyFactor;
    public double minBuffFactor;
    public double maxBuffFactor;
    public int videoWindow;

    public ArbiterParameters(int estWindow,double EXP_AVG_RATIO,double bwSafetyFactor,double minBuffFactor,double maxBuffFactor, int videoWindow){
        this.estWindow=estWindow;
        this.EXP_AVG_RATIO=EXP_AVG_RATIO;
        this.bwSafetyFactor=bwSafetyFactor;
        this.minBuffFactor=minBuffFactor;
        this.maxBuffFactor=maxBuffFactor;
        this.videoWindow=videoWindow;
    }






}
