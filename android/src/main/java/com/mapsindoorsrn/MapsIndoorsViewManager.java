package com.mapsindoorsrn;

import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.ViewGroupManager;
import com.facebook.react.uimanager.annotations.ReactPropGroup;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.mapsindoorsrn.core.models.MPCameraPosition;

import java.util.HashMap;
import java.util.Map;

public class MapsIndoorsViewManager extends ViewGroupManager<FrameLayout> {
    public static final String REACT_CLASS = "MapsIndoorsView";
    public final int COMMAND_CREATE = 1;

    private int propWidth;
    private int propHeight;

    // KNOWN LIMITATION (single-map): the SDK is effectively single-map today. `view` holds only the
    // most-recently-created host view, and MapsIndoorsPackage builds GoogleMapView(googleMap, getView())
    // from it, while MapControlModule downstream holds a single MapControl. So mounting more than one
    // MapsIndoorsView simultaneously is unsupported and the wrapper could reference the wrong host view.
    // Per-view lifecycle/cleanup IS handled correctly (see mapLifecycles below); full multi-map support
    // would additionally require threading the host view per map into onMapReady and making
    // MapControlModule per-view. Tracked as a follow-up, out of scope for SPEX-2030.
    private View view;
    private MPCameraPosition cameraPosition = null;
    private Boolean showCompass = null;

    // SPEX-2030: per-host-view MapView lifecycle, tracked so it can be driven from the view's attach state
    // and the host foreground state, and destroyed in onDropViewInstance. A ViewManager is shared across all
    // instances of its view type, so this is keyed per host view rather than a single field.
    //
    // Cleanup is manual: entries are removed in onDropViewInstance (which React Native calls when the view is
    // unmounted) and in the create/replace path. This is intentionally a plain HashMap, not a WeakHashMap: the
    // MapView is added as a child of its key (reactNativeViewId resolves to the host view itself), so the value
    // would keep the weak key reachable and it could never clear — a WeakHashMap would only give a misleading
    // illusion of a GC safety net.
    private final Map<FrameLayout, MapViewLifecycle> mapLifecycles = new HashMap<>();

    private Gson gson = new Gson();

    ReactApplicationContext mReactContext;
    OnMapReadyCallback mOnMapReadyCallback;

    public MapsIndoorsViewManager(ReactApplicationContext reactContext, OnMapReadyCallback onMapReadyCallback) {
        mReactContext = reactContext;
        mOnMapReadyCallback = onMapReadyCallback;

        // SPEX-2030: also gate the map lifecycle on the host (Activity) foreground state. The view stays
        // attached to the window when the app is merely backgrounded (home / app switch), so the attach
        // listener alone would leave the MapView resumed and rendering in the background. React Native's
        // host lifecycle (onHostResume/onHostPause) gives us the Activity foreground signal.
        mReactContext.addLifecycleEventListener(new LifecycleEventListener() {
            @Override
            public void onHostResume() {
                setHostResumedOnAll(true);
            }

            @Override
            public void onHostPause() {
                setHostResumedOnAll(false);
            }

            @Override
            public void onHostDestroy() {
                setHostResumedOnAll(false);
            }
        });

        // SPEX-2030: with a manually-managed MapView there is no host (Activity/Fragment) forwarding
        // onLowMemory() to it, so forward it ourselves via the application's ComponentCallbacks.
        mReactContext.getApplicationContext().registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onLowMemory() {
                for (MapViewLifecycle lifecycle : mapLifecycles.values()) {
                    lifecycle.onLowMemory();
                }
            }

            @Override
            public void onConfigurationChanged(@NonNull Configuration newConfig) {
                // no-op
            }
        });
    }

    private void setHostResumedOnAll(boolean hostResumed) {
        for (MapViewLifecycle lifecycle : mapLifecycles.values()) {
            lifecycle.setHostResumed(hostResumed);
        }
    }

    @NonNull
    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @NonNull
    @Override
    protected FrameLayout createViewInstance(@NonNull ThemedReactContext reactContext) {
        view = new FrameLayout(reactContext);
        return (FrameLayout) view;
    }

    @Nullable
    @Override
    public Map<String, Integer> getCommandsMap() {
        return MapBuilder.of("create", COMMAND_CREATE);
    }

    @Override
    public void receiveCommand(@NonNull FrameLayout root, int commandId, @Nullable ReadableArray args) {
        super.receiveCommand(root, commandId, args);
        assert args != null;
        int reactNativeViewId = args.getInt(0);

        if (commandId == COMMAND_CREATE) {
            createMapFragment(root, reactNativeViewId);

            if (!args.isNull(1)) {
                cameraPosition = gson.fromJson(args.getString(1), MPCameraPosition.class);
            }

            if (!args.isNull(2)) {
                showCompass = args.getBoolean(2);
            }
        }
    }

    @Override
    public void receiveCommand(@NonNull FrameLayout root, String commandId, @Nullable ReadableArray args) {
        super.receiveCommand(root, commandId, args);
        assert args != null;
        int reactNativeViewId = args.getInt(0);
        int commandIdInt = Integer.parseInt(commandId);

        if (commandIdInt == COMMAND_CREATE) {
            createMapFragment(root, reactNativeViewId);

            if (!args.isNull(1)) {
                cameraPosition = gson.fromJson(args.getString(1), MPCameraPosition.class);
            }

            if (!args.isNull(2)) {
                showCompass = args.getBoolean(2);
            }
        }
    }

    public void createMapFragment(FrameLayout root, int reactNativeViewId) {
        ViewGroup parentView = root.findViewById(reactNativeViewId);
        setupLayout(parentView);

        // SPEX-2030: use a directly-owned GoogleMap MapView instead of a SupportMapFragment. A fragment
        // committed into the RN view id is driven by the activity's FragmentManager, not by the RN view's
        // attach state, so with @react-navigation/native-stack (react-native-screens) the map's surface was
        // not restored when navigating back (black map). A MapView lets us forward its lifecycle from the
        // view's window attach/detach. GoogleMap MapView does not observe ViewTreeLifecycleOwner, so the
        // onCreate/onStart/onResume/onPause/onStop/onDestroy calls must be made explicitly.
        final MapView mapView = new MapView(parentView.getContext());
        mapView.onCreate(null);

        // The MapView is RESUMED only when attached AND the host Activity is in the foreground; detaching
        // (navigation) or backgrounding the app stops it (onPause/onStop), releasing the renderer.
        final MapViewLifecycle lifecycle = new MapViewLifecycle(mapView);
        lifecycle.setHostResumed(mReactContext.getLifecycleState() == LifecycleState.RESUMED);

        mapView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                lifecycle.setAttached(true);
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                lifecycle.setAttached(false);
            }
        });

        // Replace any previous map view in this container (mirrors the old fragment 'replace').
        MapViewLifecycle previous = mapLifecycles.remove(root);
        if (previous != null) {
            previous.destroy();
        }
        parentView.removeAllViews();
        mapLifecycles.put(root, lifecycle);
        parentView.addView(mapView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mapView.getMapAsync(googleMap -> {
            if (cameraPosition != null) {
                CameraPosition currentCameraPosition = googleMap.getCameraPosition();
                CameraPosition camPos = CameraPosition.builder()
                        .zoom(cameraPosition.zoom != null ? cameraPosition.zoom : currentCameraPosition.zoom)
                        .bearing(cameraPosition.bearing != null ? cameraPosition.bearing : currentCameraPosition.bearing)
                        .target(new LatLng(cameraPosition.target.getLat(), cameraPosition.target.getLng()))
                        .tilt(cameraPosition.tilt != null ? cameraPosition.tilt : currentCameraPosition.tilt)
                        .build();
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(camPos));
            }

            if (showCompass != null) {
                googleMap.getUiSettings().setCompassEnabled(showCompass);
            }

            mOnMapReadyCallback.onMapReady(googleMap);
        });
    }

    @Override
    public void onDropViewInstance(@NonNull FrameLayout view) {
        super.onDropViewInstance(view);
        MapViewLifecycle lifecycle = mapLifecycles.remove(view);
        if (lifecycle != null) {
            lifecycle.destroy();
        }
        if (this.view == view) {
            this.view = null;
        }
    }

    /**
     * Drives a GoogleMap {@link MapView}'s lifecycle from the view's attach state AND the host Activity
     * foreground state (SPEX-2030). The MapView is RESUMED only when attached AND the host is resumed;
     * otherwise it is stopped (onPause/onStop), so a backgrounded or navigated-away map does not keep
     * rendering. GoogleMap MapView requires these calls in order, so the current state is tracked and we
     * step up/down to the target. onCreate() is called by the caller before constructing this.
     */
    static class MapViewLifecycle {
        private static final int CREATED = 0;
        private static final int STARTED = 1;
        private static final int RESUMED = 2;

        private final MapView mapView;
        private int state = CREATED;
        private boolean attached = false;
        private boolean hostResumed = false;
        private boolean destroyed = false;

        MapViewLifecycle(MapView mapView) {
            this.mapView = mapView;
        }

        void setAttached(boolean attached) {
            this.attached = attached;
            apply();
        }

        void setHostResumed(boolean hostResumed) {
            this.hostResumed = hostResumed;
            apply();
        }

        private void apply() {
            if (destroyed) {
                return;
            }
            int target = (attached && hostResumed) ? RESUMED : CREATED;
            while (state < target) {
                if (state == CREATED) {
                    mapView.onStart();
                    state = STARTED;
                } else {
                    mapView.onResume();
                    state = RESUMED;
                }
            }
            while (state > target) {
                if (state == RESUMED) {
                    mapView.onPause();
                    state = STARTED;
                } else {
                    mapView.onStop();
                    state = CREATED;
                }
            }
        }

        void onLowMemory() {
            if (!destroyed) {
                mapView.onLowMemory();
            }
        }

        void destroy() {
            if (destroyed) {
                return;
            }
            // Step down to CREATED (onPause/onStop as needed) before destroying.
            attached = false;
            hostResumed = false;
            apply();
            destroyed = true;
            mapView.onDestroy();
        }
    }

    @ReactPropGroup(names = {"width", "height"}, customType = "Style")
    public void setStyle(FrameLayout view, int index, Integer value) {
        if (index == 0) {
            propWidth = value;
        }

        if (index == 1) {
            propHeight = value;
        }
    }

    public void setupLayout(View view) {
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                manuallyLayoutChildren(view);
                view.getViewTreeObserver().dispatchOnGlobalLayout();
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }

    /**
     * Layout all children properly
     */
    public void manuallyLayoutChildren(View view) {

        float density = mReactContext.getResources().getDisplayMetrics().density;

        // propWidth and propHeight coming from react-native props
        int width = (int)(propWidth * density);
        int height = (int)(propHeight * density);

        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));

        view.layout(0, 0, width, height);
    }

    public View getView() {
        return view;
    }
}
