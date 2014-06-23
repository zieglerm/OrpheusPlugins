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
import org.fourthline.cling.support.model.BrowseFlag;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.container.MusicAlbum;
import org.fourthline.cling.support.model.container.MusicArtist;
import org.fourthline.cling.support.model.item.AudioItem;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.model.item.MusicTrack;
import org.opensilk.music.api.Api;
import org.opensilk.music.api.RemoteLibraryService;
import org.opensilk.music.api.callback.FolderBrowseResult;
import org.opensilk.music.api.model.Resource;
import org.opensilk.music.api.model.Song;
import org.opensilk.music.plugin.upnp.ui.LibraryPickerActivity;
import org.opensilk.music.plugin.upnp.util.Helpers;
import org.opensilk.upnp.contentdirectory.Feature;
import org.opensilk.upnp.contentdirectory.Features;
import org.opensilk.upnp.contentdirectory.callback.GetFeatureList;

import java.util.ArrayList;
import java.util.List;

import hugo.weaving.DebugLog;

import static org.opensilk.music.api.options.Ability.BROWSE_FOLDERS;

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
        return BROWSE_FOLDERS;
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
            cb.failure("Unknown error");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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

    /*
     *
     */

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

    private void doBrowse(RemoteService rs, String folderIdentity, final int maxResults, Bundle paginationBundle, final FolderBrowseResult cb) {
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
                final List<Resource> resources = new ArrayList<>(containers.size() + items.size());

                for (Container c : containers) {
                    Resource r;
                    if (MusicArtist.CLASS.equals(c)) {
                        r = Helpers.parseArtist((MusicArtist)c);
                    } else if (MusicAlbum.CLASS.equals(c)) {
                        r = Helpers.parseAlbum((MusicAlbum)c);
                    } else {
                        r = Helpers.parseFolder(c);
                    }
                    resources.add(r);
                }

                for (Item item : items) {
                    if (MusicTrack.CLASS.equals(item)) {
                        MusicTrack mt = (MusicTrack) item;
                        Song s = Helpers.parseSong(mt);
                        resources.add(s);
                    }
                }

                //TODO
                final Bundle b = new Bundle(1);
                b.putInt("start", start+maxResults);

                try {
                    cb.success(resources, b);
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

}
