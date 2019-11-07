/*
 * Copyright (C) 2014 GeoODK
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.activities;

/*
 * Responsible for being the main mapping activity, GPS, offline mapping, and data point display
 *
 * @author Jon Nordling (jonnordling@gmail.com)
 */


/*
 * 06.30.2014
 * Jon Nordling
 *
 * This activity is to map the data offline
 *
 */

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.ImageButton;

import org.odk.collect.android.R;
import org.odk.collect.android.geo.OsmMBTileProvider;
import org.odk.collect.android.preferences.GeneralKeys;
import org.odk.collect.android.preferences.PrefUtils;
import org.odk.collect.android.spatial.GeoRender;
import org.odk.collect.android.spatial.MapHelper;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.TilesOverlay;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;

public class GeoODKMainMapActivity extends BaseGeoMapActivity implements IRegisterReceiver {
    // Abuja, Nigeria
    private static final GeoPoint DEFAULT_POINT = new GeoPoint(9.0544966, 7.324145);

    private SharedPreferences sharedPreferences;
    private MapView mapView;
    private MyLocationNewOverlay myLocationNewOverlay;
    private OsmMBTileProvider mbTileProvider;
    private TilesOverlay tilesOverlay;
    private ImageButton gpsButton;

    private int zoomLevel = -1;
    private int selectedLayer = -1;
    private boolean gpsStatus = true;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable centerAroundFix = () -> mHandler.post(this::zoomToMyLocation);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.osm_map_view_layout); //Setting Content to layout xml
        setTitle(this.getString(R.string.map_view_title)); // Setting title of the action
        this.sharedPreferences = PrefUtils.getSharedPrefs();

        this.mapView = this.findViewById(R.id.osm_map_view);
        this.mapView.setMultiTouchControls(true);
        this.mapView.setBuiltInZoomControls(true);
        this.mapView.setMapListener(new MapListener() {
            @Override
            public boolean onScroll(final ScrollEvent arg0) {
                return false;
            }

            @Override
            public boolean onZoom(final ZoomEvent zoomEvent) {
                zoomLevel = zoomEvent.getZoomLevel();
                return false;
            }
        });

        //T his is the gps button and its functionality
        this.gpsButton = this.findViewById(R.id.gps_button);
        this.gpsButton.setOnClickListener(v -> toggleGPSStatus());

        final ImageButton layers_button = this.findViewById(R.id.layers_button);
        layers_button.setOnClickListener(v -> showLayersDialog());

        this.myLocationNewOverlay = new MyLocationNewOverlay(this.mapView);
        this.myLocationNewOverlay.runOnFirstFix(this.centerAroundFix);
        toggleGPSStatus();

        // Initial Map Setting before Location is found
        final Handler handler = new Handler();
        handler.postDelayed(() -> {
            // Do something after 100ms

            this.mapView.getController().setZoom(3);
            this.mapView.getController().setCenter(DEFAULT_POINT);
        }, 100);

        this.mapView.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean online = this.sharedPreferences.getBoolean(GeneralKeys.KEY_MAP_ONLINE, true);
        String basemap = this.sharedPreferences.getString(GeneralKeys.KEY_BASEMAP_SOURCE, GeneralKeys.BASEMAP_SOURCE_OSM);
        ITileSource baseTiles = MapHelper.getTileSource(basemap);
        this.mapView.setTileSource(baseTiles);
        this.mapView.setUseDataConnection(online);
        drawMarkers();
        toggleGPSStatus();
        this.mapView.invalidate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableMyLocation();
        clearMap();
    }

    @Override
    protected void onStop() {
        super.onStop();
        disableMyLocation();
        clearMap();
    }

    @Override
    public void destroy() {
        disableMyLocation();
        clearMap();
    }

    private void toggleGPSStatus() {
        if (!this.gpsStatus) {
            enableMyLocation();
        } else {
            disableMyLocation();
        }
        this.gpsButton.setColorFilter(this.gpsStatus ? Color.BLUE : Color.GRAY);
    }

    private void enableMyLocation() {
        final LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            this.mapView.getOverlays().add(this.myLocationNewOverlay);
            this.myLocationNewOverlay.setEnabled(true);
            this.myLocationNewOverlay.enableMyLocation();
            this.myLocationNewOverlay.enableFollowLocation();
            this.gpsStatus = true;
        } else {
            showGPSDisabledAlert();
        }
    }

    private void showGPSDisabledAlert() {
        new AlertDialog.Builder(this)
                .setMessage(this.getString(R.string.gps_enable_message))
                .setCancelable(false)
                .setPositiveButton(
                        this.getString(R.string.gps_enable_button),
                        (dialog, id) -> startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 0)
                )
                .setNegativeButton(this.getString(R.string.cancel), (dialog, id) -> dialog.cancel())
                .create()
                .show();
    }

    private void showLayersDialog() {
        new AlertDialog.Builder(this)
                .setTitle(this.getString(R.string.select_offline_layer))
                .setSingleChoiceItems(MapHelper.getOfflineLayerList(), this.selectedLayer, (dialog, item) -> {
                    try {
                        if (item == 0) {
                            this.mapView.getOverlays().remove(this.tilesOverlay);
                        } else {
                            final String mbFilePath = MapHelper.getMBTileFromItem(item);
                            final File mbFile = new File(mbFilePath);

                            this.mapView.getOverlays().remove(this.tilesOverlay);
                            this.mbTileProvider = new OsmMBTileProvider(GeoODKMainMapActivity.this, mbFile);
                            this.tilesOverlay = new TilesOverlay(this.mbTileProvider, this.getApplicationContext());
                            this.tilesOverlay.setLoadingBackgroundColor(Color.TRANSPARENT);

                            clearMap();
                            this.mapView.getOverlays().add(this.tilesOverlay);
                            drawMarkers();
                            this.mapView.invalidate();
                        }
                        // This resets the map and sets the selected Layer
                        this.selectedLayer = item;
                        dialog.dismiss();
                    } catch (RuntimeException e) {
                        createErrorDialog(e.getMessage());
                        return;
                    }
                    final Handler handler = new Handler();
                    handler.postDelayed(() -> this.mapView.invalidate(), 400);
                })
                .create()
                .show();
    }

    private void zoomToMyLocation() {
        if (this.myLocationNewOverlay.getMyLocation() != null) {
            if (this.zoomLevel == 3) {
                this.mapView.getController().setZoom(15);
            } else {
                this.mapView.getController().setZoom(this.zoomLevel);
            }
            this.mapView.getController().setCenter(this.myLocationNewOverlay.getMyLocation());
        } else {
            this.mapView.getController().setZoom(this.zoomLevel);
        }

    }

    private void createErrorDialog(String errorMsg) {
        new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setMessage(errorMsg)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok), (dialog, id) -> dialog.dismiss())
                .create()
                .show();
    }

    private void drawMarkers() {
        new GeoRender(this.getApplicationContext(), this.mapView);
    }

    private void disableMyLocation() {
        final LocationManager locationManager = (LocationManager) this.getSystemService(LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            this.mapView.getOverlays().remove(this.myLocationNewOverlay);
            this.myLocationNewOverlay.setEnabled(false);
            this.myLocationNewOverlay.disableFollowLocation();
            this.myLocationNewOverlay.disableMyLocation();
            this.gpsStatus = false;
        }
    }

    private void clearMap() {
        this.mapView.getOverlays().clear();
        this.mapView.invalidate();
    }
}
