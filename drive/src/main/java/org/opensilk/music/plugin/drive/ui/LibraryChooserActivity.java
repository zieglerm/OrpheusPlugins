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
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import org.opensilk.music.api.OrpheusApi;
import org.opensilk.music.plugin.drive.R;

import hugo.weaving.DebugLog;

/**
 * Created by drew on 6/15/14.
 */
public class LibraryChooserActivity extends Activity implements DriveTestFragment.TestListener {

    public static final int REQUEST_ACCOUNT_PICKER = 1001;
    public static final int REQUEST_AUTH_APPROVAL = 1002;

    private String mAccountName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);

        if (savedInstanceState == null) {
            Intent i = AccountManager.newChooseAccountIntent(null, null, new String[]{"com.google"}, true, null, null, null, null);
            startActivityForResult(i, REQUEST_ACCOUNT_PICKER);
        }
    }

    @Override
    @DebugLog
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK) {
                    mAccountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    // After we select an account we still need to authorize ourselves
                    // for drive access.
                    startTest();
                } else {
                    finishFailure();
                }
                break;
            case REQUEST_AUTH_APPROVAL:
                if (resultCode == RESULT_OK) {
                    finishSuccess();
                } else {
                    finishFailure();
                }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void startTest() {
        // hack, idk why but, if screen rotates while the background task
        // is executing the returned intent will lack the EXTRA_LIBRARY_ID extra
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        // show progress
        new ProgressFragment().show(getFragmentManager(), "progress");
        // start the test
        getFragmentManager().beginTransaction()
                .add(DriveTestFragment.newInstance(mAccountName), "tester")
                .commit();
    }

    private void finishSuccess() {
        Intent i = new Intent().putExtra(OrpheusApi.EXTRA_LIBRARY_ID, mAccountName);
        setResult(RESULT_OK, i);
        finish();
    }

    private void finishFailure() {
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    @DebugLog
    public void onSuccess() {
        finishSuccess();
    }

    @Override
    public void onFailure(Intent resolveIntent) {
        if (resolveIntent != null) {
            startActivityForResult(resolveIntent, REQUEST_AUTH_APPROVAL);
        } else {
            finishFailure();
        }
    }

    public static class ProgressFragment extends DialogFragment {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setCancelable(false);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog mProgressDialog = new ProgressDialog(getActivity());
            mProgressDialog.setMessage(getString(R.string.authorizing));
            return mProgressDialog;
        }
    }

}
