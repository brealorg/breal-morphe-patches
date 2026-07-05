package app.morphe.extension.boostforreddit.gifsearch;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class GifUrlInsertion {
    private static final int ATTACH_CAMERA_ID = 0x7f0a0131;
    private static final int ATTACH_GALLERY_ID = 0x7f0a0132;
    private static final int INSERT_GIF_URL_ID = 0x7f0a029c;

    private GifUrlInsertion() {
    }

    public static void handleImageMenuOption(Object formattingBar, Object menuOption) {
        int id = readMenuOptionId(menuOption);

        if (id == INSERT_GIF_URL_ID) {
            open(formattingBar);
            return;
        }

        if (id == ATTACH_CAMERA_ID) {
            callOriginalImageListener(formattingBar, "a");
            return;
        }

        if (id == ATTACH_GALLERY_ID) {
            callOriginalImageListener(formattingBar, "b");
        }
    }

    private static int readMenuOptionId(Object menuOption) {
        try {
            Method method = menuOption.getClass().getMethod("q");
            Object value = method.invoke(menuOption);
            return value instanceof Integer ? ((Integer) value).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void callOriginalImageListener(Object formattingBar, String methodName) {
        Object listener = findOriginalImageListener(formattingBar);
        if (listener == null) {
            return;
        }

        try {
            Method method = listener.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(listener);
        } catch (Throwable ignored) {
        }
    }

    private static Object findOriginalImageListener(Object formattingBar) {
        Class<?> type = formattingBar.getClass();

        while (type != null) {
            Field[] fields = type.getDeclaredFields();

            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(formattingBar);

                    if (value != null && hasMethod(value, "a") && hasMethod(value, "b")) {
                        return value;
                    }
                } catch (Throwable ignored) {
                }
            }

            type = type.getSuperclass();
        }

        return null;
    }

    private static boolean hasMethod(Object target, String name) {
        try {
            target.getClass().getDeclaredMethod(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void open(Object formattingBar) {
        if (!(formattingBar instanceof View)) {
            return;
        }

        Context context = ((View) formattingBar).getContext();
        if (context == null) {
            return;
        }

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://media.giphy.com/media/.../giphy.gif");

        new AlertDialog.Builder(context)
                .setTitle("Insert GIF URL")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String url = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        insertRawUrl(formattingBar, url);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void insertRawUrl(Object formattingBar, String url) {
        try {
            Method method = formattingBar.getClass().getMethod("p", String.class);
            method.invoke(formattingBar, url);
        } catch (Throwable ignored) {
        }
    }
}
