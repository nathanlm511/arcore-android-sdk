/*
 * Copyright 2018 Google LLC. All Rights Reserved.
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
package com.google.ar.sceneform.samples.hellosceneform;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.util.LinkedList;
import java.util.List;

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class HelloSceneformActivity extends AppCompatActivity {
  private static final String TAG = HelloSceneformActivity.class.getSimpleName();
  private static final double MIN_OPENGL_VERSION = 3.0;

  private ArFragment arFragment;
  private ModelRenderable andyRenderable;
  private List<GeolocationMarker> geolocationMarkers;

  private class GeolocationMarker {
    ViewRenderable renderable;
    TextView textView;
    boolean isPlaced;
    double latitude;
    double longitude;

    GeolocationMarker(ViewRenderable renderable, TextView textview, boolean isPlaced, double lat, double lon) {
      this.renderable = renderable;
      this.textView = textview;
      this.isPlaced = isPlaced;
      this.latitude = lat;
      this.longitude = lon;
    }
  }

  // compass
  private ImageView imageView;

  private SensorManager sensorManager;
  private Sensor sensorAccelerometer;
  private Sensor sensorMagneticField;

  private float[] floatGravity = new float[3];
  private float[] floatGeoMagnetic = new float[3];

  private float[] floatOrientation = new float[3];
  private float[] floatRotationMatrix = new float[9];

  boolean placed = false;


  // current Lat: 37.2441088
  // current Long: -80.4225024
  //Latitude: 37.4220
  //Longitude: -122.0840
  // test Latitude: 37.4221
  // test Longitude: -122.0840
  private double[][] latLongs = new double[][]{
          {37.2441088, -80.4224},
          {37.2441092, -80.4224},
          {37.244195, -80.4224},
          {37.244083, -80.4224},
  };


  @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  // CompletableFuture requires api level 24
  // FutureReturnValueIgnored is not valid
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }

    setContentView(R.layout.activity_ux);
    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

    // compass
    imageView = findViewById(R.id.imageView);
    geolocationMarkers = new LinkedList<>();

    sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

    sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    sensorMagneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

    SensorEventListener sensorEventListenerAccelrometer = new SensorEventListener() {
      @Override
      public void onSensorChanged(SensorEvent event) {
        floatGravity = event.values;

        SensorManager.getRotationMatrix(floatRotationMatrix, null, floatGravity, floatGeoMagnetic);
        SensorManager.getOrientation(floatRotationMatrix, floatOrientation);

        imageView.setRotation((float) (-floatOrientation[0] * 180 / 3.14159));
      }

      @Override
      public void onAccuracyChanged(Sensor sensor, int accuracy) {
      }
    };

    SensorEventListener sensorEventListenerMagneticField = new SensorEventListener() {
      @Override
      public void onSensorChanged(SensorEvent event) {
        floatGeoMagnetic = event.values;

        SensorManager.getRotationMatrix(floatRotationMatrix, null, floatGravity, floatGeoMagnetic);
        SensorManager.getOrientation(floatRotationMatrix, floatOrientation);

        imageView.setRotation((float) (-floatOrientation[0] * 180 / 3.14159));
      }

      @Override
      public void onAccuracyChanged(Sensor sensor, int accuracy) {
      }
    };
    sensorManager.registerListener(sensorEventListenerAccelrometer, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    sensorManager.registerListener(sensorEventListenerMagneticField, sensorMagneticField, SensorManager.SENSOR_DELAY_NORMAL);

    // When you build a Renderable, Sceneform loads its resources in the background while returning
    // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
    ModelRenderable.builder()
            .setSource(this, R.raw.andy)
            .build()
            .thenAccept(renderable -> andyRenderable = renderable)
            .exceptionally(
                    throwable -> {
                      Toast toast =
                              Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                      toast.setGravity(Gravity.CENTER, 0, 0);
                      toast.show();
                      return null;
                    });
    for (double[] latLong: latLongs) {
      ViewRenderable.builder()
              .setView(this, R.layout.gelocation_marker)
              .build()
              .thenAccept(renderable -> {
                geolocationMarkers.add(new GeolocationMarker(renderable,
                        (TextView) renderable.getView(), false,
                        latLong[0], latLong[1]));
              });
    }

    // Note can do renderable.getView().findViewById(R.id.geolocationMarker(;

    arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);
    arFragment.setOnTapArPlaneListener(
            (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
              if (andyRenderable == null) {
                return;
              }

              // Create the Anchor.
              Anchor anchor = hitResult.createAnchor();
              AnchorNode anchorNode = new AnchorNode(anchor);
              anchorNode.setParent(arFragment.getArSceneView().getScene());

              // Create the transformable andy and add it to the anchor.
              TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
              andy.setParent(anchorNode);
              andy.setRenderable(andyRenderable);
              andy.select();
            });
  }

  private void onUpdateFrame(FrameTime frameTime) {

    Frame frame = arFragment.getArSceneView().getArFrame();

    // If there is no frame, just return.
    if (frame == null) {
      return;
    }

    //Making sure ARCore is tracking some feature points, makes the augmentation little stable.
    if (frame.getCamera().getTrackingState() == TrackingState.TRACKING) {

      for (GeolocationMarker marker : geolocationMarkers) {
        if (!marker.isPlaced) {
          // create anchor
          //Add an Anchor and a renderable in front of the camera
          //
          //
          // current Lat: 37.2441088
          // current Long: -80.4225024
          double phoneLat = 37.2441088;
          double phoneLong = -80.4225024;
          double testLat = marker.latitude;
          double testLong = marker.longitude;
          double phoneLatRadians = phoneLat * Math.PI / 180;
          double testLatRadians = testLat * Math.PI / 180;
          double latDiff = (testLat - phoneLat) * Math.PI / 180;
          double longDiff = (testLong - phoneLong) * Math.PI / 180;

          // calculate distance
          double a = Math.sin(latDiff / 2) * Math.sin(latDiff / 2) +
                  Math.cos(phoneLatRadians) * Math.cos(testLatRadians) * Math.sin(longDiff / 2) * Math.sin(longDiff / 2);
          double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
          double R = 6371000;
          double distance = R * c;

          // calculate bearing
          double y = Math.sin(longDiff) * Math.cos(testLat);
          double x = Math.cos(phoneLat) * Math.sin(testLat) - Math.sin(phoneLat) * Math.cos(testLat) * Math.cos(longDiff);
          double theta = Math.atan2(y, x); // in radians
          double bearing = (theta * 180 / Math.PI + 360) % 360; // in degrees

          // calculate vectors
          double zPos = -(3 * Math.sin(theta));
          double xPos = (3 * Math.cos(theta));
          // compass direction
          // 0 = North, 90 = west, 180 = south, 270 = east?
          // compass always starts North? so worry about later
          float phoneBearing = (float) (-floatOrientation[0] * 180 / 3.14159);
          float[] translation = {0,(float) xPos, (float) zPos};
          float[] rotation = {0, 0, 0, 1};
        /*
      Anchor testAnchor =  arFragment.getSession().createAnchor(new Pose(pos, rotation));
      anchors.add(new ColoredAnchor(anchor, new float[] {66.0f / 255.0f, 133.0f / 255.0f, 244.0f / 255.0f}, null));
      firstAnchor = false;
         */
          Log.d("test", "test");
          Pose pos = frame.getCamera().getPose().compose(Pose.makeTranslation(translation));
          //Pose pos = frame.getCamera().getPose().compose(Pose.makeTranslation(new float[] {0, 0, (float)(-0.5)}));
          Anchor anchorTest = arFragment.getArSceneView().getSession().createAnchor(pos);
          AnchorNode anchorNodeTest = new AnchorNode(anchorTest);
          anchorNodeTest.setParent(arFragment.getArSceneView().getScene());

          Vector3 translationVector = new Vector3(translation[0], translation[1], translation[2]);
          Quaternion lookRotation = Quaternion.lookRotation(translationVector, Vector3.up());
          lookRotation = Quaternion.multiply(lookRotation, Quaternion.axisAngle(new Vector3(0f, 0, 1f), 90f));

          marker.textView.setText(Double.toString(distance));
          TransformableNode andyTest = new TransformableNode(arFragment.getTransformationSystem());
          andyTest.setLocalRotation(lookRotation);
          andyTest.setParent(anchorNodeTest);
          andyTest.setRenderable(marker.renderable);
          andyTest.select();
          marker.isPlaced = true;
        }
      }

    }

  }

  /**
   * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
   * on this device.
   *
   * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
   *
   * <p>Finishes the activity if Sceneform can not run
   */
  public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
      Log.e(TAG, "Sceneform requires Android N or later");
      Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
      activity.finish();
      return false;
    }
    String openGlVersionString =
            ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                    .getDeviceConfigurationInfo()
                    .getGlEsVersion();
    if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
      Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
      Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
              .show();
      activity.finish();
      return false;
    }
    return true;
  }
}
