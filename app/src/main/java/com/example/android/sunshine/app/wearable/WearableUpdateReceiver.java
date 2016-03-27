package com.example.android.sunshine.app.wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import static com.example.android.sunshine.app.wearable.SunshineWearableService.LOGD;

public class WearableUpdateReceiver extends BroadcastReceiver {
    public WearableUpdateReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        LOGD("WearableUpdateReceiver", "onReceiver");
        SunshineWearableService.startActionUpdateWeather(context);
    }
}
