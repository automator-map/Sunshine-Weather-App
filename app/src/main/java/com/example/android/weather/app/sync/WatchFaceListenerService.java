//package com.example.android.weather.app.sync;
//
//import android.util.Log;
//
//import com.google.android.gms.wearable.DataEvent;
//import com.google.android.gms.wearable.DataEventBuffer;
//import com.google.android.gms.wearable.WearableListenerService;
//
//public class WatchFaceListenerService extends WearableListenerService {
//
//    public static final String LOG_TAG = WatchFaceListenerService.class.getSimpleName();
//
//    private static final String WATCH_FACE_WEATHER_PATH = "/watch_face_weather";
//
//    @Override
//    public void onDataChanged(DataEventBuffer dataEvents) {
//        super.onDataChanged(dataEvents);
//
//        for (DataEvent dataEvent : dataEvents) {
//            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
//                String path = dataEvent.getDataItem().getUri().getPath();
//                if (path.equals(WATCH_FACE_WEATHER_PATH)) {
//                    SunshineSyncAdapter.syncImmediately(this);
//                    Log.d(LOG_TAG, "Weather Update Request received from watchface");
//                }
//            }
//        }
//
//    }
//}
