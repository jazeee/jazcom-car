package com.jazeee.jazcomcar;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class BluetoothService extends Service {
  public static final String MESSAGE_NAME = "BLUETOOTH_MESSAGE";
  public static final String ADD_LOG_NAME = "BLUETOOTH_SERVICE_LOG";

  private BluetoothDevice bluetoothDevice = null;
  private BluetoothLocalSocket localSocket;


  public class LocalBinder extends Binder {
    BluetoothService getService() {
      return BluetoothService.this;
    }
  }
  private IBinder binder = new LocalBinder();

  @Override
  public IBinder onBind(Intent intent) {
    return binder;
  }

  public BluetoothService() {
  }

  @Override
  public void onCreate() {
    Log.i(getClass().getCanonicalName(), "onCreate");
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
    if (pairedDevices.size() > 0) {
      for (BluetoothDevice device : pairedDevices) {
        Log.d("Found device: ", device.getName() + "" + device.getAddress());
        if (device.getName().equals("JazComBT01")) {
          Log.i("JazComCar Name: ", device.getName());
          bluetoothDevice = device;
          break;
        }
      }
    }
    if (bluetoothDevice != null) {
      try {
        localSocket = new BluetoothLocalSocket(bluetoothDevice);
      } catch (IOException e) {
        e.printStackTrace();
        addLog(e.getMessage());
      }
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(getClass().getCanonicalName(), "Received start id " + startId + ": " + intent);
    if (intent != null) {
      String message = intent.getStringExtra(MESSAGE_NAME);
      Log.i(getClass().getCanonicalName(), "Got message " + message);
      try {
        sendBluetoothMessage(message);
      } catch (IOException e) {
        e.printStackTrace();
        addLog(e.getMessage());
      }
    }

    return START_NOT_STICKY;
  }

  private BluetoothSocket sendBluetoothMessage(String message) throws IOException {
    final BluetoothSocket bluetoothSocket = localSocket.reconnectIfNeeded();
    message = "JAZ:" + message;
    OutputStream outputStream = bluetoothSocket.getOutputStream();
    outputStream.write(message.getBytes());
    return bluetoothSocket;
  }

  private void addLog(String logMessage) {
    Intent intent = new Intent(ADD_LOG_NAME);
    intent.putExtra("message", logMessage);
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }

  @Override
  public void onDestroy() {
    Log.i(getClass().getCanonicalName(), "onDestroy");
    localSocket.close();
  }
}
