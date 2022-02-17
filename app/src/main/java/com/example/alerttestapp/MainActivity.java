package com.example.alerttestapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;

import com.smartdevicelink.managers.screen.AlertView;
import com.smartdevicelink.util.DebugTool;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private Button alertsButton;
    private SdlService mSdlService;
    private boolean mIsBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        alertsButton = findViewById(R.id.alerts_button);

        SdlReceiver.queryForConnectedService(this);
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mSdlService = ((SdlService.LocalBinder)iBinder).getService();

            alertsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mSdlService.startAlerts();
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mSdlService = null;
        }
    };


    public void bindSdlService(){
        if(!mIsBound || mSdlService == null){
            bindService(new Intent(getApplicationContext(), SdlService.class), mConnection, Context.BIND_AUTO_CREATE);
            mIsBound = true;
        }
    }

    public void unbindSdlService(){
        if (mIsBound) {
            if (mConnection != null){
                unbindService(mConnection);
            }
            mIsBound = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindSdlService();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindSdlService();
    }
}