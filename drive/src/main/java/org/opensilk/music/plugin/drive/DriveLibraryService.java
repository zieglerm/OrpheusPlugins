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
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.opensilk.music.api.OrpheusApi;
import org.opensilk.music.api.RemoteLibraryService;
import org.opensilk.music.api.callback.Result;
import org.opensilk.music.api.model.Folder;
import org.opensilk.music.api.model.Song;
import org.opensilk.music.plugin.drive.ui.LibraryChooserActivity;
import org.opensilk.music.plugin.drive.util.DriveHelper;
import org.opensilk.music.plugin.drive.util.Helpers;
import org.opensilk.silkdagger.DaggerInjector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import hugo.weaving.DebugLog;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;
import static org.opensilk.music.api.OrpheusApi.Abilities.SEARCH;


/**
 * Created by drew on 6/13/14.
 */
public class DriveLibraryService extends RemoteLibraryService {

    public static final String BASE_QUERY = " in parents and trashed=false ";
    public static final String FOLDER_MIMETYPE = "application/vnd.google-apps.folder";
    public static final String AUDIO_MIME_WILDCARD = "audio";
    public static final String AUDIO_OGG_MIMETYPE = "application/ogg";
    public static final String FOLDER_SONG_QUERY = BASE_QUERY+" and (mimeType='"+FOLDER_MIMETYPE+"' or mimeType contains '"+AUDIO_MIME_WILDCARD+"' or mimeType='"+AUDIO_OGG_MIMETYPE+"')";
    public static final String SONG_QUERY = BASE_QUERY+" and (mimeType contains '"+AUDIO_MIME_WILDCARD+"' or mimeType='"+AUDIO_OGG_MIMETYPE+"')";

    @Inject
    protected DriveHelper mDrive;

    @Override
    public void onCreate() {
        super.onCreate();
        DaggerInjector injector = (DaggerInjector) getApplication();
        injector.inject(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDrive = null;
    }

    /*
     * Abstract methods
     */

    @Override
    protected int getCapabilities() throws RemoteException {
        return SEARCH;
    }

    @Override
    protected Intent getLibraryChooserIntent() throws RemoteException {
        return new Intent(this, LibraryChooserActivity.class);
    }

    @Override
    protected Intent getSettingsIntent() throws RemoteException {
        return null;
    }

    @Override
    protected void pause() throws RemoteException {
        //noop
    }

    @Override
    protected void resume() throws RemoteException {
        //noop
    }

    @Override
    @DebugLog
    protected void browseFolders(String libraryIdentity, String folderIdentity, final int maxResults, Bundle paginationBundle, final Result callback) throws RemoteException {
        mDrive.setAccountName(libraryIdentity);
        final String fID;
        if (TextUtils.isEmpty(folderIdentity)) {
            fID = "'root'";
        } else {
            fID = "'" + folderIdentity + "'";
        }
        final String paginationToken;
        if (paginationBundle != null) {
            paginationToken = paginationBundle.getString("token");
        } else {
            paginationToken = null;
        }
        final String query = fID + FOLDER_SONG_QUERY;
        ListFilesRunner r = new ListFilesRunner(mDrive, maxResults, query, paginationToken, false, callback);
        THREAD_POOL_EXECUTOR.execute(r);
    }

    @Override
    protected void listSongsInFolder(String libraryIdentity, String folderIdentity, int maxResults, Bundle paginationBundle, Result callback) throws RemoteException {
        mDrive.setAccountName(libraryIdentity);
        final String paginationToken;
        if (paginationBundle != null) {
            paginationToken = paginationBundle.getString("token");
        } else {
            paginationToken = null;
        }
        final String query =  "'"+folderIdentity+"'" + SONG_QUERY;
        ListFilesRunner r = new ListFilesRunner(mDrive, maxResults, query, paginationToken, true, callback);
        THREAD_POOL_EXECUTOR.execute(r);
    }

    @Override
    protected void search(String libraryIdentity, String query, int maxResults, Bundle paginationBundle, Result callback) throws RemoteException {
        mDrive.setAccountName(libraryIdentity);
        final String paginationToken;
        if (paginationBundle != null) {
            paginationToken = paginationBundle.getString("token");
        } else {
            paginationToken = null;
        }
        final String q = "'root'" + FOLDER_SONG_QUERY + " and title contains '"+query+"'";
        ListFilesRunner r = new ListFilesRunner(mDrive, maxResults, q, paginationToken, false, callback);
        THREAD_POOL_EXECUTOR.execute(r);
    }

    static class ListFilesRunner implements Runnable {
        private final DriveHelper dHelper;
        private final int maxResults;
        private final String query;
        private final String paginationToken;
        private final boolean songsOnly;
        private final Result callback;

        ListFilesRunner(DriveHelper dHelper, int maxResults, String query, String paginationToken, boolean songsOnly, Result callback) {
            this.dHelper = dHelper;
            this.maxResults = maxResults;
            this.query = query;
            this.paginationToken = paginationToken;
            this.songsOnly = songsOnly;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                Log.d("TAG", "q="+query);
                Drive.Files.List req = dHelper.drive().files().list()
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
                final String authToken = dHelper.getAuthToken();
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
            } catch (IOException|GoogleAuthException e) {
                e.printStackTrace();
                try {
                    callback.failure(-1, "" + e.getMessage());
                } catch (RemoteException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    protected void querySongs(String libraryIdentity, final int maxResults, Bundle paginationBundle, final Result callback) throws RemoteException {
        mDrive.setAccountName(libraryIdentity);
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
                    Drive.Files.List req = mDrive.drive().files().list()
                            .setQ("trashed=false and (mimeType contains 'audio' or mimeType = 'application/ogg')")
                            .setFields(Helpers.FIELDS)
                            .setMaxResults(maxResults);
                    if (!TextUtils.isEmpty(paginationToken)) {
                        req.setPageToken(paginationToken);
                    }
                    FileList resp = req.execute();
                    List<File> files = resp.getItems();
                    List<Bundle> songs = new ArrayList<>();
                    final String authToken = mDrive.getAuthToken();
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
