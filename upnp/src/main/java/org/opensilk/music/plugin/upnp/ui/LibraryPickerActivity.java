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
import android.app.ListActivity;
import android.app.ListFragment;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;

import org.eclipse.jetty.servlet.Holder;
import org.fourthline.cling.android.AndroidUpnpService;
import org.fourthline.cling.model.meta.RemoteDevice;
import org.fourthline.cling.registry.DefaultRegistryListener;
import org.fourthline.cling.registry.Registry;
import org.fourthline.cling.registry.RegistryListener;
import org.opensilk.music.api.OrpheusApi;
import org.opensilk.music.api.meta.LibraryInfo;
import org.opensilk.music.plugin.common.PluginUtil;
import org.opensilk.music.plugin.upnp.R;
import org.opensilk.music.plugin.upnp.UpnpLibraryService;
import org.opensilk.music.plugin.upnp.UpnpServiceService;

import butterknife.ButterKnife;
import butterknife.OnClick;

import static org.opensilk.music.plugin.upnp.ui.SettingsActivity.SettingsFragment.ROOT_FOLDER;
import static org.opensilk.music.plugin.upnp.ui.SettingsActivity.SettingsFragment.ROOT_FOLDER_TITLE;

/**
 * Created by drew on 6/14/14.
 */
public class LibraryPickerActivity extends Activity implements ServiceConnection {

    AndroidUpnpService upnpService;
    ListDialog listDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean wantLightTheme = getIntent().getBooleanExtra(OrpheusApi.EXTRA_WANT_LIGHT_THEME, false);
        if (wantLightTheme) {
            setTheme(R.style.AppThemeTranslucentLight);
        } else {
            setTheme(R.style.AppThemeTranslucentDark);
        }

        FrameLayout root = new FrameLayout(this);
        root.setId(android.R.id.content);
        setContentView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (savedInstanceState == null) {
            listDialog = new ListDialog();
            listDialog.show(getFragmentManager(), "listdialog");
        }

        bindService(new Intent(this, UpnpServiceService.class), this, BIND_AUTO_CREATE);

        setResult(RESULT_CANCELED, getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (upnpService != null) {
            upnpService.getRegistry().removeListener(registryListener);
        }
        unbindService(this);
    }

    void onItemSelected(DeviceHolder holder) {
        Intent i = getIntent();
        //TODO remove
        i.putExtra(OrpheusApi.EXTRA_LIBRARY_ID, holder.id);
        SharedPreferences libraryPrefs = getSharedPreferences(PluginUtil.posixSafe(holder.id), MODE_PRIVATE);
        String rootFolderId = libraryPrefs.getString(ROOT_FOLDER, null);
        String rootFolderName = libraryPrefs.getString(ROOT_FOLDER_TITLE, null);
        i.putExtra(OrpheusApi.EXTRA_LIBRARY_INFO, new LibraryInfo(holder.id, holder.label, rootFolderId, rootFolderName));
        setResult(RESULT_OK, i);
        finish();
    }

    /*
     * Service Connection
     */

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        upnpService = (AndroidUpnpService) service;
        listDialog.listAdapter.clear();
        for (RemoteDevice d : upnpService.getRegistry().getRemoteDevices()) {
            listDialog.listAdapter.add(DeviceHolder.fromRemoteDevice(d));
        }
        upnpService.getRegistry().addListener(registryListener);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        upnpService = null;
    }

    /**
     * UpnpRegistry listener
     */
    final RegistryListener registryListener = new DefaultRegistryListener() {
        @Override
        public void remoteDeviceAdded(Registry registry, RemoteDevice device) {
            final DeviceHolder holder = DeviceHolder.fromRemoteDevice(device);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int position = listDialog.listAdapter.getPosition(holder);
                    if (position >= 0) {
                        // Device already in the list, re-set new value at same position
                        listDialog.listAdapter.remove(holder);
                        listDialog.listAdapter.insert(holder, position);
                    } else {
                        listDialog.listAdapter.add(holder);
                    }
                }
            });
        }

        @Override
        public void remoteDeviceUpdated(Registry registry, RemoteDevice device) {
            final DeviceHolder holder = DeviceHolder.fromRemoteDevice(device);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int position = listDialog.listAdapter.getPosition(holder);
                    if (position >= 0) {
                        // Device already in the list, re-set new value at same position
                        listDialog.listAdapter.remove(holder);
                        listDialog.listAdapter.insert(holder, position);
                    }
                }
            });
        }

        @Override
        public void remoteDeviceRemoved(Registry registry, RemoteDevice device) {
            final DeviceHolder holder = DeviceHolder.fromRemoteDevice(device);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listDialog.listAdapter.remove(holder);
                }
            });
        }
    };

    public static class ListDialog extends DialogFragment {
        ArrayAdapter<DeviceHolder> listAdapter;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            listAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.select_device)
                    .setAdapter(listAdapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ((LibraryPickerActivity) getActivity()).onItemSelected(listAdapter.getItem(which));
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    })
                    .create();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            getActivity().finish();
        }
    }

    static class DeviceHolder {
        final String id;
        final String label;

        DeviceHolder(String id, String label) {
            this.id = id;
            this.label = label;
        }

        static DeviceHolder fromRemoteDevice(RemoteDevice d) {
            final String id = d.getIdentity().getUdn().getIdentifierString();
            final String label = !TextUtils.isEmpty(d.getDetails().getFriendlyName()) ?
                    d.getDetails().getFriendlyName() : d.getDisplayString();
            return new DeviceHolder(id, label);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DeviceHolder)) return false;

            DeviceHolder holder = (DeviceHolder) o;

            if (!id.equals(holder.id)) return false;
//            if (!label.equals(holder.label)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
//            result = 31 * result + label.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
