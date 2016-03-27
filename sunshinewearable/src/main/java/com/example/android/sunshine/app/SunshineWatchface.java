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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.example.android.sunshine.app.DataLayerService.LOGD;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchface extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    public static final String TAG = "SunshineWatchface";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    public static final char DEG_SYMBOL = 0x0b0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchface.Engine> mWeakReference;

        public EngineHandler(SunshineWatchface.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchface.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataLayerService.DataLayerServiceCallbacks {

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        private Paint mBackgroundPaint;
        private Paint mClockPaint;
        private Paint mDatePaint;
        private Paint mSeparatorPaint;
        private Paint mWeatherStatusPaint;
        private Paint mHighPaint;
        private Paint mLowPaint;

        private Rect mCalendarPaintBounds;
        private Rect mClockTextRect;
        private Rect mHighLowTextRect;
        private Rect mLowTextRect;

        private Date mDate;
        private SimpleDateFormat mCalendarTextFormat;

        boolean mAmbient;
        private Time mTime;

        private String mHighText;
        private String mLowText;

        private int mSubtextColor;

        private float mYOffset;
        private float mYSpacing; // Spacing between each row of painted elements.
        private float mHRWidth;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        private Bitmap mStatusIcon;
        private DataLayerService mDataLayerService;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            mDataLayerService = new DataLayerService(SunshineWatchface.this, this);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchface.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchface.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYSpacing = resources.getDimension(R.dimen.digital_y_margin);
            mHRWidth = resources.getDimension(R.dimen.horizontal_rule_width);
            mSubtextColor = resources.getColor(R.color.digital_text_subtext);

            mCalendarTextFormat = new SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault());
            mTime = new Time();
            mDate = new Date();

            mBackgroundPaint = new Paint();
            mClockPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text_subtext));
            mSeparatorPaint = new Paint();
            mWeatherStatusPaint = new Paint();
            mHighPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mLowPaint = createTextPaint(mSubtextColor);

            mClockTextRect = new Rect();
            mCalendarPaintBounds = new Rect();
            mLowTextRect = new Rect();
            mHighLowTextRect = new Rect();

            mBackgroundPaint.setColor(resources.getColor(R.color.background));
            mSeparatorPaint.setColor(resources.getColor(R.color.digital_text_subtext));
            mSeparatorPaint.setStyle(Paint.Style.FILL);

            //TODO: Default is snow, this needs to come from dataapi in companion app.
            mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear);
            mStatusIcon = Bitmap.createScaledBitmap(mStatusIcon, 60, 60, false); // Sets consistent size

            //TODO: hard coded temp values.  This needs to come from dataapi in companion app.
            mHighText = "43" + DEG_SYMBOL;
            mLowText = "39" + DEG_SYMBOL;

            //TODO: Update screen shot for watchface picker.
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

            if (visible) {
                mDataLayerService.connectAsync();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
//                mDate.setTimeZone(TimeZone.getDefault());
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                mDataLayerService.disconnect();
                unregisterReceiver();
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
            SunshineWatchface.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchface.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchface.this.getResources();
            boolean isRound = insets.isRound();
            mClockPaint.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size));
            mDatePaint.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_subtext_size_round : R.dimen.digital_subtext_size));
            mHighPaint.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_temps_size_round : R.dimen.digital_temps_size));
            mLowPaint.setTextSize(resources.getDimension(isRound
                    ? R.dimen.digital_temps_size_round : R.dimen.digital_temps_size));
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
                    mClockPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SunshineWatchface.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    mBackgroundPaint.setColor(resources.getColor(R.color.background2));
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    mBackgroundPaint.setColor(resources.getColor(R.color.background));
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mBackgroundPaint.setColor(resources.getColor(R.color.background));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mDate.setTime(System.currentTimeMillis());
            float y = mYOffset;
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Set and draw the clock.
            mTime.setToNow();
            String clockText = mTime.format("%l:%M%p");
            mClockPaint.getTextBounds(clockText, 0, clockText.length(), mClockTextRect);
            float x = (canvas.getWidth() / 2) - (mClockTextRect.centerX());
            canvas.drawText(clockText, x, y, mClockPaint);
            y += mCalendarPaintBounds.height() + mYSpacing;

            // Draw subtext (change color if inAmbientMode
            if (!isInAmbientMode()) {
                mDatePaint.setColor(mSubtextColor);
            } else {
                mDatePaint.setColor(Color.WHITE);
            }
            String fakeCalendarDate = mCalendarTextFormat.format(mDate).toUpperCase();
            mDatePaint.getTextBounds(fakeCalendarDate, 0, fakeCalendarDate.length(), mCalendarPaintBounds);
            x = (canvas.getWidth() / 2) - (mCalendarPaintBounds.centerX());
            canvas.drawText(fakeCalendarDate, x, y, mDatePaint);
            y += mYSpacing;

            // Weather status panel.  Only painted if !isInAmbientMode
            if (!isInAmbientMode()) {
                //Horizontal Rule
                x = (canvas.getWidth() / 2) - mHRWidth;
                canvas.drawRect(x, y, (canvas.getWidth() / 2) + mHRWidth, y + 2, mSeparatorPaint);
                y += mYSpacing;

                // Draw the Weather status icon
                mHighPaint.getTextBounds(mHighText, 0, mHighText.length(), mHighLowTextRect);
                mLowPaint.getTextBounds(mLowText, 0, mLowText.length(), mLowTextRect);
                int xPadding = 20;
                x = (canvas.getWidth() / 2) - ((mStatusIcon.getWidth() + mHighLowTextRect.width() + mLowTextRect.width() + (xPadding * 3)) / 2);
                canvas.drawBitmap(mStatusIcon, x, y, mWeatherStatusPaint);
                y += mStatusIcon.getHeight() - 20;
                x += mStatusIcon.getWidth() + xPadding;

                // Draw high/low status text
                canvas.drawText(mHighText, x, y, mHighPaint);
                x += mHighLowTextRect.width() + xPadding;
                canvas.drawText(mLowText, x, y, mLowPaint);
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
        public void onDataUpdated(DataItem dataItem) {
            if (dataItem != null) {
                DataMap map = DataMapItem.fromDataItem(dataItem).getDataMap();
                parseStatusIcon(map.getInt("icon-id"));
                mHighText = map.getString("max");
                mLowText = map.getString("min");
                String test = map.getString("test");
                LOGD(TAG, "updateUIWithData -> DataMap: max: " + mHighText + ", min: " + mLowText + ", test: " + test);
            }
        }

        private void parseStatusIcon(int weatherId) {
            if (weatherId >= 200 && weatherId <= 232) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_storm);
            } else if (weatherId >= 300 && weatherId <= 321) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_light_rain);
            } else if (weatherId >= 500 && weatherId <= 504) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_rain);
            } else if (weatherId == 511) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_snow);
            } else if (weatherId >= 520 && weatherId <= 531) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_rain);
            } else if (weatherId >= 600 && weatherId <= 622) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_snow);
            } else if (weatherId >= 701 && weatherId <= 761) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_fog);
            } else if (weatherId == 761 || weatherId == 781) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_storm);
            } else if (weatherId == 800) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear);
            } else if (weatherId == 801) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_light_clouds);
            } else if (weatherId >= 802 && weatherId <= 804) {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_clouds);
            } else {
                mStatusIcon = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear);
            }
            mStatusIcon = Bitmap.createScaledBitmap(mStatusIcon, 60, 60, false); // Sets consistent size
        }
    }
}
