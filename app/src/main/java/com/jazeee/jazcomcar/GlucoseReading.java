package com.jazeee.jazcomcar;

import org.json.JSONException;
import org.json.JSONObject;

public class GlucoseReading {
  private final String glucoseValue;
  private final String trend;
  private final String dateStamp;

  public GlucoseReading(JSONObject output) throws JSONException {
    this.glucoseValue = output.getString("Value");
    this.trend = output.getString("Trend");
    this.dateStamp = output.getString("ST"); // DT, WT?
  }

  public String getGlucoseValue() {
    return glucoseValue;
  }

  public GlucoseTrend getTrend() {
    return GlucoseTrend.forTrendValue(trend);
  }

  public String getDateStamp() {
    return dateStamp;
  }

  @Override
  public String toString() {
    return "{" +
        "glucoseValue='" + glucoseValue + '\'' +
        ", trend='" + getTrend().name() + '\'' +
        ", dateStamp='" + dateStamp + '\'' +
        '}';
  }
}
