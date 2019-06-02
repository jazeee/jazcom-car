package com.jazeee.jazcomcar;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

public class PostHttp {
  private static String getQuery(Map<String, String> params) throws UnsupportedEncodingException {
    String result = "";
    boolean first = true;
    for(Map.Entry<String, String> entry : params.entrySet()){
      if (!result.isEmpty()) {
        result += "&";
      }
      result += (URLEncoder.encode(entry.getKey(), "UTF-8"));
      result += ("=");
      result += (URLEncoder.encode(entry.getValue(), "UTF-8"));
    }
    Log.i("Params", result);
    return result;
  }

  public static String postHttp(String urlPath, Map<String, String> params, JSONObject jsonParams) {
    HttpURLConnection connection = null;
    BufferedReader reader = null;

    try {
      if (params != null) {
        urlPath += "?" + getQuery(params);
      }
      URL url = new URL(urlPath);
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/json");
      connection.setRequestProperty("Accept","application/json");
      connection.setDoOutput(true);
      connection.setDoInput(true);
      connection.connect();

      try (OutputStream outputStream = connection.getOutputStream()) {
//        if (params != null) {
//          OutputStreamWriter osw = new OutputStreamWriter(outputStream, "UTF-8");
//          try (BufferedWriter bufferedWriter = new BufferedWriter(osw)) {
//            bufferedWriter.write(getQuery(params));
//            bufferedWriter.flush();
//          }
//        }
        try (DataOutputStream os = new DataOutputStream(outputStream)) {
          if (jsonParams != null) {
            os.writeBytes(jsonParams.toString());
          } else {
            os.writeBytes("");
          }
          os.flush();
        }
      }
      Log.i("STATUS", String.valueOf(connection.getResponseCode()));
      Log.i("RESPONSE_MESSAGE", connection.getResponseMessage());
      String body = "";
      try (BufferedReader in = new BufferedReader(
          new InputStreamReader(
              connection.getInputStream()))) {
        String line;
        while ((line = in.readLine()) != null) {
          body += line;
        }
      }
      return body;
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
      try {
        if (reader != null) {
          reader.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return null;
  }
}
