package qupath.ext.controller.input;

import org.hid4java.HidDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DualSenseLightbar {

    private static final Logger logger = LoggerFactory.getLogger(DualSenseLightbar.class);
    private static final byte USB_OUTPUT_REPORT_ID = 0x02;
    private static final int USB_OUTPUT_PAYLOAD_SIZE = 62;

    private static final int VALID_FLAG0 = 0;
    private static final int VALID_FLAG1 = 1;
    private static final int MOTOR_RIGHT = 2;
    private static final int MOTOR_LEFT = 3;
    private static final int MIC_MUTE_LED = 8;
    private static final int VALID_FLAG2 = 38;
    private static final int LIGHTBAR_SETUP = 41;
    private static final int LIGHTBAR_RED = 44;
    private static final int LIGHTBAR_GREEN = 45;
    private static final int LIGHTBAR_BLUE = 46;

    private static final int FLAG0_COMPATIBLE_VIBRATION = 1;
    private static final int FLAG0_HAPTICS_SELECT = 1 << 1;
    private static final int PLAYER_LEDS = 43;
    private static final int FLAG1_MIC_MUTE_LED_CONTROL_ENABLE = 1;
    private static final int FLAG1_LIGHTBAR_CONTROL_ENABLE = 1 << 2;
    private static final int FLAG1_PLAYER_LEDS_CONTROL_ENABLE = 1 << 4;
    private static final int[] PLAYER_LED_PATTERNS = {0x00, 0x04, 0x0A, 0x15, 0x1B};
    private static final int FLAG2_LIGHTBAR_SETUP_CONTROL_ENABLE = 1 << 1;
    private static final int LIGHTBAR_SETUP_LIGHT_OUT = 1 << 1;

    private DualSenseLightbar() {
    }

    public static boolean setUsbColor(HidDevice device, int red, int green, int blue) {
        if (device == null || !device.isOpen())
            return false;

        var setup = new byte[USB_OUTPUT_PAYLOAD_SIZE];
        setup[VALID_FLAG2] = (byte)FLAG2_LIGHTBAR_SETUP_CONTROL_ENABLE;
        setup[LIGHTBAR_SETUP] = (byte)LIGHTBAR_SETUP_LIGHT_OUT;
        write(device, setup);

        var report = new byte[USB_OUTPUT_PAYLOAD_SIZE];
        report[VALID_FLAG1] = (byte)FLAG1_LIGHTBAR_CONTROL_ENABLE;
        report[LIGHTBAR_RED] = (byte)clamp(red);
        report[LIGHTBAR_GREEN] = (byte)clamp(green);
        report[LIGHTBAR_BLUE] = (byte)clamp(blue);
        var result = write(device, report);
        logger.debug("DualSense lightbar set to rgb({}, {}, {}), write returned {}", red, green, blue, result);
        return result >= 0;
    }

    public static boolean setMicMuteLed(HidDevice device, boolean muted) {
        if (device == null || !device.isOpen())
            return false;

        var report = new byte[USB_OUTPUT_PAYLOAD_SIZE];
        report[VALID_FLAG1] = (byte)FLAG1_MIC_MUTE_LED_CONTROL_ENABLE;
        report[MIC_MUTE_LED] = (byte)(muted ? 1 : 0);
        var result = write(device, report);
        logger.info("DualSense mute LED set to {}, write returned {}", muted, result);
        return result >= 0;
    }

    public static boolean setPlayerLeds(HidDevice device, int player) {
        if (device == null || !device.isOpen() || player < 0 || player > 4)
            return false;
        var report = new byte[USB_OUTPUT_PAYLOAD_SIZE];
        report[VALID_FLAG1] = (byte) FLAG1_PLAYER_LEDS_CONTROL_ENABLE;
        report[PLAYER_LEDS] = (byte) PLAYER_LED_PATTERNS[player];
        return write(device, report) >= 0;
    }

    public static boolean setRumble(HidDevice device, int left, int right) {
        if (device == null || !device.isOpen())
            return false;

        var report = new byte[USB_OUTPUT_PAYLOAD_SIZE];
        report[VALID_FLAG0] = (byte)(FLAG0_HAPTICS_SELECT | FLAG0_COMPATIBLE_VIBRATION);
        report[MOTOR_LEFT] = (byte)clamp(left);
        report[MOTOR_RIGHT] = (byte)clamp(right);
        var result = write(device, report);
        logger.debug("DualSense rumble set to left {}, right {}, write returned {}", left, right, result);
        return result >= 0;
    }

    private static int write(HidDevice device, byte[] payload) {
        var result = device.write(payload, payload.length, USB_OUTPUT_REPORT_ID);
        if (result < 0)
            logger.warn("DualSense lightbar write failed: {}", device.getLastErrorMessage());
        return result;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
