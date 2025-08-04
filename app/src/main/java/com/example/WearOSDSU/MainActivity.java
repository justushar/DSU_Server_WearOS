// MainActivity.java
package com.example.WearOSDSU; // Ensure this matches your package name

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class MainActivity extends Activity implements SensorEventListener {

    private DsuServer dsuServer;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor gyroSensor;


    private final float[] rotationMatrix = new float[9];

    private final float[] gyroData = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ToggleButton toggleStream = findViewById(R.id.streamToggle);
        TextView ipDisplay = findViewById(R.id.ipAddressText);

        String localIp = getLocalIpAddress();
        ipDisplay.setText(localIp != null ? "IP: " + localIp + ":26760" : "Could not get IP.");

        dsuServer = new DsuServer();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);


        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);

        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (rotationSensor == null || gyroSensor == null) {
            Toast.makeText(this, "Required sensors not available", Toast.LENGTH_LONG).show();
            toggleStream.setEnabled(false);
            return;
        }

        toggleStream.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                startStreaming();
            } else {
                stopStreaming();
            }
        });
    }

    private void startStreaming() {
        dsuServer.start();
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        Toast.makeText(this, "Streaming started", Toast.LENGTH_SHORT).show();
    }

    private void stopStreaming() {
        dsuServer.stop();
        sensorManager.unregisterListener(this);
        Toast.makeText(this, "Streaming stopped", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}


    private static final float RADIANS_TO_DEGREES = (float) (180.0f / Math.PI);

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Update the data from whichever sensor fired the event
        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroData, 0, 3);
        }


        float[] gravity = {
                rotationMatrix[2] * SensorManager.GRAVITY_EARTH,
                rotationMatrix[5] * SensorManager.GRAVITY_EARTH,
                rotationMatrix[8] * SensorManager.GRAVITY_EARTH
        };


        float[] finalGravity = { gravity[1], -gravity[0], gravity[2] };

        float[] transformedGyro = { gyroData[1], -gyroData[0], gyroData[2] };


        float[] gyroInDegrees = {
                transformedGyro[0] * RADIANS_TO_DEGREES,
                transformedGyro[1] * RADIANS_TO_DEGREES,
                transformedGyro[2] * RADIANS_TO_DEGREES
        };


        float[] dsuGyro = {
                gyroInDegrees[0], // Pitch (rotation around new X axis)
                gyroInDegrees[2], // Yaw   (rotation around new Z axis)
                gyroInDegrees[1]  // Roll  (rotation around new Y axis)
        };


        if (dsuServer != null && dsuServer.isRunning()) {

            dsuServer.updateSensorData(finalGravity, dsuGyro);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (dsuServer.isRunning()) {
            stopStreaming();
            ToggleButton toggleStream = findViewById(R.id.streamToggle);
            toggleStream.setChecked(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dsuServer != null && dsuServer.isRunning()) {
            stopStreaming();
        }
    }

    public static String getLocalIpAddress() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
}