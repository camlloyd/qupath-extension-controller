package qupath.ext.controller.input;

import org.hid4java.HidDevice;
import qupath.ext.controller.mapping.ControllerMapping;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public class SpaceMouseDriver implements HidControllerDriver {

    // 3Dconnexion vendor IDs
    private static final int VID_3DX_LEGACY   = 0x046d; // older USB devices (Logitech era)
    private static final int VID_3DX_RECEIVER = 0x256f; // Universal Receiver and modern devices

    private static final int USAGE_PAGE_GENERIC = 0x01;
    private static final int USAGE_MULTI_AXIS   = 0x08;

    private static final int REPORT_MOTION  = 0x01;
    private static final int REPORT_BUTTONS = 0x03;

    // Practical axis maximum observed from device; clamp to ±1 beyond this.
    private static final float AXIS_MAX  = 350f;
    // Dead-zone in raw units (eliminates noise at rest).
    private static final float AXIS_DEAD = 15f;

    private static final int BUTTON_COUNT = 32;

    // ── Input declaration ─────────────────────────────────────────────────────

    private static final List<ControllerInput> INPUTS;

    static {
        var inputs = new ArrayList<ControllerInput>();
        inputs.add(new ControllerInput("spacemouse.tx",    "Translate X (left/right)",    true));
        inputs.add(new ControllerInput("spacemouse.ty",    "Translate Y (forward/back)",  true));
        inputs.add(new ControllerInput("spacemouse.tz",    "Translate Z (push/pull)",     true));
        inputs.add(new ControllerInput("spacemouse.pitch", "Rotate pitch (tilt fwd/bck)", true));
        inputs.add(new ControllerInput("spacemouse.roll",  "Rotate roll (tilt left/rgt)", true));
        inputs.add(new ControllerInput("spacemouse.yaw",   "Rotate yaw (twist)",          true));
        for (int i = 1; i <= BUTTON_COUNT; i++)
            inputs.add(new ControllerInput("spacemouse.btn" + i, "Button " + i, false));
        INPUTS = List.copyOf(inputs);
    }

    // Motion and button data arrive in separate reports; merge into one map per parse.
    private final float[] lastMotion  = new float[6]; // tx, ty, tz, pitch, roll, yaw
    private int           lastButtons = 0;

    // ── HidControllerDriver ───────────────────────────────────────────────────

    @Override
    public boolean matches(HidDevice device) {
        var vid = device.getVendorId();
        if (vid != VID_3DX_LEGACY && vid != VID_3DX_RECEIVER)
            return false;
        return device.getUsagePage() == USAGE_PAGE_GENERIC
                && device.getUsage() == USAGE_MULTI_AXIS;
    }

    @Override
    public boolean isPreferred(HidDevice device) {
        return device.getUsagePage() == USAGE_PAGE_GENERIC
                && device.getUsage() == USAGE_MULTI_AXIS;
    }

    @Override
    public void reset() {
        Arrays.fill(lastMotion, 0f);
        lastButtons = 0;
    }

    @Override
    public List<ControllerInput> inputs() { return INPUTS; }

    @Override
    public int minReportLength() { return 5; }

    @Override
    public Map<String, Float> parseReport(byte[] report, int length) {
        if (length < 1) return Map.of();
        var reportId = report[0] & 0xff;

        switch (reportId) {
            case REPORT_MOTION -> {
                if (length < 13) return Map.of();
                var buf = ByteBuffer.wrap(report, 1, 12).order(ByteOrder.LITTLE_ENDIAN);
                lastMotion[0] = -axis(buf.getShort()); // tx — negated to match QuPath screen coords
                lastMotion[1] =  axis(buf.getShort()); // ty
                lastMotion[2] =  axis(buf.getShort()); // tz
                lastMotion[3] =  axis(buf.getShort()); // pitch
                lastMotion[4] =  axis(buf.getShort()); // roll
                lastMotion[5] =  axis(buf.getShort()); // yaw
            }
            case REPORT_BUTTONS -> {
                var available = Math.min(4, length - 1);
                int bitmask = 0;
                for (int i = 0; i < available; i++)
                    bitmask |= (report[1 + i] & 0xff) << (i * 8);
                lastButtons = bitmask;
            }
            default -> { return Map.of(); }
        }

        var values = new LinkedHashMap<String, Float>();
        values.put("spacemouse.tx",    lastMotion[0]);
        values.put("spacemouse.ty",    lastMotion[1]);
        values.put("spacemouse.tz",    lastMotion[2]);
        values.put("spacemouse.pitch", lastMotion[3]);
        values.put("spacemouse.roll",  lastMotion[4]);
        values.put("spacemouse.yaw",   lastMotion[5]);
        for (int i = 0; i < BUTTON_COUNT; i++)
            values.put("spacemouse.btn" + (i + 1), (lastButtons & (1 << i)) != 0 ? 1f : 0f);
        return values;
    }

    @Override
    public ControllerMapping builtInMapping(String inputId) {
        return switch (inputId) {
            case "spacemouse.tx" -> mapping(inputId, "Translate X (left/right)",   ControllerMapping.ActionType.QUPATH_PAN_X, "");
            case "spacemouse.ty" -> mapping(inputId, "Translate Y (forward/back)", ControllerMapping.ActionType.QUPATH_PAN_Y, "");
            default -> null;
        };
    }

    @Override
    public boolean setLightbarColor(HidDevice device, int r, int g, int b) { return false; }

    @Override
    public boolean setMicMuteLed(HidDevice device, boolean muted) { return false; }

    @Override
    public boolean setRumble(HidDevice device, int left, int right) { return false; }

    @Override
    public boolean setPlayerLeds(HidDevice device, int player) { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float axis(short raw) {
        if (Math.abs(raw) < AXIS_DEAD) return 0f;
        var sign      = raw < 0 ? -1f : 1f;
        var magnitude = (Math.abs((float) raw) - AXIS_DEAD) / (AXIS_MAX - AXIS_DEAD);
        return sign * (float) Math.min(1.0, magnitude);
    }

    private static ControllerMapping mapping(String id, String name,
                                              ControllerMapping.ActionType type, String value) {
        return new ControllerMapping(id, name, type, value);
    }
}
