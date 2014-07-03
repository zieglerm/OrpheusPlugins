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

package org.opensilk.music.plugin.drive.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import org.opensilk.music.plugin.drive.util.DriveHelper;
import org.opensilk.silkdagger.app.ApplicationScopedDaggerFragment;

import java.io.IOException;

import javax.inject.Inject;

/**
 * Created by drew on 7/2/14.
 */
public class DriveTestFragment extends ApplicationScopedDaggerFragment {
    public interface TestListener {
        public void onSuccess();
        public void onFailure(Intent resolveIntent);
    }

    @Inject
    protected DriveHelper mDrive;
    private TestListener listener;

    private String account;

    public static DriveTestFragment newInstance(String account) {
        DriveTestFragment f = new DriveTestFragment();
        Bundle b = new Bundle();
        b.putString("acct", account);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof TestListener) {
            listener = ((TestListener) activity);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        this.account = getArguments().getString("acct");
        new DriveTest().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onDetach() {
        listener = null;
        super.onDetach();
    }

    class DriveTest extends AsyncTask<Void, Void, Boolean> {

        private Intent resolveIntent;

        @Override
        protected Boolean doInBackground(Void... params) {
            mDrive.setAccountName(account);
            try {
                mDrive.drive().files().list()
                        .setFields("items/id").setMaxResults(1)
                        .execute();
                return true;
            } catch (UserRecoverableAuthIOException e) {
                resolveIntent = e.getIntent();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (listener != null) {
                if (success) {
                    listener.onSuccess();
                } else {
                    listener.onFailure(resolveIntent);
                }
            }
        }
    }
}
