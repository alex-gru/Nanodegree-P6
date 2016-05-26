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

package com.example.android.sunshine.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface BOLD_TYPEFACE =
            Typeface.create("thin", Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create("thin", Typeface.NORMAL);
    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(5);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private float mColonWidth;
    private String mColonString = ":";

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHoursPaint;
        Paint mColonPaint;
        Paint mMinutesPaint;
        Paint mDatePaint;
        Paint mDividerPaint;
        boolean mAmbient;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;
        java.text.DateFormat mDateFormat;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
//                mTime.clear(intent.getStringExtra("time-zone"));
//                mTime.setToNow();
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            }
        };
        float mClockXOffset;
        float mDateXOffset;
        float mYClockOffset;
        float mYDateOffset;
        float mYDividerOffsetToCenter;
        float mDividerLength;
        float mDividerThickness;

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
            mDateFormat = DateFormat.getDateFormat(SunshineWatchFace.this);
            mDateFormat.setCalendar(mCalendar);
        }

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();
            mYClockOffset = resources.getDimension(R.dimen.clock_y_offset);
            mYDateOffset = resources.getDimension(R.dimen.date_y_offset);
            mYDividerOffsetToCenter = resources.getDimension(R.dimen.divider_y_offset_to_center);
            mDividerLength = resources.getDimension(R.dimen.divider_length);
            mDividerThickness = resources.getDimension(R.dimen.divider_thickness);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mHoursPaint = new Paint();
            mColonPaint = new Paint();
            mMinutesPaint = new Paint();
            mHoursPaint = createTextPaint(resources.getColor(R.color.clock_text), BOLD_TYPEFACE);
            mColonPaint = createTextPaint(resources.getColor(R.color.clock_text), NORMAL_TYPEFACE);
            mMinutesPaint = createTextPaint(resources.getColor(R.color.clock_text), NORMAL_TYPEFACE);
            mDatePaint = createTextPaint(resources.getColor(R.color.date_text), NORMAL_TYPEFACE);
            mDividerPaint = new Paint();
            mDividerPaint.setColor(resources.getColor(R.color.divider));

            mCalendar = Calendar.getInstance();
            mDate = new Date();
            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface tf) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(tf);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
//                mTime.clear(TimeZone.getDefault().getID());
//                mTime.setToNow();
            } else {
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
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mClockXOffset = resources.getDimension(R.dimen.clock_x_offset);
            mDateXOffset = resources.getDimension(R.dimen.date_x_offset);
            float textSize = resources.getDimension(R.dimen.clock_text_size);
            float dateTextSize = resources.getDimension(R.dimen.date_text_size);

//            mTextPaint.setTextSize(textSize);
            mHoursPaint.setTextSize(textSize);
            mColonPaint.setTextSize(textSize);
            mMinutesPaint.setTextSize(textSize);
            mDatePaint.setTextSize(dateTextSize);

            mColonWidth = mColonPaint.measureText(mColonString);
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
//                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mHoursPaint.setAntiAlias(!inAmbientMode);
                    mColonPaint.setAntiAlias(!inAmbientMode);
                    mMinutesPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mDividerPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFace.this);

            float x = mClockXOffset;
            String hourString;
            hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));

            canvas.drawText(hourString, x, mYClockOffset, mHoursPaint);
            x += mHoursPaint.measureText(hourString);

            canvas.drawText(mColonString, x, mYClockOffset, mColonPaint);

            x += mColonWidth;

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYClockOffset, mMinutesPaint);

            // Draw the date.
            String dateString = String.format("%s, %s %d %d",
                    mCalendar.getDisplayName(Calendar.DAY_OF_WEEK,Calendar.SHORT,Locale.US).toUpperCase(),
                    mCalendar.getDisplayName(Calendar.MONTH,Calendar.SHORT,Locale.US).toUpperCase(),
                    mCalendar.get(Calendar.DAY_OF_MONTH),
                    mCalendar.get(Calendar.YEAR)
                    );
            canvas.drawText(dateString, mDateXOffset, mYDateOffset, mDatePaint);

            canvas.drawRoundRect(
                    new RectF(bounds.width()/2 - mDividerLength/2,
                            bounds.height()/2 + mYDividerOffsetToCenter,
                            bounds.width()/2 + mDividerLength/2,
                            bounds.height()/2 + mYDividerOffsetToCenter + mDividerThickness),
                    6, 6, mDividerPaint);

        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
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
    }
}
