package com.example.android.weather.app.sync;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.example.android.weather.app.Utility;
import com.example.android.weather.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;


public class WatchFaceUpdater implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String LOG_TAG = WatchFaceUpdater.class.getSimpleName();

    GoogleApiClient mGoogleApiClient;
    Context mContext;

    // Watch Face config:
    // -------------------------------------------------------------------------------

    private static final String WATCH_FACE_WEATHER_PATH = "/watch_face_weather";

    private static final String WEATHER_ID_KEY = "weatherId";
    private static final String HIGH_TEMP_KEY = "high";
    private static final String LOW_TEMP_KEY = "low";
    private static final String TIMESTAMP_KEY = "timestamp";

    private static final String[] WATCH_FACE_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
    };

    // These indices must match the projection
    private static final int INDEX_WATCH_FACE_WEATHER_ID = 0;
    private static final int INDEX_WATCH_FACE_MAX_TEMP = 1;
    private static final int INDEX_WATCH_FACE_MIN_TEMP = 2;

    public WatchFaceUpdater(Context context) {

        mContext = context;

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult connectionResult) {
                            Log.e(LOG_TAG, "mGoogleApiClient onConnectionFailed(): Failed to connect, with result: " + connectionResult);
                        }
                    })
                    .addApi(Wearable.API)
                    .build();
        }
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(LOG_TAG, "onConnectionSuspended(): Connection to Google API client was suspended");
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(LOG_TAG, "onConnected(): Successfully connected to Google API client");
        sendDataToWearable();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.e(LOG_TAG, "onConnectionFailed(): Failed to connect, with result: " + result);
    }


    public void sendDataToWearable() {

        Cursor cursor;

        if (mGoogleApiClient == null) return;


        String locationQuery = Utility.getPreferredLocation(mContext);

        Uri weatherUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

        // Data obtained from the Content Provider
        cursor = mContext.getContentResolver().query(weatherUri, WATCH_FACE_WEATHER_PROJECTION, null, null, null);

        if (cursor.moveToFirst()) {
            int weatherId = cursor.getInt(INDEX_WATCH_FACE_WEATHER_ID);
            double high = cursor.getDouble(INDEX_WATCH_FACE_MAX_TEMP);
            double low = cursor.getDouble(INDEX_WATCH_FACE_MIN_TEMP);
            long timestamp = System.currentTimeMillis();

            try {

                mGoogleApiClient.connect();

                PutDataMapRequest dataMapReq = PutDataMapRequest.create(WATCH_FACE_WEATHER_PATH);

                dataMapReq.getDataMap().putInt(WEATHER_ID_KEY, weatherId);
                dataMapReq.getDataMap().putDouble(HIGH_TEMP_KEY, high);
                dataMapReq.getDataMap().putDouble(LOW_TEMP_KEY, low);
                dataMapReq.getDataMap().putLong(TIMESTAMP_KEY, timestamp);

                PutDataRequest dataReq = dataMapReq.asPutDataRequest();

                /* Very important for immediate syncing:
                 * Starting on Google Play Services 8.3:
                 * " Non-urgent DataItems may be delayed for up to 30 minutes, but you can expect that
                 * in most cases they will be delivered within a few minutes. Low priority is now the
                 * default, so setUrgent() is needed to obtain the previous timing."
                 */
                dataReq.setUrgent();

                Log.d(LOG_TAG, "Data to be sent:\nweatherId: " + weatherId + " high: " + high + " low: " + low + ", timestamp: " + timestamp);

                PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, dataReq);

                pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (dataItemResult.getStatus().isSuccess()) {
                            Log.d(LOG_TAG, "Data item set: " + dataItemResult.getDataItem().getUri());
                        } else {
                            Log.d(LOG_TAG, "Weather data item could not be set");
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cursor.close();
            }

        }

    }


}
