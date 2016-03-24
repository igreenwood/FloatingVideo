package com.example.floatingvideo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.devbrackets.android.exomedia.EMVideoView;

public class MainActivity extends Activity {
    private Intent intent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        intent = new Intent(getApplication(), VideoService.class);

        findViewById(R.id.start_button).setOnClickListener(clickListener);
        findViewById(R.id.stop_button).setOnClickListener(clickListener);
    }

    private final View.OnClickListener clickListener = new View.OnClickListener(){

        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.start_button:
                    startService(intent);
                    break;
                case R.id.stop_button:
                    stopService(intent);
                    break;
            }
        }
    };
}
