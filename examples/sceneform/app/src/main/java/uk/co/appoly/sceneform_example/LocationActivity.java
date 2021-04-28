/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.appoly.sceneform_example;

import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.ViewRenderable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.utils.ARLocationPermissionHelper;
import uk.co.appoly.sceneform_example.model.LocationType;
import uk.co.appoly.sceneform_example.model.Marker;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore and Sceneform APIs.
 */
public class LocationActivity extends AppCompatActivity {
    private static final String TAG = "LocationActivity";
    private boolean installRequested;
    private boolean hasFinishedLoading = false;

    private Snackbar loadingMessageSnackbar = null;

    private ArSceneView arSceneView;

    // Our ARCore-Location scene
    private LocationScene locationScene;
    private final List<Marker> markerDataList = new ArrayList<>();
    private final List<CompletableFuture<Void>> myRenderableFuture = new ArrayList<>();
    private final List<Pair<ViewRenderable, Marker>> myRenderable = new ArrayList<>();

    private void generateSampleData() {
        markerDataList.add(new Marker(
                20.9964248899242, 105.868930437133, "The Coffee House", LocationType.COFFEE_SHOP
        ));

        markerDataList.add(new Marker(
                20.996344464798728, 105.8685612447664, "T5 - Times City", LocationType.VIN_BUILDING
        ));

        markerDataList.add(new Marker(
                20.99712869246975, 105.86797196605553, "Century Tower", LocationType.VIN_BUILDING
        ));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_sceneform);
        arSceneView = findViewById(R.id.ar_scene_view);

        generateSampleData();

        for (final Marker marker : markerDataList) {
            myRenderableFuture.add(ViewRenderable.builder()
                    .setView(this, R.layout.example_layout)
                    .build()
                    .thenAccept(renderable -> {
                        View view = renderable.getView();
                        TextView title = view.findViewById(R.id.textView);
                        title.setText(marker.getTitle());
                        myRenderable.add(new Pair<>(renderable, marker));
                    }));
        }

        CompletableFuture.allOf(myRenderableFuture.toArray(new CompletableFuture[myRenderableFuture.size()])).handle(
                (a,b) -> {
                    hasFinishedLoading = true;
                    return null;
                }
        );

        // Set an update listener on the Scene that will hide the loading message once a Plane is
        // detected.
        arSceneView
                .getScene()
                .setOnUpdateListener(
                        frameTime -> {
                            if (!hasFinishedLoading) {
                                return;
                            }

                            if (locationScene == null) {
                                // If our locationScene object hasn't been setup yet, this is a good time to do it
                                // We know that here, the AR components have been initiated.
                                locationScene = new LocationScene(this, arSceneView);

                                for (final Pair<ViewRenderable, Marker> data : myRenderable) {
                                    LocationMarker locationMarker = new LocationMarker(
                                            data.second.getLongitude(),
                                            data.second.getLatitude(),
                                            getView(data.first, data.second)
                                    );
                                    locationMarker.setRenderEvent(node -> {
                                        View view = data.first.getView();
                                        TextView distanceTextView = view.findViewById(R.id.textView2);
                                        distanceTextView.setText(node.getDistance() + " m");
                                    });

                                    locationScene.mLocationMarkers.add(locationMarker);
                                }
                            }

                            Frame frame = arSceneView.getArFrame();
                            if (frame == null) {
                                return;
                            }

                            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                                return;
                            }

                            if (locationScene != null) {
                                locationScene.processFrame(frame);
                            }

                            if (loadingMessageSnackbar != null) {
                                for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                                    if (plane.getTrackingState() == TrackingState.TRACKING) {
                                        hideLoadingMessage();
                                    }
                                }
                            }
                        });


        // Lastly request CAMERA & fine location permission which is required by ARCore-Location.
        ARLocationPermissionHelper.requestPermission(this);
    }

    private void checkIfFoundAndReplace(LocationScene locationScene, LocationMarker locationMarker) {
        if (locationScene == null) {
            return;
        }
        if (locationScene.mLocationMarkers.size() >= markerDataList.size()) {
            for (int i = 0; i < locationScene.mLocationMarkers.size(); i++) {
                if (locationScene.mLocationMarkers.get(i).node.getName().equals(locationMarker.node.getName())) {
                    locationScene.mLocationMarkers.get(i).anchorNode.getAnchor().detach();
                    locationScene.mLocationMarkers.get(i).anchorNode.setAnchor(null);
                    locationScene.mLocationMarkers.get(i).anchorNode.setEnabled(false);
                    locationScene.mLocationMarkers.get(i).anchorNode = null;
                    locationScene.mLocationMarkers.remove(i);

                    locationScene.mLocationMarkers.add(locationMarker);
                    locationScene.refreshAnchors();
                    return;
                }
            }
        } else {
            locationScene.mLocationMarkers.add(locationMarker);
        }
    }

    private Node getView(ViewRenderable vr, Marker marker) {
        Node node = new Node();
        node.setRenderable(vr);

        return node;
    }

    /**
     * Make sure we call locationScene.resume();
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (locationScene != null) {
            locationScene.resume();
        }

        if (arSceneView.getSession() == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                Session session = DemoUtils.createArSession(this, installRequested);
                if (session == null) {
                    installRequested = ARLocationPermissionHelper.hasPermission(this);
                    return;
                } else {
                    arSceneView.setupSession(session);
                }
            } catch (UnavailableException e) {
                DemoUtils.handleSessionException(this, e);
            }
        }

        try {
            arSceneView.resume();
        } catch (CameraNotAvailableException ex) {
            DemoUtils.displayError(this, "Unable to get camera", ex);
            finish();
            return;
        }

        if (arSceneView.getSession() != null) {
            showLoadingMessage();
        }
    }

    /**
     * Make sure we call locationScene.pause();
     */
    @Override
    public void onPause() {
        super.onPause();

        if (locationScene != null) {
            locationScene.pause();
        }

        arSceneView.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        arSceneView.destroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if (!ARLocationPermissionHelper.hasPermission(this)) {
            if (!ARLocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                ARLocationPermissionHelper.launchPermissionSettings(this);
            } else {
                Toast.makeText(
                        this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                        .show();
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        LocationActivity.this.findViewById(android.R.id.content),
                        R.string.plane_finding,
                        Snackbar.LENGTH_INDEFINITE);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
    }

    private void hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return;
        }

        loadingMessageSnackbar.dismiss();
        loadingMessageSnackbar = null;
    }
}
