package com.example.mislplayer.Algorithm_Parameters;

/**
 * Created by Quentin L on 05/07/2017.
 */

/**
 * Created by Quentin L on 27/06/2017.
 */

public class OscarParameters {
    public static double OSCAR_H_STAT_THR;
    public static double OSCAR_H_ADAP_COEFF;
    public static double OSCAR_BW_SAFETY;
    public static int OSCAR_EST_WNDW;
    public static int OSCAR_PRED_WNDW;
    public static int OSCAR_RESV_SEGMENTS;
    public static int HISTORIC_ESTIMATION;
    public static int PREDICTIVE_ESTIMATION;
    public static int OSCAR_OPT_WINDOW;

    public OscarParameters(double OSCAR_H_STAT_THR,double OSCAR_H_ADAP_COEFF,double OSCAR_BW_SAFETY,int OSCAR_EST_WNDW,int OSCAR_PRED_WNDW,int OSCAR_RESV_SEGMENTS,int HISTORIC_ESTIMATION,int PREDICTIVE_ESTIMATION, int OSCAR_OPT_WINDOW){
        this.OSCAR_H_STAT_THR=OSCAR_H_STAT_THR;
        this.OSCAR_H_ADAP_COEFF=OSCAR_H_ADAP_COEFF;
        this.OSCAR_BW_SAFETY=OSCAR_BW_SAFETY;
        this.OSCAR_EST_WNDW=OSCAR_EST_WNDW;
        this.OSCAR_PRED_WNDW=OSCAR_PRED_WNDW;
        this.OSCAR_RESV_SEGMENTS=OSCAR_RESV_SEGMENTS;
        this.HISTORIC_ESTIMATION=HISTORIC_ESTIMATION;
        this.PREDICTIVE_ESTIMATION=PREDICTIVE_ESTIMATION;
        this.OSCAR_OPT_WINDOW=OSCAR_OPT_WINDOW;
    }
}
