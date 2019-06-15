package com.jazeee.jazcomcar;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {
  BluetoothDevice bluetoothDevice = null;
  LocalSocket localSocket;

  final byte delimiter = 33;
  int readBufferPosition = 0;
  private final AtomicReference<String> token = new AtomicReference<>(null);
  private final AtomicLong lastRetrieval = new AtomicLong(0);
  private final AtomicReference<BluetoothSocket> ref = new AtomicReference<>(null);

  private Handler handler;
  private TextView myLabel;
  Handler timerHandler = new Handler();
  long startTime = 0;
  boolean isTiming = false;
  private final AtomicLong lastTokenTime = new AtomicLong();

  String userName = "";
  String password = "";

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
        }
      }
    }
  }

  public BluetoothSocket sendBtMsg(BluetoothSocket bluetoothSocket, String message) throws IOException {
    message = "JAZ:" + message;
    OutputStream outputStream = bluetoothSocket.getOutputStream();
    outputStream.write(message.getBytes());
    return bluetoothSocket;
  }

  final class workerThread implements Runnable {
    private String message;

    public workerThread(String message) {
      this.message = message;
    }

    public void run() {
      try {
        final BluetoothSocket bluetoothSocket = localSocket.reconnectIfNeeded();
        sendBtMsg(bluetoothSocket, message);
        while (!Thread.currentThread().isInterrupted() && bluetoothSocket.isConnected()) {
          boolean workDone = false;

          final InputStream inputStream = bluetoothSocket.getInputStream();
          int bytesAvailable = inputStream.available();
          if (bytesAvailable > 0) {
            byte[] packetBytes = new byte[bytesAvailable];
            Log.e("JazCom Car Bytes", "bytes available");
            byte[] readBuffer = new byte[1024];
            inputStream.read(packetBytes);

            for (int i = 0; i < bytesAvailable; i++) {
              byte b = packetBytes[i];
              if (b == delimiter) {
                byte[] encodedBytes = new byte[readBufferPosition];
                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                final String data = new String(encodedBytes, "US-ASCII");
                readBufferPosition = 0;

                handler.post(new Runnable() {
                  public void run() {
                    myLabel.setText(data);
                  }
                });
                workDone = true;
                break;
              } else {
                readBuffer[readBufferPosition++] = b;
              }
            }
            if (workDone == true) {
              bluetoothSocket.close();
              break;
            }
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  Runnable timerRunnable = new Runnable() {
    @Override
    public void run() {
      long millis = System.currentTimeMillis() - startTime;
      int seconds = (int) (millis / 1000);
      int minutes = seconds / 60;
      seconds = seconds % 60;

      getLatestGlucose();
      TextView log = findViewById(R.id.log);
      log.setText(String.format("%d:%02d", minutes, seconds));

      timerHandler.postDelayed(this, 5 * 60 * 1000);
    }
  };

  protected void getLatestGlucose() {
    long now = System.currentTimeMillis();
    boolean shouldGetNewToken = (now - lastTokenTime.get() > 45 * 60_000);
    if (token.get() == null || shouldGetNewToken) {
      new GetToken().execute(userName, password);
    } else {
      new GetGlucose().execute();
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    this.handler = new Handler();

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
        String value = "" + (new Random().nextInt(70) + 70);
        statusText.setText("Sending... Random: " + value);
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
      }
    }
    startTime = System.currentTimeMillis();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    localSocket.close();
  }

  private class GetToken extends AsyncTask<String, String, String> {
    protected String doInBackground(String... inputParams) {
      String userName = inputParams[0];
      String password = inputParams[1];
      try {
        String url = "https://share1.dexcom.com/ShareWebServices/Services/General/LoginPublisherAccountByName";
        JSONObject jsonParam = new JSONObject();
        jsonParam.put("applicationId", "d8665ade-9673-4e27-9ff6-92db4ce13d13");
        jsonParam.put("accountName", userName);
        jsonParam.put("password", password);
        return PostHttp.postHttp(url, null, jsonParam);
      } catch (JSONException e) {
        e.printStackTrace();
        return null;
      }
    }

    @Override
    protected void onPostExecute(String result) {
      super.onPostExecute(result);
      boolean isValidToken = result != null;
      if (isValidToken) {
        token.set(result.replace("\"", ""));
        lastRetrieval.set(System.currentTimeMillis());
        result = "Retrieved token" + result.substring(0, 5);
      } else {
        result = "Unable to read password";
      }
      if (isValidToken) {
        new GetGlucose().execute();
      }
      final TextView statusText = (TextView) findViewById(R.id.statusText);
      statusText.setText("Pwd: " + result);
    }
  }

  private class GetGlucose extends AsyncTask<String, String, String> {
    protected String doInBackground(String... inputParams) {
      String url = "https://share1.dexcom.com/ShareWebServices/Services/Publisher/ReadPublisherLatestGlucoseValues";
      Map<String, String> params = new HashMap<>();
      params.put("sessionId", token.get());
      params.put("minutes", "1440");
      params.put("maxCount", "1");
      JSONObject jsonParams = new JSONObject();
      return PostHttp.postHttp(url, params, null);
    }

    @Override
    protected void onPostExecute(String result) {
      super.onPostExecute(result);
      String glucoseValue = "?";
      if (result != null) {
        try {
          JSONArray data = new JSONArray(result);
          JSONObject output = data.getJSONObject(0);
          glucoseValue = output.getString("Value");
          result = glucoseValue;
        } catch (JSONException e) {
          e.printStackTrace();
        }
        result = "Retrieved value" + result;
      } else {
        result = "Unable to read data";
      }
      final TextView statusText = (TextView) findViewById(R.id.statusText);
      statusText.setText("Data: " + result);
      (new Thread(new workerThread(glucoseValue))).start();
    }
  }
}
