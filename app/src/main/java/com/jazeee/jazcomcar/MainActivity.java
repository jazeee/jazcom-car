package com.jazeee.jazcomcar;

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
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends AppCompatActivity implements ITokenListener, IGlucoseListener {
  private final List<String> logMessages = new ArrayList<>();

  private final Handler timerHandler = new Handler();
  private boolean isTiming = false;
  private final AtomicLong lastTokenTime = new AtomicLong();

  private String userName = "";
  private String password = "";

  private final TokenManager tokenManager = new TokenManager(this);
  private final GlucoseManager glucoseManager = new GlucoseManager(this);

  private boolean isBluetoothServiceBound = false;
  private BluetoothService bluetoothService;

  private ServiceConnection serviceConnection = new ServiceConnection() {
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
        serviceConnection, Context.BIND_AUTO_CREATE)) {
      isBluetoothServiceBound = true;
    } else {
      addLog("Failed to Bind BluetoothService");
    }
  }

  void unbindBluetoothService() {
    if (isBluetoothServiceBound) {
      unbindService(serviceConnection);
      isBluetoothServiceBound = false;
    }
  }

  Handler logHandler = new Handler(new Handler.Callback() {
    @Override
    public boolean handleMessage(Message messageData) {
      String message = (String) messageData.obj;
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
      // Extract data included in the Intent
      String logMessage = intent.getStringExtra("message");
      addLog(logMessage);
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

  Runnable timerRunnable = new Runnable() {
    @Override
    public void run() {
    getLatestGlucose();
    timerHandler.postDelayed(this, 5 * 60 * 1000);
    }
  };

  protected void getLatestGlucose() {
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
      final TextView statusText = (TextView) findViewById(R.id.statusText);
      statusText.setText(glucoseReading.getGlucoseValue() + " Trend: " + glucoseReading.getTrend());
      (new Thread(new BluetoothPublisher(glucoseReading))).start();
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
        timerHandler.postDelayed(timerRunnable, 0);
        toggleTimer.setText("Stop Timer");
      } else {
        timerHandler.removeCallbacks(timerRunnable);
        toggleTimer.setText("Restart Timer");
      }
      }
    });
    LocalBroadcastManager.getInstance(this)
        .registerReceiver(logMessageReceiver,
            new IntentFilter(BluetoothService.ADD_LOG_NAME));

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBluetooth, 0);
    }
    bindBluetoothService();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unbindBluetoothService();
  }
}
