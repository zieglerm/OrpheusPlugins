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

package org.opensilk.music.plugin.drive.util;

import android.net.Uri;

import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;

import org.opensilk.music.api.model.Folder;
import org.opensilk.music.api.model.Song;

import java.util.List;
import java.util.Locale;

/**
 * Created by drew on 6/18/14.
 */
public class Helpers {

    public static final String FIELDS = "items/id,items/mimeType,items/parents,items/title,items/downloadUrl";

    public static Folder buildFolder(File f) {
        final String id = f.getId();
        final String title = f.getTitle();
        List<ParentReference> parents = f.getParents();
        final String parentId = parents.size() > 0 ? parents.get(0).getId() : null;
        return new Folder(id, title, parentId, 0, null); //TODO
    }

    public static Song buildSong(File f, String authToken) {
        final String id = f.getId();
        final String title = f.getTitle();
        final int duration = 0;
        final Uri data = buildDownloadUri(f.getDownloadUrl(), authToken);
        return new Song(id, title, null, null, null, null, duration, data, null);
    }

    public static Uri buildDownloadUri(String url, String authToken) {
        return Uri.parse(buildDownloadUriString(url, authToken));
    }

    public static String buildDownloadUriString(String url, String authToken) {
        return String.format(Locale.US, "%s&access_token=%s", url, authToken);
    }
}
