package qupath.ext.controller.input;

import org.hid4java.HidDevice;
import qupath.ext.controller.mapping.ControllerMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Xbox360Driver implements HidControllerDriver {

    private static final int MICROSOFT_VENDOR_ID = 0x045e;
    private static final int XBOX_360_PRODUCT_ID = 0x028e;
    private static final float STICK_DEADZONE = 0.20f;

    @Override
    public boolean matches(HidDevice device) {
        if (device.getVendorId() != MICROSOFT_VENDOR_ID)
            return false;
        var product = device.getProduct();
        return device.getProductId() == XBOX_360_PRODUCT_ID
                || (product != null && product.toLowerCase().contains("xbox 360"));
    }

    @Override
    public boolean isPreferred(HidDevice device) {
        return device.getUsagePage() == 0x01 && device.getUsage() == 0x05;
    }

    @Override
    public void reset() {}

    private static final List<ControllerInput> INPUTS = List.of(
            // Face buttons
            new ControllerInput("xbox360.a", "A button", false),
            new ControllerInput("xbox360.b", "B button", false),
            new ControllerInput("xbox360.x", "X button", false),
            new ControllerInput("xbox360.y", "Y button", false),
            // Shoulder buttons and triggers
            new ControllerInput("xbox360.lb", "Left bumper (LB)", false),
            new ControllerInput("xbox360.rb", "Right bumper (RB)", false),
            new ControllerInput("xbox360.lt", "Left trigger (LT)", false),
            new ControllerInput("xbox360.rt", "Right trigger (RT)", false),
            new ControllerInput("xbox360.lt_axis", "LT trigger analog", true),
            new ControllerInput("xbox360.rt_axis", "RT trigger analog", true),
            // System buttons
            new ControllerInput("xbox360.start", "Start button", false),
            new ControllerInput("xbox360.back", "Back button", false),
            new ControllerInput("xbox360.guide", "Guide button", false),
            // Stick clicks
            new ControllerInput("xbox360.ls", "Left stick click (LS)", false),
            new ControllerInput("xbox360.rs", "Right stick click (RS)", false),
            // D-pad
            new ControllerInput("xbox360.dpad_up", "D-pad up", false),
            new ControllerInput("xbox360.dpad_down", "D-pad down", false),
            new ControllerInput("xbox360.dpad_left", "D-pad left", false),
            new ControllerInput("xbox360.dpad_right", "D-pad right", false),
            // Joysticks
            new ControllerInput("xbox360.left_x", "Left joystick horizontal", true),
            new ControllerInput("xbox360.left_y", "Left joystick vertical", true),
            new ControllerInput("xbox360.right_x", "Right joystick horizontal", true),
            new ControllerInput("xbox360.right_y", "Right joystick vertical", true));

    @Override
    public List<ControllerInput> inputs() {
        return INPUTS;
    }

    @Override
    public Map<String, Float> parseReport(byte[] report, int length) {
        // Xbox 360 USB report: byte 0 = 0x00, byte 1 = 0x14 (packet length = 20)
        if (length < 14)
            return Map.of();
        if ((report[0] & 0xff) != 0x00 || (report[1] & 0xff) != 0x14)
            return Map.of();

        var values = new LinkedHashMap<String, Float>();
        // This adapter stores the button word big-endian (high byte first), opposite
        // to the Xbox 360 USB HID spec. byte[2]=dpad/system, byte[3]=face/shoulder.
        var buttonsLo = report[3] & 0xff;
        var buttonsHi = report[2] & 0xff;

        values.put("xbox360.lb",         button(buttonsLo, 0));
        values.put("xbox360.rb",         button(buttonsLo, 1));
        values.put("xbox360.guide",      button(buttonsLo, 2));
        values.put("xbox360.a",          button(buttonsLo, 4));
        values.put("xbox360.b",          button(buttonsLo, 5));
        values.put("xbox360.x",          button(buttonsLo, 6));
        values.put("xbox360.y",          button(buttonsLo, 7));
        values.put("xbox360.dpad_up",    button(buttonsHi, 0));
        values.put("xbox360.dpad_down",  button(buttonsHi, 1));
        values.put("xbox360.dpad_left",  button(buttonsHi, 2));
        values.put("xbox360.dpad_right", button(buttonsHi, 3));
        values.put("xbox360.start",      button(buttonsHi, 4));
        values.put("xbox360.back",       button(buttonsHi, 5));
        values.put("xbox360.ls",         button(buttonsHi, 6));
        values.put("xbox360.rs",         button(buttonsHi, 7));

        var lt = (report[4] & 0xff) / 255f;
        var rt = (report[5] & 0xff) / 255f;
        values.put("xbox360.lt_axis", lt);
        values.put("xbox360.rt_axis", rt);
        values.put("xbox360.lt", lt >= 0.5f ? 1f : 0f);
        values.put("xbox360.rt", rt >= 0.5f ? 1f : 0f);

        addStick(values, "xbox360.left",  stick16(report, 6),  -stick16(report, 8));
        addStick(values, "xbox360.right", stick16(report, 10), -stick16(report, 12));

        return values;
    }

    @Override
    public ControllerMapping builtInMapping(String inputId) {
        return switch (inputId) {
            case "xbox360.left_x"  -> mapping(inputId, "Left joystick horizontal",  ControllerMapping.ActionType.MOUSE_MOVE_X, "");
            case "xbox360.left_y"  -> mapping(inputId, "Left joystick vertical",    ControllerMapping.ActionType.MOUSE_MOVE_Y, "");
            case "xbox360.right_x" -> mapping(inputId, "Right joystick horizontal", ControllerMapping.ActionType.QUPATH_PAN_X, "");
            case "xbox360.right_y" -> mapping(inputId, "Right joystick vertical",   ControllerMapping.ActionType.QUPATH_PAN_Y, "");
            case "xbox360.a"    -> mapping(inputId, "A button", ControllerMapping.ActionType.MOUSE_BUTTON, "Left");
            case "xbox360.b"    -> mapping(inputId, "B button", ControllerMapping.ActionType.QUPATH_UNDO, "");
            case "xbox360.x"    -> mapping(inputId, "X button", ControllerMapping.ActionType.MOUSE_BUTTON, "Right");
            case "xbox360.y"    -> mapping(inputId, "Y button", ControllerMapping.ActionType.MOUSE_SHIFT_RIGHT_CLICK, "");
            case "xbox360.ls"   -> mapping(inputId, "Left stick click (LS)",  ControllerMapping.ActionType.QUPATH_COMMAND, "View > Zoom > Zoom to fit");
            case "xbox360.rs"   -> mapping(inputId, "Right stick click (RS)", ControllerMapping.ActionType.CONTROLLER_TOGGLE_PAN_SPEED, "");
            case "xbox360.back" -> mapping(inputId, "Back button", ControllerMapping.ActionType.QUPATH_COMMAND, "File > Export snapshot... > Main window screenshot...");
            case "xbox360.start" -> mapping(inputId, "Start button", ControllerMapping.ActionType.QUPATH_COMMAND, "File > Save As...");
            case "xbox360.lb"   -> mapping(inputId, "Left bumper (LB)",   ControllerMapping.ActionType.QUPATH_TOOL_PREVIOUS, "");
            case "xbox360.rb"   -> mapping(inputId, "Right bumper (RB)",  ControllerMapping.ActionType.QUPATH_TOOL_NEXT, "");
            case "xbox360.lt"   -> mapping(inputId, "Left trigger (LT)",  ControllerMapping.ActionType.QUPATH_ZOOM_OUT, "");
            case "xbox360.rt"   -> mapping(inputId, "Right trigger (RT)", ControllerMapping.ActionType.QUPATH_ZOOM_IN, "");
            case "xbox360.lt_axis"    -> mapping(inputId, "LT trigger analog", ControllerMapping.ActionType.NONE, "");
            case "xbox360.rt_axis"    -> mapping(inputId, "RT trigger analog", ControllerMapping.ActionType.NONE, "");
            case "xbox360.dpad_up"    -> mapping(inputId, "D-pad up",    ControllerMapping.ActionType.QUPATH_SHOW_ANNOTATIONS, "");
            case "xbox360.dpad_down"  -> mapping(inputId, "D-pad down",  ControllerMapping.ActionType.QUPATH_FILL_ANNOTATIONS, "");
            case "xbox360.dpad_left"  -> mapping(inputId, "D-pad left",  ControllerMapping.ActionType.QUPATH_SHOW_DETECTIONS, "");
            case "xbox360.dpad_right" -> mapping(inputId, "D-pad right", ControllerMapping.ActionType.QUPATH_FILL_DETECTIONS, "");
            case "xbox360.guide"      -> mapping(inputId, "Guide button", ControllerMapping.ActionType.CONTROLLER_TOGGLE_INPUT, "");
            default -> null;
        };
    }

    @Override
    public boolean isTriggerRepeat(String inputId) {
        return "xbox360.lb".equals(inputId) || "xbox360.rb".equals(inputId);
    }

    @Override
    public boolean setLightbarColor(HidDevice device, int r, int g, int b) {
        return false;
    }

    @Override
    public boolean setMicMuteLed(HidDevice device, boolean muted) {
        return false;
    }

    @Override
    public boolean setRumble(HidDevice device, int left, int right) {
        var r = (byte) (right > 0 ? Math.max(128, Math.min(255, right * 6)) : 0);
        var report = new byte[]{0x00, 0x08, 0x00, (byte) left, r, 0x00, 0x00, 0x00};
        return device.write(report, report.length, (byte) 0x00) >= 0;
    }

    @Override
    public boolean setPlayerLeds(HidDevice device, int player) {
        return false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ControllerMapping mapping(String id, String name,
                                              ControllerMapping.ActionType type, String value) {
        return new ControllerMapping(id, name, type, value);
    }

    private static void addStick(Map<String, Float> values, String prefix, float x, float y) {
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

    private static float stick16(byte[] report, int offset) {
        var raw = (short) ((report[offset] & 0xff) | ((report[offset + 1] & 0xff) << 8));
        return raw / 32768f;
    }

    private static float button(int value, int bit) {
        return (value & (1 << bit)) != 0 ? 1f : 0f;
    }
}
