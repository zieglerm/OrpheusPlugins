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

package org.opensilk.music.plugin.upnp;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;

import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.model.meta.RemoteService;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.support.contentdirectory.callback.Browse;
import org.fourthline.cling.support.contentdirectory.callback.Search;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.container.MusicAlbum;
import org.fourthline.cling.support.model.container.MusicArtist;
import org.fourthline.cling.support.model.item.AudioItem;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.model.item.MusicTrack;
import org.opensilk.music.api.RemoteLibraryService;
import org.opensilk.music.api.callback.AlbumBrowseResult;
import org.opensilk.music.api.callback.AlbumQueryResult;
import org.opensilk.music.api.callback.ArtistQueryResult;
import org.opensilk.music.api.callback.FolderBrowseResult;
import org.opensilk.music.api.model.Album;
import org.opensilk.music.api.model.Artist;
import org.opensilk.music.api.model.Folder;
import org.opensilk.music.api.model.Song;
import org.opensilk.upnp.contentdirectory.Feature;
import org.opensilk.upnp.contentdirectory.Features;
import org.opensilk.upnp.contentdirectory.callback.GetFeatureList;
import org.opensilk.music.plugin.upnp.ui.LibraryPickerActivity;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;
import timber.log.Timber;

import static org.opensilk.music.api.Api.Ability.BROWSE_FOLDERS;
import static org.opensilk.music.api.Api.Ability.QUERY_ALBUMS;
import static org.opensilk.music.api.Api.Ability.QUERY_ARTISTS;

/**
 * Created by drew on 6/8/14.
 */
public class UpnpLibraryService extends RemoteLibraryService implements ServiceConnection {

    private AndroidUpnpService mUpnpService;

    @Override
    public void onCreate() {
        super.onCreate();
        bindService(new Intent(this, UpnpServiceService.class), this, BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    /*
     * Abstract methods
     */

    @Override
    protected int getCapabilities() throws RemoteException {
        return BROWSE_FOLDERS|QUERY_ARTISTS|QUERY_ALBUMS;
    }

    @Override
    protected Intent getLibraryChooserIntent() throws RemoteException {
        return new Intent(this, LibraryPickerActivity.class);
    }

    @Override
    protected void browseFolders(String libraryIdentity, String folderIdentity, int maxResults, Bundle paginationBundle, FolderBrowseResult cb) throws RemoteException {
        if (mUpnpService == null) {
            throw new RemoteException();
        }
        RemoteService rs = acquireContentDirectoryService(libraryIdentity);
        if (rs != null) {
            // Requested root folder, lets see if there is a music only virtual folder
            if (TextUtils.isEmpty(folderIdentity)) {
                requestFeatures(rs, new BrowseCommand(rs, maxResults, paginationBundle, cb));
            } else {
                doBrowse(rs, folderIdentity, maxResults, paginationBundle, cb);
            }
            return;
        }
        try {
            cb.failure("Unknown Error");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void queryArtists(String libraryIdentity, int maxResults, Bundle paginationBundle, ArtistQueryResult cb) throws RemoteException {
        if (mUpnpService == null) {
            throw new RemoteException();
        }
        RemoteService rs = acquireContentDirectoryService(libraryIdentity);
        if (rs != null) {
            requestFeatures(rs, new ArtistSearchCommand(rs, maxResults, paginationBundle, cb));
            return;
        }
        try {
            cb.failure("Unknown error");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void queryAlbums(String libraryIdentity, int maxResults, Bundle paginationBundle, AlbumQueryResult cb) throws RemoteException {
        if (mUpnpService == null) {
            throw new RemoteException();
        }
        RemoteService rs = acquireContentDirectoryService(libraryIdentity);
        if (rs != null) {
            requestFeatures(rs, new AlbumSearchCommand(rs, maxResults, paginationBundle, cb));
            return;
        }
        try {
            cb.failure("Unknown Error");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    protected void browseAlbum(String sourceIdentity, String albumIdentity, final AlbumBrowseResult cb) {
        if (mUpnpService != null) {
            final RemoteDevice rd = mUpnpService.getRegistry().getRemoteDevice(UDN.valueOf(sourceIdentity), false);
            if (rd != null) {
                final RemoteService rs = rd.findService(new UDAServiceType("ContentDirectory", 1));
                if (rs != null) {
                    final Browse browse = new Browse(rs, albumIdentity, BrowseFlag.DIRECT_CHILDREN) {
                        @Override
                        @DebugLog
                        public void received(ActionInvocation actionInvocation, DIDLContent didlContent) {
                            final List<Item> objs = didlContent.getItems();
                            final List<Song> songs = new ArrayList<>(objs.size());
                            for (Item item : objs) {
                                if (MusicTrack.CLASS.equals(item)) {
                                    MusicTrack mt = (MusicTrack) item;
                                    final String id = mt.getId();
                                    final String n = mt.getTitle();
                                    final String aln = mt.getAlbum();
                                    final String arn = mt.getFirstArtist().getName();
                                    final int dur = parseDuration(mt.getFirstResource().getDuration());
                                    final Uri u = Uri.parse(mt.getFirstResource().getValue());
                                    songs.add(new Song(id, n, aln, arn, dur, u, null));
                                }
                            }
                            try {
                                cb.success(songs);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void updateStatus(Status status) {

                        }

                        @Override
                        @DebugLog
                        public void failure(ActionInvocation actionInvocation, UpnpResponse upnpResponse, String s) {

                        }
                    };
                    mUpnpService.getControlPoint().execute(browse);
                }
            }
        }
        //TODO failure
    }

    /*
     * Service Connection
     */

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mUpnpService = (AndroidUpnpService) service;
        if (mUpnpService != null) {
            mUpnpService.getControlPoint().search();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mUpnpService = null;
//        bindService(new Intent(this, UpnpServiceService.class), this, BIND_AUTO_CREATE);
    }

    private RemoteService acquireContentDirectoryService(String deviceIdentity) {
        RemoteDevice rd = mUpnpService.getRegistry().getRemoteDevice(UDN.valueOf(deviceIdentity), false);
        if (rd != null) {
            RemoteService rs = rd.findService(new UDAServiceType("ContentDirectory", 1));
            if (rs != null) {
                return rs;
            }
        }
        return null;
    }

    //FIXME this is utter shit
    private static int parseDuration(String duration) {
        int dur = 0;
        try {
            String[] parts = duration.split(":");
            if (parts.length == 3) {
                if (!TextUtils.isEmpty(parts[0])) {
                    dur += Integer.decode(parts[0]) * 60 * 60;
                }
                if (!TextUtils.isEmpty(parts[1])) {
                    dur += Integer.decode(parts[1]) * 60;
                }
                if (!TextUtils.isEmpty(parts[2])) {
                    String[] p2 = parts[2].split("\\.");
                    if (p2.length >= 1) {
                        dur += Integer.decode(p2[0]);
                    }
                }
            }
        } catch (NumberFormatException e) {
            Timber.w(""+e.getClass()+":"+e.getMessage());
            return 0;
        }
        return dur;
    }

    private void requestFeatures(RemoteService service, final Command command) {
        GetFeatureList req = new GetFeatureList(service) {
            @Override
            public void received(ActionInvocation actionInvocation, Features features) {
                command.execute(features);
            }

            @Override
            public void failure(ActionInvocation actionInvocation, UpnpResponse upnpResponse, String s) {
                command.execute(null);
            }
        };
        if (mUpnpService != null) {
            mUpnpService.getControlPoint().execute(req);
        }
    }

    private void doBrowse(RemoteService rs, String folderIdentity, final int maxResults, Bundle paginationBundle, final FolderBrowseResult cb) {
        final int start;
        if (paginationBundle != null) {
            start = paginationBundle.getInt("start");
        } else {
            start = 0;
        }

        final Browse browse = new Browse(rs, folderIdentity, BrowseFlag.DIRECT_CHILDREN, "*", start, (long)maxResults, null) {
            @Override
            @DebugLog
            public void received(ActionInvocation actionInvocation, DIDLContent didlContent) {

                final List<Container> containers = didlContent.getContainers();
                final List<Folder> folders = new ArrayList<>(containers.size());
                for (Container c : containers) {
                    final String id = c.getId();
                    final String name = c.getTitle();
                    final String parentId = c.getParentID();
                    folders.add(new Folder(id, name, parentId));
                }

                final List<Item> items = didlContent.getItems();
                final List<Song> songs = new ArrayList<>(items.size());
                for (Item item : items) {
                    if (MusicTrack.CLASS.equals(item)) {
                        MusicTrack mt = (MusicTrack) item;
                        final String id = mt.getId();
                        final String n = mt.getTitle();
                        final String aln = mt.getAlbum();
                        final String arn = mt.getFirstArtist().getName();
                        final int dur = parseDuration(mt.getFirstResource().getDuration());
                        final Uri u = Uri.parse(mt.getFirstResource().getValue());
                        final URI au = mt.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM_ART_URI.class);
                        final Uri u2 = au != null ? Uri.parse(au.toASCIIString()) : Uri.EMPTY;
                        songs.add(new Song(id, n, aln, arn, dur, u, u2));
                    }
                }

                //TODO how to check no more results?
                final Bundle b = new Bundle(1);
                b.putInt("start", start+maxResults);

                try {
                    cb.success(folders, songs, b);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void updateStatus(Status status) {

            }

            @Override
            @DebugLog
            public void failure(ActionInvocation actionInvocation, UpnpResponse upnpResponse, String s) {
                try {
                    cb.failure(s);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
        if (mUpnpService != null) {
            mUpnpService.getControlPoint().execute(browse);
        }
    }

    private void doArtistSearch(RemoteService rs, String folderIdentity, final int maxResults, Bundle paginationBundle, final ArtistQueryResult cb) {
        final int start;
        if (paginationBundle != null) {
            start = paginationBundle.getInt("start");
        } else {
            start = 0;
        }

        final Search search = new Search(rs, folderIdentity,
                "upnp:class = \"object.container.person.musicArtist\"",
                "*", start, (long)maxResults, null) {
            @Override
            @DebugLog
            public void received(ActionInvocation actionInvocation, DIDLContent didlContent) {
                final List<Artist> artists = new ArrayList<>(didlContent.getContainers().size());
                for (Container c : didlContent.getContainers()) {
                    if (MusicArtist.CLASS.equals(c)) {
                        MusicArtist ma = (MusicArtist) c;
                        final String id= ma.getId();
                        final String n = ma.getTitle();
                        Timber.d(n + "," + ma.getChildCount() + "," + ma.getContainers().size() + "," + ma.getItems().size());
                        artists.add(new Artist(id, n, 0, 0));
                    }
                }

                // Todo check no more results
                final Bundle resultBundle = new Bundle(1);
                resultBundle.putInt("start", start+maxResults);

                try {
                    cb.success(artists, resultBundle);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void updateStatus(Status status) {

            }

            @Override
            @DebugLog
            public void failure(ActionInvocation actionInvocation, UpnpResponse upnpResponse, String s) {
                try {
                    cb.failure(s);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };

        if (mUpnpService != null) {
            mUpnpService.getControlPoint().execute(search);
        }
    }

    private void doAlbumSearch(RemoteService rs, String folderIdentity, final int maxResults, Bundle paginationBundle, final AlbumQueryResult cb) {
        final int start;
        if (paginationBundle != null) {
            start = paginationBundle.getInt("start");
        } else {
            start = 0;
        }

        final Search search = new Search(rs, folderIdentity,
                "upnp:class = \"object.container.album.musicAlbum\"",
                "*", start, (long)maxResults, null) {
            @Override
            @DebugLog
            public void received(ActionInvocation actionInvocation, DIDLContent didlContent) {
                final List<Album> albums = new ArrayList<>();
                for (Container c : didlContent.getContainers()) {
                    if (MusicAlbum.CLASS.equals(c)) {
                        MusicAlbum ma = (MusicAlbum) c;
                        final String id = ma.getId();
                        final String n1 = ma.getTitle();
                        final String n2 = ma.getFirstArtist().getName();
                        //final String date = ma.getDate();
                        final Uri uri = Uri.parse(ma.getFirstAlbumArtURI().toASCIIString());
                        albums.add(new Album(id, n1, n2, uri));
                    }
                }

                //TODO check no more results
                final Bundle resultBundle = new Bundle(1);
                resultBundle.putInt("start", start+maxResults);

                try {
                    cb.success(albums, resultBundle);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void updateStatus(Status status) {

            }

            @Override
            @DebugLog
            public void failure(ActionInvocation actionInvocation, UpnpResponse upnpResponse, String s) {
                try {
                    cb.failure(s);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
        if (mUpnpService != null) {
            mUpnpService.getControlPoint().execute(search);
        }
    }


    interface Command {
        void execute(Object data);
    }

    abstract class UpnpCommand implements Command {
        final RemoteService service;
        final int maxResults;
        final Bundle paginationBudle;

        UpnpCommand(RemoteService service, final int maxResults, Bundle paginationBundle) {
            this.service = service;
            this.maxResults = maxResults;
            this.paginationBudle = paginationBundle;
        }

        @Override
        public void execute(Object data) {
            String fId = null;
            if (data != null) {
                Features features = (Features) data;
                Feature feature = features.getFeature(Feature.SEC_BASICVIEW, 1);
                if (feature != null) {
                    String id = feature.getContainerIdOf(AudioItem.CLASS);
                    if (id != null) {
                        fId = id;
                    }
                }
            }
            if (fId == null) {
                fId = "0";
            }
            doExecute(fId);
        }

        abstract void doExecute(String folderIdentity);
    }

    class BrowseCommand extends UpnpCommand {

        final FolderBrowseResult callback;

        BrowseCommand(RemoteService service, int maxResults, Bundle paginationBundle, FolderBrowseResult cb) {
            super(service, maxResults, paginationBundle);
            this.callback = cb;
        }

        @Override
        void doExecute(String folderIdentity) {
            doBrowse(service, folderIdentity, maxResults, paginationBudle, callback);
        }


    }

    class ArtistSearchCommand extends UpnpCommand {

        final ArtistQueryResult callback;

        ArtistSearchCommand(RemoteService service, int maxResults, Bundle paginationBundle, ArtistQueryResult cb) {
            super(service, maxResults, paginationBundle);
            this.callback = cb;
        }

        @Override
        void doExecute(String folderIdentity) {
            doArtistSearch(service, folderIdentity, maxResults, paginationBudle, callback);
        }

    }

    class AlbumSearchCommand extends UpnpCommand {

        final AlbumQueryResult callback;

        AlbumSearchCommand(RemoteService service, int maxResults, Bundle paginationBundle, AlbumQueryResult cb) {
            super(service, maxResults, paginationBundle);
            this.callback = cb;
        }

        @Override
        void doExecute(String folderIdentity) {
            doAlbumSearch(service, folderIdentity, maxResults, paginationBudle, callback);
        }
    }

}
