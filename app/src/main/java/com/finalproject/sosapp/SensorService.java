package com.finalproject.sosapp;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;

import java.util.List;

public class SensorService extends Service {

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;

    public SensorService(){
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i("Sensor Service", "On create start foreground");
            startMyOwnForeground();
        }
        else {
            Log.i("Sensor Service", "On create start foreground");
            startForeground(1, new Notification());

            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mAccelerometer = mSensorManager
                    .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mShakeDetector = new ShakeDetector();
            mShakeDetector.setOnShakeListener(new ShakeDetector.OnShakeListener() {

                @SuppressLint("MissingPermission")
                @Override
                public void onShake(int count) {
                    if (count == 10) {
                        vibrate();
                        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
                        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, new CancellationToken() {
                            @Override
                            public boolean isCancellationRequested() {
                                return false;
                            }

                            @NonNull
                            @Override
                            public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                                return null;
                            }
                        }).addOnSuccessListener(new OnSuccessListener<Location>() {
                            @Override
                            public void onSuccess(Location location) {
                                if (location != null) {
                                    SmsManager smsManager = SmsManager.getDefault();
                                    DbHelper db = new DbHelper(SensorService.this);
                                    List<ContactModel> list = db.getAllContacts();
                                    for (ContactModel c : list) {
                                        String message = c.getName() + "I am in DANGER. Please help me. Here are my coordinates.\n " + "http://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
                                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                                    }
                                } else {
                                    String message = "I am in DANGER. Please help me.\n" + "GPS was turned off. Couldn't find location. Call your nearest Police Station.";
                                    SmsManager smsManager = SmsManager.getDefault();
                                    DbHelper db = new DbHelper(SensorService.this);
                                    List<ContactModel> list = db.getAllContacts();
                                    for (ContactModel c : list) {
                                        smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                                    }
                                }
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.d("Check: ", "OnFailure");
                                String message = ", I am in DANGER. Please help me.\n" + "GPS was turned off. Couldn't find location. Call your nearest Police Station.";
                                SmsManager smsManager = SmsManager.getDefault();
                                DbHelper db = new DbHelper(SensorService.this);
                                List<ContactModel> list = db.getAllContacts();
                                for (ContactModel c : list) {
                                    smsManager.sendTextMessage(c.getPhoneNo(), null, message, null, null);
                                }
                            }
                        });

                    }
                }
            });
        }
        mSensorManager.registerListener(mShakeDetector, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    public void vibrate(){
        final Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        VibrationEffect vibEff;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibEff=VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK);
            vibrator.cancel();
            vibrator.vibrate(vibEff);
        }else{
            vibrator.vibrate(500);
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void startMyOwnForeground()
    {
        String NOTIFICATION_CHANNEL_ID = "example.permanence";
        String channelName = "Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("You are protected.")
                .setContentText("We are there for you")

                .setSmallIcon(R.drawable.ic_launcher_foreground)

                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }


    @Override
    public void onDestroy() {

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, ReactivateService.class);
        this.sendBroadcast(broadcastIntent);
        super.onDestroy();
    }

}
