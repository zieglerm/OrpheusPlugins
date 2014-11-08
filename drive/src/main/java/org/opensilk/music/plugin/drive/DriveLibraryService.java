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

package org.opensilk.music.plugin.drive;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.opensilk.music.api.OrpheusApi.Error;
import org.opensilk.music.api.RemoteLibraryService;
import org.opensilk.music.api.callback.Result;
import org.opensilk.music.api.meta.LibraryInfo;
import org.opensilk.music.api.model.Folder;
import org.opensilk.music.api.model.Song;
import org.opensilk.music.plugin.common.LibraryPreferences;
import org.opensilk.music.plugin.common.PluginPreferences;
import org.opensilk.music.plugin.drive.ui.LibraryChooserActivity;
import org.opensilk.music.plugin.drive.ui.SettingsActivity;
import org.opensilk.music.plugin.drive.util.DriveHelper;
import org.opensilk.music.plugin.drive.util.Helpers;
import org.opensilk.common.dagger.DaggerInjector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import timber.log.Timber;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;
import static org.opensilk.music.api.OrpheusApi.Ability.*;


/**
 * Created by drew on 6/13/14.
 */
public class DriveLibraryService extends RemoteLibraryService {

    public static final String DEFAULT_ROOT_FOLDER = "root";
    public static final String BASE_QUERY = " in parents and trashed=false ";
    public static final String FOLDER_MIMETYPE = "application/vnd.google-apps.folder";
    public static final String AUDIO_MIME_WILDCARD = "audio";
    public static final String AUDIO_OGG_MIMETYPE = "application/ogg";
    public static final String FOLDER_SONG_QUERY = " (mimeType='"+FOLDER_MIMETYPE+"' or mimeType contains '"+AUDIO_MIME_WILDCARD+"' or mimeType='"+AUDIO_OGG_MIMETYPE+"')";
    public static final String SONG_QUERY = " (mimeType contains '"+AUDIO_MIME_WILDCARD+"' or mimeType='"+AUDIO_OGG_MIMETYPE+"')";

    @Inject DriveHelper mDriveHelper;
    @Inject LibraryPreferences mLibraryPrefs;

    @Override
    public void onCreate() {
        super.onCreate();
        ((DaggerInjector) getApplication()).inject(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDriveHelper.destroy();
    }

    /*
     * Abstract methods
     */

    @Override
    protected int getCapabilities() {
        return SEARCH|SETTINGS;
    }

    @Override
    protected Intent getLibraryChooserIntent() {
        return new Intent(this, LibraryChooserActivity.class);
    }

    @Override
    protected Intent getSettingsIntent() {
        return new Intent(this, SettingsActivity.class);
    }

    @Override
    protected void browseFolders(String libraryIdentity, String folderIdentity, final int maxResults, Bundle paginationBundle, final Result callback) {
        final DriveHelper.Session session = mDriveHelper.getSession(libraryIdentity);
        final String fID;
        if (TextUtils.isEmpty(folderIdentity)) {
            String root = mLibraryPrefs.getRootFolder(libraryIdentity);
            if (!TextUtils.isEmpty(root)) {
                // use preferred root
                fID = "'"+root+"'";
            } else {
                // use real root
                fID = "'"+DEFAULT_ROOT_FOLDER+"'";
            }
        } else {
            fID = "'"+folderIdentity+"'";
        }
        final String paginationToken;
        if (paginationBundle != null) {
            paginationToken = paginationBundle.getString("token");
        } else {
            paginationToken = null;
        }
        final String query = fID + BASE_QUERY + " and" + FOLDER_SONG_QUERY;
        ListFilesRunner r = new ListFilesRunner(session, maxResults, query, paginationToken, false, callback);
        THREAD_POOL_EXECUTOR.execute(r);
    }

    @Override
    protected void listSongsInFolder(String libraryIdentity, String folderIdentity, int maxResults, Bundle paginationBundle, Result callback) {
        final DriveHelper.Session session = mDriveHelper.getSession(libraryIdentity);
        final String paginationToken;
        if (paginationBundle != null) {
            paginationToken = paginationBundle.getString("token");
        } else {
            paginationToken = null;
        }
        final String query =  "'"+folderIdentity+"'" + BASE_QUERY + " and" + SONG_QUERY;
        ListFilesRunner r = new ListFilesRunner(session, maxResults, query, paginationToken, true, callback);
        THREAD_POOL_EXECUTOR.execute(r);
    }

    @Override
    protected void search(String libraryIdentity, String query, int maxResults, Bundle paginationBundle, Result callback) {
        final DriveHelper.Session session = mDriveHelper.getSession(libraryIdentity);
        final String paginationToken;
        if (paginationBundle != null) {
            paginationToken = paginationBundle.getString("token");
        } else {
            paginationToken = null;
        }
        final String q = "title contains '"+query+"' and trashed=false and" + FOLDER_SONG_QUERY;
        ListFilesRunner r = new ListFilesRunner(session, maxResults, q, paginationToken, false, callback);
        THREAD_POOL_EXECUTOR.execute(r);
    }

    /**
     *
     */
    static class ListFilesRunner implements Runnable {
        private final DriveHelper.Session driveSession;
        private final int maxResults;
        private final String query;
        private final String paginationToken;
        private final boolean songsOnly;
        private final Result callback;

        ListFilesRunner(DriveHelper.Session driveSession, int maxResults, String query,
                        String paginationToken, boolean songsOnly, Result callback) {
            this.driveSession = driveSession;
            this.maxResults = maxResults;
            this.query = query;
            this.paginationToken = paginationToken;
            this.songsOnly = songsOnly;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                Timber.d("q=" + query);
                Drive.Files.List req = driveSession.getDrive().files().list()
                        .setQ(query)
                        .setFields(Helpers.FIELDS)
                        .setMaxResults(maxResults);
                if (!TextUtils.isEmpty(paginationToken)) {
                    req.setPageToken(paginationToken);
                }
                FileList resp = req.execute();
                List<File> files = resp.getItems();
                List<Folder> folders = new ArrayList<>();
                List<Song> songs = new ArrayList<>();
                final String authToken = driveSession.getCredential().getToken();
                for (File f : files) {
                    final String mime = f.getMimeType();
                    if (TextUtils.equals(FOLDER_MIMETYPE, mime)) {
                        if (!songsOnly) {
                            Folder folder = Helpers.buildFolder(f);
                            folders.add(folder);
                        }
                    } else if (mime.contains("audio") || TextUtils.equals(mime, "application/ogg")) {
                        Song song = Helpers.buildSong(f, authToken);
                        songs.add(song);
                    }
                }

                // Combine results into single list
                final List<Bundle> resources = new ArrayList<>((songsOnly ? 0 : folders.size()) + songs.size());
                if (!songsOnly) {
                    for (Folder f : folders) {
                        resources.add(f.toBundle());
                    }
                }
                for (Song s : songs) {
                    resources.add(s.toBundle());
                }

                final Bundle b;
                if (!TextUtils.isEmpty(resp.getNextPageToken())) {
                    b = new Bundle(1);
                    b.putString("token", resp.getNextPageToken());
                } else {
                    b = null;
                }
                try {
                    callback.success(resources, b);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    callback.failure(Error.NETWORK, ""+e.getMessage());
                } catch (RemoteException e1) {
                    e1.printStackTrace();
                }
            } catch (GoogleAuthException e) {
                e.printStackTrace();
                try {
                    callback.failure(Error.AUTH_FAILURE, "" + e.getMessage());
                } catch (RemoteException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    protected void querySongs(String libraryIdentity, final int maxResults, Bundle paginationBundle, final Result callback) throws RemoteException {
        final DriveHelper.Session session = mDriveHelper.getSession(libraryIdentity);
        final String paginationToken;
        if (paginationBundle != null) {
            paginationToken = paginationBundle.getString("token");
        } else {
            paginationToken = null;
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    Drive.Files.List req = session.getDrive().files().list()
                            .setQ("trashed=false and (mimeType contains 'audio' or mimeType = 'application/ogg')")
                            .setFields(Helpers.FIELDS)
                            .setMaxResults(maxResults);
                    if (!TextUtils.isEmpty(paginationToken)) {
                        req.setPageToken(paginationToken);
                    }
                    FileList resp = req.execute();
                    List<File> files = resp.getItems();
                    List<Bundle> songs = new ArrayList<>();
                    final String authToken = session.getCredential().getToken();
                    for (File f : files) {
                        final String mime = f.getMimeType();
                        if (mime.contains("audio") || TextUtils.equals(mime, "application/ogg")) {
                            Song song = Helpers.buildSong(f, authToken);
                            songs.add(song.toBundle());
                        }
                    }
                    final Bundle b;
                    if (!TextUtils.isEmpty(resp.getNextPageToken())) {
                        b = new Bundle(1);
                        b.putString("token", resp.getNextPageToken());
                    } else {
                        b = null;
                    }
                    try {
                        callback.success(songs, b);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                } catch (IOException|GoogleAuthException e) {
                    e.printStackTrace();
                    try {
                        callback.failure(-1, "" + e.getMessage());
                    } catch (RemoteException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        };
        THREAD_POOL_EXECUTOR.execute(r);
    }

}
