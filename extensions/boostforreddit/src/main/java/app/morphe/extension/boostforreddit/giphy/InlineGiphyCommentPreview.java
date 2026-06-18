package app.morphe.extension.boostforreddit.giphy;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.WeakHashMap;

public final class InlineGiphyCommentPreview {
    private static final String PREVIEW_TAG = "morphe_boost_inline_giphy_preview";
    private static final Map<Object, PreviewSource> PREVIEW_SOURCES = new WeakHashMap<>();

    private static final Pattern DIRECT_PREVIEW_URL_PATTERN =
            Pattern.compile("https?://(?:external-preview|preview)\\.redd\\.it/[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern DIRECT_GIF_URL_PATTERN =
            Pattern.compile("https?://[^\\s\"'<>]+?\\.gif(?:\\?[^\\s\"'<>)]*)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern[] GIPHY_PATTERNS = new Pattern[] {
            Pattern.compile("!\\[gif\\]\\(giphy\\|([A-Za-z0-9_-]+)\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("giphy\\|([A-Za-z0-9_-]+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("media\\.giphy\\.com/media/([A-Za-z0-9_-]+)/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("giphy\\.com/gifs/(?:[^\\s\"'<>/]+-)?([A-Za-z0-9_-]+)(?:[\\s\"'<>/?#]|$)", Pattern.CASE_INSENSITIVE)
    };

    private InlineGiphyCommentPreview() {
    }

    private static final class PreviewSource {
        final String gifUrl;
        final String sourceUrl;

        PreviewSource(String gifUrl, String sourceUrl) {
            this.gifUrl = gifUrl;
            this.sourceUrl = sourceUrl;
        }
    }

    public static void cleanCommentHtml(Object commentModel) {
        try {
            if (commentModel == null) return;

            PreviewSource previewSource = findPreviewSource(commentModel);
            if (previewSource == null) return;

            PREVIEW_SOURCES.put(commentModel, previewSource);

            replaceGiphyStringFields(commentModel);
        } catch (Throwable throwable) {
        }
    }

    public static void bind(Object holder, Object commentModel, Object glideRequestManager) {
        try {
            if (holder == null || commentModel == null) return;

            PreviewSource previewSource = findPreviewSource(commentModel);
            if (previewSource == null) {
                previewSource = PREVIEW_SOURCES.get(commentModel);
            }
            View itemView = getItemView(holder);

            if (!(itemView instanceof ViewGroup)) {
                return;
            }

            removeExistingPreview((ViewGroup) itemView);

            if (previewSource == null || previewSource.gifUrl == null || previewSource.gifUrl.length() == 0) {
                return;
            }

            final Context context = itemView.getContext();
            final String gifUrl = previewSource.gifUrl;
            final String sourceUrl = previewSource.sourceUrl;

            LinearLayout container = new LinearLayout(context);
            container.setTag(PREVIEW_TAG);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(0, dp(context, 6), 0, dp(context, 4));

            ImageView imageView = new ImageView(context);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 170)
            );

            container.addView(imageView, imageParams);

            TextView label = new TextView(context);
            label.setText("Source: " + sourceUrl);
            label.setTextSize(10f);
            label.setAlpha(0.65f);
            label.setSingleLine(true);

            container.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            View.OnClickListener sourceClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        view.getContext().startActivity(intent);
                    } catch (Throwable throwable) {
                    }
                }
            };

            container.setClickable(false);
            container.setFocusable(false);
            imageView.setClickable(false);
            imageView.setFocusable(false);
            label.setClickable(true);
            label.setFocusable(true);

            label.setOnClickListener(sourceClickListener);

            if (!insertBelowCommentText(holder, (ViewGroup) itemView, container)) return;
            loadWithGlide(context, glideRequestManager, gifUrl, imageView);
            syncWithCommentState(holder);
        } catch (Throwable ignored) {
        }
    }

    public static void syncWithCommentState(Object holder) {
        try {
            View itemView = getItemView(holder);
            if (!(itemView instanceof ViewGroup)) return;

            View preview = findPreview((ViewGroup) itemView);
            if (preview == null) return;

            View commentText = findCommentTextView(holder);
            boolean showPreview = commentText == null || commentText.getVisibility() == View.VISIBLE;

            preview.setVisibility(showPreview ? View.VISIBLE : View.GONE);
            updateRelativeLayoutAnchors(commentText, preview, showPreview);
        } catch (Throwable ignored) {
        }
    }

    private static boolean insertBelowCommentText(Object holder, ViewGroup itemView, View preview) {
        View commentText = findCommentTextView(holder);

        if (!isActualCommentTextView(commentText)) {
            return false;
        }

        if (commentText.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) commentText.getParent();
            int index = parent.indexOfChild(commentText);
            if (index >= 0) {
                if (parent instanceof RelativeLayout) {
                    insertInRelativeLayoutBelowComment((RelativeLayout) parent, commentText, preview);
                } else {
                    parent.addView(preview, Math.min(index + 1, parent.getChildCount()));
                }
                return true;
            }
        }

        return false;
    }

    private static boolean isActualCommentTextView(View view) {
        if (view == null) return false;
        String className = view.getClass().getName();
        return "com.rubenmayayo.reddit.ui.customviews.TableTextView".equals(className)
                || "com.rubenmayayo.reddit.ui.customviews.LinkTextView".equals(className);
    }

    private static void insertInRelativeLayoutBelowComment(RelativeLayout parent, View commentText, View preview) {
        try {
            if (preview.getId() == View.NO_ID) {
                preview.setId(View.generateViewId());
            }

            RelativeLayout.LayoutParams previewParams = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

            if (commentText.getId() != View.NO_ID) {
                previewParams.addRule(RelativeLayout.BELOW, commentText.getId());
            }

            parent.addView(preview, previewParams);

            View expandableLayout = findFirstChildByClassName(parent, "net.cachapa.expandablelayout.ExpandableLayout");
            if (expandableLayout != null && expandableLayout.getLayoutParams() instanceof RelativeLayout.LayoutParams) {
                RelativeLayout.LayoutParams expandableParams =
                        (RelativeLayout.LayoutParams) expandableLayout.getLayoutParams();
                expandableParams.addRule(RelativeLayout.BELOW, preview.getId());
                expandableLayout.setLayoutParams(expandableParams);
            }
        } catch (Throwable throwable) {
            parent.addView(preview);
        }
    }

    private static void updateRelativeLayoutAnchors(View commentText, View preview, boolean showPreview) {
        try {
            if (!(preview.getParent() instanceof RelativeLayout)) return;
            RelativeLayout parent = (RelativeLayout) preview.getParent();

            View expandableLayout = findFirstChildByClassName(parent, "net.cachapa.expandablelayout.ExpandableLayout");
            if (expandableLayout == null) return;
            if (!(expandableLayout.getLayoutParams() instanceof RelativeLayout.LayoutParams)) return;

            RelativeLayout.LayoutParams expandableParams =
                    (RelativeLayout.LayoutParams) expandableLayout.getLayoutParams();

            if (showPreview && preview.getId() != View.NO_ID) {
                expandableParams.addRule(RelativeLayout.BELOW, preview.getId());
            } else if (commentText != null && commentText.getId() != View.NO_ID) {
                expandableParams.addRule(RelativeLayout.BELOW, commentText.getId());
            }

            expandableLayout.setLayoutParams(expandableParams);
            parent.requestLayout();
        } catch (Throwable ignored) {
        }
    }

    private static View findFirstChildByClassName(ViewGroup parent, String className) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child != null && className.equals(child.getClass().getName())) {
                return child;
            }
        }
        return null;
    }

    private static View findCommentTextView(Object holder) {
        View direct = getViewField(holder, "commentTv");
        if (direct != null) return direct;

        Class<?> cls = holder.getClass();
        while (cls != null) {
            Field[] fields = cls.getDeclaredFields();
            for (Field field : fields) {
                try {
                    if (!View.class.isAssignableFrom(field.getType())) continue;

                    String name = field.getName().toLowerCase();
                    if (!name.contains("comment") && !name.contains("body") && !name.contains("text")) continue;

                    field.setAccessible(true);
                    Object value = field.get(holder);
                    if (value instanceof View) return (View) value;
                } catch (Throwable ignored) {
                }
            }
            cls = cls.getSuperclass();
        }

        return null;
    }

    private static View getViewField(Object holder, String name) {
        try {
            Field field = findField(holder.getClass(), name);
            if (field == null) return null;

            field.setAccessible(true);
            Object value = field.get(holder);
            return value instanceof View ? (View) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static View getItemView(Object holder) {
        return getViewField(holder, "itemView");
    }

    private static void removeExistingPreview(ViewGroup root) {
        View preview = findPreview(root);
        if (preview != null && preview.getParent() instanceof ViewGroup) {
            ((ViewGroup) preview.getParent()).removeView(preview);
        }
    }

    private static View findPreview(ViewGroup root) {
        Object tag = root.getTag();
        if (PREVIEW_TAG.equals(tag)) return root;

        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (PREVIEW_TAG.equals(child.getTag())) return child;

            if (child instanceof ViewGroup) {
                View nested = findPreview((ViewGroup) child);
                if (nested != null) return nested;
            }
        }

        return null;
    }

    private static void loadWithGlide(Context context, Object glideRequestManager, String url, ImageView imageView) {
        try {
            Object requestManager = glideRequestManager;

            if (requestManager == null) {
                Class<?> glideClass = Class.forName("com.bumptech.glide.Glide");
                Method with = glideClass.getMethod("with", Context.class);
                requestManager = with.invoke(null, context);
            }

            Object requestBuilder = invokeLoad(requestManager, url);
            if (requestBuilder == null) return;

            invokeInto(requestBuilder, imageView);
        } catch (Throwable ignored) {
        }
    }

    private static Object invokeLoad(Object requestManager, String url) {
        try {
            try {
                Method method = requestManager.getClass().getMethod("t", String.class);
                method.setAccessible(true);
                Object result = method.invoke(requestManager, url);
                return result;
            } catch (Throwable ignored) {
            }

            Method[] methods = requestManager.getClass().getMethods();
            for (Method method : methods) {
                if (!"load".equals(method.getName())) continue;
                if (method.getParameterTypes().length != 1) continue;

                Class<?> parameter = method.getParameterTypes()[0];
                if (parameter == String.class || parameter == Object.class || CharSequence.class.isAssignableFrom(parameter)) {
                    Object result = method.invoke(requestManager, url);
                    return result;
                }
            }
        } catch (Throwable throwable) {
        }

        return null;
    }

    private static void invokeInto(Object requestBuilder, ImageView imageView) {
        try {
            try {
                Method method = requestBuilder.getClass().getMethod("C0", ImageView.class);
                method.setAccessible(true);
                method.invoke(requestBuilder, imageView);
                return;
            } catch (Throwable ignored) {
            }

            Method[] methods = requestBuilder.getClass().getMethods();
            for (Method method : methods) {
                if (!"into".equals(method.getName())) continue;
                if (method.getParameterTypes().length != 1) continue;

                Class<?> parameter = method.getParameterTypes()[0];
                if (parameter.isAssignableFrom(ImageView.class) || parameter == ImageView.class) {
                    method.invoke(requestBuilder, imageView);
                    return;
                }
            }

        } catch (Throwable throwable) {
        }
    }

    private static PreviewSource findPreviewSource(Object commentModel) {
        PreviewSource source = extractPreviewSource(callStringMethod(commentModel, "S0"));
        if (source != null) return source;

        source = extractPreviewSource(callStringMethod(commentModel, "T0"));
        if (source != null) return source;

        return findFirstPreviewSourceInStringFields(commentModel);
    }

    private static PreviewSource findFirstPreviewSourceInStringFields(Object target) {
        Class<?> cls = target.getClass();

        while (cls != null) {
            Field[] fields = cls.getDeclaredFields();

            for (Field field : fields) {
                try {
                    if (field.getType() != String.class) continue;
                    field.setAccessible(true);

                    Object value = field.get(target);
                    if (!(value instanceof String)) continue;

                    PreviewSource source = extractPreviewSource((String) value);
                    if (source != null) return source;
                } catch (Throwable ignored) {
                }
            }

            cls = cls.getSuperclass();
        }

        return null;
    }

    private static PreviewSource extractPreviewSource(String value) {
        if (value == null) return null;

        String normalized = normalizeText(value);

        Matcher direct = DIRECT_PREVIEW_URL_PATTERN.matcher(normalized);
        if (direct.find()) {
            String url = cleanUrlTail(direct.group(0));
            return new PreviewSource(url, url);
        }

        Matcher directGif = DIRECT_GIF_URL_PATTERN.matcher(normalized);
        if (directGif.find()) {
            String url = directGif.group();
            return new PreviewSource(url, url);
        }

        String giphyId = extractGiphyId(normalized);
        if (giphyId == null || giphyId.length() == 0) return null;

        return new PreviewSource(
                "https://media.giphy.com/media/" + giphyId + "/giphy.gif",
                "https://giphy.com/gifs/" + giphyId
        );
    }

    private static String extractGiphyId(String value) {
        if (value == null) return null;

        for (Pattern pattern : GIPHY_PATTERNS) {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }

    private static void replaceGiphyStringFields(Object target) {
        Class<?> cls = target.getClass();

        while (cls != null) {
            Field[] fields = cls.getDeclaredFields();

            for (Field field : fields) {
                try {
                    int modifiers = field.getModifiers();
                    if (Modifier.isStatic(modifiers)) continue;
                    if (field.getType() != String.class) continue;

                    field.setAccessible(true);

                    Object value = field.get(target);
                    if (!(value instanceof String)) continue;

                    String oldValue = (String) value;
                    if (extractPreviewSource(oldValue) == null) continue;

                    String newValue = removeGiphyText(oldValue);
                    field.set(target, newValue);
                } catch (Throwable ignored) {
                }
            }

            cls = cls.getSuperclass();
        }
    }

    private static String removeGiphyText(String value) {
        if (value == null) return null;

        return value
                .replaceAll("(?i)<img[^>]+(?:giphy|external-preview\\.redd\\.it|preview\\.redd\\.it)[^>]*>", "")
                .replaceAll("(?i)!\\[gif\\]\\(giphy\\|[A-Za-z0-9_-]+\\)", "")
                .replaceAll("(?i)!\\[[^\\]]*\\]\\(https?://(?:external-preview|preview)\\.redd\\.it/[^\\s)]+\\)", "")
                .replaceAll("(?i)https?://(?:www\\.)?giphy\\.com/gifs/\\S+", "")
                .replaceAll("(?i)https?://media\\.giphy\\.com/media/[A-Za-z0-9_-]+/giphy\\.(?:gif|mp4)", "")
                .replaceAll("(?i)https?://(?:external-preview|preview)\\.redd\\.it/\\S+", "")
                .replaceAll("(?i)https?://[^\\s\"'<>]+?\\.gif(?:\\?[^\\s\"'<>)]*)?", "")
                .trim();
    }

    private static String normalizeText(String value) {
        if (value == null) return null;

        return value
                .replace("&amp;", "&")
                .replace("\\u0026", "&")
                .replace("\\/", "/");
    }

    private static String cleanUrlTail(String value) {
        if (value == null) return null;

        while (value.endsWith(")") || value.endsWith("]") || value.endsWith(".") || value.endsWith(",")) {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }

    private static String callStringMethod(Object target, String name) {
        try {
            Method method = findMethod(target.getClass(), name);
            if (method == null) return null;

            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> cls, String name) {
        while (cls != null) {
            try {
                Method method = cls.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
                cls = cls.getSuperclass();
            }
        }

        return null;
    }

    private static Field findField(Class<?> cls, String name) {
        while (cls != null) {
            try {
                Field field = cls.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
                cls = cls.getSuperclass();
            }
        }

        return null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
