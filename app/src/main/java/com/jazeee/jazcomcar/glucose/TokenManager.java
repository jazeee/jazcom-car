package com.jazeee.jazcomcar.glucose;

import android.os.AsyncTask;

import com.jazeee.jazcomcar.PostHttp;
import com.jazeee.jazcomcar.glucose.ITokenListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TokenManager {
  private final AtomicReference<String> token = new AtomicReference<>(null);
  private final AtomicLong lastRetrieval = new AtomicLong(0);
  private final ITokenListener tokenListener;

  public TokenManager(ITokenListener glucoseListener) {
    this.tokenListener = glucoseListener;
  }

  public void requestTokenAsync(String userName, String password) {
    new RequestTokenTask().execute(userName, password);
  }

  public String getToken() {
    return token.get();
  }

  private class RequestTokenTask extends AsyncTask<String, String, String> {
    protected String doInBackground(String... inputParams) {
      String userName = inputParams[0];
      String password = inputParams[1];
      try {
        String url = "https://share1.dexcom.com/ShareWebServices/Services/General/LoginPublisherAccountByName";
        JSONObject jsonParam = new JSONObject();
        jsonParam.put("applicationId", "d8665ade-9673-4e27-9ff6-92db4ce13d13");
        jsonParam.put("accountName", userName);
        jsonParam.put("password", password);
        return PostHttp.postHttp(url, null, jsonParam);
      } catch (JSONException e) {
        e.printStackTrace();
        return null;
      }
    }

    @Override
    protected void onPostExecute(String result) {
      super.onPostExecute(result);
      boolean isValidToken = result != null;
      if (isValidToken) {
        token.set(result.replace("\"", ""));
        lastRetrieval.set(System.currentTimeMillis());
        result = "Retrieved token" + result.substring(0, 5);
      } else {
        token.set(null);
        result = "Unable to read password";
      }
      tokenListener.onTokenProcessed(isValidToken, result);
    }
  }
}
