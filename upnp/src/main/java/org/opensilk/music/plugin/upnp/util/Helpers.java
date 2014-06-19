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

package org.opensilk.music.plugin.upnp.util;

import android.net.Uri;

import org.fourthline.cling.support.model.DIDLObject;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.container.MusicAlbum;
import org.fourthline.cling.support.model.container.MusicArtist;
import org.fourthline.cling.support.model.item.MusicTrack;
import org.opensilk.music.api.model.Album;
import org.opensilk.music.api.model.Artist;
import org.opensilk.music.api.model.Folder;
import org.opensilk.music.api.model.Song;

import java.net.URI;

/**
 * Created by drew on 6/18/14.
 */
public class Helpers {

    public static Folder parseFolder(Container c) {
        final String id = c.getId();
        final String name = c.getTitle();
        final String parentId = c.getParentID();
        return new Folder(id, name, parentId);
    }

    public static Artist parseArtist(MusicArtist ma) {
        final String id = ma.getId();
        final String name = ma.getTitle();
        return new Artist(id, name, 0, 0);
    }

    public static Album parseAlbum(MusicAlbum ma) {
        final String id = ma.getId();
        final String name = ma.getTitle();
        final String artist = ma.getFirstArtist().getName();
        final Uri artUri = Uri.parse(ma.getFirstAlbumArtURI().toASCIIString());
        return new Album(id, name, artist, artUri);
    }

    public static Song parseSong(MusicTrack mt) {
        final String id = mt.getId();
        final String name = mt.getTitle();
        final String album = mt.getAlbum();
        final String artist = mt.getFirstArtist().getName();
        final int duration = 0; //TODO parseDuration(mt.getFirstResource().getDuration());
        final Uri dataUri = Uri.parse(mt.getFirstResource().getValue());
        final URI artURI = mt.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM_ART_URI.class);
        final Uri artUri = artURI != null ? Uri.parse(artURI.toASCIIString()) : Uri.EMPTY;
        return new Song(id, name, album, artist, duration, dataUri, artUri);
    }

}
