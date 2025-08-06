package com.example.WearOSDSU;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

public class DsuPacketBuilder {

    private static final byte[] MAGIC_BYTES = "DSUS".getBytes();
    private static final short PROTOCOL_VERSION = 1001;

    // Message Types
    private static final int MSG_TYPE_CONTROLLER_INFO = 0x100001;
    private static final int MSG_TYPE_CONTROLLER_DATA = 0x100002;

    // Controller Slot States
    private static final byte SLOT_STATE_NOT_CONNECTED = 0;
    private static final byte SLOT_STATE_CONNECTED = 2;

    // Controller Models - Should be 2
    private static final byte MODEL_FULL_GYRO = 2;

    // Controller Connection Types
    private static final byte CONNECTION_TYPE_BLUETOOTH = 2;

    // Controller Battery States
    private static final byte BATTERY_STATE_FULL = 5;

    private static int packetCounter = 0;

    /**
     * Builds the response packet for a "controller information" request.
     * @param slot The controller slot (0-3) this packet is for.
     * @param isConnected Whether this slot should be reported as connected.
     * @return A byte array ready to be sent via UDP.
     */
    public static byte[] buildControllerInfoPacket(int slot, boolean isConnected) {
        ByteBuffer buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);

        // Header
        buffer.put(MAGIC_BYTES);
        buffer.putShort(PROTOCOL_VERSION);
        buffer.putShort((short) 16); // Payload Length (fixed for this packet type)
        buffer.putInt(0); // Placeholder for CRC32

        // Event
        buffer.putInt(0); // Server ID (can be 0)
        buffer.putInt(MSG_TYPE_CONTROLLER_INFO);

        // Payload
        buffer.put((byte) slot);
        buffer.put(isConnected ? SLOT_STATE_CONNECTED : SLOT_STATE_NOT_CONNECTED);
        buffer.put(MODEL_FULL_GYRO);
        buffer.put(CONNECTION_TYPE_BLUETOOTH);
        buffer.put(new byte[6]); // MAC Address (can be all zeroes)
        buffer.put(BATTERY_STATE_FULL);
        buffer.put((byte) 0); // Zero padding

        // Calculate and insert CRC32
        byte[] packetBytes = buffer.array();
        CRC32 crc32 = new CRC32();
        crc32.update(packetBytes);
        buffer.putInt(8, (int) crc32.getValue());

        return packetBytes;
    }

    /**
     * Builds the motion data packet containing the latest sensor readings.
     * @param accel The 3-axis accelerometer data.
     * @param gyro The 3-axis gyroscope data.
     * @return A byte array ready to be sent via UDP.
     */
    public static byte[] buildControllerDataPacket(float[] accel, float[] gyro) {
        ByteBuffer buffer = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN);

        // Header
        buffer.put(MAGIC_BYTES);
        buffer.putShort(PROTOCOL_VERSION);
        buffer.putShort((short) 84); // Payload Length
        buffer.putInt(0); // Placeholder for CRC32

        // Event
        buffer.putInt(0); // Server ID
        buffer.putInt(MSG_TYPE_CONTROLLER_DATA);

        // Payload Start
        buffer.put((byte) 0); // Slot 0
        buffer.put(SLOT_STATE_CONNECTED);
        buffer.put(MODEL_FULL_GYRO);
        buffer.put(CONNECTION_TYPE_BLUETOOTH);
        buffer.put(new byte[6]); // MAC Address
        buffer.put(BATTERY_STATE_FULL);
        buffer.put((byte) 1); // isConnected flag
        buffer.putInt(packetCounter++);

        // All buttons and sticks are zero since we only care about motion
        buffer.put(new byte[20]); // Buttons, PS, Touch, Sticks, DPAD values

        // Touch data (not used)
        buffer.put(new byte[12]);

        // Motion Timestamp
        buffer.putLong(System.nanoTime());


        buffer.putFloat(gyro[0]); // Pitch (Rotation around X-axis)
        buffer.putFloat(gyro[2]); // Yaw (Rotation around Z-axis)
        buffer.putFloat(gyro[1]); // Roll (Rotation around Y-axis)

        // Accelerometer data is standard X, Y, Z
        buffer.putFloat(accel[0]); // X
        buffer.putFloat(accel[1]); // Y
        buffer.putFloat(accel[2]); // Z

        // Calculate and insert CRC32
        byte[] packetBytes = buffer.array();
        CRC32 crc32 = new CRC32();

        buffer.putInt(8, 0); // Zero out CRC field for calculation
        crc32.update(packetBytes);
        buffer.putInt(8, (int) crc32.getValue());

        return buffer.array();
    }
}