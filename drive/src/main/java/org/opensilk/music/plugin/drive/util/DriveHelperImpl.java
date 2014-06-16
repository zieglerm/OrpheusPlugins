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

package org.opensilk.music.plugin.drive.util;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import org.opensilk.music.plugin.drive.BuildConfig;
import org.opensilk.silkdagger.qualifier.ForApplication;

import java.io.IOException;
import java.util.Collections;

import javax.inject.Inject;

/**
 * Created by drew on 6/15/14.
 */
public class DriveHelperImpl implements DriveHelper {
    private static final String APP_NAME = BuildConfig.PACKAGE_NAME+"/"+BuildConfig.VERSION_NAME;

    private final GoogleAccountCredential mCredentials;
    private final Drive mDriveService;

    @Inject
    public DriveHelperImpl(@ForApplication Context context) {
        mCredentials = GoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE_READONLY));
        mDriveService = new Drive.Builder(AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(), mCredentials).setApplicationName(APP_NAME).build();
    }

    @Override
    public Drive drive() {
        return mDriveService;
    }

    @Override
    public void setAccountName(String accountName) {
        if (!TextUtils.equals(mCredentials.getSelectedAccountName(), accountName)) {
            mCredentials.setSelectedAccountName(accountName);
        }
    }

    @Override
    public String getAuthToken() throws GoogleAuthException, IOException {
        return mCredentials.getToken();
    }
}
