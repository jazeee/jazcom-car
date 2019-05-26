package com.jazeee.jazcomcar;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity {
  BluetoothDevice bluetoothDevice = null;
  final byte delimiter = 33;
  int readBufferPosition = 0;
  private final AtomicReference<BluetoothSocket> ref = new AtomicReference<>(null);

  private class LocalSocket implements AutoCloseable {
    LocalSocket() throws IOException {
      close();
      UUID uuid = UUID.fromString("8c9d4b09-4106-45b1-9936-67a28cce6070");
      BluetoothSocket bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid);
      if (!bluetoothSocket.isConnected()){
        bluetoothSocket.connect();
      }
      ref.set(bluetoothSocket);
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

  public BluetoothSocket sendBtMsg(BluetoothSocket bluetoothSocket, String msg2send) throws IOException {
    String msg = msg2send;
    OutputStream outputStream = bluetoothSocket.getOutputStream();
    outputStream.write(msg.getBytes());
    return bluetoothSocket;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    final Handler handler = new Handler();

    final TextView myLabel = (TextView) findViewById(R.id.btMessage);
    final Button lightOnButton = (Button) findViewById(R.id.lightOn);
    final Button lightOffButton = (Button) findViewById(R.id.lightOff);

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    final class workerThread implements Runnable {
      private String btMsg;

      public workerThread(String msg) {
        btMsg = msg;
      }

      public void run() {
        try (LocalSocket localSocket = new LocalSocket()){
          final BluetoothSocket bluetoothSocket = localSocket.get();
          sendBtMsg(bluetoothSocket, btMsg);
          while (!Thread.currentThread().isInterrupted()) {
            int bytesAvailable;
            boolean workDone = false;

            final InputStream inputStream = bluetoothSocket.getInputStream();
            bytesAvailable = inputStream.available();
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
    ;
    lightOnButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        (new Thread(new workerThread("lightOn"))).start();
      }
    });
    lightOffButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        (new Thread(new workerThread("lightOff"))).start();
      }
    });

    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableBluetooth, 0);
    }

    Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
    if (pairedDevices.size() > 0) {
      for (BluetoothDevice device : pairedDevices) {
        if (device.getName().equals("pi3")) {
          Log.e("JazComCar Name: ", device.getName());
          bluetoothDevice = device;
          break;
        }
      }
    }
  }
}
