package com.clevertap.android.sdk.ab_testing.uieditor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.DisplayMetrics;
import android.util.JsonWriter;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.clevertap.android.sdk.CleverTapInstanceConfig;
import com.clevertap.android.sdk.Logger;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class SnapshotBuilder {

    final static class ViewSnapshotConfig {
        ResourceIds resourceIds;
        final List<ViewProperty> propertyDescriptionList;

        ViewSnapshotConfig(List<ViewProperty> propertyDescriptions, ResourceIds resourceIds) {
            this.resourceIds = resourceIds;
            this.propertyDescriptionList = propertyDescriptions;
        }
    }
    private static final int MAX_CLASS_CACHE_SIZE = 255;
    private static final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private static final RootViewsGenerator rootViewsGenerator = new RootViewsGenerator();
    static private final ClassCache classCache = new ClassCache(MAX_CLASS_CACHE_SIZE);

    private static class ClassCache extends LruCache<Class<?>, String> {
        ClassCache(int maxSize) {
            super(maxSize);
        }
        @Override
        protected String create(Class<?> klass) {
            return klass.getCanonicalName();
        }
    }

    private static Logger getConfigLogger(CleverTapInstanceConfig config){
        return config.getLogger();
    }

    private static String getAccountId(CleverTapInstanceConfig config){
        return config.getAccountId();
    }

    static void writeSnapshot(ViewSnapshotConfig snapshotConfig, UIEditor.ActivitySet liveActivities, OutputStream out, CleverTapInstanceConfig config) throws IOException {

        rootViewsGenerator.findInActivities(liveActivities);
        final FutureTask<List<RootView>> rootViewsFuture = new FutureTask<>(rootViewsGenerator);
        mainThreadHandler.post(rootViewsFuture);

        final OutputStreamWriter writer = new OutputStreamWriter(out);
        List<RootView> rootViewList = Collections.emptyList();
        writer.write("[");

        try {
            rootViewList = rootViewsFuture.get(1, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            getConfigLogger(config).debug(getAccountId(config),"Screenshot interrupted.", e);
        } catch (final TimeoutException e) {
            getConfigLogger(config).debug(getAccountId(config), "Screenshot timed out.", e);
        } catch (final ExecutionException e) {
            getConfigLogger(config).verbose(getAccountId(config),"Screenshot error", e);
        }

        final int viewCount = rootViewList.size();
        for (int i = 0; i < viewCount; i++) {
            if (i > 0) {
                writer.write(",");
            }
            final RootView rootView = rootViewList.get(i);
            writer.write("{");
            writer.write("\"activity\":");
            writer.write(JSONObject.quote(rootView.activityName));
            writer.write(",");
            writer.write("\"scale\":");
            writer.write(String.format("%s", rootView.scale));
            writer.write(",");
            writer.write("\"serialized_objects\":");
            {
                final JsonWriter j = new JsonWriter(writer);
                j.beginObject();
                j.name("rootObject").value(rootView.rootView.hashCode());
                j.name("objects");
                viewHierarchySnapshot(j, rootView.rootView, snapshotConfig);
                j.endObject();
                j.flush();
            }
            writer.write(",");
            writer.write("\"screenshot\":");
            writer.flush();
            rootView.screenshot.writeJSON(Bitmap.CompressFormat.PNG, 100, out);
            writer.write("}");
        }

        writer.write("]");
        writer.flush();
    }

    private static void viewHierarchySnapshot(JsonWriter j, View rootView, ViewSnapshotConfig snapshotConfig) throws IOException {
        j.beginArray();
        viewSnapshot(j, rootView, snapshotConfig);
        j.endArray();
    }

    private static void viewSnapshot(JsonWriter j, View view, ViewSnapshotConfig snapshotConfig) throws IOException {
        final int viewId = view.getId();
        final String viewIdName;
        if (viewId == -1) {
            viewIdName = null;
        } else {
            viewIdName = snapshotConfig.resourceIds.nameForId(viewId);
        }

        j.beginObject();
        j.name("hashCode").value(view.hashCode());
        j.name("id").value(viewId);
        j.name("ct_id_name").value(viewIdName);

        final CharSequence description = view.getContentDescription();
        if (null == description) {
            j.name("contentDescription").nullValue();
        } else {
            j.name("contentDescription").value(description.toString());
        }

        final Object tag = view.getTag();
        if (null == tag) {
            j.name("tag").nullValue();
        } else if (tag instanceof CharSequence) {
            j.name("tag").value(tag.toString());
        }

        j.name("top").value(view.getTop());
        j.name("left").value(view.getLeft());
        j.name("width").value(view.getWidth());
        j.name("height").value(view.getHeight());
        j.name("scrollX").value(view.getScrollX());
        j.name("scrollY").value(view.getScrollY());
        j.name("visibility").value(view.getVisibility());

        float translationX;
        float translationY;
        translationX = view.getTranslationX();
        translationY = view.getTranslationY();

        j.name("translationX").value(translationX);
        j.name("translationY").value(translationY);

        j.name("classes");
        j.beginArray();
        Class<?> klass = view.getClass();
        do {
            j.value(classCache.get(klass));
            klass = klass.getSuperclass();
        } while (klass != Object.class && klass != null);
        j.endArray();

        addProperties(j, view, snapshotConfig);

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof RelativeLayout.LayoutParams) {
            RelativeLayout.LayoutParams relativeLayoutParams = (RelativeLayout.LayoutParams) layoutParams;
            int[] rules = relativeLayoutParams.getRules();
            j.name("layoutRules");
            j.beginArray();
            for (int rule : rules) {
                j.value(rule);
            }
            j.endArray();
        }

        j.name("subviews");
        j.beginArray();
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            final int childCount = group.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = group.getChildAt(i);
                // child can be null when views are getting disposed.
                if (null != child) {
                    j.value(child.hashCode());
                }
            }
        }
        j.endArray();
        j.endObject();

        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            final int childCount = group.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = group.getChildAt(i);
                // child can be null when views are getting disposed.
                if (null != child) {
                    viewSnapshot(j, child, snapshotConfig);
                }
            }
        }
    }

    private static void addProperties(JsonWriter j, View v, ViewSnapshotConfig snapshotConfig) throws IOException {
        final Class<?> viewClass = v.getClass();
        for (final ViewProperty desc : snapshotConfig.propertyDescriptionList) {
            if (desc.target.isAssignableFrom(viewClass) && null != desc.accessor) {
                final Object value = desc.accessor.invokeMethod(v);
                //noinspection StatementWithEmptyBody
                if (null == value) {
                    // no-op
                }  else if (value instanceof Boolean) {
                    j.name(desc.name).value((Boolean) value);
                } else if (value instanceof Number) {
                    j.name(desc.name).value((Number) value);
                } else if (value instanceof ColorStateList) {
                    j.name(desc.name).value((Integer) ((ColorStateList) value).getDefaultColor());
                } else if (value instanceof Drawable) {
                    final Drawable drawable = (Drawable) value;
                    final Rect bounds = drawable.getBounds();
                    j.name(desc.name);
                    j.beginObject();
                    j.name("classes");
                    j.beginArray();
                    Class klass = drawable.getClass();
                    while (klass != Object.class) {
                        if (klass != null) {
                            j.value(klass.getCanonicalName());
                            klass = klass.getSuperclass();
                        }
                    }
                    j.endArray();
                    j.name("dimensions");
                    j.beginObject();
                    j.name("left").value(bounds.left);
                    j.name("right").value(bounds.right);
                    j.name("top").value(bounds.top);
                    j.name("bottom").value(bounds.bottom);
                    j.endObject();
                    if (drawable instanceof ColorDrawable) {
                        final ColorDrawable colorDrawable = (ColorDrawable) drawable;
                        j.name("color").value(colorDrawable.getColor());
                    }
                    j.endObject();
                } else {
                    j.name(desc.name).value(value.toString());
                }
            }
        }
    }

    private static class RootView {
        RootView(String activityName, View rootView) {
            this.activityName = activityName;
            this.rootView = rootView;
            this.screenshot = null;
            this.scale = 1.0f;
        }

        final String activityName;
        final View rootView;
        Screenshot screenshot;
        float scale;
    }

    private static class Screenshot {
        private Bitmap cachedScreenshot;
        private final Paint paint;

        Screenshot() {
            cachedScreenshot = null;
            paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        }

        @SuppressWarnings("SameParameterValue")
        synchronized void regenerate(int width, int height, int destDensity, Bitmap source) {
            if (null == cachedScreenshot || cachedScreenshot.getWidth() != width || cachedScreenshot.getHeight() != height) {
                try {
                    cachedScreenshot = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                } catch (final OutOfMemoryError e) {
                    cachedScreenshot = null;
                }

                if (cachedScreenshot != null) {
                    cachedScreenshot.setDensity(destDensity);
                }
            }

            if (cachedScreenshot != null) {
                final Canvas scaledCanvas = new Canvas(cachedScreenshot);
                scaledCanvas.drawBitmap(source, 0, 0, paint);
            }
        }

        @SuppressWarnings({"SameParameterValue", "unused"})
        synchronized void writeJSON(Bitmap.CompressFormat format, int quality, OutputStream out) throws IOException {
            if (cachedScreenshot == null || cachedScreenshot.getWidth() == 0 || cachedScreenshot.getHeight() == 0) {
                out.write("null".getBytes());
            } else {
                out.write('"');
                final Base64OutputStream imageOut = new Base64OutputStream(out, Base64.NO_WRAP);
                cachedScreenshot.compress(Bitmap.CompressFormat.PNG, 100, imageOut);
                imageOut.flush();
                out.write('"');
            }
        }
    }

    private static class RootViewsGenerator implements Callable<List<RootView>> {
        private UIEditor.ActivitySet liveActivities;
        private final List<RootView> rootViews;
        private final DisplayMetrics displayMetrics;
        private final Screenshot cachedBitmap;

        private final int clientDensity = DisplayMetrics.DENSITY_DEFAULT;

        RootViewsGenerator() {
            displayMetrics = new DisplayMetrics();
            rootViews = new ArrayList<>();
            cachedBitmap = new Screenshot();
        }

        void findInActivities(UIEditor.ActivitySet liveActivities) {
            this.liveActivities = liveActivities;
        }

        @Override
        public List<RootView> call() throws Exception {
            rootViews.clear();

            final Set<Activity> liveActivities = this.liveActivities.getAll();

            for (final Activity a : liveActivities) {
                final String activityName = a.getClass().getCanonicalName();
                final View rootView = a.getWindow().getDecorView().getRootView();
                a.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                final RootView info = new RootView(activityName, rootView);
                rootViews.add(info);
            }

            final int viewCount = rootViews.size();
            for (int i = 0; i < viewCount; i++) {
                final RootView info = rootViews.get(i);
                takeScreenshot(info);
            }

            return rootViews;
        }

        private void takeScreenshot(final RootView root) {
            final View rootView = root.rootView;
            Bitmap rawBitmap = null;

            try {
                @SuppressLint("PrivateApi")
                final Method createSnapshot = View.class.getDeclaredMethod("createSnapshot", Bitmap.Config.class, Integer.TYPE, Boolean.TYPE);
                createSnapshot.setAccessible(true);
                rawBitmap = (Bitmap) createSnapshot.invoke(rootView, Bitmap.Config.RGB_565, Color.WHITE, false);
            } catch (final NoSuchMethodException e) {
                Logger.v("Can't call createSnapshot, will use drawCache");
            } catch (final IllegalArgumentException e) {
                Logger.v("Can't call createSnapshot with arguments");
            } catch (final InvocationTargetException e) {
                Logger.v("Exception when calling createSnapshot", e.getLocalizedMessage());
            } catch (final IllegalAccessException e) {
                Logger.v("Can't access createSnapshot, using drawCache");
            } catch (final ClassCastException e) {
                Logger.v("createSnapshot didn't return a bitmap?", e.getLocalizedMessage());
            }

            Boolean originalCacheState = null;
            try {
                if (null == rawBitmap) {
                    originalCacheState = rootView.isDrawingCacheEnabled();
                    rootView.setDrawingCacheEnabled(true);
                    rootView.buildDrawingCache(true);
                    rawBitmap = rootView.getDrawingCache();
                }
            } catch (final RuntimeException e) {
                Logger.v("Can't take a bitmap snapshot of view " + rootView + ", skipping for now.", e);
            }

            float scale = 1.0f;
            if (null != rawBitmap) {
                final int rawDensity = rawBitmap.getDensity();

                if (rawDensity != Bitmap.DENSITY_NONE) {
                    scale = ((float) clientDensity) / rawDensity;
                }

                final int rawWidth = rawBitmap.getWidth();
                final int rawHeight = rawBitmap.getHeight();
                final int destWidth = (int) ((rawBitmap.getWidth() * scale) + 0.5);
                final int destHeight = (int) ((rawBitmap.getHeight() * scale) + 0.5);

                if (rawWidth > 0 && rawHeight > 0 && destWidth > 0 && destHeight > 0) {
                    cachedBitmap.regenerate(destWidth, destHeight, clientDensity, rawBitmap);
                }
            }

            if (null != originalCacheState && !originalCacheState) {
                rootView.setDrawingCacheEnabled(false);
            }
            root.scale = scale;
            root.screenshot = cachedBitmap;
        }
    }

}