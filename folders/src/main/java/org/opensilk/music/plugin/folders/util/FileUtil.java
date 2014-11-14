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

package org.opensilk.music.plugin.folders.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.webkit.MimeTypeMap;

import org.opensilk.music.api.model.Folder;
import org.opensilk.music.api.model.Song;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by drew on 11/13/14.
 */
public class FileUtil {

    public static final String PRIMARY_STORAGE_ID = "0";
    public static final String SECONDARY_STORAGE_ID = "1";

    public static final File PRIMARY_STORAGE_DIR;
    public static final File SECONDARY_STORAGE_DIR;

    public static final Uri BASE_ARTWORK_URI;
    public static final String[] SONG_PROJECTION;
    public static final String[] MEDIA_TYPE_PROJECTION;

    private static final DateFormat sDateFormat;

    static {
        PRIMARY_STORAGE_DIR = Environment.getExternalStorageDirectory();
        SECONDARY_STORAGE_DIR = getSecondaryStorageDir();
        BASE_ARTWORK_URI = Uri.parse("content://media/external/audio/albumart");
        SONG_PROJECTION = new String[] {
                BaseColumns._ID,
                MediaStore.Audio.AudioColumns.TITLE,
                MediaStore.Audio.AudioColumns.ARTIST,
                MediaStore.Audio.AudioColumns.ALBUM,
                MediaStore.Audio.AudioColumns.ALBUM_ID,
                MediaStore.Audio.AudioColumns.DURATION,
                MediaStore.Audio.AudioColumns.MIME_TYPE,
        };
        MEDIA_TYPE_PROJECTION = new String[] {
                MediaStore.Files.FileColumns.MEDIA_TYPE
        };
        sDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    }

    @Nullable
    static File getSecondaryStorageDir() {
        File[] files = checkSecondaryStorage();
        if (files.length >= 2) {
            return files[1];
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static File[] checkSecondaryStorage() {
        //Kitkat and above only
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return new File[0];
        try {
            Field f = Environment.class.getDeclaredField("sCurrentUser");
            f.setAccessible(true);
            Object o = f.get(null);
            Class c = Class.forName("android.os.Environment$UserEnvironment");
            Method m = c.getDeclaredMethod("getExternalDirsForApp");
            Object o2 = m.invoke(o);
            return (File[]) o2;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new File[0];
    }

    public static String getFileExtension(String name) {
        String ext;
        int lastDot = name.lastIndexOf('.');
        int secondLastDot = name.lastIndexOf('.', lastDot-1);
        if (secondLastDot > 0 ) { // Double extension
            ext = name.substring(secondLastDot + 1);
            if (!ext.startsWith("tar")) {
                ext = name.substring(lastDot + 1);
            }
        } else if (lastDot > 0) { // Single extension
            ext = name.substring(lastDot + 1);
        } else { // No extension
            ext = "";
        }
        return ext;
    }

    public static String getFileExtension(File f) {
        return getFileExtension(f.getName());
    }

    public static String guessMimeType(File f) {
        return guessMimeType(getFileExtension(f));
    }

    public static String guessMimeType(String ext) {
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mimeType == null) {
            mimeType = "*/*";
        }
        return mimeType;
    }

    @NonNull
    public static String formatDate(long ms) {
        return sDateFormat.format(new Date(ms));
    }

    public static String toRelativePath(File base, File f) {
        return f.getAbsolutePath().replace(base.getAbsolutePath(), "");
    }

    @NonNull
    public static Folder makeFolder(File base, File dir) {
        return new Folder.Builder()
                .setIdentity(toRelativePath(base, dir))
                .setName(dir.getName())
                .setChildCount(dir.list().length)
                .setDate(formatDate(dir.lastModified()))
                .build();
    }

    @NonNull
    public static Song makeSong(Context context, File base, File f) {
        Song song = makeSongMediaStore(context, base, f);
        if (song != null) return song;
        return new Song.Builder()
                .setIdentity(toRelativePath(base, f))
                .setName(f.getName())
                .setMimeType(guessMimeType(f))
                .setDataUri(Uri.fromFile(f))
                .build();
    }

    @Nullable
    public static Song makeSongMediaStore(Context context, File base, File f) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    SONG_PROJECTION,
                    MediaStore.Audio.AudioColumns.DATA+"=?",
                    new String[]{f.getAbsolutePath()},
                    null);
            c.moveToFirst();
            return new Song.Builder()
                    .setIdentity(toRelativePath(base, f))
                    .setName(c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)))
                    .setArtistName(c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)))
                    .setAlbumName(c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM)))
                    .setAlbumIdentity(c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID)))
                    .setDuration(c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION)))
                    .setMimeType(c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.MIME_TYPE)))
                    .setDataUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID))))
                    .setArtworkUri(ContentUris.withAppendedId(BASE_ARTWORK_URI,
                            c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))))
                    .build();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.close();
        }
    }

    public static boolean isAudio(Context context, File f) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    MediaStore.Files.getContentUri("external"),
                    MEDIA_TYPE_PROJECTION,
                    MediaStore.Files.FileColumns.DATA+"=?",
                    new String[]{f.getAbsolutePath()},
                    null);
            c.moveToFirst();
            int mediaType = c.getInt(0);
            return mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
        } catch (Exception e) {
            String mime = guessMimeType(f);
            return mime.contains("audio") || mime.equals("application/ogg");
        } finally {
            if (c != null) c.close();
        }
    }

}
