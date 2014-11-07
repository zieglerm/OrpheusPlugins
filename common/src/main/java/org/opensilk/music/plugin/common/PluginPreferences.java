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

package org.opensilk.music.plugin.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.Gson;

import org.opensilk.common.dagger.qualifier.ForApplication;
import org.opensilk.music.api.meta.LibraryInfo;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Created by drew on 11/6/14.
 */
@Singleton
public class PluginPreferences {

    public static final String DEFAULT_LIBRARY_INFO = "default_library_info";

    final Gson gson;

    final SharedPreferences prefs;

    @Inject
    public PluginPreferences(@ForApplication Context appContext, Gson gson) {
        this.gson = gson;
        prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
    }

    public LibraryInfo getDefaultLibraryInfo() {
        String json = prefs.getString(DEFAULT_LIBRARY_INFO, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, LibraryInfo.class);
        } catch (Exception e) {
            removeDefaultLibraryInfo();
            return null;
        }
    }

    public void setDefaultLibraryInfo(LibraryInfo libraryInfo) {
        String json = gson.toJson(libraryInfo);
        if (json != null) {
            prefs.edit().putString(DEFAULT_LIBRARY_INFO, json).apply();
        }
    }

    public void updateDefaultLibraryInfo(String libraryId, String folderId, String folderName) {
        LibraryInfo info = getDefaultLibraryInfo();
        if (libraryId.equals(info.libraryId)) {
            info.buildUpon(folderId, folderName);
            setDefaultLibraryInfo(info);
        }
    }

    public void removeDefaultLibraryInfo() {
        prefs.edit().remove(DEFAULT_LIBRARY_INFO).apply();
    }

}
