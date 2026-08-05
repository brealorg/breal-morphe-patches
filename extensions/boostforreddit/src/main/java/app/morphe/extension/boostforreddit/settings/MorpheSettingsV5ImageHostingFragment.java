/*
 * Modifications Copyright 2026 brealorg.
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */
package app.morphe.extension.boostforreddit.settings;

import android.app.Activity;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import app.morphe.extension.boostforreddit.upload.ExternalImageUploadSettings;
import app.morphe.extension.boostforreddit.utils.BoostSystemBarInsetsFix;

/** Morphe-owned Material settings page for image-upload routing. */
public final class MorpheSettingsV5ImageHostingFragment extends Fragment {
    public static final String CONTRACT_MARKER =
            "MORPHE_BOOST_IMAGE_HOSTING_MATERIAL_SETTINGS_ISSUE66_V2";

    private MorpheSettingsV4Theme.Tokens tokens;
    private LinearLayout providerGroup;
    private LinearLayout imgBbSection;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            android.os.Bundle savedInstanceState
    ) {
        Context context = inflater.getContext();
        tokens = MorpheSettingsV4Theme.resolve(context);
        setHasOptionsMenu(true);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(tokens.background);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(32));
        scrollView.addView(
                content,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        content.addView(MorpheSettingsV14Ui.pageIntro(
                context,
                tokens,
                "Image posts and galleries use Reddit's native uploader. "
                        + "Images inserted into comments or text posts use "
                        + "Imgur by default, with ImgBB as an alternative."
        ));

        addSpace(content, 22);
        addSectionLabel(content, "Post media");
        addSpace(content, 7);

        LinearLayout postGroup = addGroup(content);
        addStaticRow(
                postGroup,
                "Image posts and galleries",
                "Reddit native"
        );

        addSpace(content, 22);
        addSectionLabel(content, "Comments and text posts");
        addSpace(content, 7);

        providerGroup = addGroup(content);
        rebuildProviderRows();

        imgBbSection = new LinearLayout(context);
        imgBbSection.setOrientation(LinearLayout.VERTICAL);
        content.addView(
                imgBbSection,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        rebuildImgBbSection();

        addSpace(content, 22);
        addSectionLabel(content, "Failure handling");
        addSpace(content, 7);

        LinearLayout behaviorGroup = addGroup(content);
        addStaticRow(
                behaviorGroup,
                "Automatic fallback",
                "Off — a failed upload stays with the selected provider"
        );

        TextView note = MorpheSettingsV14Ui.supportingText(
                context,
                tokens,
                "Provider changes apply to new comment and text-post uploads. "
                        + "GIF and video upload behavior is unchanged."
        );
        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        noteParams.topMargin = dp(10);
        content.addView(note, noteParams);

        return scrollView;
    }

    @Override
    public void onResume() {
        super.onResume();
        styleHost();
        if (providerGroup != null) {
            rebuildProviderRows();
        }
        if (imgBbSection != null) {
            rebuildImgBbSection();
        }
    }

    @Override
    public void onPause() {
        Activity activity = hostActivity();
        if (activity != null) {
            BoostSystemBarInsetsFix.clearMorpheSettingsV4SystemBars(activity);
        }
        super.onPause();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MorpheSettingsV5Search.prepareMenu(this, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MorpheSettingsV5Search.handleMenuItem(this, item)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void rebuildProviderRows() {
        providerGroup.removeAllViews();
        String selected = ExternalImageUploadSettings.getEditorProvider();

        addProviderRow(
                "Imgur",
                "Default for comments and text posts.",
                ExternalImageUploadSettings.PROVIDER_IMGUR,
                selected
        );
        addProviderRow(
                "ImgBB",
                "Alternative external host. Requires your personal API key.",
                ExternalImageUploadSettings.PROVIDER_IMGBB,
                selected
        );
    }

    private void addProviderRow(
            String title,
            String summary,
            String provider,
            String selected
    ) {
        MorpheSettingsV14Ui.ChoiceRow row =
                MorpheSettingsV14Ui.choiceRow(
                        requireContext(),
                        tokens,
                        title,
                        summary,
                        TextUtils.equals(provider, selected)
                );

        row.setOnClickListener(view -> {
            ExternalImageUploadSettings.save(provider, null);
            rebuildProviderRows();
            rebuildImgBbSection();
        });

        MorpheSettingsV14Ui.addSegmentedRow(
                providerGroup,
                row,
                tokens
        );
    }

    private void rebuildImgBbSection() {
        imgBbSection.removeAllViews();

        boolean selected = ExternalImageUploadSettings.PROVIDER_IMGBB.equals(
                ExternalImageUploadSettings.getEditorProvider()
        );
        imgBbSection.setVisibility(selected ? View.VISIBLE : View.GONE);

        if (!selected) {
            return;
        }

        addSpace(imgBbSection, 22);
        addSectionLabel(imgBbSection, "ImgBB API key");
        addSpace(imgBbSection, 7);

        String currentKey = ExternalImageUploadSettings.getImgBbApiKey();
        MorpheSettingsV14Ui.Field field =
                MorpheSettingsV14Ui.outlinedField(
                        requireContext(),
                        tokens,
                        "Personal API key",
                        currentKey
                );
        field.input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        field.input.setHint("Required for ImgBB uploads");
        imgBbSection.addView(
                field.root,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TextView save = MorpheSettingsV14Ui.action(
                requireContext(),
                tokens,
                "Save API key",
                true
        );
        save.setOnClickListener(view -> {
            String value = field.input.getText().toString().trim();
            if (TextUtils.isEmpty(value)) {
                field.input.setError("An ImgBB API key is required");
                field.input.requestFocus();
                return;
            }

            ExternalImageUploadSettings.save(
                    ExternalImageUploadSettings.PROVIDER_IMGBB,
                    value
            );
            Toast.makeText(
                    requireContext(),
                    "ImgBB API key saved",
                    Toast.LENGTH_SHORT
            ).show();
            rebuildImgBbSection();
        });

        LinearLayout.LayoutParams saveParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f
                );
        actions.addView(save, saveParams);

        if (!TextUtils.isEmpty(currentKey)) {
            TextView remove = MorpheSettingsV14Ui.action(
                    requireContext(),
                    tokens,
                    "Remove key",
                    false
            );
            remove.setOnClickListener(view -> {
                ExternalImageUploadSettings.save(
                        ExternalImageUploadSettings.PROVIDER_IMGBB,
                        ""
                );
                Toast.makeText(
                        requireContext(),
                        "ImgBB API key removed",
                        Toast.LENGTH_SHORT
                ).show();
                rebuildImgBbSection();
            });

            LinearLayout.LayoutParams removeParams =
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f
                    );
            removeParams.setMarginStart(dp(10));
            actions.addView(remove, removeParams);
        }

        LinearLayout.LayoutParams actionsParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        actionsParams.topMargin = dp(12);
        imgBbSection.addView(actions, actionsParams);

        TextView note = MorpheSettingsV14Ui.supportingText(
                requireContext(),
                tokens,
                "Use a personal key from api.imgbb.com. The key is stored "
                        + "in Boost's local preferences and is used only for "
                        + "ImgBB uploads."
        );
        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        noteParams.topMargin = dp(6);
        imgBbSection.addView(note, noteParams);
    }

    private void addStaticRow(
            LinearLayout group,
            String title,
            String summary
    ) {
        LinearLayout row = MorpheSettingsV14Ui.baseRow(
                requireContext(),
                tokens
        );
        row.setClickable(false);
        row.setFocusable(false);

        LinearLayout labels = MorpheSettingsV14Ui.labels(
                requireContext(),
                tokens,
                title,
                summary
        );
        row.addView(
                labels,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1.0f
                )
        );

        MorpheSettingsV14Ui.addSegmentedRow(group, row, tokens);
    }

    private LinearLayout addGroup(LinearLayout parent) {
        LinearLayout group = MorpheSettingsV14Ui.group(requireContext());
        parent.addView(
                group,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
        return group;
    }

    private void addSectionLabel(LinearLayout parent, String value) {
        parent.addView(MorpheSettingsV14Ui.sectionLabel(
                requireContext(),
                tokens,
                value
        ));
    }

    private void addSpace(LinearLayout parent, int heightDp) {
        View space = new View(requireContext());
        parent.addView(
                space,
                new LinearLayout.LayoutParams(1, dp(heightDp))
        );
    }

    private void styleHost() {
        Activity activity = hostActivity();
        if (activity == null || tokens == null) {
            return;
        }

        activity.setTitle("Image hosting");
        BoostSystemBarInsetsFix.applyMorpheSettingsV4SystemBars(
                activity,
                tokens.background,
                tokens.dark
        );

        int toolbarId = MorpheSettingsV4Catalog.resourceId(
                activity,
                "id",
                "toolbar"
        );
        View toolbar = toolbarId == 0
                ? null
                : activity.findViewById(toolbarId);
        if (toolbar != null) {
            toolbar.setBackgroundColor(tokens.background);
            toolbar.setElevation(0);
        }
    }

    private Activity hostActivity() {
        Context context = getContext();
        return context instanceof Activity ? (Activity) context : null;
    }

    private int dp(float value) {
        return MorpheSettingsV4Theme.dp(requireContext(), value);
    }
}
