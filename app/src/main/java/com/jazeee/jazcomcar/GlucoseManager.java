package com.jazeee.jazcomcar;

import android.os.AsyncTask;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class GlucoseManager {
  private final IGlucoseListener glucoseListener;
  private final AtomicReference<GlucoseReading> glucoseReading = new AtomicReference<>();

  public GlucoseManager(IGlucoseListener glucoseListener) {
    this.glucoseListener = glucoseListener;
  }

  public void requestGlucoseAsync(String token) {
    new RequestGlucoseTask().execute(token);
  }

  public GlucoseReading getGlucoseReading() {
    return glucoseReading.get();
  }

  private class RequestGlucoseTask extends AsyncTask<String, String, String> {
    protected String doInBackground(String... inputParams) {
      String token = inputParams[0];
      String url = "https://share1.dexcom.com/ShareWebServices/Services/Publisher/ReadPublisherLatestGlucoseValues";
      Map<String, String> params = new HashMap<>();
      params.put("sessionId", token);
      params.put("minutes", "1440");
      params.put("maxCount", "1");
      JSONObject jsonParams = new JSONObject();
      return PostHttp.postHttp(url, params, null);
    }

    @Override
    protected void onPostExecute(String result) {
      super.onPostExecute(result);
      String glucoseValue = "?";
      GlucoseReading glucoseReading = null;
      String log;
      if (result != null) {
        try {
          JSONArray data = new JSONArray(result);
          JSONObject output = data.getJSONObject(0);
          glucoseReading = new GlucoseReading(output);
          log = "Successfully read: " + glucoseReading.toString();
        } catch (JSONException e) {
          e.printStackTrace();
          log = e.getMessage();
        }
      } else {
        log = "Unable to read data";
      }
      glucoseListener.onGlucoseRead(glucoseReading, log);
    }
  }}
