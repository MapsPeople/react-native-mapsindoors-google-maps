package com.mapsindoorsrn;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.TurboReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.model.ReactModuleInfo;
import com.facebook.react.module.model.ReactModuleInfoProvider;
import com.facebook.react.uimanager.ViewManager;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.mapsindoorsrn.core.DirectionsRendererModule;
import com.mapsindoorsrn.core.DirectionsServiceModule;
import com.mapsindoorsrn.core.MPDisplayRuleModule;
import com.mapsindoorsrn.core.MapControlModule;
import com.mapsindoorsrn.core.MapsIndoorsModule;
import com.mapsindoorsrn.core.UtilsModule;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapsIndoorsPackage extends TurboReactPackage implements OnMapReadyCallback {
    private volatile MapControlModule mapControlModule;
    private MapsIndoorsViewManager viewManager;

    private MapControlModule getOrCreateMapControlModule(ReactApplicationContext context) {
        if (mapControlModule == null) {
            synchronized (this) {
                if (mapControlModule == null) {
                    mapControlModule = new MapControlModule(context);
                }
            }
        }
        return mapControlModule;
    }

    @Nullable
    @Override
    public NativeModule getModule(@NonNull String name, @NonNull ReactApplicationContext reactContext) {
        switch (name) {
            case MapsIndoorsModule.NAME:
                return new MapsIndoorsModule(reactContext);
            case MapControlModule.NAME:
                return getOrCreateMapControlModule(reactContext);
            case DirectionsServiceModule.NAME:
                return new DirectionsServiceModule(reactContext);
            case DirectionsRendererModule.NAME:
                return new DirectionsRendererModule(reactContext, getOrCreateMapControlModule(reactContext));
            case MPDisplayRuleModule.NAME:
                return new MPDisplayRuleModule(reactContext);
            case UtilsModule.NAME:
                return new UtilsModule(reactContext);
            default:
                return null;
        }
    }

    @Override
    public ReactModuleInfoProvider getReactModuleInfoProvider() {
        return () -> {
            Map<String, ReactModuleInfo> map = new HashMap<>();
            map.put(MapsIndoorsModule.NAME, new ReactModuleInfo(
                    MapsIndoorsModule.NAME, MapsIndoorsModule.class.getName(),
                    false, false, false, false));
            map.put(MapControlModule.NAME, new ReactModuleInfo(
                    MapControlModule.NAME, MapControlModule.class.getName(),
                    false, false, false, false));
            map.put(DirectionsServiceModule.NAME, new ReactModuleInfo(
                    DirectionsServiceModule.NAME, DirectionsServiceModule.class.getName(),
                    false, false, false, false));
            map.put(DirectionsRendererModule.NAME, new ReactModuleInfo(
                    DirectionsRendererModule.NAME, DirectionsRendererModule.class.getName(),
                    false, false, false, false));
            map.put(MPDisplayRuleModule.NAME, new ReactModuleInfo(
                    MPDisplayRuleModule.NAME, MPDisplayRuleModule.class.getName(),
                    false, false, false, false));
            map.put(UtilsModule.NAME, new ReactModuleInfo(
                    UtilsModule.NAME, UtilsModule.class.getName(),
                    false, false, false, false));
            return map;
        };
    }

    @NonNull
    @Override
    public List<ViewManager> createViewManagers(@NonNull ReactApplicationContext reactContext) {
        viewManager = new MapsIndoorsViewManager(reactContext, this);
        return Collections.singletonList(viewManager);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        if (mapControlModule != null && viewManager != null) {
            mapControlModule.setView(new GoogleMapView(googleMap, viewManager.getView()));
        }
    }
}
