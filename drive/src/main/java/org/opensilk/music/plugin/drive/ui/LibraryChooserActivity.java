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

import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import org.opensilk.music.api.OrpheusApi;
import org.opensilk.music.plugin.drive.util.DriveHelper;
import org.opensilk.silkdagger.app.DaggerActivity;

import java.io.IOException;

import javax.inject.Inject;

/**
 * Created by drew on 6/15/14.
 */
public class LibraryChooserActivity extends DaggerActivity {

    public static final int REQUEST_ACCOUNT_PICKER = 1001;
    public static final int REQUEST_AUTH_APPROVAL = 1002;

    private String mAccountName;

    @Inject
    protected DriveHelper mDrive;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED, getIntent());

        Intent i = AccountManager.newChooseAccountIntent(null, null, new String[]{"com.google"}, true, null, null, null, null);
        startActivityForResult(i, REQUEST_ACCOUNT_PICKER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK) {
                    mAccountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    // After we select an account we still need to authorize ourselves
                    // for drive access.
                    new DriveTest().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else {
                    //TODO
                }
                break;
            case REQUEST_AUTH_APPROVAL:
                if (resultCode == RESULT_OK) {
                    doFinish();
                } else {
                    //TODO
                }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void doFinish() {
        Intent i = getIntent();
        i.putExtra(OrpheusApi.EXTRA_LIBRARY_ID, mAccountName);
        setResult(RESULT_OK, i);
        finish();
    }

    /*
     * Abstract methods
     */

    @Override
    protected Object[] getModules() {
        return new Object[] {
                new UiModule(this)
        };
    }

    private class DriveTest extends AsyncTask<Void, Void, Boolean> {

        private ProgressDialog progress;
        private Intent resolveIntent;

        @Override
        protected void onPreExecute() {
            progress = new ProgressDialog(LibraryChooserActivity.this);
            progress.setMessage("Verifying");
            progress.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            mDrive.setAccountName(mAccountName);
            try {
                mDrive.drive().files().list().setFields("items/id").setMaxResults(1)
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
            progress.dismiss();
            if (success) {
                doFinish();
            } else {
                if (resolveIntent != null) {
                    startActivityForResult(resolveIntent, REQUEST_AUTH_APPROVAL);
                } else {
                    //TODO
                }
            }
        }
    }
}
