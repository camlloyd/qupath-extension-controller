package qupath.ext.controller.input;

import org.hid4java.HidDevice;
import qupath.ext.controller.mapping.ControllerMapping;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates everything specific to one controller model.
 * Implement this interface to add support for a new device.
 */
public interface HidControllerDriver {

    /** Return true if this driver handles the given HID device. */
    boolean matches(HidDevice device);

    /**
     * When multiple matching devices are found, prefer the one for which
     * this returns true (e.g. USB over Bluetooth for the same controller).
     */
    boolean isPreferred(HidDevice device);

    /** Reset any per-connection state (called on device connect/disconnect). */
    void reset();

    /** The full list of logical inputs this controller exposes. */
    List<ControllerInput> inputs();

    /**
     * Parse a raw HID report into a map of input ID → axis/button value.
     * Returns an empty map if the report is unrecognised or too short.
     */
    Map<String, Float> parseReport(byte[] report, int length);

    /**
     * Runtime fallback mapping used when the user has not configured an input.
     * Returns null if there is no built-in default for this input.
     */
    ControllerMapping builtInMapping(String inputId);

    /**
     * Return true if this input ID should use trigger-repeat behaviour
     * (fires once immediately, then repeats on hold after a delay).
     */
    default boolean isTriggerRepeat(String inputId) { return false; }

    /**
     * Return true if this input ID is a touch pad swipe axis, so the
     * poller can route it through the touch pad pan path instead of
     * the stick pan path.
     */
    default boolean isTouchpadSwipe(String inputId) { return false; }

    boolean setLightbarColor(HidDevice device, int r, int g, int b);
    boolean setMicMuteLed(HidDevice device, boolean muted);
    boolean setRumble(HidDevice device, int left, int right);
    boolean setPlayerLeds(HidDevice device, int player);
}
