package org.odk.collect.android.spatial;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.XYTileSource;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class MapHelper {

    private static String[] offlineOverlays = getOfflineLayerList();

    public static ITileSource getTileSource(String basemap) {
        String[] baseURL = new String[]{
                "http://a.tile.openstreetmap.org/",
                "http://b.tile.openstreetmap.org/",
                "http://c.tile.openstreetmap.org/"
        };

        return new XYTileSource(basemap, 1, 19, 256, ".png", baseURL);
    }

    public static String[] getOfflineLayerList() {
        File files = new File(Collect.OFFLINE_LAYERS);
        ArrayList<String> results = new ArrayList<>();
        results.add("None");
        Collections.addAll(results, files.list());
        String[] finala = new String[results.size()];
        finala = results.toArray(finala);
        return finala;
    }

    public static String getMBTileFromItem(final int item) {
        String folderName = offlineOverlays[item];
        File dir = new File(Collect.OFFLINE_LAYERS + File.separator + folderName);

        if (dir.isFile()) {
            // we already have a file
            return dir.getAbsolutePath();
        }

        // search first mbtiles file in the directory
        String mbtilePath;
        final File[] files = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".mbtiles"));

        if (files.length == 0) {
            throw new RuntimeException(Collect.getInstance().getString(R.string.mbtiles_not_found, dir.getAbsolutePath()));
        }
        mbtilePath = files[0].getAbsolutePath();

        return mbtilePath;
    }
}
