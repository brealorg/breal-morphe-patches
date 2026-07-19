package app.morphe.extension.boostforreddit.giphy;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

import java.util.Locale;

public final class PreviewSizePreference extends ListPreference {
    private static final String SIZE_COMPACT = "compact";
    private static final String SIZE_BALANCED = "balanced";
    private static final String SIZE_LARGE = "large";
    private static final String DEFAULT_SIZE = SIZE_BALANCED;

    private static final CharSequence[] ENTRIES = new CharSequence[]{
            "Compact",
            "Balanced",
            "Large"
    };

    private static final CharSequence[] VALUES = new CharSequence[]{
            SIZE_COMPACT,
            SIZE_BALANCED,
            SIZE_LARGE
    };

    public PreviewSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        configure();
    }

    public PreviewSizePreference(Context context) {
        super(context);
        configure();
    }

    private void configure() {
        setEntries(ENTRIES);
        setEntryValues(VALUES);
        setDefaultValue(DEFAULT_SIZE);
        setDialogTitle("Preview size");

        String current = getValue();
        if (current == null) {
            setSummary(labelFor(DEFAULT_SIZE));
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

    private static String labelFor(String size) {
        String normalized = normalize(size);

        if (SIZE_COMPACT.equals(normalized)) {
            return "Compact";
        }

        if (SIZE_LARGE.equals(normalized)) {
            return "Large";
        }

        return "Balanced";
    }

    private static String normalize(String value) {
        if (value == null) {
            return DEFAULT_SIZE;
        }

        String normalized = value.trim().toLowerCase(Locale.US);

        if (SIZE_COMPACT.equals(normalized)
                || SIZE_BALANCED.equals(normalized)
                || SIZE_LARGE.equals(normalized)) {
            return normalized;
        }

        return DEFAULT_SIZE;
    }
}
