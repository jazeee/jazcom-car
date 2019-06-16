package com.jazeee.jazcomcar.glucose;

public interface IGlucoseListener {
  public void onGlucoseRead(GlucoseReading glucoseReading, String logMessage);
}
