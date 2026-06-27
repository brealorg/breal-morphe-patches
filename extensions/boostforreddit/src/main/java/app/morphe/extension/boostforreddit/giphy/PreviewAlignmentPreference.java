package app.morphe.extension.boostforreddit.giphy;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

import java.util.Locale;

public final class PreviewAlignmentPreference extends ListPreference {
    private static final String ALIGNMENT_LEFT = "left";
    private static final String ALIGNMENT_CENTER = "center";
    private static final String ALIGNMENT_RIGHT = "right";
    private static final String DEFAULT_ALIGNMENT = ALIGNMENT_CENTER;

    private static final CharSequence[] ENTRIES = new CharSequence[]{
            "Left",
            "Center",
            "Right"
    };

    private static final CharSequence[] VALUES = new CharSequence[]{
            ALIGNMENT_LEFT,
            ALIGNMENT_CENTER,
            ALIGNMENT_RIGHT
    };

    public PreviewAlignmentPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        configure();
    }

    public PreviewAlignmentPreference(Context context) {
        super(context);
        configure();
    }

    private void configure() {
        setEntries(ENTRIES);
        setEntryValues(VALUES);
        setDefaultValue(DEFAULT_ALIGNMENT);
        setDialogTitle("Preview alignment");

        String current = getValue();
        if (current == null) {
            setSummary(labelFor(DEFAULT_ALIGNMENT));
        } else {
            setSummary(labelFor(current));
        }
    }

    @Override
    public void setValue(String value) {
        String normalized = normalize(value);
        super.setValue(normalized);
        setSummary(labelFor(normalized));
    }

    @Override
    public CharSequence getSummary() {
        return labelFor(getValue());
    }

    private static String labelFor(String alignment) {
        String normalized = normalize(alignment);

        if (ALIGNMENT_LEFT.equals(normalized)) {
            return "Left";
        }

        if (ALIGNMENT_RIGHT.equals(normalized)) {
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
