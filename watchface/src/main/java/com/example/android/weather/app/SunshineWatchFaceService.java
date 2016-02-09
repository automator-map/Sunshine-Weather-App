/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.weather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;


/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    private static final String LOG_TAG = SunshineWatchFaceService.class.getSimpleName();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private static final String WATCH_FACE_WEATHER_REQUEST_PATH = "/watch_face_weather_request";

    private static final String WATCH_FACE_WEATHER_PATH = "/watch_face_weather";

    private static final String TIMESTAMP_KEY = "watchface_timestamp";
    private static final String WEATHER_ID_KEY = "weatherId";
    private static final String HIGH_TEMP_KEY = "high";
    private static final String LOW_TEMP_KEY = "low";

    private static final String PREF_WEATHER_ID_KEY = "sp_weatherId";
    private static final String PREF_HIGH_TEMP_KEY = "sp_high";
    private static final String PREF_LOW_TEMP_KEY = "sp_low";


    @Override
    public Engine onCreateEngine() {
        Log.d(LOG_TAG, "Watch Face onCreateEngine");
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        GoogleApiClient mGoogleApiClient;

        Resources mResources;
        Context mContext = SunshineWatchFaceService.this;

        boolean mRegisteredTimeZoneReceiver = false;

        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        Paint mWeatherIconPaint;

        boolean mAmbient;

        float mLineHeight;
        float mDividerSpaceHeight;
        float mWeatherLineHeight;

        // Time and Date
        Time mTime;
        Date mDate;
        Calendar mCalendar;
        SimpleDateFormat mDateFormat;

        // Weather
        Bitmap mWeatherIcon;
        Bitmap mAmbientWeatherIcon;
        String mHighTemp;
        String mLowTemp;
        int mWeatherId;


        // Offsets
        float mXOffset;
        float mYOffset;
        float mDividerYOffset;
        float mTempYOffset;
        float mIconYOffset;
        float mIconXOffset;
        float mYWeatherTopMargin;
        float mXWeatherMargins;
        float mXTempMaxLength;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(Engine.this)
                    .addOnConnectionFailedListener(Engine.this)
                    .build();

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL)
                    .setShowSystemUiTime(false)
                    .build());

            mResources = SunshineWatchFaceService.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(ContextCompat.getColor(mContext, R.color.primary));

            mTextPaint = createTextPaint(ContextCompat.getColor(mContext, R.color.digital_text));
            // Paint.Align.CENTER is used in order to center the text on the watch face more easily
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mDatePaint = createTextPaint(ContextCompat.getColor(mContext, R.color.digital_date));
            mDatePaint.setTextAlign(Paint.Align.CENTER);

            mHighTempPaint = createTextPaint(ContextCompat.getColor(mContext, R.color.weather_high_temp));
            mHighTempPaint.setTextAlign(Paint.Align.CENTER);

            mLowTempPaint = createTextPaint(ContextCompat.getColor(mContext, R.color.weather_low_temp));
            mLowTempPaint.setTextAlign(Paint.Align.CENTER);

            mWeatherIconPaint = new Paint();

            mTime = new Time();

            mLineHeight = mResources.getDimension(R.dimen.digital_line_height);
            mDividerSpaceHeight = mResources.getDimension(R.dimen.separator_line_height);

            mCalendar = Calendar.getInstance();
            mDate = new Date();

            initFormats();
        }

        private void initFormats() {
            mDateFormat = new SimpleDateFormat("EEE MMM dd, yyyy", Locale.getDefault());
            mDateFormat.setCalendar(mCalendar);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);

            if (visible) {
                mGoogleApiClient.connect();

                Log.d(LOG_TAG, "mGoogleApiClient connected");

                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();

                restoreWeatherData(sp);

            } else {

                saveWeatherData(sp);

                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                    Log.d(LOG_TAG, "mGoogleApiClient disconnected");
                }

            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
//            mXOffset = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mTextPaint.setTextSize(textSize);
            mHighTempPaint.setTextSize(resources.getDimension(R.dimen.weather_temp_text_size));
            mLowTempPaint.setTextSize(resources.getDimension(R.dimen.weather_temp_text_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            if (isInAmbientMode()) {
                mBackgroundPaint.setColor(Color.BLACK);
                mDatePaint.setColor(Color.WHITE);
                mLowTempPaint.setColor(Color.WHITE);
                mWeatherIconPaint.setAntiAlias(false);
            } else {
                mBackgroundPaint.setColor(ContextCompat.getColor(mContext, R.color.primary));
                mDatePaint.setColor(ContextCompat.getColor(mContext, R.color.digital_date));
                mLowTempPaint.setColor(ContextCompat.getColor(mContext, R.color.weather_low_temp));
                mWeatherIconPaint.setAntiAlias(true);
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            // Get the offsets from the time and date texts
            mYOffset = canvas.getHeight() / 2 - mLineHeight;
            mXOffset = canvas.getWidth() / 2;
            mDividerYOffset = canvas.getHeight() / 2 + mDividerSpaceHeight;

            mYWeatherTopMargin = 0;
            mXWeatherMargins = 70;

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);

            // Draw the background.
            canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);


            // Date text
            String dateText = mDateFormat.format(mDate).toUpperCase();

            // Date
            canvas.drawText(dateText, mXOffset, mYOffset + mLineHeight, mDatePaint);

            // Draw Divider Line
            canvas.drawLine(bounds.centerX() - 30, mDividerYOffset, bounds.centerX() + 30, mDividerYOffset, mDatePaint);


            if (getPeekCardPosition().isEmpty()) {

                mTempYOffset = mDividerYOffset + mLineHeight + mYWeatherTopMargin;

                // Draw the weather information
                if (mLowTemp != null && mHighTemp != null && mWeatherIcon != null) {

//                    mXTempMaxLength = (mHighTempPaint.measureText(mHighTemp) > mLowTempPaint.measureText(mLowTemp)) ?
//                            mHighTempPaint.measureText(mHighTemp) : mLowTempPaint.measureText(mLowTemp);

//                    mXWeatherMargins += mXTempMaxLength;

                    mWeatherLineHeight = mWeatherIcon.getHeight();

                    mTempYOffset = mDividerYOffset + mWeatherLineHeight + mYWeatherTopMargin;

                    // Minimum Temperature Forecast
                    canvas.drawText(mLowTemp, mXOffset + mXWeatherMargins, mTempYOffset, mLowTempPaint);

                    // Maximum Temperature Forecast
                    canvas.drawText(mHighTemp, mXOffset, mTempYOffset, mHighTempPaint);


                    // Weather Icon Offsets
                    mIconYOffset = mTempYOffset - mWeatherIcon.getHeight()/2 - 15;
                    mIconXOffset = mXOffset - mXWeatherMargins - mWeatherIcon.getWidth()/2;

                    // Icon Forecast Drawing
                    if (!mAmbient) {
                        canvas.drawBitmap(mWeatherIcon, mIconXOffset, mIconYOffset, mWeatherIconPaint);
                    } else {
                        canvas.drawBitmap(mAmbientWeatherIcon, mIconXOffset, mIconYOffset, mWeatherIconPaint);
                    }
                }
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.w(LOG_TAG, "onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
//            requestWeatherData();
        }

        public void requestWeatherData() {

            long timestamp = System.currentTimeMillis();

            PutDataMapRequest dataMapReq = PutDataMapRequest.create(WATCH_FACE_WEATHER_REQUEST_PATH);
            dataMapReq.getDataMap().putLong(TIMESTAMP_KEY, timestamp);


            /* Very important for immediate syncing:
             * Starting on Google Play Services 8.3:
             * " Non-urgent DataItems may be delayed for up to 30 minutes, but you can expect that
             * in most cases they will be delivered within a few minutes. Low priority is now the
             * default, so setUrgent() is needed to obtain the previous timing."
             */

            // If there is no weather data available on the watch face, request an immediate sync.
            if (mHighTemp == null) {
                dataMapReq.setUrgent();
            }

            PutDataRequest dataReq = dataMapReq.asPutDataRequest();

            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, dataReq);

            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(DataApi.DataItemResult dataItemResult) {
                    if (!dataItemResult.getStatus().isSuccess()) {
                        Log.d(LOG_TAG, "Weather data update request not sent to phone");
                    } else {
                        Log.d(LOG_TAG, "Weather data update request sent successfully");
                    }
                }
            });

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {

            Log.d(LOG_TAG, "onDataChanged called");

            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    // DataItem changed
                    DataItem item = event.getDataItem();
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    String path = event.getDataItem().getUri().getPath();

                    if (path.equals(WATCH_FACE_WEATHER_PATH)) {
                        if (dataMap.containsKey(WEATHER_ID_KEY)) {
                            mWeatherId = dataMap.getInt(WEATHER_ID_KEY);

                            Log.d(LOG_TAG, "mWeatherId: " + mWeatherId);

                            mWeatherIcon = getIconFromWeatherId(mWeatherId);
                            initAmbientWeatherIconBitmap();
                        }

                        if (dataMap.containsKey(LOW_TEMP_KEY)) {
                            mLowTemp = SunshineWatchFaceUtil.formatTemperature(mContext, dataMap.getDouble(LOW_TEMP_KEY));
                            Log.d(LOG_TAG, "mLowTemp: " + mLowTemp);
                        }

                        if (dataMap.containsKey(HIGH_TEMP_KEY)) {
                            mHighTemp = SunshineWatchFaceUtil.formatTemperature(mContext, dataMap.getDouble(HIGH_TEMP_KEY));
                            Log.d(LOG_TAG, "mHighTemp: " + mHighTemp);
                        }

                        invalidate();
                    }
                }
            }

        }

        public Bitmap getIconFromWeatherId(int weatherId) {
            Drawable icon = getResources().getDrawable(SunshineWatchFaceUtil.getIconResourceForWeatherCondition(weatherId));
            if (icon != null) {
                return ((BitmapDrawable) icon).getBitmap();
            } else {
                Log.d(LOG_TAG,"Couldn't load icon. Check mWeatherId");
                return null;
            }
        }

        private void saveWeatherData(SharedPreferences sp) {
            if (mHighTemp != null && mLowTemp != null && mWeatherId != -1) {
                SharedPreferences.Editor spe = sp.edit();
                spe.putString(PREF_HIGH_TEMP_KEY, mHighTemp);
                spe.putString(PREF_LOW_TEMP_KEY, mLowTemp);
                spe.putInt(PREF_WEATHER_ID_KEY, mWeatherId);
                spe.apply();
            }
        }

        private void restoreWeatherData(SharedPreferences sp) {
            mHighTemp = sp.getString(PREF_HIGH_TEMP_KEY, null);
            mLowTemp = sp.getString(PREF_LOW_TEMP_KEY, null);
            mWeatherId = sp.getInt(PREF_WEATHER_ID_KEY, -1);

            if (mWeatherId != -1) {
                mWeatherIcon = getIconFromWeatherId(mWeatherId);
                initAmbientWeatherIconBitmap();
            }
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(LOG_TAG, "Connection Suspended");
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.d(LOG_TAG, "Connection Failed");
        }

        private void initAmbientWeatherIconBitmap() {
            if (mWeatherIcon != null) {
                mAmbientWeatherIcon = Bitmap.createBitmap(
                        mWeatherIcon.getWidth(),
                        mWeatherIcon.getHeight(),
                        Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(mAmbientWeatherIcon);
                Paint blackAndWhitePaint = new Paint();
                ColorMatrix colorMatrix = new ColorMatrix();
                colorMatrix.setSaturation(0);
                ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
                blackAndWhitePaint.setColorFilter(filter);
                canvas.drawBitmap(mWeatherIcon, 0, 0, blackAndWhitePaint);
            }
        }
    }

        private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
