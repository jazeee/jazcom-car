package com.jazeee.jazcomcar;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity implements ITokenListener, IGlucoseListener {
  private final List<String> logMessages = new ArrayList<>();
  private BluetoothDevice bluetoothDevice = null;
  private LocalSocket localSocket;

  private final AtomicReference<BluetoothSocket> ref = new AtomicReference<>(null);

  private final Handler timerHandler = new Handler();
  private boolean isTiming = false;
  private final AtomicLong lastTokenTime = new AtomicLong();

  private String userName = "";
  private String password = "";

  private final TokenManager tokenManager = new TokenManager(this);
  private final GlucoseManager glucoseManager = new GlucoseManager(this);

  private class LocalSocket implements AutoCloseable {
    LocalSocket() throws IOException {
      close();
      UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
      BluetoothSocket bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
      ref.set(bluetoothSocket);
    }

    BluetoothSocket reconnectIfNeeded() throws IOException {
      BluetoothSocket bluetoothSocket = get();
      if (!bluetoothSocket.isConnected()){
        bluetoothSocket.connect();
      }
      return bluetoothSocket;
    }

    public BluetoothSocket get() {
      return ref.get();
    }

    @Override
    public void close() {
      BluetoothSocket bluetoothSocket = ref.getAndSet(null);
      if (bluetoothSocket != null) {
        try {
          bluetoothSocket.close();
        } catch (IOException e) {
          e.printStackTrace();
          addLog(e.getMessage());
        }
      }
    }
  }

  public BluetoothSocket sendBluetoothMessage(BluetoothSocket bluetoothSocket, String message) throws IOException {
    message = "JAZ:" + message;
    OutputStream outputStream = bluetoothSocket.getOutputStream();
    outputStream.write(message.getBytes());
    return bluetoothSocket;
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

  public void addLog(String line) {
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
      try {
        final BluetoothSocket bluetoothSocket = localSocket.reconnectIfNeeded();
        sendBluetoothMessage(bluetoothSocket, glucoseReading.getGlucoseValue());
      } catch (IOException e) {
        e.printStackTrace();
        addLog(e.getMessage());
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

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBluetooth, 0);
    }

    Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
    if (pairedDevices.size() > 0) {
      for (BluetoothDevice device : pairedDevices) {
        Log.d("Found device: ", device.getName() + "" + device.getAddress());
        if (device.getName().equals("JazComBT01")) {
          Log.e("JazComCar Name: ", device.getName());
          bluetoothDevice = device;
          break;
        }
      }
    }
    if (bluetoothDevice != null) {
      try {
        localSocket = new LocalSocket();
      } catch (IOException e) {
        e.printStackTrace();
        addLog(e.getMessage());
      }
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    localSocket.close();
  }
}
