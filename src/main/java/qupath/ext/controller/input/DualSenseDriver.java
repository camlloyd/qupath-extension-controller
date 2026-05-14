package qupath.ext.controller.input;

import org.hid4java.HidDevice;
import qupath.ext.controller.mapping.ControllerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DualSenseDriver implements HidControllerDriver {

    private static final int SONY_VENDOR_ID = 0x054c;
    private static final int DUALSENSE_PRODUCT_ID = 0x0ce6;
    private static final int DUALSENSE_EDGE_PRODUCT_ID = 0x0df2;
    private static final float STICK_DEADZONE = 0.12f;
    private static final int TOUCHPAD_SWIPE_DEADZONE = 20;
    private static final double TOUCHPAD_SWIPE_SCALE = 45.0;

    private boolean touchpadActive;
    private int lastTouchpadX;
    private int lastTouchpadY;

    @Override
    public boolean matches(HidDevice device) {
        if (device.getVendorId() != SONY_VENDOR_ID)
            return false;
        return device.getProductId() == DUALSENSE_PRODUCT_ID
                || device.getProductId() == DUALSENSE_EDGE_PRODUCT_ID
                || containsIgnoreCase(device.getProduct(), "dualsense");
    }

    @Override
    public boolean isPreferred(HidDevice device) {
        return device.getUsagePage() == 0x01 && device.getUsage() == 0x05;
    }

    @Override
    public void reset() {
        touchpadActive = false;
        lastTouchpadX = 0;
        lastTouchpadY = 0;
    }

    private static final List<ControllerInput> INPUTS = List.of(
                // Face buttons
                new ControllerInput("dualsense.cross", "Cross button", false),
                new ControllerInput("dualsense.circle", "Circle button", false),
                new ControllerInput("dualsense.square", "Square button", false),
                new ControllerInput("dualsense.triangle", "Triangle button", false),
                // Shoulder buttons and triggers
                new ControllerInput("dualsense.l1", "Left bumper (L1)", false),
                new ControllerInput("dualsense.r1", "Right bumper (R1)", false),
                new ControllerInput("dualsense.l2", "Left trigger (L2)", false),
                new ControllerInput("dualsense.r2", "Right trigger (R2)", false),
                new ControllerInput("dualsense.l2_axis", "L2 trigger analog", true),
                new ControllerInput("dualsense.r2_axis", "R2 trigger analog", true),
                // System buttons
                new ControllerInput("dualsense.create", "Create button", false),
                // Touchpad
                new ControllerInput("dualsense.touchpad", "Touch pad press", false),
                new ControllerInput("dualsense.touchpad_left", "Touch pad left press", false),
                new ControllerInput("dualsense.touchpad_middle", "Touch pad middle press", false),
                new ControllerInput("dualsense.touchpad_bottom", "Touch pad bottom press", false),
                new ControllerInput("dualsense.touchpad_right", "Touch pad right press", false),
                // Stick clicks
                new ControllerInput("dualsense.l3", "Left stick click (L3)", false),
                new ControllerInput("dualsense.r3", "Right stick click (R3)", false),
                // System buttons (continued)
                new ControllerInput("dualsense.mic_mute", "Mute button", false),
                new ControllerInput("dualsense.options", "Options button", false),
                new ControllerInput("dualsense.ps", "PS button", false),
                // D-pad
                new ControllerInput("dualsense.dpad_up", "D-pad up", false),
                new ControllerInput("dualsense.dpad_down", "D-pad down", false),
                new ControllerInput("dualsense.dpad_left", "D-pad left", false),
                new ControllerInput("dualsense.dpad_right", "D-pad right", false),
                // Joysticks
                new ControllerInput("dualsense.left_x", "Left joystick horizontal", true),
                new ControllerInput("dualsense.left_y", "Left joystick vertical", true),
                new ControllerInput("dualsense.right_x", "Right joystick horizontal", true),
                new ControllerInput("dualsense.right_y", "Right joystick vertical", true),
                // Touchpad swipe
                new ControllerInput("dualsense.touchpad_swipe_x", "Touch pad swipe horizontal", true),
                new ControllerInput("dualsense.touchpad_swipe_y", "Touch pad swipe vertical", true));

    @Override
    public List<ControllerInput> inputs() {
        return INPUTS;
    }

    @Override
    public Map<String, Float> parseReport(byte[] report, int length) {
        var reportId = report[0] & 0xff;
        if (reportId != 0x01 && reportId != 0x31)
            return Map.of();

        var offset = reportId == 0x31 ? 1 : 0;
        if (length <= offset + 10)
            return Map.of();

        var values = new LinkedHashMap<String, Float>();
        addStick(values, "dualsense.left", axis(report[offset + 1]), axis(report[offset + 2]));
        addStick(values, "dualsense.right", axis(report[offset + 3]), axis(report[offset + 4]));
        values.put("dualsense.l2_axis", trigger(report[offset + 5]));
        values.put("dualsense.r2_axis", trigger(report[offset + 6]));

        var buttons0 = report[offset + 8] & 0xff;
        var buttons1 = report[offset + 9] & 0xff;
        var buttons2 = report[offset + 10] & 0xff;
        var dpad = buttons0 & 0x0f;

        values.put("dualsense.dpad_up",    dpadButton(dpad, 0));
        values.put("dualsense.dpad_right", dpadButton(dpad, 2));
        values.put("dualsense.dpad_down",  dpadButton(dpad, 4));
        values.put("dualsense.dpad_left",  dpadButton(dpad, 6));
        values.put("dualsense.square",   button(buttons0, 4));
        values.put("dualsense.cross",    button(buttons0, 5));
        values.put("dualsense.circle",   button(buttons0, 6));
        values.put("dualsense.triangle", button(buttons0, 7));
        values.put("dualsense.l1",       button(buttons1, 0));
        values.put("dualsense.r1",       button(buttons1, 1));
        values.put("dualsense.l2",       button(buttons1, 2));
        values.put("dualsense.r2",       button(buttons1, 3));
        values.put("dualsense.create",   button(buttons1, 4));
        values.put("dualsense.options",  button(buttons1, 5));
        values.put("dualsense.l3",       button(buttons1, 6));
        values.put("dualsense.r3",       button(buttons1, 7));
        values.put("dualsense.ps",       button(buttons2, 0));
        values.put("dualsense.touchpad", button(buttons2, 1));
        values.put("dualsense.mic_mute", button(buttons2, 2));

        var touchpadPressed = button(buttons2, 1) >= 0.5f;
        addTouchpadSwipe(values, report, offset, length, touchpadPressed);
        values.put("dualsense.touchpad_left",   touchpadZone(report, offset, length, touchpadPressed, Zone.LEFT));
        values.put("dualsense.touchpad_middle", touchpadZone(report, offset, length, touchpadPressed, Zone.MIDDLE));
        values.put("dualsense.touchpad_bottom", touchpadZone(report, offset, length, touchpadPressed, Zone.BOTTOM));
        values.put("dualsense.touchpad_right",  touchpadZone(report, offset, length, touchpadPressed, Zone.RIGHT));
        return values;
    }

    @Override
    public ControllerMapping builtInMapping(String inputId) {
        return switch (inputId) {
            case "dualsense.left_x"  -> mapping(inputId, "Left joystick horizontal",  ControllerMapping.ActionType.MOUSE_MOVE_X, "");
            case "dualsense.left_y"  -> mapping(inputId, "Left joystick vertical",    ControllerMapping.ActionType.MOUSE_MOVE_Y, "");
            case "dualsense.right_x" -> mapping(inputId, "Right joystick horizontal", ControllerMapping.ActionType.QUPATH_PAN_X, "");
            case "dualsense.right_y" -> mapping(inputId, "Right joystick vertical",   ControllerMapping.ActionType.QUPATH_PAN_Y, "");
            case "dualsense.touchpad_swipe_x" -> mapping(inputId, "Touch pad swipe horizontal", ControllerMapping.ActionType.QUPATH_PAN_X, "");
            case "dualsense.touchpad_swipe_y" -> mapping(inputId, "Touch pad swipe vertical",   ControllerMapping.ActionType.QUPATH_PAN_Y, "");
            case "dualsense.cross"   -> mapping(inputId, "Cross button", ControllerMapping.ActionType.MOUSE_BUTTON, "Left");
            case "dualsense.circle"  -> mapping(inputId, "Circle button",    ControllerMapping.ActionType.QUPATH_UNDO, "");
            case "dualsense.square"  -> mapping(inputId, "Square button",    ControllerMapping.ActionType.MOUSE_BUTTON, "Right");
            case "dualsense.triangle" -> mapping(inputId, "Triangle button", ControllerMapping.ActionType.MOUSE_SHIFT_RIGHT_CLICK, "");
            case "dualsense.l3"      -> mapping(inputId, "Left stick click (L3)",  ControllerMapping.ActionType.QUPATH_COMMAND, "View > Zoom > Zoom to fit");
            case "dualsense.r3"      -> mapping(inputId, "Right stick click (R3)", ControllerMapping.ActionType.CONTROLLER_TOGGLE_PAN_SPEED, "");
            case "dualsense.create"  -> mapping(inputId, "Create button",          ControllerMapping.ActionType.QUPATH_COMMAND, "File > Export snapshot... > Main window screenshot...");
            case "dualsense.options" -> mapping(inputId, "Options button",         ControllerMapping.ActionType.QUPATH_COMMAND, "File > Save As...");
            case "dualsense.l2"      -> mapping(inputId, "Left trigger (L2)",      ControllerMapping.ActionType.QUPATH_TOOL_PREVIOUS, "");
            case "dualsense.r2"      -> mapping(inputId, "Right trigger (R2)",     ControllerMapping.ActionType.QUPATH_TOOL_NEXT, "");
            case "dualsense.mic_mute"       -> mapping(inputId, "Mute button",           ControllerMapping.ActionType.CONTROLLER_TOGGLE_INPUT, "");
            case "dualsense.touchpad_left"  -> mapping(inputId, "Touch pad left press", ControllerMapping.ActionType.QUPATH_TOGGLE_SIDEBAR, "");
            case "dualsense.touchpad_right" -> mapping(inputId, "Touch pad right press",ControllerMapping.ActionType.QUPATH_SLIDE_OVERVIEW, "");
            case "dualsense.dpad_up"    -> mapping(inputId, "D-pad up",    ControllerMapping.ActionType.KEY, "UP");
            case "dualsense.dpad_down"  -> mapping(inputId, "D-pad down",  ControllerMapping.ActionType.KEY, "DOWN");
            case "dualsense.dpad_left"  -> mapping(inputId, "D-pad left",  ControllerMapping.ActionType.KEY, "LEFT");
            case "dualsense.dpad_right" -> mapping(inputId, "D-pad right", ControllerMapping.ActionType.KEY, "RIGHT");
            default -> null;
        };
    }

    @Override
    public boolean isTriggerRepeat(String inputId) {
        return "dualsense.l2".equals(inputId) || "dualsense.r2".equals(inputId);
    }

    @Override
    public boolean isTouchpadSwipe(String inputId) {
        return "dualsense.touchpad_swipe_x".equals(inputId)
                || "dualsense.touchpad_swipe_y".equals(inputId);
    }

    @Override
    public boolean setLightbarColor(HidDevice device, int r, int g, int b) {
        return DualSenseLightbar.setUsbColor(device, r, g, b);
    }

    @Override
    public boolean setMicMuteLed(HidDevice device, boolean muted) {
        return DualSenseLightbar.setMicMuteLed(device, muted);
    }

    @Override
    public boolean setRumble(HidDevice device, int left, int right) {
        return DualSenseLightbar.setRumble(device, left, right);
    }

    @Override
    public boolean setPlayerLeds(HidDevice device, int player) {
        return DualSenseLightbar.setPlayerLeds(device, player);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ControllerMapping mapping(String id, String name,
                                              ControllerMapping.ActionType type, String value) {
        return new ControllerMapping(id, name, type, value);
    }

    private void addTouchpadSwipe(Map<String, Float> values, byte[] report,
                                   int offset, int length, boolean pressed) {
        if (pressed) { resetSwipe(values); return; }
        var pointOffset = offset + 33;
        if (length <= pointOffset + 3) { resetSwipe(values); return; }
        var contact = report[pointOffset] & 0xff;
        if ((contact & 0x80) != 0) { resetSwipe(values); return; }

        var x = (report[pointOffset + 1] & 0xff) | ((report[pointOffset + 2] & 0x0f) << 8);
        var y = ((report[pointOffset + 2] & 0xf0) >> 4) | ((report[pointOffset + 3] & 0xff) << 4);
        if (!touchpadActive) {
            touchpadActive = true;
            lastTouchpadX = x;
            lastTouchpadY = y;
            values.put("dualsense.touchpad_swipe_x", 0f);
            values.put("dualsense.touchpad_swipe_y", 0f);
            return;
        }
        values.put("dualsense.touchpad_swipe_x", scaledDelta(x - lastTouchpadX));
        values.put("dualsense.touchpad_swipe_y", scaledDelta(y - lastTouchpadY));
        lastTouchpadX = x;
        lastTouchpadY = y;
    }

    private void resetSwipe(Map<String, Float> values) {
        touchpadActive = false;
        values.put("dualsense.touchpad_swipe_x", 0f);
        values.put("dualsense.touchpad_swipe_y", 0f);
    }

    private float scaledDelta(int delta) {
        if (Math.abs(delta) < TOUCHPAD_SWIPE_DEADZONE) return 0f;
        return (float) Math.max(-1.0, Math.min(1.0, delta / TOUCHPAD_SWIPE_SCALE));
    }

    private float touchpadZone(byte[] report, int offset, int length,
                                boolean pressed, Zone zone) {
        if (!pressed) return 0f;
        var pointOffset = offset + 33;
        if (length <= pointOffset + 3) return 0f;
        if ((report[pointOffset] & 0x80) != 0) return 0f;
        var x = (report[pointOffset + 1] & 0xff) | ((report[pointOffset + 2] & 0x0f) << 8);
        var y = ((report[pointOffset + 2] & 0xf0) >> 4) | ((report[pointOffset + 3] & 0xff) << 4);
        return switch (zone) {
            case LEFT   -> x < 640 ? 1f : 0f;
            case MIDDLE -> x >= 640 && x < 1280 && y < 471 ? 1f : 0f;
            case BOTTOM -> x >= 640 && x < 1280 && y >= 471 ? 1f : 0f;
            case RIGHT  -> x >= 1280 ? 1f : 0f;
        };
    }

    private enum Zone { LEFT, MIDDLE, BOTTOM, RIGHT }

    private void addStick(Map<String, Float> values, String prefix, float x, float y) {
        var magnitude = Math.sqrt(x * x + y * y);
        if (magnitude < STICK_DEADZONE) {
            values.put(prefix + "_x", 0f);
            values.put(prefix + "_y", 0f);
            return;
        }
        var scaled = Math.min(1.0, (magnitude - STICK_DEADZONE) / (1.0 - STICK_DEADZONE));
        var scale = scaled / magnitude;
        values.put(prefix + "_x", (float) (x * scale));
        values.put(prefix + "_y", (float) (y * scale));
    }

    private float axis(byte value)    { return ((value & 0xff) - 128) / 127f; }
    private float trigger(byte value) { return (value & 0xff) / 255f; }
    private float button(int value, int bit) { return (value & (1 << bit)) != 0 ? 1f : 0f; }

    /** dpad value 0=N, 1=NE, 2=E … 7=NW, 8=neutral. centerDir is the primary direction index. */
    private float dpadButton(int dpad, int centerDir) {
        if (dpad == 8) return 0f;
        var diff = Math.floorMod(dpad - centerDir, 8);
        return diff <= 1 || diff == 7 ? 1f : 0f;
    }

    private boolean containsIgnoreCase(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }
}
