package org.odk.collect.android.spatial;

/*
 * Created by jnordling on 9/13/15.
 */

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.widget.Button;
import android.widget.TextView;

import org.odk.collect.android.R;
import org.odk.collect.android.provider.FormsProviderAPI;
import org.odk.collect.android.provider.InstanceProviderAPI;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.OverlayWithIW;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class GeoRender {

    private static final String GEOSHAPE = "geoshape";
    private static final String GEOPOINT = "geopoint";
    private static final String GEOTRACE = "geotrace";

    private Context context;
    private MapView mapView;

    public GeoRender(final Context pContext, final MapView mapView) {
        if ((pContext != null) && (mapView != null)) {
            this.context = pContext;
            this.mapView = mapView;

            final Cursor instances = this.getAllInstances();
            while (instances.moveToNext()) {
                String instanceFilePath = instances.getString(instances.getColumnIndex(InstanceProviderAPI.InstanceColumns.INSTANCE_FILE_PATH));
                String formId = instances.getString(instances.getColumnIndex(InstanceProviderAPI.InstanceColumns.JR_FORM_ID));
                String formFilePath = getFormFilePath(formId);

                if (formFilePath != null) {
                    Document form = getDocument(formFilePath);
                    Document instance = getDocument(instanceFilePath);

                    if (form != null && instance != null) {
                        long id = instances.getLong(instances.getColumnIndex(InstanceProviderAPI.InstanceColumns._ID));
                        String displayName = instances.getString(instances.getColumnIndex(InstanceProviderAPI.InstanceColumns.DISPLAY_NAME));
                        String status = instances.getString(instances.getColumnIndex(InstanceProviderAPI.InstanceColumns.STATUS));
                        Uri uri = ContentUris.withAppendedId(InstanceProviderAPI.InstanceColumns.CONTENT_URI, id);

                        GeoFeature geoFeature = new GeoFeature(id, displayName, status, uri);
                        addToMap(geoFeature, form, instance);
                    }
                }
            }
            instances.close();
        }
    }

    private Cursor getCursor(Uri apiUri, String selection, String[] selectionArgs, String sortOrder) {
        return this.context
                .getContentResolver()
                .query(apiUri, null, selection, selectionArgs, sortOrder);
    }

    private Cursor getAllInstances() {
        String selection = InstanceProviderAPI.InstanceColumns.STATUS + "=? or " +
                InstanceProviderAPI.InstanceColumns.STATUS + "=? or " +
                InstanceProviderAPI.InstanceColumns.STATUS + "=? or " +
                InstanceProviderAPI.InstanceColumns.STATUS + "=?";

        String[] selectionArgs = {
                InstanceProviderAPI.STATUS_COMPLETE,
                InstanceProviderAPI.STATUS_SUBMISSION_FAILED,
                InstanceProviderAPI.STATUS_SUBMITTED,
                InstanceProviderAPI.STATUS_INCOMPLETE
        };

        String sortOrder = InstanceProviderAPI.InstanceColumns.DISPLAY_NAME + " ASC";

        return getCursor(InstanceProviderAPI.InstanceColumns.CONTENT_URI, selection, selectionArgs, sortOrder);
    }

    private String getFormFilePath(String formId) {
        String selection = FormsProviderAPI.FormsColumns.JR_FORM_ID + "=?";
        String[] selectionArgs = {formId};
        String sortOrder = FormsProviderAPI.FormsColumns.DISPLAY_NAME + " ASC, "
                + FormsProviderAPI.FormsColumns.JR_VERSION + " DESC";

        Cursor cursor = getCursor(FormsProviderAPI.FormsColumns.CONTENT_URI, selection, selectionArgs, sortOrder);

        String formFilePath = null;
        if (cursor != null && cursor.moveToFirst()) {
            formFilePath = cursor.getString(cursor.getColumnIndex(FormsProviderAPI.FormsColumns.FORM_FILE_PATH));
        }

        cursor.close();
        return formFilePath;
    }

    private Document getDocument(final String filePath) {
        try {
            final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            final Document doc = dBuilder.parse(new File(filePath));
            doc.getDocumentElement().normalize();

            return doc;
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void addToMap(GeoFeature geoFeature, Document form, Document instance) {
        NodeList fields = form.getElementsByTagName("bind");
        for (int i = 0; i < fields.getLength(); i++) {
            Node fieldNone = fields.item(i);
            if (fieldNone.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element field = (Element) fieldNone;
            String type = field.getAttribute("type");
            if (type.equals(GEOPOINT) || type.equals(GEOSHAPE) || type.equals(GEOTRACE)) {
                String[] tagNames = field.getAttribute("nodeset").split("/");
                String tagName = tagNames[tagNames.length - 1];

                NodeList items = instance.getElementsByTagName(tagName);
                for (int j = 0; j < items.getLength(); j++) {
                    Node item = items.item(j);
                    if (item.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }

                    String value = item.getTextContent();
                    if ((value != null) && (!value.equals(""))) {
                        if (type.equals(GEOPOINT)) {
                            addMarker(geoFeature, value, tagName);
                        } else {
                            addPolyline(geoFeature, value, tagName);
                        }
                    }
                }
            }
        }
    }

    private void addMarker(GeoFeature geoFeature, String value, String fieldName) {
        Marker marker = new Marker(this.mapView);
        marker.setPosition(getGeoPoint(value));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        marker.setIcon(this.context.getResources().getDrawable(
                geoFeature.isComplete() ? R.drawable.ic_marker_green : R.drawable.ic_marker_red));

        addOverlay(geoFeature, marker, fieldName);
    }

    private void addPolyline(GeoFeature geoFeature, String value, String fieldName) {
        String[] coordinates = value.replace("; ", ";").split(";");

        List<GeoPoint> points = new ArrayList<>();
        for (final String p : coordinates) {
            points.add(getGeoPoint(p));
        }

        if (points.size() > 0) {
            Polyline polyline = new Polyline();
            polyline.setWidth(6);
            polyline.setColor(geoFeature.getColor());
            polyline.setPoints(points);

            addOverlay(geoFeature, polyline, fieldName);
        }
    }

    private void addOverlay(GeoFeature geoFeature, OverlayWithIW overlay, String fieldName) {
        overlay.setTitle(geoFeature.getName());
        overlay.setSnippet(geoFeature.getId() + "  (" + fieldName + ")");
        overlay.setSubDescription(geoFeature.getStatus());

        InfoWindow window = new MarkerInfoWindow(R.layout.osm_map_infowindow_layout, this.mapView) {
            @Override
            public void onOpen(Object item) {
                Button btn = this.mView.findViewById(R.id.bubble_moreinfo);
                btn.setOnClickListener(view -> {
                    view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, geoFeature.getUri()));
                    this.close();
                });

                TextView statusText = this.mView.findViewById(R.id.bubble_subdescription);
                statusText.setTextColor(geoFeature.getColor());
                super.onOpen(item);
            }
        };
        overlay.setInfoWindow(window);

        this.mapView.getOverlays().add(overlay);
    }

    private GeoPoint getGeoPoint(String value) {
        String[] location = value.split(" ");
        double lat = Double.parseDouble(location[0]);
        double lng = Double.parseDouble(location[1]);
        return new GeoPoint(lat, lng);
    }

    private class GeoFeature {
        private long id;
        private String name;
        private String status;
        private Uri uri;

        GeoFeature(long id, String name, String status, Uri uri) {
            this.id = id;
            this.name = name;
            this.status = status;
            this.uri = uri;
        }

        String getId() {
            return "ID: " + this.id;
        }

        String getName() {
            return name;
        }

        String getStatus() {
            return status.toUpperCase(Locale.ROOT);
        }

        Uri getUri() {
            return uri;
        }

        boolean isComplete() {
            return this.status.equalsIgnoreCase(InstanceProviderAPI.STATUS_COMPLETE);
        }

        int getColor() {
            return isComplete() ? Color.GREEN : Color.RED;
        }
    }
}
