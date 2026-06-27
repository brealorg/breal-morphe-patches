package app.morphe.extension.boostforreddit.giphy;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;

import java.util.Locale;

public final class PreviewAlignmentPreference extends DialogPreference {
    private static final String ALIGNMENT_LEFT = "left";
    private static final String ALIGNMENT_CENTER = "center";
    private static final String ALIGNMENT_RIGHT = "right";
    private static final String DEFAULT_ALIGNMENT = ALIGNMENT_CENTER;

    private static final CharSequence[] ENTRIES = new CharSequence[]{
            "Left",
            "Center",
            "Right"
    };

    private static final String[] VALUES = new String[]{
            ALIGNMENT_LEFT,
            ALIGNMENT_CENTER,
            ALIGNMENT_RIGHT
    };

    private String value = DEFAULT_ALIGNMENT;

    public PreviewAlignmentPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(true);
        setDialogTitle("Preview alignment");
        updateSummary(getPersistedString(DEFAULT_ALIGNMENT));
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return normalize(a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        String initial;

        if (restorePersistedValue) {
            initial = getPersistedString(DEFAULT_ALIGNMENT);
        } else if (defaultValue instanceof String) {
            initial = (String) defaultValue;
        } else {
            initial = DEFAULT_ALIGNMENT;
        }

        setValue(initial);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        final int checked = indexOf(getPersistedString(value));

        builder.setSingleChoiceItems(ENTRIES, checked, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which >= 0 && which < VALUES.length) {
                    setValue(VALUES[which]);
                }

                dialog.dismiss();
            }
        });
    }

    private void setValue(String newValue) {
        String normalized = normalize(newValue);
        value = normalized;
        persistString(normalized);
        updateSummary(normalized);
        notifyChanged();
    }

    private void updateSummary(String alignment) {
        setSummary(labelFor(normalize(alignment)));
    }

    private static int indexOf(String rawValue) {
        String normalized = normalize(rawValue);

        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].equals(normalized)) {
                return i;
            }
        }

        return 1;
    }

    private static String labelFor(String alignment) {
        if (ALIGNMENT_LEFT.equals(alignment)) {
            return "Left";
        }

        if (ALIGNMENT_RIGHT.equals(alignment)) {
            return "Right";
        }

        return "Center";
    }

    private static String normalize(String value) {
        if (value == null) {
            return DEFAULT_ALIGNMENT;
        }

        String normalized = value.trim().toLowerCase(Locale.US);

        if (ALIGNMENT_LEFT.equals(normalized)
                || ALIGNMENT_CENTER.equals(normalized)
                || ALIGNMENT_RIGHT.equals(normalized)) {
            return normalized;
        }

        return DEFAULT_ALIGNMENT;
    }
}
