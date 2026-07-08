package app.morphe.extension.boostforreddit.giphy;


import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.WeakHashMap;

public final class InlineGiphyCommentPreview {
    private static final WeakHashMap<View, RelativeLayout.LayoutParams> ORIGINAL_RELATIVE_LAYOUT_PARAMS =
            new WeakHashMap<>();


    private static final String PREVIEW_TAG = "morphe_boost_inline_giphy_preview";
    private static final String COMMENT_TEXT_INSET_MARKER =
            "morphe_boost_inline_media_comment_text_inset_v1";
    private static final int FALLBACK_COMMENT_TEXT_HORIZONTAL_INSET_DP = 8;
    private static final String LOG_TAG = "InlineGiphy";
    private static final String PREF_INLINE_MEDIA_PREVIEWS_ENABLED =
            "morphe_boost_inline_media_previews_enabled";
    private static final String PREF_INLINE_MEDIA_PREVIEW_SHOW_SOURCE_TEXT =
            "morphe_boost_inline_media_preview_show_source_text";
    private static final String PREF_INLINE_MEDIA_PREVIEW_ALIGNMENT =
            "morphe_boost_inline_media_preview_alignment";
    private static final String PREF_DIRECT_REDDIT_GIF_TAP_ACTION =
            "morphe_boost_direct_reddit_gif_tap_action";
    private static final String COMMENT_DIRECT_GIF_ROUTE_MARKER =
            "morphe_boost_comment_direct_reddit_gif_route";
    private static final String COMMENT_STATIC_IMAGE_URL_PREVIEW_MARKER =
            "morphe_boost_comment_static_image_url_preview_v1";
    private static final String PREF_GIPHY_PREVIEW_TAP_ACTION =
            "morphe_boost_giphy_preview_tap_action";
    private static final String PREF_STATIC_PREVIEW_TAP_ACTION =
            "morphe_boost_static_preview_tap_action";
    private static final String TAP_ACTION_IMAGE_VIEWER = "image_viewer";
    private static final String TAP_ACTION_VIDEO_VIEWER = "video_viewer";
    private static final String TAP_ACTION_BROWSER = "browser";
    private static final String TAP_ACTION_DISABLED = "disabled";
    private static final String ALIGNMENT_LEFT = "left";
    private static final String ALIGNMENT_CENTER = "center";
    private static final String ALIGNMENT_RIGHT = "right";
    private static final boolean DEFAULT_INLINE_MEDIA_PREVIEWS_ENABLED = true;
    private static final boolean DEFAULT_INLINE_MEDIA_PREVIEW_SHOW_SOURCE_TEXT = false;
    private static final String DEFAULT_INLINE_MEDIA_PREVIEW_ALIGNMENT = ALIGNMENT_CENTER;
    private static final String DEFAULT_DIRECT_REDDIT_GIF_TAP_ACTION = TAP_ACTION_IMAGE_VIEWER;
    private static final String DEFAULT_GIPHY_PREVIEW_TAP_ACTION = TAP_ACTION_VIDEO_VIEWER;
    private static final String DEFAULT_STATIC_PREVIEW_TAP_ACTION = TAP_ACTION_IMAGE_VIEWER;
    private static final Map<Object, PreviewSource> PREVIEW_SOURCES = new WeakHashMap<>();

    private static final Pattern DIRECT_PREVIEW_URL_PATTERN =
            Pattern.compile("https?://(?:external-preview|preview)\\.redd\\.it/[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern DIRECT_STATIC_IMAGE_URL_PATTERN =
            Pattern.compile("https?://(?:i\\.imgur\\.com|i\\.redd\\.it)/[^\\s\"'<>]+?\\.(?:png|jpe?g|webp)(?:\\?[^\\s\"'<>)]*)?", Pattern.CASE_INSENSITIVE);

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

    public static void cleanCommentHtml(Object holder, Object commentModel) {
        try {
            if (commentModel == null) return;

            Context context = null;
            View itemView = getItemView(holder);
            if (itemView != null) {
                context = itemView.getContext();
            }

            if (!isInlineMediaPreviewsEnabled(context)) {
                PREVIEW_SOURCES.remove(commentModel);
                return;
            }

            PreviewSource previewSource = findPreviewSource(commentModel);
            if (previewSource == null) return;

            PREVIEW_SOURCES.put(commentModel, previewSource);

            // Issue #29: preserve Boost's original comment text/HTML before native link spans are built.
            // Rewriting CommentModel string fields here can corrupt normal link targets in mixed link+media comments.
            // Keep preview extraction/rendering active, but leave source text intact until cleanup is span-safe.
        } catch (Throwable throwable) {
        }
    }

    public static void bind(Object holder, Object commentModel, Object glideRequestManager) {
        try {
            if (holder == null || commentModel == null) return;

            View itemView = getItemView(holder);

            if (!(itemView instanceof ViewGroup)) {
                return;
            }

            removeExistingPreview((ViewGroup) itemView);

            if (!isInlineMediaPreviewsEnabled(itemView.getContext())) {
                PREVIEW_SOURCES.remove(commentModel);
                return;
            }

            PreviewSource previewSource = findPreviewSource(commentModel);
            if (previewSource == null) {
                previewSource = PREVIEW_SOURCES.get(commentModel);
            }

            if (previewSource == null || previewSource.gifUrl == null || previewSource.gifUrl.length() == 0) {
                return;
            }

            final Context context = itemView.getContext();
            final String gifUrl = previewSource.gifUrl;
            final String sourceUrl = previewSource.sourceUrl;
            final String previewAlignment = getPreviewAlignment(context);

            LinearLayout container = new LinearLayout(context);
            container.setTag(PREVIEW_TAG);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(0, dp(context, 6), 0, dp(context, 4));

            ImageView imageView = new ImageView(context);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    imageWidthForAlignment(previewAlignment),
                    dp(context, 170)
            );

            container.addView(imageView, imageParams);

            applyPreviewAlignment(container, previewAlignment);

            View.OnClickListener mediaClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openInBoostViewerOrFallback(view, gifUrl, sourceUrl);
                }
            };

            container.setClickable(false);
            container.setFocusable(false);
            imageView.setClickable(true);
            imageView.setFocusable(true);

            imageView.setOnClickListener(mediaClickListener);

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
            applyCommentTextHorizontalInset(preview, commentText);
            boolean showPreview = commentText == null || commentText.getVisibility() == View.VISIBLE;

            preview.setVisibility(showPreview ? View.VISIBLE : View.GONE);
            updateRelativeLayoutAnchors(commentText, preview, showPreview);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isInlineMediaPreviewsEnabled(Context context) {
        if (context == null) {
            return DEFAULT_INLINE_MEDIA_PREVIEWS_ENABLED;
        }

        try {
            android.content.SharedPreferences preferences = context.getSharedPreferences(
                    context.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );

            return preferences.getBoolean(
                    PREF_INLINE_MEDIA_PREVIEWS_ENABLED,
                    DEFAULT_INLINE_MEDIA_PREVIEWS_ENABLED
            );
        } catch (Throwable ignored) {
            return DEFAULT_INLINE_MEDIA_PREVIEWS_ENABLED;
        }
    }

    private static boolean isSourceTextWithPreviewEnabled(Context context) {
        if (context == null) {
            return DEFAULT_INLINE_MEDIA_PREVIEW_SHOW_SOURCE_TEXT;
        }

        try {
            android.content.SharedPreferences preferences = context.getSharedPreferences(
                    context.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );

            return preferences.getBoolean(
                    PREF_INLINE_MEDIA_PREVIEW_SHOW_SOURCE_TEXT,
                    DEFAULT_INLINE_MEDIA_PREVIEW_SHOW_SOURCE_TEXT
            );
        } catch (Throwable ignored) {
            return DEFAULT_INLINE_MEDIA_PREVIEW_SHOW_SOURCE_TEXT;
        }
    }

    private static String getPreviewAlignment(Context context) {
        if (context == null) {
            return DEFAULT_INLINE_MEDIA_PREVIEW_ALIGNMENT;
        }

        try {
            android.content.SharedPreferences preferences = context.getSharedPreferences(
                    context.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );

            String value = preferences.getString(
                    PREF_INLINE_MEDIA_PREVIEW_ALIGNMENT,
                    DEFAULT_INLINE_MEDIA_PREVIEW_ALIGNMENT
            );

            return normalizePreviewAlignment(value);
        } catch (Throwable ignored) {
            return DEFAULT_INLINE_MEDIA_PREVIEW_ALIGNMENT;
        }
    }

    private static String getDirectRedditGifTapAction(Context context) {
        if (context == null) {
            return DEFAULT_DIRECT_REDDIT_GIF_TAP_ACTION;
        }

        try {
            android.content.SharedPreferences preferences = context.getSharedPreferences(
                    context.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );

            String value = preferences.getString(
                    PREF_DIRECT_REDDIT_GIF_TAP_ACTION,
                    DEFAULT_DIRECT_REDDIT_GIF_TAP_ACTION
            );

            return normalizeMediaTapAction(value, DEFAULT_DIRECT_REDDIT_GIF_TAP_ACTION);
        } catch (Throwable ignored) {
            return DEFAULT_DIRECT_REDDIT_GIF_TAP_ACTION;
        }
    }

    private static String getGiphyPreviewTapAction(Context context) {
        if (context == null) {
            return DEFAULT_GIPHY_PREVIEW_TAP_ACTION;
        }

        try {
            android.content.SharedPreferences preferences = context.getSharedPreferences(
                    context.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );

            String value = preferences.getString(
                    PREF_GIPHY_PREVIEW_TAP_ACTION,
                    DEFAULT_GIPHY_PREVIEW_TAP_ACTION
            );

            return normalizeMediaTapAction(value, DEFAULT_GIPHY_PREVIEW_TAP_ACTION);
        } catch (Throwable ignored) {
            return DEFAULT_GIPHY_PREVIEW_TAP_ACTION;
        }
    }

    private static String getStaticPreviewTapAction(Context context) {
        if (context == null) {
            return DEFAULT_STATIC_PREVIEW_TAP_ACTION;
        }

        try {
            android.content.SharedPreferences preferences = context.getSharedPreferences(
                    context.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );

            String value = preferences.getString(
                    PREF_STATIC_PREVIEW_TAP_ACTION,
                    DEFAULT_STATIC_PREVIEW_TAP_ACTION
            );

            return normalizeMediaTapAction(value, DEFAULT_STATIC_PREVIEW_TAP_ACTION);
        } catch (Throwable ignored) {
            return DEFAULT_STATIC_PREVIEW_TAP_ACTION;
        }
    }

    private static String normalizeMediaTapAction(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String normalized = value.trim().toLowerCase(java.util.Locale.US);

        if (TAP_ACTION_IMAGE_VIEWER.equals(normalized)
                || TAP_ACTION_VIDEO_VIEWER.equals(normalized)
                || TAP_ACTION_BROWSER.equals(normalized)
                || TAP_ACTION_DISABLED.equals(normalized)) {
            return normalized;
        }

        return fallback;
    }

    private static String normalizePreviewAlignment(String value) {
        if (value == null) {
            return DEFAULT_INLINE_MEDIA_PREVIEW_ALIGNMENT;
        }

        String normalized = value.trim().toLowerCase(java.util.Locale.US);
        if (ALIGNMENT_LEFT.equals(normalized)
                || ALIGNMENT_CENTER.equals(normalized)
                || ALIGNMENT_RIGHT.equals(normalized)) {
            return normalized;
        }

        return DEFAULT_INLINE_MEDIA_PREVIEW_ALIGNMENT;
    }

    private static int imageWidthForAlignment(String alignment) {
        if (ALIGNMENT_LEFT.equals(alignment) || ALIGNMENT_RIGHT.equals(alignment)) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }

        return ViewGroup.LayoutParams.MATCH_PARENT;
    }

    private static void applyPreviewAlignment(LinearLayout container, String alignment) {
        if (container == null) {
            return;
        }

        if (ALIGNMENT_LEFT.equals(alignment)) {
            container.setGravity(Gravity.START);
        } else if (ALIGNMENT_RIGHT.equals(alignment)) {
            container.setGravity(Gravity.END);
        } else {
            container.setGravity(Gravity.CENTER_HORIZONTAL);
        }
    }

    private static boolean insertBelowCommentText(Object holder, ViewGroup itemView, View preview) {
        View commentText = findCommentTextView(holder);

        if (!isActualCommentTextView(commentText)) {
            return false;
        }

        applyCommentTextHorizontalInset(preview, commentText);

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


    private static void rememberRelativeLayoutParams(View view) {
        if (view == null) return;
        if (!(view.getLayoutParams() instanceof RelativeLayout.LayoutParams)) return;

        if (!ORIGINAL_RELATIVE_LAYOUT_PARAMS.containsKey(view)) {
            ORIGINAL_RELATIVE_LAYOUT_PARAMS.put(
                    view,
                    new RelativeLayout.LayoutParams((RelativeLayout.LayoutParams) view.getLayoutParams())
            );
        }
    }

    private static void restoreRelativeLayoutParamsRecursive(View view) {
        if (view == null) return;

        RelativeLayout.LayoutParams original = ORIGINAL_RELATIVE_LAYOUT_PARAMS.remove(view);
        if (original != null) {
            view.setLayoutParams(new RelativeLayout.LayoutParams(original));
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                restoreRelativeLayoutParamsRecursive(group.getChildAt(i));
            }
        }
    }

    private static void applyCommentTextHorizontalInset(View preview, View commentText) {
        try {
            if (preview == null) return;

            int leftInset = 0;
            int rightInset = 0;

            if (commentText != null) {
                leftInset = Math.max(0, commentText.getPaddingLeft());
                rightInset = Math.max(0, commentText.getPaddingRight());

                ViewGroup.LayoutParams params = commentText.getLayoutParams();
                if (params instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                    leftInset += Math.max(0, margins.leftMargin);
                    rightInset += Math.max(0, margins.rightMargin);
                }
            }

            if (leftInset == 0 && rightInset == 0 && preview.getContext() != null) {
                leftInset = dp(preview.getContext(), FALLBACK_COMMENT_TEXT_HORIZONTAL_INSET_DP);
                rightInset = dp(preview.getContext(), FALLBACK_COMMENT_TEXT_HORIZONTAL_INSET_DP);
            }

            preview.setPadding(
                    leftInset,
                    preview.getPaddingTop(),
                    rightInset,
                    preview.getPaddingBottom()
            );
        } catch (Throwable throwable) {
            Log.w(LOG_TAG, COMMENT_TEXT_INSET_MARKER + ": failed to apply comment text inset", throwable);
        }
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
                rememberRelativeLayoutParams(expandableLayout);
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

            rememberRelativeLayoutParams(expandableLayout);
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
        restoreRelativeLayoutParamsRecursive(root);

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

    private static void openInBoostViewerOrFallback(View view, String mediaUrl, String sourceUrl) {
        Context context = view.getContext();
        String internalUrl = firstNonEmpty(mediaUrl, sourceUrl);
        String externalUrl = firstNonEmpty(sourceUrl, mediaUrl);
        boolean giphyPreview = isGiphyPreview(mediaUrl, sourceUrl);
        boolean staticPreview = !giphyPreview && isStaticPreview(mediaUrl, sourceUrl);
        String giphyTapAction = giphyPreview
                ? getGiphyPreviewTapAction(context)
                : DEFAULT_GIPHY_PREVIEW_TAP_ACTION;
        String staticPreviewTapAction = staticPreview
                ? getStaticPreviewTapAction(context)
                : DEFAULT_STATIC_PREVIEW_TAP_ACTION;

        if (giphyPreview && TAP_ACTION_DISABLED.equals(giphyTapAction)) {
            Log.d(LOG_TAG, "giphy preview tap disabled: " + internalUrl);
            return;
        }

        if (giphyPreview && TAP_ACTION_BROWSER.equals(giphyTapAction)) {
            Log.d(LOG_TAG, "open giphy preview externally: " + externalUrl);
            openExternally(context, externalUrl);
            return;
        }

        if (staticPreview && TAP_ACTION_DISABLED.equals(staticPreviewTapAction)) {
            Log.d(LOG_TAG, "static preview tap disabled: " + internalUrl);
            return;
        }

        if (staticPreview && TAP_ACTION_BROWSER.equals(staticPreviewTapAction)) {
            Log.d(LOG_TAG, "open static preview externally: " + externalUrl);
            openExternally(context, externalUrl);
            return;
        }

        try {
            Activity activity = findActivity(context);

            if (activity != null && openViaBoostRouter(
                    activity,
                    internalUrl,
                    giphyTapAction,
                    staticPreviewTapAction
            )) {
                return;
            }

            Log.w(LOG_TAG, "Boost internal router unavailable/failed, falling back");
        } catch (Throwable throwable) {
            Log.w(LOG_TAG, "Boost internal router threw, falling back", throwable);
        }

        openExternally(context, externalUrl);
    }

    private static boolean openViaBoostRouter(
            Activity activity,
            String url,
            String giphyTapAction,
            String staticPreviewTapAction
    ) {
        if (activity == null || url == null || url.length() == 0) return false;

        try {
            Class<?> submissionClass = Class.forName("com.rubenmayayo.reddit.models.reddit.SubmissionModel");
            Object submission = submissionClass.getDeclaredConstructor().newInstance();

            boolean animated = isLikelyAnimatedMediaUrl(url);
            boolean directIRedditGif = isDirectIRedditGif(url);
            boolean giphyMedia = isGiphyMediaUrl(url);
            boolean staticPreview = !giphyMedia && isStaticPreviewUrl(url);
            String host = hostFromUrl(url);
            if (host != null && host.length() > 0) {
                callStringSetter(submission, "m2", host);
            }
            String directIRedditGifTapAction = directIRedditGif
                    ? getDirectRedditGifTapAction(activity)
                    : DEFAULT_DIRECT_REDDIT_GIF_TAP_ACTION;
            String normalizedGiphyTapAction = normalizeMediaTapAction(
                    giphyTapAction,
                    DEFAULT_GIPHY_PREVIEW_TAP_ACTION
            );
            String normalizedStaticPreviewTapAction = normalizeMediaTapAction(
                    staticPreviewTapAction,
                    DEFAULT_STATIC_PREVIEW_TAP_ACTION
            );
            boolean forceVideoViewerForDirectGif = directIRedditGif
                    && TAP_ACTION_VIDEO_VIEWER.equals(directIRedditGifTapAction);
            boolean forceImageViewerForGiphy = giphyMedia
                    && TAP_ACTION_IMAGE_VIEWER.equals(normalizedGiphyTapAction);
            boolean forceVideoViewerForGiphy = giphyMedia
                    && TAP_ACTION_VIDEO_VIEWER.equals(normalizedGiphyTapAction);
            boolean forceImageViewerForStaticPreview = staticPreview
                    && TAP_ACTION_IMAGE_VIEWER.equals(normalizedStaticPreviewTapAction);
            boolean forceVideoViewerForStaticPreview = staticPreview
                    && TAP_ACTION_VIDEO_VIEWER.equals(normalizedStaticPreviewTapAction);
            boolean animatedForRouting = (animated && !forceImageViewerForGiphy && !forceImageViewerForStaticPreview)
                    || forceVideoViewerForDirectGif
                    || forceVideoViewerForGiphy
                    || forceVideoViewerForStaticPreview;

            if (directIRedditGif && TAP_ACTION_DISABLED.equals(directIRedditGifTapAction)) {
                Log.d(LOG_TAG, "direct i.redd.it gif tap disabled: " + url);
                return true;
            }

            if (directIRedditGif && TAP_ACTION_BROWSER.equals(directIRedditGifTapAction)) {
                Log.d(LOG_TAG, "open direct i.redd.it gif externally: " + url);
                openExternally(activity, url);
                return true;
            }

            // Known from Boost's own GalleryActivity/ImageActivity paths:
            // K2(4) = static image-ish media
            // K2(5) = gif/video-ish media
            callIntSetter(
                    submission,
                    "K2",
                    directIRedditGif ? (forceVideoViewerForDirectGif ? 5 : 4) : (animatedForRouting ? 5 : 4)
            );
            callStringSetter(submission, "L2", url);

            if (directIRedditGif && !forceVideoViewerForDirectGif
                    && openStaticImageViaBoost(activity, submissionClass, submission, true)) {
                Log.d(LOG_TAG, COMMENT_DIRECT_GIF_ROUTE_MARKER + ": open direct i.redd.it gif via Boost image viewer: " + url);
                return true;
            }

            if (!animatedForRouting && openStaticImageViaBoost(activity, submissionClass, submission, false)) {
                return true;
            }

            if (animatedForRouting) {
                callStringSetter(submission, "u2", url);
            }

            Class<?> navigationClass = Class.forName("com.rubenmayayo.reddit.ui.activities.i");

            Method[] methods = navigationClass.getMethods();
            for (Method method : methods) {
                if (!"U".equals(method.getName())) continue;

                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 2) continue;
                if (!parameterTypes[0].isAssignableFrom(activity.getClass())) continue;
                if (!parameterTypes[1].isAssignableFrom(submissionClass)) continue;

                method.setAccessible(true);
                method.invoke(null, activity, submission);
                return true;
            }
        } catch (Throwable throwable) {
            Log.w(LOG_TAG, "openViaBoostRouter failed", throwable);
        }

        return false;
    }

    private static boolean openStaticImageViaBoost(Activity activity, Class<?> submissionClass, Object submission, boolean directRedditGif) {
        if (activity == null || submissionClass == null || submission == null) return false;

        // Forced image routes must avoid MediaImageActivity. That activity can reclassify
        // inline comment media through its async metadata path and bounce GIF/static URLs
        // into MediaVideoActivity. Legacy ImageActivity is the stricter image-only viewer.
        //
        // Direct i.redd.it GIFs are a special case: ImageActivity with comment=true opens
        // first, but immediately bounces to MediaVideoActivity. Use non-comment image mode
        // for this direct-GIF image-viewer route while preserving comment mode for normal
        // static inline previews.
        boolean legacyCommentMode = !directRedditGif;
        if (openLegacyImageActivityViaBoost(activity, submission, legacyCommentMode)) {
            if (directRedditGif) {
                Log.d(LOG_TAG, COMMENT_DIRECT_GIF_ROUTE_MARKER + ": forced direct i.redd.it gif to non-comment image route");
            }
            return true;
        }

        try {
            Class<?> navigationClass = Class.forName("com.rubenmayayo.reddit.ui.activities.i");
            Method method = navigationClass.getMethod("h", Context.class, submissionClass, boolean.class);
            method.setAccessible(true);

            Object intent = method.invoke(null, activity, submission, true);
            if (intent instanceof Intent) {
                activity.startActivity((Intent) intent);
                return true;
            }
        } catch (Throwable throwable) {
            Log.w(LOG_TAG, "openStaticImageViaBoost failed", throwable);
        }

        return false;
    }

    private static boolean openLegacyImageActivityViaBoost(Activity activity, Object submission, boolean commentMode) {
        if (activity == null || !(submission instanceof Parcelable)) return false;

        try {
            Class<?> imageActivityClass = Class.forName("com.rubenmayayo.reddit.ui.activities.ImageActivity");
            Intent intent = new Intent(activity, imageActivityClass);
            intent.putExtra("submission", (Parcelable) submission);
            intent.putExtra("comment", commentMode);

            Integer accentColor = getBoostAccentColor(activity);
            if (accentColor != null) {
                intent.putExtra("accent_color", accentColor.intValue());
            }

            activity.startActivity(intent);
            return true;
        } catch (Throwable throwable) {
            Log.w(LOG_TAG, "openLegacyImageActivityViaBoost failed", throwable);
            return false;
        }
    }

    private static Integer getBoostAccentColor(Context context) {
        if (context == null) return null;

        try {
            Class<?> colorClass = Class.forName("he.f0");
            Method method = colorClass.getMethod("f", Context.class);
            Object value = method.invoke(null, context);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }


    private static Activity findActivity(Context context) {
        Context current = context;
        while (current != null) {
            if (current instanceof Activity) {
                return (Activity) current;
            }

            if (current instanceof ContextWrapper) {
                Context base = ((ContextWrapper) current).getBaseContext();
                if (base == current) {
                    return null;
                }
                current = base;
                continue;
            }

            return null;
        }

        return null;
    }

    private static void openExternally(Context context, String url) {
        if (context == null || url == null || url.length() == 0) return;

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Throwable throwable) {
            Log.w(LOG_TAG, "openExternally failed", throwable);
        }
    }

    private static String hostFromUrl(String url) {
        if (url == null || url.length() == 0) return null;

        try {
            return Uri.parse(url).getHost();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && first.length() > 0) return first;
        if (second != null && second.length() > 0) return second;
        return null;
    }

    private static boolean isGiphyPreview(String mediaUrl, String sourceUrl) {
        return isGiphyMediaUrl(mediaUrl) || isGiphyMediaUrl(sourceUrl);
    }

    private static boolean isGiphyMediaUrl(String url) {
        if (url == null) return false;

        String lower = url.toLowerCase(java.util.Locale.US);
        return lower.contains("://giphy.com/")
                || lower.contains("://www.giphy.com/")
                || lower.contains("://media.giphy.com/");
    }

    private static boolean isRedditProfileOrAvatarImageUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(java.util.Locale.US);

        boolean redditProfileImageHost =
                lower.contains("://preview.redd.it/")
                        || lower.contains("://external-preview.redd.it/")
                        || lower.contains("://styles.redditmedia.com/")
                        || lower.contains("://i.redd.it/");

        if (!redditProfileImageHost) {
            return false;
        }

        return lower.contains("snoovatar")
                || lower.contains("avatar_default")
                || lower.contains("/avatar")
                || lower.contains("profileicon")
                || lower.contains("communityicon")
                || lower.contains("/styles/profileicon_")
                || lower.contains("/styles/communityicon_");
    }

    private static boolean isStaticPreview(String mediaUrl, String sourceUrl) {
        return isStaticPreviewUrl(mediaUrl) || isStaticPreviewUrl(sourceUrl);
    }

    private static boolean isStaticPreviewUrl(String url) {
        if (url == null) return false;

        String lower = url.toLowerCase(java.util.Locale.US);
        return lower.contains("://preview.redd.it/")
                || lower.contains("://external-preview.redd.it/")
                || isDirectStaticImageUrl(lower);
    }

    private static boolean isDirectStaticImageUrl(String url) {
        if (url == null) return false;

        String lower = url.toLowerCase(java.util.Locale.US);

        int queryIndex = lower.indexOf('?');
        if (queryIndex >= 0) {
            lower = lower.substring(0, queryIndex);
        }

        int fragmentIndex = lower.indexOf('#');
        if (fragmentIndex >= 0) {
            lower = lower.substring(0, fragmentIndex);
        }

        boolean supportedHost = lower.startsWith("https://i.imgur.com/")
                || lower.startsWith("http://i.imgur.com/")
                || lower.startsWith("https://i.redd.it/")
                || lower.startsWith("http://i.redd.it/");

        return supportedHost
                && (lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".webp"));
    }

    private static boolean isDirectIRedditGif(String url) {
        if (url == null) return false;

        String lower = url.toLowerCase();

        int queryIndex = lower.indexOf('?');
        if (queryIndex >= 0) {
            lower = lower.substring(0, queryIndex);
        }

        int fragmentIndex = lower.indexOf('#');
        if (fragmentIndex >= 0) {
            lower = lower.substring(0, fragmentIndex);
        }

        return lower.startsWith("https://i.redd.it/") && lower.endsWith(".gif");
    }

    private static boolean isLikelyAnimatedMediaUrl(String url) {
        if (url == null) return false;

        String lower = url.toLowerCase();
        return lower.endsWith(".gif")
                || lower.contains(".gif?")
                || lower.endsWith(".gifv")
                || lower.contains(".gifv?")
                || lower.endsWith(".mp4")
                || lower.contains(".mp4?")
                || lower.endsWith(".webm")
                || lower.contains(".webm?")
                || lower.contains("giphy.com")
                || lower.contains("media.giphy.com")
                || lower.contains("gfycat.com")
                || lower.contains("redgifs.com");
    }

    private static boolean callIntSetter(Object target, String name, int value) {
        try {
            Method method = target.getClass().getMethod(name, int.class);
            method.setAccessible(true);
            method.invoke(target, value);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Method[] methods = target.getClass().getMethods();
            for (Method method : methods) {
                if (!name.equals(method.getName())) continue;

                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1 || parameterTypes[0] != int.class) continue;

                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static boolean callStringSetter(Object target, String name, String value) {
        try {
            Method method = target.getClass().getMethod(name, String.class);
            method.setAccessible(true);
            method.invoke(target, value);
            return true;
        } catch (Throwable ignored) {
        }

        try {
            Method[] methods = target.getClass().getMethods();
            for (Method method : methods) {
                if (!name.equals(method.getName())) continue;

                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1 || parameterTypes[0] != String.class) continue;

                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
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
        while (direct.find()) {
            String url = cleanUrlTail(direct.group(0));
            if (isRedditProfileOrAvatarImageUrl(url)) {
                Log.d(LOG_TAG, "morphe_boost_skip_profile_avatar_preview_v1: ignored profile/avatar preview " + url);
                continue;
            }
            return new PreviewSource(url, url);
        }

        Matcher staticImage = DIRECT_STATIC_IMAGE_URL_PATTERN.matcher(normalized);
        while (staticImage.find()) {
            String url = cleanUrlTail(staticImage.group(0));
            if (isRedditProfileOrAvatarImageUrl(url)) {
                Log.d(LOG_TAG, "morphe_boost_skip_profile_avatar_preview_v1: ignored profile/avatar static image " + url);
                continue;
            }
            Log.d(LOG_TAG, COMMENT_STATIC_IMAGE_URL_PREVIEW_MARKER + ": found static image url preview " + url);
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


    private static String removeGiphyAnchors(String value) {
        if (value == null || value.length() == 0) return value;

        // Boost stores some comment HTML escaped as &lt;...&gt;, so handle that before stripping plain URLs.
        value = value.replaceAll("(?is)&lt;a\\s+[^&]*href=[\\\"']https?://(?:www\\.)?giphy\\.com/gifs/[^\\\"']+[\\\"'][^&]*&gt;.*?&lt;/a&gt;", "");
        value = value.replaceAll("(?is)&lt;a\\s+[^&]*href=[\\\"']https?://media\\.giphy\\.com/media/[A-Za-z0-9_-]+/giphy\\.(?:gif|mp4)[^\\\"']*[\\\"'][^&]*&gt;.*?&lt;/a&gt;", "");
        value = value.replaceAll("(?is)&lt;a\\s+[^&]*href=[\\\"']https?://(?:i\\.imgur\\.com|i\\.redd\\.it)/[^\\\"'&]+?\\.(?:png|jpe?g|webp)(?:\\?[^\\\"']*)?[\\\"'][^&]*&gt;.*?&lt;/a&gt;", "");

        // Clean up malformed leftovers from earlier passes, e.g. &lt;a href=" target="_blank"&gt;
        value = value.replaceAll("(?is)&lt;a\\s+[^&]*href=[\\\"']\\s*target=[\\\"']_blank[\\\"'][^&]*&gt;", "");
        value = value.replaceAll("(?is)<a\\s+[^>]*href=[\\\"']\\s*target=[\\\"']_blank[\\\"'][^>]*>", "");

        value = value.replaceAll("(?is)<a\\s+[^>]*href=[\"']https?://(?:www\\.)?giphy\\.com/gifs/[^\"']+[\"'][^>]*>.*?</a>", "");
        value = value.replaceAll("(?is)<a\\s+[^>]*href=[\"']https?://media\\.giphy\\.com/media/[A-Za-z0-9_-]+/giphy\\.(?:gif|mp4)[^\"']*[\"'][^>]*>.*?</a>", "");
        value = value.replaceAll("(?is)<a\\s+[^>]*href=[\"']https?://(?:i\\.imgur\\.com|i\\.redd\\.it)/[^\"'>]+?\\.(?:png|jpe?g|webp)(?:\\?[^\"']*)?[\"'][^>]*>.*?</a>", "");

        return value;
    }

    private static String removeGiphyText(String value) {
        if (value == null || value.length() == 0) return value;

        value = removeGiphyAnchors(value);
        if (value == null) return null;

        return value
                .replaceAll("(?i)<img[^>]+(?:giphy|external-preview\\.redd\\.it|preview\\.redd\\.it|i\\.imgur\\.com|i\\.redd\\.it)[^>]*>", "")
                .replaceAll("(?i)!\\[gif\\]\\(giphy\\|[A-Za-z0-9_-]+\\)", "")
                .replaceAll("(?i)!\\[[^\\]]*\\]\\(https?://(?:external-preview|preview)\\.redd\\.it/[^\\s)]+\\)", "")
                .replaceAll("(?i)!\\[[^\\]]*\\]\\(https?://(?:i\\.imgur\\.com|i\\.redd\\.it)/[^\\s)]+?\\.(?:png|jpe?g|webp)(?:\\?[^\\s)]*)?\\)", "")
                .replaceAll("(?i)https?://(?:www\\.)?giphy\\.com/gifs/\\S+", "")
                .replaceAll("(?i)https?://media\\.giphy\\.com/media/[A-Za-z0-9_-]+/giphy\\.(?:gif|mp4)", "")
                .replaceAll("(?i)https?://(?:external-preview|preview)\\.redd\\.it/\\S+", "")
                .replaceAll("(?i)https?://(?:i\\.imgur\\.com|i\\.redd\\.it)/[^\\s\"'<>]+?\\.(?:png|jpe?g|webp)(?:\\?[^\\s\"'<>)]*)?", "")
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
