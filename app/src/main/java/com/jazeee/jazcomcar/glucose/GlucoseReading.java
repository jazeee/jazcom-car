package com.jazeee.jazcomcar.glucose;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONException;
import org.json.JSONObject;

public class GlucoseReading implements Parcelable {
  private final String glucoseValue;
  private final String trend;
  private final String dateStamp;

  public GlucoseReading(JSONObject output) throws JSONException {
    this.glucoseValue = output.getString("Value");
    this.trend = output.getString("Trend");
    this.dateStamp = output.getString("ST"); // DT, WT?
  }

  protected GlucoseReading(Parcel in) {
    glucoseValue = in.readString();
    trend = in.readString();
    dateStamp = in.readString();
  }

  public static final Creator<GlucoseReading> CREATOR = new Creator<GlucoseReading>() {
    @Override
    public GlucoseReading createFromParcel(Parcel in) {
      return new GlucoseReading(in);
    }

    @Override
    public GlucoseReading[] newArray(int size) {
      return new GlucoseReading[size];
    }
  };

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
  public int describeContents() {
    return 0;
  }

  // write your object's data to the passed-in Parcel
  @Override
  public void writeToParcel(Parcel parcel, int flags) {
    parcel.writeString(glucoseValue);
    parcel.writeString(trend);
    parcel.writeString(dateStamp);
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
