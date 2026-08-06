package app.morphe.extension.boostforreddit.giphy;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Preserves the resolved inline-preview slot while Boost recycles comment rows.
 *
 * <p>The existing preview implementation intentionally uses {@code WRAP_CONTENT}
 * so Glide can resolve the media aspect ratio. A recycled holder creates a fresh
 * ImageView, however, and temporarily collapses that slot until Glide resolves
 * the drawable again. Cache the resolved geometry against the active comment
 * model and restore it synchronously on the next bind.</p>
 */
public final class StableInlinePreviewHeight {
    private static final String LOG_TAG = "InlineGiphy";
    private static final String PREVIEW_TAG =
            "morphe_boost_inline_giphy_preview";
    private static final String PREVIEW_CONTENT_DESCRIPTION =
            "Inline media preview";
    private static final String CONTRACT_MARKER =
            "MORPHE_BOOST_INLINE_MEDIA_STABLE_RECYCLED_HEIGHT_ISSUE164_V1";

    private static final Map<Object, PreviewGeometry> CACHED_GEOMETRIES =
            new WeakHashMap<>();

    private StableInlinePreviewHeight() {
    }

    private static final class PreviewGeometry {
        final int contentSignature;
        final int widthPx;
        final int heightPx;

        PreviewGeometry(int contentSignature, int widthPx, int heightPx) {
            this.contentSignature = contentSignature;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
        }
    }

    public static void bind(
            Object holder,
            Object commentModel,
            Object glideRequestManager
    ) {
        InlineGiphyCommentPreview.bind(
                holder,
                commentModel,
                glideRequestManager
        );

        try {
            stabilize(holder, commentModel);
        } catch (Throwable throwable) {
            Log.w(
                    LOG_TAG,
                    CONTRACT_MARKER + ": stabilization failed",
                    throwable
            );
        }
    }

    private static void stabilize(Object holder, final Object commentModel) {
        if (holder == null || commentModel == null) return;

        View itemView = getItemView(holder);
        if (!(itemView instanceof ViewGroup)) return;

        View preview = findTaggedPreview((ViewGroup) itemView);
        if (!(preview instanceof ViewGroup)) return;

        final ImageView imageView =
                findPreviewImage((ViewGroup) preview);
        if (imageView == null) return;

        final int contentSignature =
                resolveContentSignature(commentModel);
        restoreCachedGeometry(
                commentModel,
                contentSignature,
                imageView
        );

        imageView.addOnLayoutChangeListener(
                new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(
                            View view,
                            int left,
                            int top,
                            int right,
                            int bottom,
                            int oldLeft,
                            int oldTop,
                            int oldRight,
                            int oldBottom
                    ) {
                        cacheResolvedGeometryIfReady(
                                commentModel,
                                contentSignature,
                                imageView
                        );
                    }
                }
        );

        // Glide may satisfy a memory-cache request synchronously.
        cacheResolvedGeometryIfReady(
                commentModel,
                contentSignature,
                imageView
        );
    }

    private static void restoreCachedGeometry(
            Object commentModel,
            int contentSignature,
            ImageView imageView
    ) {
        PreviewGeometry geometry;
        synchronized (CACHED_GEOMETRIES) {
            geometry = CACHED_GEOMETRIES.get(commentModel);
        }

        if (
                geometry == null
                        || geometry.contentSignature != contentSignature
                        || geometry.widthPx <= 0
                        || geometry.heightPx <= 0
        ) {
            return;
        }

        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        if (params == null) return;

        int targetWidthPx = resolveTargetWidthPx(imageView, params);
        if (targetWidthPx <= 0) return;

        int restoredHeightPx = Math.round(
                geometry.heightPx
                        * (targetWidthPx / (float) geometry.widthPx)
        );
        restoredHeightPx = clampToImageMaximum(
                imageView,
                restoredHeightPx
        );
        if (restoredHeightPx <= 0) return;

        params.height = restoredHeightPx;
        imageView.setLayoutParams(params);

        Log.d(
                LOG_TAG,
                CONTRACT_MARKER
                        + ": restored widthPx=" + targetWidthPx
                        + " heightPx=" + restoredHeightPx
        );
    }

    private static void cacheResolvedGeometryIfReady(
            Object commentModel,
            int contentSignature,
            ImageView imageView
    ) {
        if (
                commentModel == null
                        || imageView == null
                        || imageView.getDrawable() == null
        ) {
            return;
        }

        int widthPx = imageView.getWidth();
        int heightPx = imageView.getHeight();
        if (widthPx <= 0 || heightPx <= 0) return;

        heightPx = clampToImageMaximum(imageView, heightPx);
        if (heightPx <= 0) return;

        PreviewGeometry previous;
        PreviewGeometry resolved = new PreviewGeometry(
                contentSignature,
                widthPx,
                heightPx
        );

        synchronized (CACHED_GEOMETRIES) {
            previous = CACHED_GEOMETRIES.put(
                    commentModel,
                    resolved
            );
        }

        if (
                previous == null
                        || previous.contentSignature != contentSignature
                        || previous.widthPx != widthPx
                        || previous.heightPx != heightPx
        ) {
            Log.d(
                    LOG_TAG,
                    CONTRACT_MARKER
                            + ": cached widthPx=" + widthPx
                            + " heightPx=" + heightPx
            );
        }
    }

    private static int resolveTargetWidthPx(
            ImageView imageView,
            ViewGroup.LayoutParams params
    ) {
        if (params.width > 0) return params.width;
        if (imageView.getWidth() > 0) return imageView.getWidth();

        int maxWidthPx = imageView.getMaxWidth();
        return maxWidthPx > 0 ? maxWidthPx : 0;
    }

    private static int clampToImageMaximum(
            ImageView imageView,
            int heightPx
    ) {
        if (heightPx <= 0) return 0;

        int maximumHeightPx = imageView.getMaxHeight();
        if (
                maximumHeightPx > 0
                        && maximumHeightPx < Integer.MAX_VALUE
        ) {
            return Math.min(heightPx, maximumHeightPx);
        }

        return heightPx;
    }

    private static int resolveContentSignature(Object commentModel) {
        String source = callStringMethod(commentModel, "S0");
        if (source == null) {
            source = callStringMethod(commentModel, "T0");
        }

        return source == null ? 0 : source.hashCode();
    }

    private static String callStringMethod(
            Object target,
            String methodName
    ) {
        if (target == null || methodName == null) return null;

        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static View getItemView(Object holder) {
        if (holder == null) return null;

        Class<?> cursor = holder.getClass();
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField("itemView");
                field.setAccessible(true);
                Object value = field.get(holder);
                return value instanceof View ? (View) value : null;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }

        return null;
    }

    private static View findTaggedPreview(ViewGroup root) {
        if (root == null) return null;
        if (PREVIEW_TAG.equals(root.getTag())) return root;

        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child == null) continue;
            if (PREVIEW_TAG.equals(child.getTag())) return child;

            if (child instanceof ViewGroup) {
                View nested = findTaggedPreview((ViewGroup) child);
                if (nested != null) return nested;
            }
        }

        return null;
    }

    private static ImageView findPreviewImage(ViewGroup root) {
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child == null) continue;

            if (child instanceof ImageView) {
                CharSequence description =
                        child.getContentDescription();
                if (
                        description != null
                                && PREVIEW_CONTENT_DESCRIPTION.contentEquals(
                                description
                        )
                ) {
                    return (ImageView) child;
                }
            }

            if (child instanceof ViewGroup) {
                ImageView nested =
                        findPreviewImage((ViewGroup) child);
                if (nested != null) return nested;
            }
        }

        return null;
    }
}
