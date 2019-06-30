package com.jazeee.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.jazeee.logger.ILogger;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BluetoothService extends Service implements ILogger {
  public static final String MESSAGE_NAME = "BLUETOOTH_MESSAGE";
  public static final String ADD_LOG_NAME = "BLUETOOTH_SERVICE_LOG";

  private Map<String, BluetoothDevice> bluetoothDevices = new HashMap<>();
  private Map<String, BluetoothLocalSocket> localSockets = new HashMap<>();


  public class LocalBinder extends Binder {
    public BluetoothService getService() {
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
    for(BluetoothLocalSocket localSocket : localSockets.values()) {
      localSocket.close();
    }
    localSockets.clear();
    bluetoothDevices.clear();

    Set<BluetoothDevice> matchingBondedDevices = getMatchingBondedDevices();
    for (BluetoothDevice device : matchingBondedDevices) {
      bluetoothDevices.put(device.getAddress(), device);
    }
    for (String address: bluetoothDevices.keySet()) {
      BluetoothDevice bluetoothDevice = bluetoothDevices.get(address);
      BluetoothLocalSocket localSocket = new BluetoothLocalSocket(bluetoothDevice, this);
      localSockets.put(address, localSocket);
    }
  }

  private static Set<BluetoothDevice> getMatchingBondedDevices() {
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
    Set<BluetoothDevice> matchingDevices = new HashSet<>();
    for (BluetoothDevice device : pairedDevices) {
      Log.d("Found device: ", device.getName() + "" + device.getAddress());
      if (device.getName().startsWith("JazComBT0")) {
        Log.i("JazComCar Name: ", device.getName());
        matchingDevices.add(device);
      }
    }
    return matchingDevices;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(getClass().getCanonicalName(), "Received start id " + startId + ": " + intent);
    if (intent != null) {
      String message = intent.getStringExtra(MESSAGE_NAME);
      Log.i(getClass().getCanonicalName(), "Got message " + message);
      sendBluetoothMessage(message);
    }

    return START_NOT_STICKY;
  }

  private void sendBluetoothMessage(String message) {
    final String updatedMessage = "JAZ:" + message;
    for (final String address : localSockets.keySet()) {
      final Thread thread = new Thread() {
        @Override
        public void run() {
          try {
            BluetoothLocalSocket localSocket = localSockets.get(address);
            localSocket.sendBluetoothMessage(updatedMessage.getBytes());
            addLog("Sent to " + address);
          } catch (IOException e) {
            e.printStackTrace();
            addLog(address + " Failed " + e.getMessage());
          }
        }
      };
      thread.start();
    }
  }

  public void addLog(String logMessage) {
    Intent intent = new Intent(ADD_LOG_NAME);
    intent.putExtra("message", logMessage);
    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
  }

  @Override
  public void onDestroy() {
    Log.i(getClass().getCanonicalName(), "onDestroy");
    for (BluetoothLocalSocket localSocket : localSockets.values()) {
      localSocket.close();
    }
  }
}
