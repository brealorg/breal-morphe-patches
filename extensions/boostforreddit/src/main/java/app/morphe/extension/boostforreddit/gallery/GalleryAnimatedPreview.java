package app.morphe.extension.boostforreddit.gallery;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Coordinates animated Reddit gallery previews around Boost's legacy
 * ViewPager. Only the selected page owns an inline ExoPlayer. Boost's poster,
 * autoplay/data policy, swipe behavior, and full-screen play route remain the
 * fallback contract.
 */
public final class GalleryAnimatedPreview {
    public static final String MARKER =
            "MORPHE_BOOST_GALLERY_ANIMATED_PREVIEW_V8_MEDIA_SOURCES";

    private static final String LOG_TAG = "GalleryAnimated";
    private static final String PREF_AUTOPLAY_SWIPE = "pref_autoplay_swipe";
    private static final String PREF_AUTOPLAY_CARDS = "pref_autoplay_cards";
    private static final String PREF_REDUCE_MOBILE = "pref_reduce_mobile";
    private static final String PREF_REDUCE_WIFI = "pref_reduce_wifi";

    private static final int AUTOPLAY_ALWAYS = 0;
    private static final int AUTOPLAY_WIFI = 1;
    private static final int AUTOPLAY_NEVER = 2;
    private static final int AUTOPLAY_FOLLOW_GENERAL = 3;

    private static final int PLAYER_STATE_READY = 3;
    private static final int PLAYER_REPEAT_ALL = 2;
    private static final int MEDIA_SOURCE_PROGRESSIVE = 0;
    private static final int MEDIA_SOURCE_DASH = 2;
    private static final int MEDIA_SOURCE_HLS = 3;
    private static final int PAGER_STATE_IDLE = 0;
    private static final int READY_POLL_INTERVAL_MS = 100;
    private static final int READY_POLL_LIMIT = 150;

    private static final Map<Object, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<Object, Page> PAGES = new WeakHashMap<>();

    private GalleryAnimatedPreview() {
    }

    private static final class Session {
        int selectedPosition;
        int pendingPosition = -1;
        int pagerState = PAGER_STATE_IDLE;
        final Map<Integer, WeakReference<Page>> pages = new HashMap<>();
    }

    private static final class Page {
        final WeakReference<Object> fragment;
        Session session;
        int position = -1;
        WeakReference<View> root = new WeakReference<>(null);
        WeakReference<ImageView> imageView = new WeakReference<>(null);
        WeakReference<View> playButton = new WeakReference<>(null);
        View.OnAttachStateChangeListener attachListener;
        String animatedUrl;
        boolean eligible;
        boolean active;
        boolean inlinePlayerStarted;
        boolean inlineReady;

        Object inlinePlayer;
        View inlinePlayerView;
        WeakReference<ViewGroup> inlinePlayerParent = new WeakReference<>(null);
        Runnable readyPoll;
        int playerGeneration;

        int originalImageVisibility = Integer.MIN_VALUE;
        int originalPlayButtonVisibility = Integer.MIN_VALUE;
        float originalPlayButtonAlpha = Float.NaN;

        Page(Object fragment) {
            this.fragment = new WeakReference<>(fragment);
        }
    }

    public static void beginPage(Object activity, int position) {
        try {
            if (activity == null || position < 0) return;
            sessionFor(activity).pendingPosition = position;
        } catch (Throwable throwable) {
            logFailure("begin", throwable);
        }
    }

    public static void registerPage(Object activity, Object fragment) {
        try {
            if (activity == null || fragment == null) return;

            Session session = sessionFor(activity);
            int position = session.pendingPosition;
            session.pendingPosition = -1;
            if (position < 0) return;

            Page page = PAGES.get(fragment);
            if (page == null) {
                page = new Page(fragment);
                PAGES.put(fragment, page);
            }

            page.session = session;
            page.position = position;
            session.pages.put(position, new WeakReference<>(page));
            prune(session);

            Log.d(LOG_TAG, MARKER + ": register position=" + position);
            updatePage(page);
        } catch (Throwable throwable) {
            logFailure("register", throwable);
        }
    }

    public static void bindPage(Object fragment, View root) {
        try {
            if (fragment == null || root == null) return;

            Page page = PAGES.get(fragment);
            if (page == null) page = recoverPage(fragment);
            if (page == null) {
                Log.w(LOG_TAG, MARKER + ": bind skipped; page position unavailable");
                return;
            }

            View oldRoot = page.root.get();
            if (oldRoot != null && page.attachListener != null) {
                oldRoot.removeOnAttachStateChangeListener(page.attachListener);
            }

            Object model = readField(fragment, "f");
            Object photoView = readField(fragment, "photoView");
            if (!(photoView instanceof ImageView) || model == null) {
                Log.w(LOG_TAG, MARKER + ": bind skipped; model/photoView unavailable");
                return;
            }

            ImageView imageView = (ImageView) photoView;
            if (page.imageView.get() != imageView) {
                page.originalImageVisibility = imageView.getVisibility();
            }
            page.root = new WeakReference<>(root);
            page.imageView = new WeakReference<>(imageView);

            Object playButton = readField(fragment, "playButton");
            if (playButton instanceof View) {
                View buttonView = (View) playButton;
                if (page.playButton.get() != buttonView) {
                    page.originalPlayButtonVisibility = buttonView.getVisibility();
                    page.originalPlayButtonAlpha = buttonView.getAlpha();
                }
                page.playButton = new WeakReference<>(buttonView);
            }

            page.animatedUrl = callString(model, "getMp4");
            page.eligible = callBoolean(model, "isAnimated")
                    && isSupportedMediaUrl(page.animatedUrl);
            if (shouldHidePlayButton(page)) {
                hidePlayButton(page);
            } else {
                showPlayButton(page);
            }

            final Page boundPage = page;
            page.attachListener = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    updatePage(boundPage);
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    stopPage(boundPage);
                }
            };
            root.addOnAttachStateChangeListener(page.attachListener);

            Log.d(
                    LOG_TAG,
                    MARKER + ": bind position=" + page.position
                            + " eligible=" + page.eligible
                            + " source=" + mediaSourceName(mediaSourceType(page.animatedUrl))
            );
            updatePage(page);
        } catch (Throwable throwable) {
            logFailure("bind", throwable);
        }
    }

    public static void selectPage(Object activity, int position) {
        try {
            if (activity == null || position < 0) return;

            Session session = sessionFor(activity);
            session.selectedPosition = position;
            prune(session);

            Page selected = null;
            for (Map.Entry<Integer, WeakReference<Page>> entry : session.pages.entrySet()) {
                Page page = entry.getValue().get();
                if (page == null) continue;
                if (entry.getKey() == position) {
                    selected = page;
                } else {
                    stopPage(page);
                }
            }

            if (selected != null && session.pagerState == PAGER_STATE_IDLE) {
                updatePage(selected);
            }
            Log.d(LOG_TAG, MARKER + ": select position=" + position);
        } catch (Throwable throwable) {
            logFailure("select", throwable);
        }
    }

    public static void setPagerState(Object activity, int state) {
        try {
            if (activity == null) return;

            Session session = sessionFor(activity);
            session.pagerState = state;
            if (state == PAGER_STATE_IDLE) {
                WeakReference<Page> reference = session.pages.get(session.selectedPosition);
                Page selected = reference == null ? null : reference.get();
                if (selected != null) updatePage(selected);
            }
            Log.d(LOG_TAG, MARKER + ": pager state=" + state);
        } catch (Throwable throwable) {
            logFailure("pager-state", throwable);
        }
    }

    public static void unbindPage(Object fragment) {
        try {
            if (fragment == null) return;
            Page page = PAGES.remove(fragment);
            if (page == null) return;

            View root = page.root.get();
            if (root != null && page.attachListener != null) {
                root.removeOnAttachStateChangeListener(page.attachListener);
            }
            page.active = false;
            releaseInlinePlayer(page, false);

            if (page.session != null) {
                WeakReference<Page> reference = page.session.pages.get(page.position);
                if (reference != null && reference.get() == page) {
                    page.session.pages.remove(page.position);
                }
            }
            Log.d(LOG_TAG, MARKER + ": unbind position=" + page.position);
        } catch (Throwable throwable) {
            logFailure("unbind", throwable);
        }
    }

    private static Session sessionFor(Object activity) {
        Session session = SESSIONS.get(activity);
        if (session == null) {
            session = new Session();
            SESSIONS.put(activity, session);
        }
        return session;
    }

    private static Page recoverPage(Object fragment) {
        Object activity = callNoArg(fragment, "getActivity");
        if (activity == null) return null;

        Integer position = positionFromTag(callNoArg(fragment, "getTag"));
        if (position == null) {
            Boolean visible = callBooleanObject(fragment, "getUserVisibleHint");
            if (Boolean.TRUE.equals(visible)) {
                position = sessionFor(activity).selectedPosition;
            }
        }
        if (position == null || position < 0) return null;

        Session session = sessionFor(activity);
        Page page = new Page(fragment);
        page.session = session;
        page.position = position;
        PAGES.put(fragment, page);
        session.pages.put(position, new WeakReference<>(page));
        return page;
    }

    private static Integer positionFromTag(Object tagValue) {
        if (!(tagValue instanceof String)) return null;
        String tag = (String) tagValue;
        int separator = tag.lastIndexOf(':');
        if (separator < 0 || separator == tag.length() - 1) return null;
        try {
            long itemId = Long.parseLong(tag.substring(separator + 1));
            if (itemId < 0 || itemId > Integer.MAX_VALUE) return null;
            return (int) itemId;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void updatePage(Page page) {
        if (page == null || page.session == null) return;
        if (page.position != page.session.selectedPosition) {
            stopPage(page);
            return;
        }
        if (page.session.pagerState != PAGER_STATE_IDLE) return;

        View root = page.root.get();
        ImageView imageView = page.imageView.get();
        if (root == null || imageView == null || !root.isAttachedToWindow()) return;
        if (!page.eligible || !autoplayAllowed(root.getContext())) {
            stopPage(page);
            Log.d(
                    LOG_TAG,
                    MARKER + ": poster position=" + page.position
                            + " eligible=" + page.eligible
            );
            return;
        }
        if (page.active && page.inlinePlayerStarted) return;

        page.active = true;
        page.inlinePlayerStarted = startInlinePlayer(page);
        Log.d(
                LOG_TAG,
                MARKER + ": start position=" + page.position
                        + " player=" + page.inlinePlayerStarted
        );
    }

    private static void stopPage(Page page) {
        if (page == null) return;
        page.active = false;
        releaseInlinePlayer(page, shouldHidePlayButton(page));
    }

    private static boolean startInlinePlayer(Page page) {
        ImageView imageView = page.imageView.get();
        if (imageView == null || page.animatedUrl == null || page.animatedUrl.length() == 0) {
            return false;
        }
        if (!(imageView.getParent() instanceof ViewGroup)) return false;

        Object fragment = page.fragment.get();
        Object activity = callNoArg(fragment, "getActivity");
        Context context = activity instanceof Context
                ? (Context) activity
                : imageView.getContext();
        if (context instanceof Activity && ((Activity) context).isDestroyed()) return false;

        try {
            ViewGroup parent = (ViewGroup) imageView.getParent();
            Class<?> playerViewClass = Class.forName(
                    "com.google.android.exoplayer2.ui.PlayerView"
            );
            Constructor<?> playerViewConstructor = playerViewClass.getConstructor(
                    Context.class,
                    AttributeSet.class
            );
            Object playerViewObject = playerViewConstructor.newInstance(context, null);
            if (!(playerViewObject instanceof View)) return false;

            View playerView = (View) playerViewObject;
            if (!replaceSurfaceViewWithTextureView(playerViewObject, playerViewClass, context)) {
                Log.w(
                        LOG_TAG,
                        MARKER + ": texture surface unavailable position=" + page.position
                );
                return false;
            }
            playerViewClass.getMethod("setUseController", boolean.class)
                    .invoke(playerViewObject, false);
            playerViewClass.getMethod("setResizeMode", int.class)
                    .invoke(playerViewObject, 0);
            playerViewClass.getMethod("setShutterBackgroundColor", int.class)
                    .invoke(playerViewObject, Color.TRANSPARENT);
            playerView.setBackgroundColor(Color.TRANSPARENT);
            playerView.setClickable(false);
            playerView.setFocusable(false);

            int imageIndex = parent.indexOfChild(imageView);
            if (imageIndex < 0) imageIndex = 0;
            parent.addView(
                    playerView,
                    imageIndex,
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )
            );

            page.inlinePlayerView = playerView;
            page.inlinePlayerParent = new WeakReference<>(parent);

            Class<?> playerBuilderClass = Class.forName(
                    "com.google.android.exoplayer2.k$b"
            );
            Object playerBuilder = playerBuilderClass.getConstructor(Context.class)
                    .newInstance(context);
            Object player = playerBuilderClass.getMethod("f").invoke(playerBuilder);
            page.inlinePlayer = player;

            Class<?> playerInterface = Class.forName("com.google.android.exoplayer2.w1");
            playerViewClass.getMethod("setPlayer", playerInterface)
                    .invoke(playerViewObject, player);

            Object mediaSource = buildMediaSource(context, page.animatedUrl);
            Class<?> exoPlayerInterface = Class.forName("com.google.android.exoplayer2.k");
            Class<?> mediaSourceInterface = Class.forName("s4.t");

            playerInterface.getMethod("setVolume", float.class).invoke(player, 0.0f);
            playerInterface.getMethod("S", int.class).invoke(player, PLAYER_REPEAT_ALL);
            exoPlayerInterface.getMethod("y", mediaSourceInterface).invoke(player, mediaSource);
            playerInterface.getMethod("f").invoke(player);
            playerInterface.getMethod("m", boolean.class).invoke(player, true);

            int generation = ++page.playerGeneration;
            scheduleReadyPoll(page, generation);
            Log.d(
                    LOG_TAG,
                    MARKER + ": exoplayer prepared position=" + page.position
                            + " source=" + mediaSourceName(mediaSourceType(page.animatedUrl))
            );
            return true;
        } catch (Throwable throwable) {
            logFailure("inline-player-start", throwable);
            releaseInlinePlayer(page, false);
            return false;
        }
    }

    /**
     * PlayerView defaults to SurfaceView, whose separate compositor layer does
     * not follow legacy ViewPager clipping and transforms reliably. Replace it
     * before setPlayer() so the video participates in the normal view tree.
     */
    private static boolean replaceSurfaceViewWithTextureView(
            Object playerViewObject,
            Class<?> playerViewClass,
            Context context
    ) throws Exception {
        if (!(playerViewObject instanceof ViewGroup)) return false;

        SurfaceView surfaceView = findSurfaceView((ViewGroup) playerViewObject);
        if (surfaceView == null || !(surfaceView.getParent() instanceof ViewGroup)) {
            return false;
        }

        Field surfaceField = findFieldHoldingValue(
                playerViewObject,
                playerViewClass,
                surfaceView
        );
        if (surfaceField == null) return false;

        ViewGroup parent = (ViewGroup) surfaceView.getParent();
        int index = parent.indexOfChild(surfaceView);
        if (index < 0) return false;

        ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
        TextureView textureView = new TextureView(context);
        textureView.setId(surfaceView.getId());
        textureView.setVisibility(surfaceView.getVisibility());
        textureView.setAlpha(surfaceView.getAlpha());
        textureView.setBackground(surfaceView.getBackground());

        parent.removeViewAt(index);
        try {
            parent.addView(textureView, index, layoutParams);
            surfaceField.set(playerViewObject, textureView);
        } catch (Exception exception) {
            if (textureView.getParent() == parent) parent.removeView(textureView);
            parent.addView(surfaceView, index, layoutParams);
            try {
                surfaceField.set(playerViewObject, surfaceView);
            } catch (Throwable ignored) {
            }
            throw exception;
        }

        Log.d(LOG_TAG, MARKER + ": surface=texture-view");
        return true;
    }

    private static SurfaceView findSurfaceView(ViewGroup root) {
        for (int index = 0; index < root.getChildCount(); index++) {
            View child = root.getChildAt(index);
            if (child instanceof SurfaceView) return (SurfaceView) child;
            if (child instanceof ViewGroup) {
                SurfaceView nested = findSurfaceView((ViewGroup) child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static Field findFieldHoldingValue(
            Object owner,
            Class<?> ownerClass,
            Object expectedValue
    ) throws IllegalAccessException {
        for (Class<?> type = ownerClass; type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.get(owner) == expectedValue) return field;
            }
        }
        return null;
    }

    private static Object buildMediaSource(Context context, String url)
            throws Exception {
        Class<?> boostHttpClass = Class.forName("tb.a");
        Method boostHttpMethod = boostHttpClass.getDeclaredMethod("b");
        boostHttpMethod.setAccessible(true);
        Object okHttpClient = boostHttpMethod.invoke(null);

        Class<?> callFactoryClass = Class.forName("okhttp3.Call$Factory");
        Class<?> dataSourceFactoryClass = Class.forName("x3.a$b");
        Object dataSourceFactory = dataSourceFactoryClass
                .getConstructor(callFactoryClass)
                .newInstance(okHttpClient);

        String userAgent = "Boost";
        try {
            Class<?> boostMediaUtilsClass = Class.forName("he.h0");
            Method userAgentMethod = boostMediaUtilsClass.getDeclaredMethod(
                    "T",
                    Context.class,
                    String.class
            );
            userAgentMethod.setAccessible(true);
            Object value = userAgentMethod.invoke(null, context, "Boost");
            if (value instanceof String) userAgent = (String) value;
        } catch (Throwable ignored) {
        }
        dataSourceFactory = dataSourceFactoryClass.getMethod("d", String.class)
                .invoke(dataSourceFactory, userAgent);

        Class<?> mediaItemClass = Class.forName("com.google.android.exoplayer2.x0");
        Object mediaItem = mediaItemClass.getMethod("d", Uri.class)
                .invoke(null, Uri.parse(url));

        Class<?> dataSourceFactoryInterface = Class.forName("m5.j$a");
        int sourceType = mediaSourceType(url);
        if (sourceType == MEDIA_SOURCE_DASH) {
            Class<?> dashChunkSourceFactoryInterface = Class.forName(
                    "com.google.android.exoplayer2.source.dash.a$a"
            );
            Class<?> dashChunkSourceFactoryClass = Class.forName(
                    "com.google.android.exoplayer2.source.dash.c$a"
            );
            Object dashChunkSourceFactory = dashChunkSourceFactoryClass
                    .getConstructor(dataSourceFactoryInterface)
                    .newInstance(dataSourceFactory);

            Class<?> dashMediaSourceFactoryClass = Class.forName(
                    "com.google.android.exoplayer2.source.dash.DashMediaSource$Factory"
            );
            Object dashMediaSourceFactory = dashMediaSourceFactoryClass
                    .getConstructor(
                            dashChunkSourceFactoryInterface,
                            dataSourceFactoryInterface
                    )
                    .newInstance(dashChunkSourceFactory, dataSourceFactory);
            return dashMediaSourceFactoryClass.getMethod("a", mediaItemClass)
                    .invoke(dashMediaSourceFactory, mediaItem);
        }

        if (sourceType == MEDIA_SOURCE_HLS) {
            Class<?> hlsMediaSourceFactoryClass = Class.forName(
                    "com.google.android.exoplayer2.source.hls.HlsMediaSource$Factory"
            );
            Object hlsMediaSourceFactory = hlsMediaSourceFactoryClass
                    .getConstructor(dataSourceFactoryInterface)
                    .newInstance(dataSourceFactory);
            return hlsMediaSourceFactoryClass.getMethod("a", mediaItemClass)
                    .invoke(hlsMediaSourceFactory, mediaItem);
        }

        Class<?> progressiveFactoryClass = Class.forName("s4.h0$b");
        Object progressiveFactory = progressiveFactoryClass
                .getConstructor(dataSourceFactoryInterface)
                .newInstance(dataSourceFactory);
        return progressiveFactoryClass.getMethod("b", mediaItemClass)
                .invoke(progressiveFactory, mediaItem);
    }

    private static void scheduleReadyPoll(final Page page, final int generation) {
        View playerView = page.inlinePlayerView;
        if (playerView == null) return;

        Runnable poll = new Runnable() {
            int attempts;

            @Override
            public void run() {
                if (
                        !page.active
                                || page.playerGeneration != generation
                                || page.inlinePlayer == null
                ) {
                    return;
                }

                try {
                    Class<?> playerInterface = Class.forName("com.google.android.exoplayer2.w1");
                    Object value = playerInterface.getMethod("Q").invoke(page.inlinePlayer);
                    int state = value instanceof Integer ? (Integer) value : -1;
                    if (state == PLAYER_STATE_READY) {
                        View host = page.inlinePlayerView;
                        if (host != null) {
                            host.postDelayed(
                                    () -> revealInlinePlayer(page, generation),
                                    READY_POLL_INTERVAL_MS
                            );
                        }
                        return;
                    }
                } catch (Throwable throwable) {
                    logFailure("inline-player-state", throwable);
                    failInlinePlayer(page, "state query failed");
                    return;
                }

                attempts++;
                View host = page.inlinePlayerView;
                if (attempts >= READY_POLL_LIMIT || host == null) {
                    failInlinePlayer(page, "ready timeout");
                    return;
                }
                host.postDelayed(this, READY_POLL_INTERVAL_MS);
            }
        };
        page.readyPoll = poll;
        playerView.post(poll);
    }

    private static void revealInlinePlayer(Page page, int generation) {
        if (
                !page.active
                        || page.playerGeneration != generation
                        || page.inlinePlayer == null
        ) {
            return;
        }

        ImageView imageView = page.imageView.get();
        if (imageView == null) {
            failInlinePlayer(page, "image view unavailable");
            return;
        }

        imageView.setVisibility(View.INVISIBLE);
        hidePlayButton(page);
        page.inlineReady = true;
        Log.d(LOG_TAG, MARKER + ": ready exoplayer position=" + page.position);
    }

    private static void failInlinePlayer(Page page, String reason) {
        Log.w(
                LOG_TAG,
                MARKER + ": exoplayer fallback position=" + page.position
                        + " reason=" + reason
        );
        releaseInlinePlayer(page, false);
    }

    private static void releaseInlinePlayer(Page page, boolean keepPlayButtonHidden) {
        if (page == null) return;
        page.playerGeneration++;

        View playerView = page.inlinePlayerView;
        Runnable readyPoll = page.readyPoll;
        if (playerView != null && readyPoll != null) {
            playerView.removeCallbacks(readyPoll);
        }

        restorePoster(page, keepPlayButtonHidden);

        Object player = page.inlinePlayer;
        if (playerView != null) {
            try {
                Class<?> playerViewClass = Class.forName(
                        "com.google.android.exoplayer2.ui.PlayerView"
                );
                Class<?> playerInterface = Class.forName("com.google.android.exoplayer2.w1");
                playerViewClass.getMethod("setPlayer", playerInterface)
                        .invoke(playerView, (Object) null);
            } catch (Throwable throwable) {
                logFailure("inline-player-detach", throwable);
            }
        }

        if (player != null) {
            try {
                Class<?> playerInterface = Class.forName("com.google.android.exoplayer2.w1");
                playerInterface.getMethod("release").invoke(player);
            } catch (Throwable throwable) {
                logFailure("inline-player-release", throwable);
            }
        }

        ViewGroup parent = page.inlinePlayerParent.get();
        if (parent != null && playerView != null && playerView.getParent() == parent) {
            parent.removeView(playerView);
        }

        page.inlinePlayer = null;
        page.inlinePlayerView = null;
        page.inlinePlayerParent = new WeakReference<>(null);
        page.readyPoll = null;
        page.inlinePlayerStarted = false;
        page.inlineReady = false;
    }

    private static void restorePoster(Page page, boolean keepPlayButtonHidden) {
        ImageView imageView = page.imageView.get();
        if (imageView != null) {
            int visibility = page.originalImageVisibility;
            imageView.setVisibility(
                    visibility == Integer.MIN_VALUE ? View.VISIBLE : visibility
            );
        }
        if (keepPlayButtonHidden) {
            hidePlayButton(page);
        } else {
            showPlayButton(page);
        }
    }

    private static boolean shouldHidePlayButton(Page page) {
        if (page == null || !page.eligible) return false;
        View root = page.root.get();
        return root != null && autoplayAllowed(root.getContext());
    }

    private static void hidePlayButton(Page page) {
        View playButton = page.playButton.get();
        if (playButton == null) return;
        int visibility = page.originalPlayButtonVisibility;
        playButton.setVisibility(visibility == Integer.MIN_VALUE ? View.VISIBLE : visibility);
        playButton.setAlpha(0.0f);
    }

    private static void showPlayButton(Page page) {
        View playButton = page.playButton.get();
        if (playButton == null) return;
        int visibility = page.originalPlayButtonVisibility;
        playButton.setVisibility(visibility == Integer.MIN_VALUE ? View.VISIBLE : visibility);
        playButton.setAlpha(
                Float.isNaN(page.originalPlayButtonAlpha)
                        ? 1.0f
                        : page.originalPlayButtonAlpha
        );
    }

    private static boolean autoplayAllowed(Context context) {
        if (context == null) return false;
        try {
            SharedPreferences preferences = context.getSharedPreferences(
                    context.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );

            int autoplay = readIntPreference(
                    preferences,
                    PREF_AUTOPLAY_SWIPE,
                    AUTOPLAY_FOLLOW_GENERAL
            );
            if (autoplay == AUTOPLAY_FOLLOW_GENERAL) {
                autoplay = readIntPreference(
                        preferences,
                        PREF_AUTOPLAY_CARDS,
                        AUTOPLAY_WIFI
                );
            }
            if (autoplay == AUTOPLAY_NEVER) return false;

            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean metered = connectivityManager == null
                    || connectivityManager.isActiveNetworkMetered();

            if (autoplay == AUTOPLAY_WIFI && metered) return false;
            if (metered && preferences.getBoolean(PREF_REDUCE_MOBILE, true)) return false;
            if (!metered && preferences.getBoolean(PREF_REDUCE_WIFI, false)) return false;

            if (
                    metered
                            && connectivityManager != null
                            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                            && connectivityManager.getRestrictBackgroundStatus()
                            == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            ) {
                return false;
            }

            return autoplay == AUTOPLAY_ALWAYS || autoplay == AUTOPLAY_WIFI;
        } catch (Throwable throwable) {
            logFailure("policy", throwable);
            return false;
        }
    }

    private static int readIntPreference(
            SharedPreferences preferences,
            String key,
            int defaultValue
    ) {
        try {
            return Integer.parseInt(preferences.getString(key, String.valueOf(defaultValue)));
        } catch (Throwable ignored) {
        }
        try {
            return preferences.getInt(key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private static boolean isSupportedMediaUrl(String value) {
        if (value == null || value.length() == 0) return false;
        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && host != null
                    && host.length() > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int mediaSourceType(String value) {
        if (value == null) return MEDIA_SOURCE_PROGRESSIVE;
        try {
            String path = Uri.parse(value).getPath();
            if (path == null) return MEDIA_SOURCE_PROGRESSIVE;
            path = path.toLowerCase(java.util.Locale.ROOT);
            if (path.endsWith(".mpd")) return MEDIA_SOURCE_DASH;
            if (path.endsWith(".m3u8")) return MEDIA_SOURCE_HLS;
        } catch (Throwable ignored) {
        }
        return MEDIA_SOURCE_PROGRESSIVE;
    }

    private static String mediaSourceName(int sourceType) {
        if (sourceType == MEDIA_SOURCE_DASH) return "dash";
        if (sourceType == MEDIA_SOURCE_HLS) return "hls";
        return "progressive";
    }

    private static void prune(Session session) {
        Iterator<Map.Entry<Integer, WeakReference<Page>>> iterator =
                session.pages.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().get() == null) iterator.remove();
        }
    }

    private static Object readField(Object target, String name) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Object callNoArg(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String callString(Object target, String name) {
        Object value = callNoArg(target, name);
        return value instanceof String ? (String) value : null;
    }

    private static boolean callBoolean(Object target, String name) {
        return Boolean.TRUE.equals(callBooleanObject(target, name));
    }

    private static Boolean callBooleanObject(Object target, String name) {
        Object value = callNoArg(target, name);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private static void logFailure(String operation, Throwable throwable) {
        Log.w(LOG_TAG, MARKER + ": " + operation + " failed", throwable);
    }
}
