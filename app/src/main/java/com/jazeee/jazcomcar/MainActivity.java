package com.jazeee.jazcomcar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.jazeee.bluetooth.BluetoothService;
import com.jazeee.jazcomcar.glucose.GlucoseReading;
import com.jazeee.jazcomcar.glucose.GlucoseService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  private final List<String> logMessages = new ArrayList<>();
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

  private boolean isTiming = false;
  private PendingIntent alarmIntent;

  private String userName = "";
  private String password = "";

  private boolean isGlucoseServiceBound = false;
  private GlucoseService glucoseService;
  private boolean isBluetoothServiceBound = false;
  private BluetoothService bluetoothService;
  private BroadcastReceiver broadcastReceiver;

  private ServiceConnection bluetoothServiceConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      bluetoothService = ((BluetoothService.LocalBinder)service).getService();
      addLog("Connected to BluetoothService");
    }

    public void onServiceDisconnected(ComponentName className) {
      bluetoothService = null;
      addLog("Disconnected from BluetoothService");
    }
  };

  void bindBluetoothService() {
    if (bindService(new Intent(MainActivity.this, BluetoothService.class),
        bluetoothServiceConnection, Context.BIND_AUTO_CREATE)) {
      isBluetoothServiceBound = true;
    } else {
      addLog("Failed to Bind BluetoothService");
    }
  }

  void unbindBluetoothService() {
    if (isBluetoothServiceBound) {
      unbindService(bluetoothServiceConnection);
      isBluetoothServiceBound = false;
    }
  }
  private ServiceConnection glucoseServiceConnection = new ServiceConnection() {
    public void onServiceConnected(ComponentName className, IBinder service) {
      glucoseService = ((GlucoseService.LocalBinder)service).getService();
      addLog("Connected to GlucoseService");
    }

    public void onServiceDisconnected(ComponentName className) {
      glucoseService = null;
      addLog("Disconnected from GlucoseService");
    }
  };

  void bindGlucoseService() {
    if (bindService(new Intent(MainActivity.this, GlucoseService.class),
        glucoseServiceConnection, Context.BIND_AUTO_CREATE)) {
      isGlucoseServiceBound = true;
    } else {
      addLog("Failed to Bind GlucoseService");
    }
  }

  void unbindGlucoseService() {
    if (isGlucoseServiceBound) {
      unbindService(glucoseServiceConnection);
      isGlucoseServiceBound = false;
    }
  }

  Handler logHandler = new Handler(new Handler.Callback() {
    @Override
    public boolean handleMessage(Message messageData) {
      String message = (String) messageData.obj;
      message = dateFormat.format(Calendar.getInstance().getTime()) + ": " + message;
      logMessages.add(message);
      while (logMessages.size() > 16) {
        logMessages.remove(0);
      }
      EditText logConsole = (EditText) findViewById(R.id.logConsole);
      logConsole.setText(TextUtils.join(System.getProperty("line.separator"), logMessages));
      return false;
    }
  });

  private BroadcastReceiver logMessageReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String logMessage = intent.getStringExtra("message");
      addLog(logMessage);
    }
  };

  private BroadcastReceiver glucoseReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      // Extract data included in the Intent
    GlucoseReading glucoseReading = (GlucoseReading) intent.getParcelableExtra("glucoseReading");
    if (glucoseReading != null) {
      final TextView statusText = (TextView) findViewById(R.id.statusText);
      statusText.setText(glucoseReading.getGlucoseValue() + " Trend: " + glucoseReading.getTrend());
      (new Thread(new BluetoothPublisher(glucoseReading))).start();
    }
    }
  };

  public void addLog(String line) {
    Log.i("MainActivity", line);
    Message messageData = new Message();
    messageData.obj = line;
    logHandler.sendMessage(messageData);
  }

  final class BluetoothPublisher implements Runnable {
    private GlucoseReading glucoseReading;

    public BluetoothPublisher(GlucoseReading glucoseReading) {
      this.glucoseReading = glucoseReading;
    }

    public void run() {
      if (isBluetoothServiceBound && bluetoothService != null) {
        Intent messageIntent = new Intent(MainActivity.this, BluetoothService.class);
        messageIntent.putExtra(BluetoothService.MESSAGE_NAME, glucoseReading.getGlucoseValue());
        bluetoothService.onStartCommand(messageIntent, 0, 0);
      } else {
        addLog("No BluetoothService");
      }
    }
  }

  private Intent getGlucoseIntent() {
    Intent intent = new Intent(MainActivity.this, GlucoseService.class);
    intent.putExtra(GlucoseService.USERNAME_NAME, userName);
    intent.putExtra(GlucoseService.PASSWORD_NAME, password);
    intent.setAction(GlucoseService.CLASS_NAME);
    return intent;
  }

  private void getLatestGlucose() {
    if (isGlucoseServiceBound && glucoseService != null) {
      glucoseService.onStartCommand(getGlucoseIntent(), 0, 0);
    } else {
      addLog("No GlucoseService");
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    final TextView statusText = (TextView) findViewById(R.id.statusText);
    final Button updateGlucoseButton = (Button) findViewById(R.id.updateGlucose);
    final Button toggleTimer = (Button) findViewById(R.id.toggleTimer);
    final SharedPreferences sharedPreferences = getPreferences(Context.MODE_PRIVATE);
    userName = sharedPreferences.getString("userName", "jazeee");
    password = sharedPreferences.getString("password", "");
    final EditText userNameInput = (EditText) findViewById(R.id.userName);
    final EditText passwordInput = (EditText) findViewById(R.id.password);
    userNameInput.setText(userName);
    passwordInput.setText(password);

    userNameInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View view, boolean isFocused) {
      if (!isFocused) {
        userName = userNameInput.getText().toString();
        sharedPreferences.edit().putString("userName", userName).commit();
      }
      }
    });

    passwordInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      @Override
      public void onFocusChange(View view, boolean isFocused) {
      if (!isFocused) {
        password = passwordInput.getText().toString();
        sharedPreferences.edit().putString("password", password).commit();
      }
      }
    });


    updateGlucoseButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
      statusText.setText("Sending...");
      getLatestGlucose();
      }
    });
    toggleTimer.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
      isTiming = !isTiming;
      if (isTiming) {
        toggleTimer.setText("Stop Timer");
        startTimer();
      } else {
        toggleTimer.setText("Restart Timer");
        stopTimer();
      }
      }
    });
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(logMessageReceiver,
            new IntentFilter(BluetoothService.ADD_LOG_NAME));
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(logMessageReceiver,
        new IntentFilter(GlucoseService.ADD_LOG_NAME));
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(glucoseReceiver,
            new IntentFilter(GlucoseService.PUBLISH_GLUCOSE_READING_NAME));

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBluetooth, 0);
    }
    bindBluetoothService();
    bindGlucoseService();
    broadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        addLog("Sending broadcast request for new reading");
        glucoseService.onStartCommand(getGlucoseIntent(), 0, 0);
      }
    };
    this.registerReceiver(broadcastReceiver, new IntentFilter(GlucoseService.CLASS_NAME));
  }

  private void startTimer() {
    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    Intent intent = new Intent(GlucoseService.CLASS_NAME);
    alarmIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
    alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
        SystemClock.elapsedRealtime(),
        5 * 60 * 1000,
        alarmIntent
    );
  }

  private void stopTimer() {
    if (alarmIntent != null) {
      AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
      alarmManager.cancel(alarmIntent);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.unregisterReceiver(broadcastReceiver);
    unbindGlucoseService();
    unbindBluetoothService();
  }
}
