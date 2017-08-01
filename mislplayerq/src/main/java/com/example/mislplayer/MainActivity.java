package com.example.mislplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;


public class MainActivity extends Activity  {

    public Handler mainHandler;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler();
        setContentView(R.layout.algorithm_chooser);
        getSelectedButtonValue();
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    //Get the string value of the selected radio button
    public void getSelectedButtonValue() {

        final RadioGroup radioGroup = (RadioGroup) findViewById(R.id.radioAlgo);
        Button bValidate = (Button) findViewById(R.id.validate);
        bValidate.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                // get selected radio button from radioGroup
                int selectedId = radioGroup.getCheckedRadioButtonId();

                // find the radiobutton by returned id
                RadioButton radioButton = (RadioButton) findViewById(selectedId);
                sendMessage(radioButton.getId());

            }

        });
    }

    //Method to launch next activity, we provide the ALGORITHM TYPE string for next activity
    public void sendMessage(int algorithmType)
    {
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        AdaptationAlgorithmType type;

        switch (algorithmType) {
            case R.id.basic_exoplayer:
                type = AdaptationAlgorithmType.BASIC_EXOPLAYER;
                break;
            case R.id.basic_adaptive:
                type = AdaptationAlgorithmType.BASIC_ADAPTIVE;
                break;
            case R.id.bba2:
                type = AdaptationAlgorithmType.BBA2;
                break;
            case R.id.oscar_h:
                type = AdaptationAlgorithmType.OSCAR_H;
                break;
            case R.id.arbiter:
                type = AdaptationAlgorithmType.ARBITER;
                break;
            case R.id.elastic:
                type = AdaptationAlgorithmType.ELASTIC;
                break;
            default:
                throw new IllegalArgumentException(
                        "Unrecognised adaptation algorithm");
        }
        intent.putExtra("com.example.misl.AlgorithmType", type);
        startActivity(intent);
    }



}
