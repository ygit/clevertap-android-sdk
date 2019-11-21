package com.clevertap.android.sdk.ads.views;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;

import com.clevertap.android.sdk.Utils;
import com.clevertap.android.sdk.ads.CTAdUnit;

public class BannerAdView extends BaseAdView {
    public BannerAdView(@NonNull Context context) {
        super(context);
    }

    public BannerAdView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BannerAdView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    void populateAdView(CTAdUnit adUnit) {
        if (adUnit != null) {

        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = getMeasuredWidth();
        int height = (int) Utils.toPixel(getContext(), 50);
        setMeasuredDimension(width, height);
    }
}