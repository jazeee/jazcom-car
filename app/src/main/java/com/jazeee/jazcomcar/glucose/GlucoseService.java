package com.jazeee.jazcomcar.glucose;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicLong;

public class GlucoseService extends Service implements IGlucoseListener, ITokenListener{
  public static final String CLASS_NAME = GlucoseService.class.getName();
  public static final String USERNAME_NAME = "GLUCOSE_SERVICE_USERNAME";
  public static final String PASSWORD_NAME = "GLUCOSE_SERVICE_PASSWORD";
  public static final String ADD_LOG_NAME = "GLUCOSE_SERVICE_LOG";
  public static final String PUBLISH_GLUCOSE_READING_NAME = "GLUCOSE_READING";

  private final TokenManager tokenManager = new TokenManager(this);
  private final GlucoseManager glucoseManager = new GlucoseManager(this);

  private final AtomicLong lastTokenTime = new AtomicLong();

  public class LocalBinder extends Binder {
    public GlucoseService getService() {
      return GlucoseService.this;
    }
  }
  private IBinder binder = new LocalBinder();

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public GlucoseService() {
  }

  @Override
  public void onCreate() {
    Log.i(getClass().getCanonicalName(), "onCreate");
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(getClass().getCanonicalName(), "Received start id " + startId + ": " + intent);
    if (intent != null) {
      String userName = intent.getStringExtra(USERNAME_NAME);
      String password = intent.getStringExtra(PASSWORD_NAME);
      getLatestGlucose(userName, password);
    }

    return START_STICKY;
  }

  protected void getLatestGlucose(String userName, String password) {
    long now = System.currentTimeMillis();
    boolean shouldGetNewToken = (now - lastTokenTime.get() > 45 * 60_000);
    if (tokenManager.getToken() == null || shouldGetNewToken) {
      tokenManager.requestTokenAsync(userName, password);
    } else {
      glucoseManager.requestGlucoseAsync(tokenManager.getToken());
    }
  }

  @Override
  public void onTokenProcessed(boolean isSuccessful, String logMessage) {
    addLog(logMessage);
    if (isSuccessful) {
      glucoseManager.requestGlucoseAsync(tokenManager.getToken());
    }
  }

  public void onGlucoseRead(GlucoseReading glucoseReading, String logMessage) {
    addLog(logMessage);
    if (glucoseReading != null) {
      Intent intent = new Intent(PUBLISH_GLUCOSE_READING_NAME);
      intent.putExtra("glucoseReading", glucoseReading);
      LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
  }

  private void addLog(String logMessage) {
    Intent intent = new Intent(ADD_LOG_NAME);
    intent.putExtra("message", logMessage);
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }

  @Override
  public void onDestroy() {
    Log.i(getClass().getCanonicalName(), "onDestroy");
  }
}
