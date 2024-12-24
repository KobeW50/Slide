package me.edgan.redditslide.Fragments;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.commons.R;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 * https://github.com/afollestad/material-dialogs/
 * <p>
 * Directly based on 0.9.6.0 release source, adapted to support https://github.com/ccrama/Slide/pull/3144
 * alongside some miscellaneous code improvements.
 */
public class FolderChooserDialogCreate extends DialogFragment implements MaterialDialog.ListCallback {

    private static final String DEFAULT_TAG = "[MD_FOLDER_SELECTOR]";

    private File parentFolder;
    private File[] parentContents;
    private boolean canGoUp = false;
    private FolderCallback callback;

    String[] getContentsArray() {
        if (parentContents == null) {
            if (canGoUp) {
                return new String[]{getBuilder().goUpLabel};
            }
            return new String[]{};
        }
        final String[] results = new String[parentContents.length + (canGoUp ? 1 : 0)];
        if (canGoUp) {
            results[0] = getBuilder().goUpLabel;
        }
        for (int i = 0; i < parentContents.length; i++) {
            results[canGoUp ? i + 1 : i] = parentContents[i].getName();
        }
        return results;
    }

    File[] listFiles() {
        final File[] contents = parentFolder.listFiles();
        final List<File> results = new ArrayList<>();
        if (contents != null) {
            for (final File fi : contents) {
                if (fi.isDirectory()) {
                    results.add(fi);
                }
            }
            Collections.sort(results, new FolderSorter());
            return results.toArray(new File[0]);
        }
        return null;
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ActivityCompat.checkSelfPermission(
                getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
        ) {
            return new MaterialDialog.Builder(getActivity())
                    .title(R.string.md_error_label)
                    .content(R.string.md_storage_perm_error)
                    .positiveText(android.R.string.ok)
                    .build();
        }
        if (getArguments() == null || !getArguments().containsKey("builder")) {
            throw new IllegalStateException("You must create a FolderChooserDialog using the Builder.");
        }
        if (!getArguments().containsKey("current_path")) {
            getArguments().putString("current_path", getBuilder().initialPath);
        }
        parentFolder = new File(getArguments().getString("current_path"));
        checkIfCanGoUp();
        parentContents = listFiles();
        final MaterialDialog.Builder builder =
                new MaterialDialog.Builder(getActivity())
                        .typeface(getBuilder().mediumFont, getBuilder().regularFont)
                        .title(parentFolder.getAbsolutePath())
                        .items(getContentsArray())
                        .itemsCallback(this)
                        .onPositive((dialog, which) -> {
                            dialog.dismiss();
                            callback.onFolderSelection(
                                    FolderChooserDialogCreate.this,
                                    parentFolder,
                                    getBuilder().isSaveToLocation);
                        })
                        .onNegative((dialog, which) ->
                                dialog.dismiss())
                        .autoDismiss(false)
                        .positiveText(getBuilder().chooseButton)
                        .negativeText(getBuilder().cancelButton);

        if (getBuilder().allowNewFolder) {
            builder.neutralText(getBuilder().newFolderButton);
            builder.onNeutral((dialog, which) ->
                    createNewFolder());
        }
        if ("/".equals(getBuilder().initialPath)) {
            canGoUp = false;
        }
        return builder.build();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (callback != null) {
            callback.onFolderChooserDismissed(this);
        }
    }

    private void createNewFolder() {
        new MaterialDialog.Builder(getActivity())
                .title(getBuilder().newFolderButton)
                .input(0, 0, false, (dialog, input) -> {
                    final File newFile = new File(parentFolder, input.toString());
                    if (newFile.mkdir()) {
                        reload();
                    } else {
                        final String msg = "Unable to create folder "
                                + newFile.getAbsolutePath()
                                + ", make sure you have the READ_MEDIA_VISUAL_USER_SELECTED permission or root permissions.";
                        Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    @Override
    public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence s) {
        if (canGoUp && i == 0) {
            parentFolder = parentFolder.getParentFile();
            if (parentFolder != null && parentFolder.getAbsolutePath().equals("/storage/emulated")) {
                parentFolder = parentFolder.getParentFile();
            }
            if (parentFolder != null) {
                canGoUp = parentFolder.getParent() != null;
            }
        } else {
            parentFolder = parentContents[canGoUp ? i - 1 : i];
            canGoUp = true;
            if (parentFolder.getAbsolutePath().equals("/storage/emulated")) {
                parentFolder = Environment.getExternalStorageDirectory();
            }
        }
        reload();
    }

    private void checkIfCanGoUp() {
        try {
            canGoUp = parentFolder.getPath().split("/").length > 1;
        } catch (final IndexOutOfBoundsException e) {
            canGoUp = false;
        }
    }

    private void reload() {
        parentContents = listFiles();
        final MaterialDialog dialog = (MaterialDialog) getDialog();
        dialog.setTitle(parentFolder.getAbsolutePath());
        getArguments().putString("current_path", parentFolder.getAbsolutePath());
        dialog.setItems(getContentsArray());
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (getActivity() instanceof FolderCallback) {
            callback = (FolderCallback) getActivity();
        } else if (getParentFragment() instanceof FolderCallback) {
            callback = (FolderCallback) getParentFragment();
        } else {
            throw new IllegalStateException(
                    "FolderChooserDialog needs to be shown from an Activity/Fragment implementing FolderCallback.");
        }
    }

    public void show(final FragmentActivity fragmentActivity) {
        show(fragmentActivity.getSupportFragmentManager());
    }

    public void show(final FragmentManager fragmentManager) {
        final String tag = getBuilder().tag;
        final Fragment frag = fragmentManager.findFragmentByTag(tag);
        if (frag != null) {
            ((DialogFragment) frag).dismiss();
            fragmentManager.beginTransaction().remove(frag).commit();
        }
        show(fragmentManager, tag);
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    private Builder getBuilder() {
        return (Builder) getArguments().getSerializable("builder");
    }

    public interface FolderCallback {
        void onFolderSelection(@NonNull FolderChooserDialogCreate dialog,
                               @NonNull File folder, boolean isSaveToLocation);

        void onFolderChooserDismissed(@NonNull FolderChooserDialogCreate dialog);
    }

    public static class Builder implements Serializable {

        @NonNull
        final transient Context context;
        protected boolean isSaveToLocation;
        @StringRes
        int chooseButton;
        @StringRes
        int cancelButton;
        String initialPath;
        String tag;
        boolean allowNewFolder;
        @StringRes
        int newFolderButton;
        String goUpLabel;
        @Nullable
        String mediumFont;
        @Nullable
        String regularFont;

        public Builder(@NonNull Context context) {
            this.context = context;
            chooseButton = R.string.md_choose_label;
            cancelButton = android.R.string.cancel;
            goUpLabel = "...";
            initialPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        }

        @NonNull
        public Builder typeface(@Nullable String medium, @Nullable String regular) {
            this.mediumFont = medium;
            this.regularFont = regular;
            return this;
        }

        @NonNull
        public Builder chooseButton(@StringRes int text) {
            chooseButton = text;
            return this;
        }

        @NonNull
        public Builder cancelButton(@StringRes int text) {
            cancelButton = text;
            return this;
        }

        @NonNull
        public Builder goUpLabel(String text) {
            goUpLabel = text;
            return this;
        }

        @NonNull
        public Builder allowNewFolder(boolean allow, @StringRes int buttonLabel) {
            allowNewFolder = allow;
            if (buttonLabel == 0) {
                buttonLabel = R.string.new_folder;
            }
            newFolderButton = buttonLabel;
            return this;
        }

        @NonNull
        public Builder initialPath(@Nullable String initialPath) {
            if (initialPath == null) {
                initialPath = File.separator;
            }
            this.initialPath = initialPath;
            return this;
        }

        @NonNull
        public Builder tag(@Nullable String tag) {
            if (tag == null) {
                tag = DEFAULT_TAG;
            }
            this.tag = tag;
            return this;
        }

        @NonNull
        public Builder isSaveToLocation(boolean isSaveToLocation) {
            this.isSaveToLocation = isSaveToLocation;
            return this;
        }

        @NonNull
        public FolderChooserDialogCreate build() {
            final FolderChooserDialogCreate dialog = new FolderChooserDialogCreate();
            final Bundle args = new Bundle();
            args.putSerializable("builder", this);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        public FolderChooserDialogCreate show(FragmentManager fragmentManager) {
            final FolderChooserDialogCreate dialog = build();
            dialog.show(fragmentManager);
            return dialog;
        }

        @NonNull
        public FolderChooserDialogCreate show(FragmentActivity fragmentActivity) {
            return show(fragmentActivity.getSupportFragmentManager());
        }
    }

    private static class FolderSorter implements Comparator<File> {

        @Override
        public int compare(File lhs, File rhs) {
            return lhs.getName().compareTo(rhs.getName());
        }
    }
}
