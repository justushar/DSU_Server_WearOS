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

    // State for processing
    private final float[] rotationMatrix = new float[9];
    private final float[] gravityData = new float[3];
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

        // We will use TWO sensors now, mimicking how MotionSource can use them together.
        // 1. The Rotation Vector to get a stable "down" direction (for the accelerometer part)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        // 2. The raw Gyroscope to get the rotation rate
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Update the data from whichever sensor fired the event
        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroData, 0, 3);
        }

        // --- FINAL Corrected Data Processing ---

        // 1. Correctly calculate the Gravity vector. It's the third COLUMN of the
        // rotation matrix (indices 2, 5, 8). We normalize it and multiply by G.
        float[] gravity = {
                rotationMatrix[2] * SensorManager.GRAVITY_EARTH,
                rotationMatrix[5] * SensorManager.GRAVITY_EARTH,
                rotationMatrix[8] * SensorManager.GRAVITY_EARTH
        };

        // 2. Apply the single, correct landscape transformation to the gravity vector.
        // New(X, Y, Z) = Old(Y, -X, Z)
        float[] finalGravity = { gravity[1], -gravity[0], gravity[2] };

        // 3. Apply the single, correct landscape transformation to the raw gyro data.
        float[] transformedGyro = { gyroData[1], -gyroData[0], gyroData[2] };

        // 4. Remap the transformed Gyroscope axes to the DSU protocol's order.
        // DSU wants: Pitch, Yaw, Roll
        // Our transformed axes now correspond to:
        // Pitch (rotation around new X axis) = transformedGyro[0]
        // Yaw   (rotation around new Z axis) = transformedGyro[2]
        // Roll  (rotation around new Y axis) = transformedGyro[1]
        float[] dsuGyro = {
                transformedGyro[0], // Pitch
                transformedGyro[2], // Yaw
                transformedGyro[1]  // Roll
        };

        // 5. Send the fully processed data to the server
        if (dsuServer != null && dsuServer.isRunning()) {
            dsuServer.updateSensorData(finalGravity, dsuGyro);
        }
    }

    // It's important to include the rest of the Activity lifecycle methods for stability.
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