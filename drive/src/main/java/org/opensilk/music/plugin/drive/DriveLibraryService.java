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

import com.google.android.gms.auth.GoogleAuthException;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import org.opensilk.music.api.RemoteLibraryService;
import org.opensilk.music.api.callback.Result;
import org.opensilk.music.api.model.Folder;
import org.opensilk.music.api.model.Song;
import org.opensilk.music.plugin.drive.ui.LibraryChooserActivity;
import org.opensilk.music.plugin.drive.util.DriveHelper;
import org.opensilk.music.plugin.drive.util.Helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.ObjectGraph;
import hugo.weaving.DebugLog;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;
import static org.opensilk.music.api.options.Ability.BROWSE_FOLDERS;

/**
 * Created by drew on 6/13/14.
 */
public class DriveLibraryService extends RemoteLibraryService {

    @Inject
    protected DriveHelper mDrive;

    @Override
    public void onCreate() {
        super.onCreate();
        DriveApp app = (DriveApp) getApplication();
        ObjectGraph graph = app.getApplicationGraph();
        if (graph != null) {
            graph.inject(this);
        } else {
            throw new RuntimeException();
        }
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
        return BROWSE_FOLDERS;
    }

    @Override
    protected Intent getLibraryChooserIntent() throws RemoteException {
        return new Intent(this, LibraryChooserActivity.class);
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
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    Drive.Files.List req = mDrive.drive().files().list()
                            .setQ(fID + " in parents and trashed=false and (mimeType = 'application/vnd.google-apps.folder'"
                                    + " or mimeType contains 'audio' or mimeType = 'application/ogg')")
                            .setFields(Helpers.FIELDS)
                            .setMaxResults(maxResults);
                    if (!TextUtils.isEmpty(paginationToken)) {
                        req.setPageToken(paginationToken);
                    }
                    FileList resp = req.execute();
                    List<File> files = resp.getItems();
                    List<Folder> folders = new ArrayList<>();
                    List<Song> songs = new ArrayList<>();
                    final String authToken = mDrive.getAuthToken();
                    for (File f : files) {
                        final String mime = f.getMimeType();
                        if (TextUtils.equals("application/vnd.google-apps.folder", mime)) {
                            Folder folder = Helpers.buildFolder(f);
                            folders.add(folder);
                        } else if (mime.contains("audio") || TextUtils.equals(mime, "application/ogg")) {
                            Song song = Helpers.buildSong(f, authToken);
                            songs.add(song);
                        }
                    }

                    // Combine results into single list
                    final List<Bundle> resources = new ArrayList<>(folders.size() + songs.size());
                    for (Folder f : folders) {
                        resources.add(f.toBundle());
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
        };
        THREAD_POOL_EXECUTOR.execute(r);
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

    protected void downloadSong(String sourceIdentity, final Song song) {
//        if (!TextUtils.equals(mAccountCred.getSelectedAccountName(), sourceIdentity)) {
//            mAccountCred.setSelectedAccountName(sourceIdentity);
//        }
//        Thread t = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                HttpClient hc = new DefaultHttpClient();
//                Timber.d(song.dataUri.toString());
//                HttpGet hr = new HttpGet(song.dataUri.toString());
//                try {
//                    Timber.d("AuthToken="+mAccountCred.getToken());
//                    hr.addHeader("Authorization", "Bearer " + mAccountCred.getToken());
//                } catch (IOException e) {
//                    e.printStackTrace();
//                } catch (GoogleAuthException e) {
//                    e.printStackTrace();
//                }
//                try {
//                    org.apache.http.HttpResponse hrr = hc.execute(hr);
//                    Timber.d(hrr.getStatusLine().toString());
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
//        t.start();
    }

}
