package com.example.android.sunshine.app.wearable;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.CountDownLatch;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class SunshineWearableService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "SunshineWearableService";

    private static final boolean LOGGING_ENABLED = false;

      private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };
    static final int INDEX_WEATHER_CONDITION_ID = 1;
    static final int INDEX_WEATHER_MAX_TEMP = 2;
    static final int INDEX_WEATHER_MIN_TEMP = 3;

    private GoogleApiClient mGoogleApiClient;

    public SunshineWearableService() {
        super("SunshineWearableService");
    }

    public static void startActionUpdateWeather(Context context) {
        LOGD(TAG, "startActionUpdateWeather");
        Intent intent = new Intent(context, SunshineWearableService.class);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        LOGD(TAG, "onHandleIntent called with intent: " + intent);
        if (intent != null) {
            if(mGoogleApiClient == null) {
                mGoogleApiClient = new GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addApi(Wearable.API)
                        .build();
            }
            mGoogleApiClient.blockingConnect();
            LOGD(TAG, "onHandleIntent mGoogleApiClient.blockingConnect(), isConnected?: " + mGoogleApiClient.isConnected());

            String location = Utility.getPreferredLocation(SunshineWearableService.this);
            Uri weatherForLocationUri = WeatherContract.WeatherEntry
                    .buildWeatherLocationWithStartDate(location, System.currentTimeMillis());
            Cursor data = getContentResolver().query(weatherForLocationUri,
                    FORECAST_COLUMNS,
                    null,
                    null,
                    WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
            if (data == null) {
                return;
            }
            if (!data.moveToFirst()) {
                data.close();
                return;
            }
            //Latch to wait for result callback
            final CountDownLatch latch = new CountDownLatch(1);

            int weatherId = data.getInt(INDEX_WEATHER_CONDITION_ID);
            String maxTemp = Utility.formatTemperature(this, data.getDouble(INDEX_WEATHER_MAX_TEMP));
            String minTemp = Utility.formatTemperature(this, data.getDouble(INDEX_WEATHER_MIN_TEMP));

            PutDataMapRequest request = PutDataMapRequest.create("/weather-update");
            request.getDataMap().putInt("icon-id",weatherId);
            request.getDataMap().putString("max", maxTemp);
            request.getDataMap().putLong("timestamp", System.currentTimeMillis());
            request.getDataMap().putString("min", minTemp);
            LOGD(TAG, "request: " + request);
            Wearable.DataApi.putDataItem(mGoogleApiClient,request.asPutDataRequest())
                    .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                        @Override
                        public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                            latch.countDown();
                        }
                    });
            try {
                latch.await();
            } catch (InterruptedException e) { }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        LOGD(TAG, "onConnectionFailed");
    }

    @Override
    public void onConnected(Bundle bundle) {
        LOGD(TAG, "onConnected");
    }

    @Override
    public void onConnectionSuspended(int i) {
        LOGD(TAG, "onConnectionSuspended");
    }

    public static void LOGD(String tag, String msg) {
        if(LOGGING_ENABLED)
            Log.d(tag, "WEARABLE_TAG " + msg);
    }
}
