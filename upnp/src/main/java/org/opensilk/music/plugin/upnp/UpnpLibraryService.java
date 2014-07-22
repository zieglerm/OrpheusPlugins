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
import android.content.SharedPreferences;
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
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.contentdirectory.callback.Browse;
import org.fourthline.cling.support.contentdirectory.callback.Search;
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.container.MusicAlbum;
import org.fourthline.cling.support.model.container.MusicArtist;
import org.fourthline.cling.support.model.item.AudioItem;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.model.item.MusicTrack;
import org.opensilk.music.api.OrpheusApi.Error;
import org.opensilk.music.api.RemoteLibraryService;
import org.opensilk.music.api.callback.Result;
import org.opensilk.music.api.model.Song;
import org.opensilk.music.plugin.common.PluginUtil;
import org.opensilk.music.plugin.upnp.ui.LibraryPickerActivity;
import org.opensilk.music.plugin.upnp.ui.SettingsActivity;
import org.opensilk.music.plugin.upnp.util.Helpers;
import org.opensilk.upnp.contentdirectory.Feature;
import org.opensilk.upnp.contentdirectory.Features;
import org.opensilk.upnp.contentdirectory.callback.GetFeatureList;

import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

import static org.opensilk.music.api.OrpheusApi.Ability.*;

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
    protected int getCapabilities() {
        return SEARCH|SETTINGS;
    }

    @Override
    protected Intent getLibraryChooserIntent() {
        return new Intent(this, LibraryPickerActivity.class);
    }

    @Override
    protected Intent getSettingsIntent() {
        return new Intent(this, SettingsActivity.class);
    }

    @Override
    protected void pause() {
        if (mUpnpService != null) {
            if (!mUpnpService.getRegistry().isPaused()) {
                mUpnpService.getRegistry().pause();
            }
        }
    }

    @Override
    protected void resume() {
        if (mUpnpService != null) {
            if (mUpnpService.getRegistry().isPaused()) {
                mUpnpService.getRegistry().resume();
            }
        }
    }

    @Override
    @DebugLog
    protected void browseFolders(String libraryIdentity, String folderIdentity, int maxResults, Bundle paginationBundle, Result callback) {
        if (mUpnpService != null) {
            RemoteService rs = acquireContentDirectoryService(libraryIdentity);
            if (rs != null) {
                // Requested root folder
                if (TextUtils.isEmpty(folderIdentity)) {
                    String rootFolder = getDefaultFolder(libraryIdentity);
                    if (!TextUtils.isEmpty(rootFolder)) {
                        // use preferred root folder
                        doBrowse(rs, rootFolder, maxResults, paginationBundle, callback, false);
                    } else {
                        // lets see if there is a music only virtual folder
                        requestFeatures(rs, new BrowseCommand(rs, maxResults, paginationBundle, callback));
                    }
                } else {
                    doBrowse(rs, folderIdentity, maxResults, paginationBundle, callback, false);
                }
                return;
            }
        }
        try {
            callback.failure(Error.RETRY, "Upnp Service not bound yet");
        } catch (RemoteException ignored) {}
    }

    @Override
    protected void listSongsInFolder(String libraryIdentity, String folderIdentity, int maxResults, Bundle paginationBundle, Result callback) {
        if (mUpnpService != null) {
            RemoteService rs = acquireContentDirectoryService(libraryIdentity);
            if (rs != null) {
                doBrowse(rs, folderIdentity, maxResults, paginationBundle, callback, true);
                return;
            }
        }
        try {
            callback.failure(Error.RETRY, "Upnp Service not bound yet");
        } catch (RemoteException ignored) {}
    }

    @Override
    protected void search(String libraryIdentity, String query, int maxResults, Bundle paginationBundle, Result callback) {
        if (mUpnpService != null) {
            RemoteService rs = acquireContentDirectoryService(libraryIdentity);
            if (rs != null) {
                String searchFolder = getSearchFolder(libraryIdentity);
                if (!TextUtils.isEmpty(searchFolder)) {
                    // use preferred search folder
                    doSearch(rs, searchFolder, query, maxResults, paginationBundle, callback);
                } else {
                    // lets see if there is a music only virtual folder
                    requestFeatures(rs, new SearchCommand(rs, query, maxResults, paginationBundle, callback));
                }
                return;
            }
        }
        try {
            callback.failure(Error.RETRY, "Upnp Service not bound yet");
        } catch (RemoteException ignored) {}
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
        bindService(new Intent(this, UpnpServiceService.class), this, BIND_AUTO_CREATE);
    }

    /*
     *
     */

    private String getDefaultFolder(String libraryIdentity) {
        return getSettings(libraryIdentity).getString(SettingsActivity.SettingsFragment.ROOT_FOLDER, null);
    }

    private String getSearchFolder(String libraryIdentity) {
        return getSettings(libraryIdentity).getString(SettingsActivity.SettingsFragment.SEARCH_FOLDER, null);
    }

    private SharedPreferences getSettings(String libraryIdentity) {
        return getSharedPreferences(PluginUtil.posixSafe(libraryIdentity), MODE_PRIVATE);
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

    @DebugLog
    private void doBrowse(RemoteService rs, String folderIdentity, final int maxResults, Bundle paginationBundle, final Result callback, final boolean songsOnly) {
        final int start;
        if (paginationBundle != null) {
            start = paginationBundle.getInt("start");
        } else {
            start = 0;
        }
        final Browse browse = new Browse(rs, folderIdentity, BrowseFlag.DIRECT_CHILDREN, Browse.CAPS_WILDCARD, start, (long)maxResults, null) {
            @Override
            @DebugLog
            public void received(ActionInvocation actionInvocation, DIDLContent didlContent) {

                final List<Container> containers = didlContent.getContainers();
                final List<Item> items = didlContent.getItems();
                final List<Bundle> resources = new ArrayList<>(containers.size() + items.size());

                if (!songsOnly) {
                    for (Container c : containers) {
                        Bundle b;
                        if (MusicArtist.CLASS.equals(c)) {
                            b = Helpers.parseArtist((MusicArtist)c).toBundle();
                        } else if (MusicAlbum.CLASS.equals(c)) {
                            b = Helpers.parseAlbum((MusicAlbum)c).toBundle();
                        } else {
                            b = Helpers.parseFolder(c).toBundle();
                        }
                        resources.add(b);
                    }
                }

                for (Item item : items) {
                    if (MusicTrack.CLASS.equals(item)) {
                        MusicTrack mt = (MusicTrack) item;
                        Song s = Helpers.parseSong(mt);
                        resources.add(s.toBundle());
                    }
                }

                UnsignedIntegerFourBytes numRet = (UnsignedIntegerFourBytes) actionInvocation.getOutput("NumberReturned").getValue();
                UnsignedIntegerFourBytes total = (UnsignedIntegerFourBytes) actionInvocation.getOutput("TotalMatches").getValue();
                final Bundle b;
                // server was unable to compute total matches
                if (numRet.getValue() != 0 && total.getValue() == 0) {
                    // this isnt exactly right, it will cause an extra call
                    // but i dont know of a more specific manner to determine
                    // end of results
                    if (containers.size() == 0 && items.size() == 0) {
                        b = null;
                    } else {
                        b = new Bundle(1);
                        b.putInt("start", start+maxResults);
                    }
                    // no results, total should return an error
                } else if (numRet.getValue() == 0 && total.getValue() == 720) {
                    b = null;
                } else {
                    int nextStart = start+maxResults;
                    if (nextStart < total.getValue()) {
                        b = new Bundle(1);
                        b.putInt("start", nextStart);
                    } else {
                        b = null;
                    }
                }

                try {
                    callback.success(resources, b);
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
                    callback.failure(Error.NETWORK, s);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
        if (mUpnpService != null) {
            mUpnpService.getControlPoint().execute(browse);
        }
    }

    private void doSearch(RemoteService rs, String folderIdentity, String query, final int maxResults, Bundle paginationBundle, final Result callback) {
        final int start;
        if (paginationBundle != null) {
            start = paginationBundle.getInt("start");
        } else {
            start = 0;
        }
        Search search = new Search(rs, folderIdentity,
                "dc:title contains \"" + query + "\" or upnp:artist contains \""
                        + query + "\" or upnp:album contains \"" + query + "\""
                        + " or upnp:genre contains \"" + query + "\"",
                "*", start, (long)maxResults, null) {
            @Override
            public void received(ActionInvocation actionInvocation, DIDLContent didlContent) {
                final List<Container> containers = didlContent.getContainers();
                final List<Item> items = didlContent.getItems();
                final List<Bundle> resources = new ArrayList<>(containers.size() + items.size());

                for (Container c : containers) {
                    Bundle b;
                    if (MusicArtist.CLASS.equals(c)) {
                        b = Helpers.parseArtist((MusicArtist)c).toBundle();
                    } else if (MusicAlbum.CLASS.equals(c)) {
                        b = Helpers.parseAlbum((MusicAlbum)c).toBundle();
                    } else {
                        b = Helpers.parseFolder(c).toBundle();
                    }
                    resources.add(b);
                }

                for (Item item : items) {
                    if (MusicTrack.CLASS.equals(item)) {
                        MusicTrack mt = (MusicTrack) item;
                        Song s = Helpers.parseSong(mt);
                        resources.add(s.toBundle());
                    }
                }

                UnsignedIntegerFourBytes numRet = (UnsignedIntegerFourBytes) actionInvocation.getOutput("NumberReturned").getValue();
                UnsignedIntegerFourBytes total = (UnsignedIntegerFourBytes) actionInvocation.getOutput("TotalMatches").getValue();
                final Bundle b;
                // server was unable to compute total matches
                if (numRet.getValue() != 0 && total.getValue() == 0) {
                    // this isnt exactly right, it will cause an extra call
                    // but i dont know of a more specific manner to determine
                    // end of results
                    if (containers.size() == 0 && items.size() == 0) {
                        b = null;
                    } else {
                        b = new Bundle(1);
                        b.putInt("start", start+maxResults);
                    }
                    // no results, total should return an error
                } else if (numRet.getValue() == 0 && total.getValue() == 720) {
                    b = null;
                } else {
                    int nextStart = start+maxResults;
                    if (nextStart < total.getValue()) {
                        b = new Bundle(1);
                        b.putInt("start", nextStart);
                    } else {
                        b = null;
                    }
                }

                try {
                    callback.success(resources, b);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void updateStatus(Status status) {

            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String s) {
                try {
                    callback.failure(Error.NETWORK, s);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        };
        if (mUpnpService != null) {
            mUpnpService.getControlPoint().execute(search);
        }
    }


    /**
     *
     */
    interface Command {
        void execute(Object data);
    }

    /**
     *
     */
    abstract class UpnpCommand implements Command {
        final RemoteService service;
        final int maxResults;
        final Bundle paginationBudle;
        final Result callback;

        UpnpCommand(RemoteService service, final int maxResults, Bundle paginationBundle, Result callback) {
            this.service = service;
            this.maxResults = maxResults;
            this.paginationBudle = paginationBundle;
            this.callback = callback;
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

    /**
     *
     */
    class BrowseCommand extends UpnpCommand {

        BrowseCommand(RemoteService service, int maxResults, Bundle paginationBundle, Result callback) {
            super(service, maxResults, paginationBundle, callback);
        }

        @Override
        void doExecute(String folderIdentity) {
            doBrowse(service, folderIdentity, maxResults, paginationBudle, callback, false);
        }

    }

    /**
     *
     */
    class SearchCommand extends UpnpCommand {
        final String query;

        SearchCommand(RemoteService service, String query, int maxResults, Bundle paginationBundle, Result callback) {
            super(service, maxResults, paginationBundle, callback);
            this.query = query;
        }

        @Override
        void doExecute(String folderIdentity) {
            doSearch(service, folderIdentity, query, maxResults, paginationBudle, callback);
        }
    }

}
