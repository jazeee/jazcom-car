package com.jazeee.jazcomcar;

import android.util.Log;

public enum GlucoseTrend {
  UP_UP(1),
  UP(2),
  UP_RIGHT(3),
  RIGHT(4),
  DOWN_RIGHT(5),
  DOWN(6),
  DOWN_DOWN(7),
  UNKNOWN(-1),
  ;

  private final int trendValue;

  GlucoseTrend(int trendValue) {
    this.trendValue = trendValue;
  }

  public static GlucoseTrend forTrendValue(String trendValue) {
    try {
      return forTrendValue(Integer.valueOf(trendValue));
    } catch (Exception e){
      e.printStackTrace();
      return UNKNOWN;
    }
  }

  public static GlucoseTrend forTrendValue(int trendValue) {
    for (GlucoseTrend glucoseTrend : values()) {
      if (glucoseTrend.trendValue == trendValue) {
        return glucoseTrend;
      }
    }
    return UNKNOWN;
  }
}
