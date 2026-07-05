package app.morphe.extension.boostforreddit.gifsearch;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContentInfo;
import android.view.OnReceiveContentListener;
import android.view.View;
import android.widget.EditText;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class ImeGifInputConnection {
    private static final String TAG = "MorpheImeGif";

    private static final String[] MIME_TYPES = new String[] {
            "image/gif",
            "image/webp",
            "image/png",
            "image/*"
    };

    private static final Set<View> ENABLED_EDITORS =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private ImeGifInputConnection() {
    }

    public static void enableForEditText(EditText editText) {
        if (editText == null) {
            return;
        }

        ENABLED_EDITORS.add(editText);

        if (Build.VERSION.SDK_INT >= 31) {
            Api31ReceiveContent.register(editText);
        } else {
            Log.d(TAG, "MORPHE_IME_GIF_RECEIVE_REGISTER_SKIPPED sdk=" + Build.VERSION.SDK_INT);
        }

        Log.d(TAG, "MORPHE_IME_GIF_ENABLE view=" + describeView(editText)
                + " mimeTypes=" + Arrays.toString(MIME_TYPES));
    }

    private static boolean isEnabled(View view) {
        return view != null && ENABLED_EDITORS.contains(view);
    }

    @TargetApi(31)
    private static final class Api31ReceiveContent {
        private Api31ReceiveContent() {
        }

        static void register(final EditText editText) {
            editText.setOnReceiveContentListener(MIME_TYPES, new OnReceiveContentListener() {
                @Override
                public ContentInfo onReceiveContent(View view, ContentInfo payload) {
                    Log.d(TAG, "MORPHE_IME_GIF_RECEIVE_CONTENT_API31 view="
                            + describeView(view)
                            + " source="
                            + safeSource(payload)
                            + " flags="
                            + safeFlags(payload));

                    boolean handled = handleApi31Content(view, payload);
                    return handled ? null : payload;
                }
            });

            Log.d(TAG, "MORPHE_IME_GIF_RECEIVE_REGISTER_API31 view="
                    + describeView(editText)
                    + " mimeTypes="
                    + Arrays.toString(MIME_TYPES));
        }

        private static int safeSource(ContentInfo payload) {
            if (payload == null) {
                return -1;
            }

            try {
                return payload.getSource();
            } catch (Throwable ignored) {
                return -1;
            }
        }

        private static int safeFlags(ContentInfo payload) {
            if (payload == null) {
                return -1;
            }

            try {
                return payload.getFlags();
            } catch (Throwable ignored) {
                return -1;
            }
        }
    }

    @TargetApi(31)
    private static boolean handleApi31Content(View view, ContentInfo payload) {
        if (!isEnabled(view) || payload == null) {
            return false;
        }

        Uri linkUri = null;
        ClipData clip = null;

        try {
            linkUri = payload.getLinkUri();
        } catch (Throwable ignored) {
        }

        try {
            clip = payload.getClip();
        } catch (Throwable ignored) {
        }

        Log.d(TAG, "MORPHE_IME_GIF_CONTENT_API31 linkUri=" + linkUri
                + " clipItems=" + (clip == null ? -1 : clip.getItemCount()));

        String url = publicUrl(linkUri);

        if (TextUtils.isEmpty(url) && clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                ClipData.Item item = clip.getItemAt(i);

                CharSequence text = null;
                Uri itemUri = null;

                try {
                    text = item.getText();
                } catch (Throwable ignored) {
                }

                try {
                    itemUri = item.getUri();
                } catch (Throwable ignored) {
                }

                Log.d(TAG, "MORPHE_IME_GIF_CLIP_ITEM index=" + i
                        + " text=" + text
                        + " uri=" + itemUri);

                url = publicUrl(itemUri);

                if (TextUtils.isEmpty(url) && text != null) {
                    url = firstPublicUrl(text.toString());
                }

                if (!TextUtils.isEmpty(url)) {
                    break;
                }
            }
        }

        if (!TextUtils.isEmpty(url)) {
            return insertText(view, url);
        }

        Log.d(TAG, "MORPHE_IME_GIF_NO_PUBLIC_URL_API31 linkUri=" + linkUri);
        return false;
    }

    private static String firstPublicUrl(String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }

        String[] parts = text.split("\\s+");

        for (String part : parts) {
            String cleaned = part.trim();

            if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                return cleaned;
            }
        }

        return null;
    }

    private static String publicUrl(Uri uri) {
        if (uri == null) {
            return null;
        }

        String scheme = uri.getScheme();

        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            return uri.toString();
        }

        return null;
    }

    private static boolean insertText(View view, String text) {
        if (!(view instanceof EditText) || TextUtils.isEmpty(text)) {
            return false;
        }

        EditText editText = (EditText) view;
        Editable editable = editText.getText();

        if (editable == null) {
            return false;
        }

        int start = Math.max(0, editText.getSelectionStart());
        int end = Math.max(0, editText.getSelectionEnd());

        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }

        editable.replace(start, end, text);
        editText.setSelection(start + text.length());

        Log.d(TAG, "MORPHE_IME_GIF_INSERT_OK text=" + text);
        return true;
    }

    private static String describeView(View view) {
        if (view == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder(view.getClass().getName());

        try {
            int id = view.getId();

            if (id != View.NO_ID) {
                sb.append("#").append(view.getResources().getResourceEntryName(id));
            }
        } catch (Throwable ignored) {
        }

        return sb.toString();
    }
}
