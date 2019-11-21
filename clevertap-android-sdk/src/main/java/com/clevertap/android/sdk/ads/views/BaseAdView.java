package com.clevertap.android.sdk.ads.views;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.clevertap.android.sdk.ads.CTAdUnit;

public abstract class BaseAdView extends FrameLayout {
    public BaseAdView(@NonNull Context context) {
        super(context);
    }

    public BaseAdView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BaseAdView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    abstract void populateAdView(CTAdUnit adUnit);
}