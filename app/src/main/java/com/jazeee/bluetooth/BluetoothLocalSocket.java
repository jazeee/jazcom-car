package com.jazeee.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import com.jazeee.logger.ILogger;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class BluetoothLocalSocket implements AutoCloseable {
  private final ILogger logger;
  private final BluetoothDevice bluetoothDevice;
  private final AtomicReference<BluetoothSocket> bluetoothSocket = new AtomicReference<>(null);
  private final AtomicReference<OutputStream> outputStream = new AtomicReference<>(null);
  private final UUID BT_DEVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

  BluetoothLocalSocket(BluetoothDevice bluetoothDevice, ILogger logger) {
    this.bluetoothDevice = bluetoothDevice;
    this.logger = logger;
  }

  private void addLog(String message) {
    this.logger.addLog(message);
  }

  private BluetoothSocket getBluetoothSocket() throws IOException {
    BluetoothSocket bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(BT_DEVICE_UUID);
    this.bluetoothSocket.set(bluetoothSocket);
    return bluetoothSocket;
  }

  private OutputStream getOutputStream() throws IOException {
    BluetoothSocket bluetoothSocket = this.bluetoothSocket.get();
    OutputStream outputStream = this.outputStream.get();
    if (bluetoothSocket != null && !bluetoothSocket.isConnected()) {
      addLog("Bluetooth Socket is open, but not connected. Closing.");
    }
    if (bluetoothSocket == null || !bluetoothSocket.isConnected()) {
//      if (outputStream != null) {
//        try {
//          outputStream.close();
//        } catch (IOException e) {
//          // ignore
//        }
//        outputStream = null;
//      }
//      if (bluetoothSocket != null) {
//        bluetoothSocket.close();
//      }
      close();
      outputStream = null;
      bluetoothSocket = getBluetoothSocket();
      if (bluetoothSocket != null) {
        addLog("Connecting socket for device: " + bluetoothDevice.getAddress());
        bluetoothSocket.connect();
      }
    }
    if (bluetoothSocket != null && outputStream == null) {
      addLog("Opening stream for device: " + bluetoothDevice.getAddress());
      outputStream = bluetoothSocket.getOutputStream();
      this.outputStream.set(outputStream);
    }
    return outputStream;
  }

  public void sendBluetoothMessage(byte[] message) throws IOException {
    sendBluetoothMessage(message, true);
  }

  private void sendBluetoothMessage(byte[] message, boolean isFirstAttempt) throws IOException {
    addLog("Sending message for device: " + bluetoothDevice.getAddress());
    try {
      OutputStream outputStream = getOutputStream();
      if (outputStream != null && bluetoothSocket.get().isConnected()) {
        outputStream.write(message);
      }
    } catch ( IOException e ) {
      e.printStackTrace();
      addLog(e.getMessage());
      close();
      throw e;
    }
  }

  @Override
  public void close() {
    OutputStream outputStream = this.outputStream.getAndSet(null);
    if (outputStream != null) {
      try {
        outputStream.close();
      } catch (IOException e) {
        e.printStackTrace();
        addLog("Failure to close OutputStream " + bluetoothDevice.getAddress());
        addLog(e.getMessage());
      }
    }
    BluetoothSocket bluetoothSocket = this.bluetoothSocket.getAndSet(null);
    if (bluetoothSocket != null) {
      try {
        bluetoothSocket.close();
      } catch (IOException e) {
        e.printStackTrace();
        addLog("Failure to close Bluetooth socket " + bluetoothDevice.getAddress());
        addLog(e.getMessage());
      }
    }
  }
}
