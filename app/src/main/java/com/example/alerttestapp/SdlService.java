package com.example.alerttestapp;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.smartdevicelink.managers.SdlManager;
import com.smartdevicelink.managers.SdlManagerListener;
import com.smartdevicelink.managers.file.filetypes.SdlArtwork;
import com.smartdevicelink.managers.lifecycle.LifecycleConfigurationUpdate;
import com.smartdevicelink.managers.screen.AlertView;
import com.smartdevicelink.managers.screen.SoftButtonObject;
import com.smartdevicelink.managers.screen.SoftButtonState;
import com.smartdevicelink.protocol.enums.FunctionID;
import com.smartdevicelink.proxy.RPCNotification;
import com.smartdevicelink.proxy.rpc.OnButtonEvent;
import com.smartdevicelink.proxy.rpc.OnButtonPress;
import com.smartdevicelink.proxy.rpc.OnHMIStatus;
import com.smartdevicelink.proxy.rpc.enums.AppHMIType;
import com.smartdevicelink.proxy.rpc.enums.FileType;
import com.smartdevicelink.proxy.rpc.enums.Language;
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener;
import com.smartdevicelink.transport.BaseTransportConfig;
import com.smartdevicelink.transport.MultiplexTransportConfig;
import com.smartdevicelink.util.DebugTool;
import com.smartdevicelink.util.SystemInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class SdlService extends Service {

    private static final String TAG = "SDL Service";

    private static final String APP_ID = "";
    private static final int FOREGROUND_SERVICE_ID = 111;
    private static final String ICON_FILENAME = "hello_sdl_icon.png";
    private static final String APP_NAME = "";

    private SdlManager sdlManager = null;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        DebugTool.logInfo(TAG, "SdlService.onBind: ", false);
        return mBinder;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterForeground();
        }
    }

    private final IBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        public SdlService getService() {
            return SdlService.this;
        }
    }


    // Helper method to let the service enter foreground mode
    @SuppressLint("NewApi")
    public void enterForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Notification.Builder builder = new Notification.Builder(this, channel.getId())
                        .setContentTitle("Connected through SDL")
                        .setSmallIcon(R.drawable.ic_sdl);
                Notification serviceNotification = builder.build();
                startForeground(FOREGROUND_SERVICE_ID, serviceNotification);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startProxy();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }

        if (sdlManager != null) {
            sdlManager.dispose();
        }

        super.onDestroy();
    }

    private void startProxy() {
        // This logic is to select the correct transport and security levels defined in the selected build flavor
        // Build flavors are selected by the "build variants" tab typically located in the bottom left of Android Studio
        // Typically in your app, you will only set one of these.
        if (sdlManager == null) {
            Log.i(TAG, "Starting SDL Proxy");
            // Enable DebugTool for debug build type
            DebugTool.enableDebugTool();
            BaseTransportConfig transport = null;
            int securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF;
            transport = new MultiplexTransportConfig(this, APP_ID, securityLevel);

            // The app type to be used
            Vector<AppHMIType> appType = new Vector<>();
            appType.add(AppHMIType.BACKGROUND_PROCESS);

            // The manager listener helps you know when certain events that pertain to the SDL Manager happen
            // Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
            SdlManagerListener listener = new SdlManagerListener() {
                @Override
                public void onStart() {
                    // HMI Status Listener
                    sdlManager.addOnRPCNotificationListener(FunctionID.ON_HMI_STATUS, new OnRPCNotificationListener() {
                        @Override
                        public void onNotified(RPCNotification notification) {
                            OnHMIStatus onHMIStatus = (OnHMIStatus) notification;
                            DebugTool.logInfo(TAG, "HMI LEVEL: " + onHMIStatus.getHmiLevel());
                        }
                    });
                }

                @Override
                public void onDestroy() {
                    SdlService.this.stopSelf();
                }

                @Override
                public void onError(String info, Exception e) {
                }

                @Override
                public LifecycleConfigurationUpdate managerShouldUpdateLifecycle(Language language, Language hmiLanguage) {
                    return null;
                }

                @Override
                public boolean onSystemInfoReceived(SystemInfo systemInfo) {
                    //Check the SystemInfo object to ensure that the connection to the device should continue
                    return true;
                }
            };


            // Create App Icon, this is set in the SdlManager builder
            SdlArtwork appIcon = new SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true);

            // The manager builder sets options for your session
            SdlManager.Builder builder = new SdlManager.Builder(this, APP_ID, APP_NAME, listener);
            builder.setAppTypes(appType);
            builder.setTransportType(transport);
            builder.setAppIcon(appIcon);
            sdlManager = builder.build();
            sdlManager.start();
        }
    }

    public void startAlerts() {
        DebugTool.logInfo(TAG, "StartAlerts");
        int i = 0;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendAlert();
            }
        }, 0, 10000);
    }

    public void sendAlert() {
        AlertView.Builder builder = new AlertView.Builder();
        builder.setText("text 1");
        builder.setSecondaryText("text 2");
        builder.setSoftButtons(getButtons());
        AlertView alert = builder.build();
        sdlManager.getScreenManager().presentAlert(alert, (success, tryAgainTime) -> {
            DebugTool.logInfo(TAG, "Success: " + success);
        });
        DebugTool.logInfo(TAG, "Alert Sent");
    }

    private ArrayList<SoftButtonObject> getButtons() {
        SoftButtonState okButtonState = new SoftButtonState("okButtonAlertState", "okButtonAlertState", null);
        SoftButtonObject okSoftButtonObj = new SoftButtonObject("okButtonAlert", okButtonState, new
                SoftButtonObject.OnEventListener() {
                    @Override
                    public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                        DebugTool.logInfo(TAG, "OK BUTTON PRESSED");
                    }

                    @Override
                    public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {
                        DebugTool.logInfo(TAG, "OK BUTTON " + onButtonEvent.toString());
                    }
                });
        SoftButtonState cancelButtonState = new SoftButtonState("cancelButtonAlertState", "cancelButtonState", null);
        SoftButtonObject cancelButtonObj = new SoftButtonObject("cancelButtonAlertState", cancelButtonState, new SoftButtonObject.OnEventListener() {
            @Override
            public void onPress(SoftButtonObject softButtonObject, OnButtonPress onButtonPress) {
                DebugTool.logInfo(TAG, "CANCEL BUTTON PRESSED");
            }

            @Override
            public void onEvent(SoftButtonObject softButtonObject, OnButtonEvent onButtonEvent) {
                DebugTool.logInfo(TAG, "CANCEL BUTTON " + onButtonEvent.toString());
            }
        });
        return new ArrayList<SoftButtonObject>(Arrays.asList(okSoftButtonObj, cancelButtonObj));
    }
}
