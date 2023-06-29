package com.reactlibrary;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;

import com.facebook.react.bridge.ReadableMap;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.mapsindoors.core.MPIMapConfig;
import com.mapsindoors.core.MPPoint;
import com.mapsindoors.core.OnResultReadyListener;
import com.mapsindoors.googlemaps.MPMapConfig;
import com.reactlibrary.core.RCMapView;
import com.reactlibrary.core.models.MPCameraPosition;
import com.reactlibrary.core.models.MPCameraUpdate;

public class GoogleMapView implements RCMapView {
    private GoogleMap mMap;
    private View mView;

    public GoogleMapView(GoogleMap map, View view) {
        mMap = map;
        mView = view;
    }

    @Override
    public void animateCamera(MPCameraUpdate cameraUpdate, Integer duration, OnResultReadyListener onFinished) {
        CameraUpdate update = toCameraOptions(cameraUpdate);

        if (duration != null) {
            mMap.animateCamera(update, duration, new GoogleMap.CancelableCallback() {
                @Override
                public void onCancel() {
                    onFinished.onResultReady(null);
                }

                @Override
                public void onFinish() {
                    onFinished.onResultReady(null);
                }
            });
        } else {
            mMap.animateCamera(update, new GoogleMap.CancelableCallback() {
                @Override
                public void onCancel() {
                    onFinished.onResultReady(null);
                }

                @Override
                public void onFinish() {
                    onFinished.onResultReady(null);
                }
            });
        }
    }

    @Override
    public void moveCamera(MPCameraUpdate cameraUpdate) {
        mMap.moveCamera(toCameraOptions(cameraUpdate));
    }

    @Override
    public MPCameraPosition getCurrentCameraPosition() {
        CameraPosition cameraPosition = mMap.getCameraPosition();
        return new MPCameraPosition(cameraPosition.zoom,
                cameraPosition.tilt,
                cameraPosition.bearing,
                new MPPoint(cameraPosition.target.latitude, cameraPosition.target.longitude));
    }

    @Override
    public MPIMapConfig parseConfig(ReadableMap configMap, Activity activity) {
        String apiKey = activity.getString(R.string.google_maps_key);
        boolean useDefaultStyle = !configMap.hasKey("useDefaultMapsIndoorsStyle") || configMap.getBoolean("useDefaultMapsIndoorsStyle");

        MPMapConfig.Builder config = new MPMapConfig.Builder(activity, mMap, apiKey, mView, useDefaultStyle);
        if (configMap.hasKey("typeface")) {
            config.setMapLabelFont(Typeface.create("typeface", Typeface.NORMAL), configMap.getString("color"), configMap.getBoolean("showHalo"));
        }

        if (configMap.hasKey("textSize")) {
            config.setMapLabelTextSize(configMap.getInt("textSize"));
        }

        if (configMap.hasKey("showFloorSelector")) {
            config.setShowFloorSelector(configMap.getBoolean("showFloorSelector"));
        }

        if (configMap.hasKey("showInfoWindowOnClick")) {
            config.setShowInfoWindowOnLocationClicked(configMap.getBoolean("showInfoWindowOnClick"));
        }

        if (configMap.hasKey("showUserPosition")) {
            config.setShowUserPosition(configMap.getBoolean("showUserPosition"));
        }

        if (configMap.hasKey("enableTileFadeIn")) {
            config.setTileFadeInEnabled(configMap.getBoolean("enableTileFadeIn"));
        }

        return config.build();
    }

    public CameraUpdate toCameraOptions(MPCameraUpdate cameraUpdate) {
        MPCameraUpdate.CameraUpdateMode mode = cameraUpdate.getMode();
        if (mode == null) {
            return null;
        }

        switch (mode) {
            case FROM_POINT:
                return CameraUpdateFactory.newLatLng(new LatLng(cameraUpdate.point.getLat(), cameraUpdate.point.getLng()));
            case FROM_BOUNDS:
                if (cameraUpdate.width != null && cameraUpdate.height != null) {
                    return CameraUpdateFactory.newLatLngBounds(
                            new LatLngBounds(
                                    new LatLng(cameraUpdate.bounds.getSouthWest().getLat(), cameraUpdate.bounds.getSouthWest().getLng()),
                                    new LatLng(cameraUpdate.bounds.getNorthEast().getLat(), cameraUpdate.bounds.getNorthEast().getLng())),
                            cameraUpdate.padding,
                            cameraUpdate.height,
                            cameraUpdate.width);
                } else {
                    return CameraUpdateFactory.newLatLngBounds(
                            new LatLngBounds(
                                    new LatLng(cameraUpdate.bounds.getSouthWest().getLat(), cameraUpdate.bounds.getSouthWest().getLng()),
                                    new LatLng(cameraUpdate.bounds.getNorthEast().getLat(), cameraUpdate.bounds.getNorthEast().getLng())),
                            cameraUpdate.padding
                    );
                }
            case ZOOM_BY:
                return CameraUpdateFactory.zoomBy(cameraUpdate.zoom);
            case ZOOM_TO:
                return CameraUpdateFactory.zoomTo(cameraUpdate.zoom);
            case FROM_CAMERA_POSITION:
                CameraPosition cameraPosition = CameraPosition.builder()
                        .zoom(cameraUpdate.position.zoom)
                        .bearing(cameraUpdate.position.bearing)
                        .target(new LatLng(cameraUpdate.position.target.getLat(), cameraUpdate.position.target.getLng()))
                        .tilt(cameraUpdate.position.tilt)
                        .build();
                return CameraUpdateFactory.newCameraPosition(cameraPosition);
        }
        return null;
    }
}
