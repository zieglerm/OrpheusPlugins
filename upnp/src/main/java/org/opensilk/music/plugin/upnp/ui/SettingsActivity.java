/*
 * Copyright (c) 2014 OpenSilk Productions LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.opensilk.music.plugin.upnp.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.opensilk.music.api.OrpheusApi;
import org.opensilk.music.plugin.common.AbsSettingsActivity;
import org.opensilk.music.plugin.common.FolderPickerActivity;
import org.opensilk.music.plugin.common.PluginUtil;
import org.opensilk.music.plugin.upnp.R;
import org.opensilk.music.plugin.upnp.UpnpLibraryService;
import org.opensilk.music.plugin.upnp.util.Helpers;

/**
 * Created by drew on 7/18/14.
 */
public class SettingsActivity extends AbsSettingsActivity {

    @Override
    protected Fragment getSettingsFragment(String libraryId) {
        return SettingsFragment.newInstance(libraryId);
    }

    public static class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {

        public static final String ROOT_FOLDER = "root_folder_identity";
        public static final String ROOT_FOLDER_TITLE = "root_folder_title";
        public static final String SEARCH_FOLDER = "search_folder_identity";
        public static final String SEARCH_FOLDER_TITLE = "search_folder_title";
        public static final String LICENSES = "licenses";

        public static SettingsFragment newInstance(String libraryId) {
            SettingsFragment f = new SettingsFragment();
            Bundle b = new Bundle();
            b.putString("__id", libraryId);
            f.setArguments(b);
            return f;
        }

        private String mLibraryId;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mLibraryId = getArguments().getString("__id");
            // Change preferences file per Orpheus api guidelines
            getPreferenceManager().setSharedPreferencesName(PluginUtil.posixSafe(mLibraryId));
            addPreferencesFromResource(R.xml.settings);

            // default browse folder
            findPreference(ROOT_FOLDER).setOnPreferenceClickListener(this);
            String rootFolderTitle = getPreferenceManager().getSharedPreferences().getString(ROOT_FOLDER_TITLE, null);
            if (!TextUtils.isEmpty(rootFolderTitle)) {
                findPreference(ROOT_FOLDER).setSummary(rootFolderTitle);
            }

            // default search folder
            findPreference(SEARCH_FOLDER).setOnPreferenceClickListener(this);
            String searchFolderTitle = getPreferenceManager().getSharedPreferences().getString(SEARCH_FOLDER_TITLE, null);
            if (!TextUtils.isEmpty(searchFolderTitle)) {
                findPreference(SEARCH_FOLDER).setSummary(searchFolderTitle);
            }

            // licenses dialog
            findPreference(LICENSES).setOnPreferenceClickListener(this);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == 0 || requestCode == 1) {
                String pref = requestCode == 0 ? ROOT_FOLDER : SEARCH_FOLDER;
                String titlePref = requestCode == 0 ? ROOT_FOLDER_TITLE : SEARCH_FOLDER_TITLE;
                if (resultCode == Activity.RESULT_OK) {
                    String pickedFolder = data.getStringExtra(FolderPickerActivity.PICKED_FOLDER_IDENTITY);
                    String pickedFolderTitle = data.getStringExtra(FolderPickerActivity.PICKED_FOLDER_TITLE);
                    if (!TextUtils.isEmpty(pickedFolder)) {
                        getPreferenceManager().getSharedPreferences().edit()
                                .putString(pref, pickedFolder)
                                .putString(titlePref, pickedFolderTitle).apply();
                        findPreference(pref).setSummary(pickedFolderTitle);
                    }
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            if (findPreference(ROOT_FOLDER) == preference) {
                Intent i = getActivity().getIntent()
                        .setClass(getActivity(), FolderPickerActivity.class)
                        .putExtra(FolderPickerActivity.SERVICE_COMPONENT, new ComponentName(getActivity(), UpnpLibraryService.class))
                        .putExtra(FolderPickerActivity.STARTING_FOLDER, "0");
                getPreferenceManager().getSharedPreferences().edit().remove(ROOT_FOLDER).remove(ROOT_FOLDER_TITLE).apply();
                findPreference(ROOT_FOLDER).setSummary(null);
                startActivityForResult(i, 0);
                return true;
            } else if (findPreference(SEARCH_FOLDER) == preference) {
                Intent i = getActivity().getIntent()
                        .setClass(getActivity(), FolderPickerActivity.class)
                        .putExtra(FolderPickerActivity.SERVICE_COMPONENT, new ComponentName(getActivity(), UpnpLibraryService.class))
                        .putExtra(FolderPickerActivity.STARTING_FOLDER, "0");
                getPreferenceManager().getSharedPreferences().edit().remove(SEARCH_FOLDER).remove(SEARCH_FOLDER_TITLE).apply();
                findPreference(SEARCH_FOLDER).setSummary(null);
                startActivityForResult(i, 1);
                return true;
            } else if (findPreference(LICENSES) == preference) {
                new LicensesDialog().show(getFragmentManager(), "licenses");
                return true;
            }
            return false;
        }
    }

    public static class LicensesDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final WebView webView = new WebView(getActivity());
            webView.loadUrl("file:///android_asset/licenses.html");
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.about_licenses)
                    .setView(webView)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }
    }
}
