package com.example.WearOSDSU; // Ensure this matches your package name

import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

public class DsuServer {
    private static final String TAG = "DsuServer";
    private static final int PORT = 26760;

    private Thread serverThread;
    private volatile boolean isRunning = false;
    private DatagramSocket socket;

    private InetAddress clientAddress;
    private int clientPort;
    private long lastClientMessageTime;

    private final Object sensorLock = new Object();
    private final float[] currentGyro = new float[3];
    private final float[] currentAccel = new float[3];

    public void start() {
        if (isRunning) return;
        isRunning = true;
        serverThread = new Thread(this::serverLoop);
        serverThread.start();
        Log.i(TAG, "DSU Server started.");
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        if (socket != null) {
            socket.close();
        }
        try {
            if (serverThread != null) {
                serverThread.join();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Error stopping server thread", e);
            Thread.currentThread().interrupt();
        }
        Log.i(TAG, "DSU Server stopped.");
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void updateSensorData(float[] accel, float[] gyro) {
        synchronized (sensorLock) {
            System.arraycopy(accel, 0, this.currentAccel, 0, 3);
            System.arraycopy(gyro, 0, this.currentGyro, 0, 3);
        }
    }

    private void serverLoop() {
        try {
            socket = new DatagramSocket(PORT);
            socket.setSoTimeout(1000); // Use a timeout to make the loop responsive
            Log.i(TAG, "Socket created, listening on port " + PORT);

            byte[] receiveBuffer = new byte[128];

            while (isRunning) {
                if (clientAddress != null && (System.currentTimeMillis() - lastClientMessageTime > 5000)) {
                    Log.i(TAG, "Client timed out. Awaiting new connection.");
                    clientAddress = null;
                }

                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                try {
                    socket.receive(receivePacket);
                    clientAddress = receivePacket.getAddress();
                    clientPort = receivePacket.getPort();
                    lastClientMessageTime = System.currentTimeMillis();
                    handleRequest(receivePacket);
                } catch (SocketTimeoutException e) {
                    if (clientAddress != null) {
                        sendMotionData();
                    }
                }
            }
        } catch (SocketException e) {
            if (isRunning) Log.e(TAG, "SocketException", e);
        } catch (IOException e) {
            Log.e(TAG, "IOException", e);
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            isRunning = false;
            clientAddress = null;
            Log.i(TAG, "Server loop finished.");
        }
    }

    private void handleRequest(DatagramPacket packet) throws IOException {
        byte[] data = packet.getData();
        if (data.length < 20) return;

        int messageType = ((data[19] & 0xFF) << 24) | ((data[18] & 0xFF) << 16) | ((data[17] & 0xFF) << 8) | (data[16] & 0xFF);

        if (messageType == 0x100001) { // "List Controllers" request
            Log.i(TAG, "Responding to controller info request...");
            sendPacket(DsuPacketBuilder.buildControllerInfoPacket(0, true));
            for (int i = 1; i < 4; i++) {
                sendPacket(DsuPacketBuilder.buildControllerInfoPacket(i, false));
            }
        }
    }

    private void sendMotionData() throws IOException {
        float[] accel, gyro;
        synchronized (sensorLock) {
            accel = Arrays.copyOf(currentAccel, currentAccel.length);
            gyro = Arrays.copyOf(currentGyro, currentGyro.length);
        }
        byte[] dataPacket = DsuPacketBuilder.buildControllerDataPacket(accel, gyro);
        sendPacket(dataPacket);
    }

    private void sendPacket(byte[] data) throws IOException {
        if (clientAddress == null) return;
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, clientAddress, clientPort);
        socket.send(sendPacket);
    }
}