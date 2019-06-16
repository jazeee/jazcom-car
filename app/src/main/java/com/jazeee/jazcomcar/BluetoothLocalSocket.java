package com.jazeee.jazcomcar;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class BluetoothLocalSocket implements AutoCloseable {
  private final AtomicReference<BluetoothSocket> ref = new AtomicReference<>(null);

  BluetoothLocalSocket(BluetoothDevice bluetoothDevice) throws IOException {
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
