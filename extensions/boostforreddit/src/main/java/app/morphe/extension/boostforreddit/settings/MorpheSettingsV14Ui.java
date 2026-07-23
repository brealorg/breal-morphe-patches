package app.morphe.extension.boostforreddit.settings;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared, ABI-safe Material 3 view primitives for the Settings v4 shell.
 *
 * <p>The Boost 1.12.12 APK does not expose every Material Components class.
 * These primitives intentionally depend only on framework Views that are
 * already part of Boost while implementing the M3 list, selection, field,
 * state, spacing, and accessibility contracts used by Morphe settings.</p>
 */
final class MorpheSettingsV14Ui {
    static final String CONTRACT_MARKER =
            "MORPHE_BOOST_SETTINGS_V14_COHERENT_UI_ISSUE106_V1";
    static final String ABI_MARKER =
            "MORPHE_BOOST_SETTINGS_V14_FRAMEWORK_VIEW_ABI_ISSUE106_V1";
    static final String SEGMENTED_LIST_MARKER =
            "MORPHE_BOOST_SETTINGS_V14_SEGMENTED_LISTS_ISSUE106_V1";
    static final String ACCESSIBILITY_MARKER =
            "MORPHE_BOOST_SETTINGS_V14_SELECTION_A11Y_ISSUE106_V1";

    private static final int ROW_SINGLE_DP = 56;
    private static final int ROW_TWO_LINE_DP = 72;
    private static final int GROUP_GAP_DP = 4;
    private static final float OUTER_RADIUS_DP = 18.0f;
    private static final float INNER_RADIUS_DP = 6.0f;

    private MorpheSettingsV14Ui() {
    }

    static LinearLayout group(Context context) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setClipChildren(false);
        group.setClipToPadding(false);
        return group;
    }

    static LinearLayout baseRow(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, ROW_SINGLE_DP));
        row.setPadding(dp(context, 16), dp(context, 8), dp(context, 12), dp(context, 8));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(interactiveShape(
                context,
                rowColor(tokens, false),
                corners(context, true, true),
                tokens.navigationAccent().color
        ));
        return row;
    }

    static LinearLayout labels(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens,
            String titleValue,
            String summaryValue
    ) {
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text(context, titleValue, 16, tokens.textPrimary);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(title);

        if (!TextUtils.isEmpty(summaryValue)) {
            TextView summary = text(
                    context,
                    summaryValue,
                    14,
                    tokens.textSecondary
            );
            summary.setMaxLines(3);
            summary.setEllipsize(TextUtils.TruncateAt.END);
            summary.setLineSpacing(0, 1.06f);
            LinearLayout.LayoutParams params = wrapParams();
            params.topMargin = dp(context, 2);
            labels.addView(summary, params);
        }
        return labels;
    }

    static TextView sectionLabel(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens,
            String value
    ) {
        TextView label = text(context, value, 14, tokens.primary);
        label.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.NORMAL
        ));
        label.setLetterSpacing(0.01f);
        label.setPadding(dp(context, 4), 0, dp(context, 4), 0);
        return label;
    }

    static TextView supportingText(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens,
            String value
    ) {
        TextView text = text(context, value, 14, tokens.textSecondary);
        text.setLineSpacing(0, 1.08f);
        text.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), 0);
        return text;
    }

    static void addSegmentedRow(
            LinearLayout group,
            View row,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        if (group.getChildCount() > 0) {
            params.topMargin = dp(group.getContext(), GROUP_GAP_DP);
        }
        group.addView(row, params);
        restyleGroup(group, tokens);
    }

    static ChoiceRow choiceRow(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens,
            String title,
            String summary,
            boolean checked
    ) {
        return new ChoiceRow(context, tokens, title, summary, checked);
    }

    static View chevron(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        ChevronView chevron = new ChevronView(
                context,
                tokens.navigationAccent().color
        );
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        chevron.setContentDescription(null);
        return chevron;
    }

    static TextView action(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens,
            String value,
            boolean filled
    ) {
        TextView action = text(
                context,
                value,
                14,
                filled
                        ? tokens.navigationAccent().onContainer
                        : tokens.navigationAccent().color
        );
        action.setGravity(Gravity.CENTER);
        action.setMinHeight(dp(context, 48));
        action.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        action.setClickable(true);
        action.setFocusable(true);
        int color = filled
                ? tokens.navigationAccent().container
                : MorpheSettingsV4Theme.blend(
                        tokens.surfaceContainer,
                        tokens.navigationAccent().container,
                        tokens.dark ? 0.12f : 0.09f
                );
        action.setBackground(MorpheSettingsV4Theme.interactive(
                context,
                color,
                24,
                tokens.navigationAccent().color
        ));
        return action;
    }

    static Field outlinedField(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens,
            String labelValue,
            String value
    ) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        root.setMinimumHeight(dp(context, 64));
        root.setBackground(fieldBackground(context, tokens));

        TextView label = text(context, labelValue, 12, tokens.primary);
        root.addView(label, matchWrapParams());

        EditText input = new EditText(context);
        input.setText(value);
        input.setTextSize(16);
        input.setTextColor(tokens.textPrimary);
        input.setHintTextColor(tokens.textSecondary);
        input.setSingleLine(true);
        input.setPadding(0, 0, 0, 0);
        input.setBackgroundColor(0x00000000);
        input.setOnFocusChangeListener((view, focused) -> root.setSelected(focused));
        root.addView(input, matchWrapParams());
        return new Field(root, input);
    }

    static Drawable roundedSurface(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens,
            boolean selected
    ) {
        return interactiveShape(
                context,
                rowColor(tokens, selected),
                corners(context, true, true),
                tokens.navigationAccent().color
        );
    }

    private static void restyleGroup(
            LinearLayout group,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        int count = group.getChildCount();
        for (int index = 0; index < count; index++) {
            View child = group.getChildAt(index);
            boolean selected = child instanceof Checkable
                    && ((Checkable) child).isChecked();
            child.setBackground(interactiveShape(
                    group.getContext(),
                    rowColor(tokens, selected),
                    corners(
                            group.getContext(),
                            index == 0,
                            index == count - 1
                    ),
                    tokens.navigationAccent().color
            ));
            if (child instanceof ChoiceRow) {
                ((ChoiceRow) child).applyCheckedColors();
            }
        }
    }

    private static int rowColor(
            MorpheSettingsV4Theme.Tokens tokens,
            boolean selected
    ) {
        if (selected) {
            return MorpheSettingsV4Theme.blend(
                    tokens.surfaceContainerHigh,
                    tokens.secondaryContainer,
                    tokens.dark ? 0.74f : 0.68f
            );
        }
        return MorpheSettingsV4Theme.blend(
                tokens.surfaceContainer,
                tokens.secondaryContainer,
                tokens.dark ? 0.055f : 0.04f
        );
    }

    private static Drawable fieldBackground(
            Context context,
            MorpheSettingsV4Theme.Tokens tokens
    ) {
        GradientDrawable focused = shape(
                context,
                tokens.surfaceContainerHigh,
                corners(context, true, true)
        );
        focused.setStroke(dp(context, 2), tokens.navigationAccent().color);
        GradientDrawable normal = shape(
                context,
                tokens.surfaceContainer,
                corners(context, true, true)
        );
        normal.setStroke(dp(context, 1), tokens.outline);

        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected}, focused);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[0], normal);
        return states;
    }

    private static Drawable interactiveShape(
            Context context,
            int color,
            float[] corners,
            int rippleColor
    ) {
        Drawable content = shape(context, color, corners);
        if (Build.VERSION.SDK_INT < 21) {
            return content;
        }
        return new RippleDrawable(
                ColorStateList.valueOf(
                        MorpheSettingsV4Theme.withAlpha(rippleColor, 42)
                ),
                content,
                null
        );
    }

    private static GradientDrawable shape(
            Context context,
            int color,
            float[] corners
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(corners);
        return drawable;
    }

    private static float[] corners(
            Context context,
            boolean first,
            boolean last
    ) {
        float top = dp(context, first ? OUTER_RADIUS_DP : INNER_RADIUS_DP);
        float bottom = dp(context, last ? OUTER_RADIUS_DP : INNER_RADIUS_DP);
        return new float[]{
                top, top,
                top, top,
                bottom, bottom,
                bottom, bottom
        };
    }

    private static TextView text(
            Context context,
            String value,
            int sizeSp,
            int color
    ) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        return text;
    }

    private static int dp(Context context, float value) {
        return MorpheSettingsV4Theme.dp(context, value);
    }

    private static LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private static LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    static final class Field {
        final LinearLayout root;
        final EditText input;

        Field(LinearLayout root, EditText input) {
            this.root = root;
            this.input = input;
        }
    }

    static final class ChoiceRow extends LinearLayout implements Checkable {
        private final MorpheSettingsV4Theme.Tokens tokens;
        private final TextView title;
        private final TextView summary;
        private final RadioIndicator indicator;
        private boolean checked;

        ChoiceRow(
                Context context,
                MorpheSettingsV4Theme.Tokens tokens,
                String titleValue,
                String summaryValue,
                boolean checked
        ) {
            super(context);
            this.tokens = tokens;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(context, TextUtils.isEmpty(summaryValue)
                    ? ROW_SINGLE_DP
                    : ROW_TWO_LINE_DP));
            setPadding(dp(context, 16), dp(context, 8), dp(context, 12), dp(context, 8));
            setClickable(true);
            setFocusable(true);

            LinearLayout labels = MorpheSettingsV14Ui.labels(
                    context,
                    tokens,
                    titleValue,
                    summaryValue
            );
            title = (TextView) labels.getChildAt(0);
            summary = labels.getChildCount() > 1
                    ? (TextView) labels.getChildAt(1)
                    : null;
            LayoutParams labelsParams = new LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            addView(labels, labelsParams);

            indicator = new RadioIndicator(context, tokens);
            indicator.setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
            );
            addView(indicator, new LayoutParams(dp(context, 48), dp(context, 48)));
            setChecked(checked);
        }

        @Override
        public void setChecked(boolean checked) {
            this.checked = checked;
            indicator.setChecked(checked);
            applyCheckedColors();
            ViewParentCompat.restyleParent(this, tokens);
            refreshDrawableState();
        }

        @Override
        public boolean isChecked() {
            return checked;
        }

        @Override
        public void toggle() {
            setChecked(!checked);
        }

        @Override
        public CharSequence getAccessibilityClassName() {
            return "android.widget.RadioButton";
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(
                AccessibilityNodeInfo info
        ) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setCheckable(true);
            info.setChecked(checked);
        }

        void setTitleTypeface(android.graphics.Typeface typeface) {
            title.setTypeface(typeface);
        }

        void setTitleSize(float sizeSp) {
            title.setTextSize(sizeSp);
        }

        private void applyCheckedColors() {
            title.setTextColor(checked
                    ? tokens.onSecondaryContainer
                    : tokens.textPrimary);
            if (summary != null) {
                summary.setTextColor(checked
                        ? MorpheSettingsV4Theme.blend(
                                tokens.onSecondaryContainer,
                                tokens.secondaryContainer,
                                0.24f
                        )
                        : tokens.textSecondary);
            }
        }
    }

    interface OnToggleChangedListener {
        void onToggleChanged(Toggle toggle, boolean checked);
    }

    /**
     * M3-sized switch drawn with framework Canvas APIs. The complete View is a
     * 56 x 48 dp touch target; the visual track is 52 x 32 dp.
     */
    static final class Toggle extends View implements Checkable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private final MorpheSettingsV4Theme.Tokens tokens;
        private boolean checked;
        private OnToggleChangedListener listener;

        Toggle(
                Context context,
                MorpheSettingsV4Theme.Tokens tokens,
                boolean checked
        ) {
            super(context);
            this.tokens = tokens;
            this.checked = checked;
            setClickable(true);
            setFocusable(true);
            setOnClickListener(view -> toggle());
        }

        void setOnCheckedChangeListener(OnToggleChangedListener listener) {
            this.listener = listener;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(getContext(), 56), dp(getContext(), 48));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(getContext(), 2);
            float top = dp(getContext(), 8);
            float right = getWidth() - dp(getContext(), 2);
            float bottom = getHeight() - dp(getContext(), 8);
            track.set(left, top, right, bottom);
            float radius = dp(getContext(), 16);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(checked
                    ? tokens.navigationAccent().color
                    : MorpheSettingsV4Theme.blend(
                            tokens.surfaceContainerHigh,
                            tokens.outline,
                            tokens.dark ? 0.42f : 0.30f
                    ));
            canvas.drawRoundRect(track, radius, radius, paint);

            if (!checked) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(getContext(), 2));
                paint.setColor(tokens.outline);
                canvas.drawRoundRect(track, radius, radius, paint);
            }

            float thumbRadius = dp(getContext(), checked ? 12 : 8);
            float centerX = checked
                    ? right - dp(getContext(), 16)
                    : left + dp(getContext(), 16);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(checked
                    ? tokens.navigationAccent().onContainer
                    : tokens.textSecondary);
            canvas.drawCircle(
                    centerX,
                    getHeight() / 2.0f,
                    thumbRadius,
                    paint
            );
        }

        @Override
        public void setChecked(boolean checked) {
            if (this.checked == checked) {
                return;
            }
            this.checked = checked;
            invalidate();
            refreshDrawableState();
            if (listener != null) {
                listener.onToggleChanged(this, checked);
            }
        }

        void setCheckedSilently(boolean checked) {
            if (this.checked == checked) {
                return;
            }
            this.checked = checked;
            invalidate();
            refreshDrawableState();
        }

        @Override
        public boolean isChecked() {
            return checked;
        }

        @Override
        public void toggle() {
            setChecked(!checked);
        }

        @Override
        public CharSequence getAccessibilityClassName() {
            return "android.widget.Switch";
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(
                AccessibilityNodeInfo info
        ) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setCheckable(true);
            info.setChecked(checked);
        }
    }

    private static final class ViewParentCompat {
        private ViewParentCompat() {
        }

        static void restyleParent(
                View child,
                MorpheSettingsV4Theme.Tokens tokens
        ) {
            if (child.getParent() instanceof LinearLayout) {
                restyleGroup((LinearLayout) child.getParent(), tokens);
            }
        }
    }

    private static final class RadioIndicator extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final MorpheSettingsV4Theme.Tokens tokens;
        private boolean checked;

        RadioIndicator(
                Context context,
                MorpheSettingsV4Theme.Tokens tokens
        ) {
            super(context);
            this.tokens = tokens;
        }

        void setChecked(boolean checked) {
            this.checked = checked;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() / 2.0f;
            float centerY = getHeight() / 2.0f;
            float radius = dp(getContext(), 10);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2));
            paint.setColor(checked
                    ? tokens.navigationAccent().color
                    : tokens.textSecondary);
            canvas.drawCircle(centerX, centerY, radius, paint);
            if (checked) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(
                        centerX,
                        centerY,
                        dp(getContext(), 5),
                        paint
                );
            }
        }
    }

    private static final class ChevronView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChevronView(Context context, int color) {
            super(context);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(context, 2));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(getContext(), 40), dp(getContext(), 48));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x = getWidth() * 0.45f;
            float middle = getHeight() * 0.5f;
            float size = dp(getContext(), 5);
            canvas.drawLine(x, middle - size, x + size, middle, paint);
            canvas.drawLine(x + size, middle, x, middle + size, paint);
        }
    }
}
