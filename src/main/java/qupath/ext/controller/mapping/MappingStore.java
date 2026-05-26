package qupath.ext.controller.mapping;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.DoubleProperty;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class MappingStore {

    private static final String SEPARATOR = "\\|";
    private static final String PREF_KEY = "gamepad.mapping.v1";

    public static final double DEFAULT_POINTER_SENSITIVITY = 30.0;
    public static final double DEFAULT_PAN_SENSITIVITY = 8.0;
    public static final double DEFAULT_TOUCHPAD_SENSITIVITY = 8.0;
    public static final double DEFAULT_ZOOM_SENSITIVITY = 18.0;

    private static final javafx.beans.property.StringProperty serializedMappings =
            PathPrefs.createPersistentPreference(PREF_KEY, defaultMappings());
    private static final DoubleProperty pointerSensitivity =
            PathPrefs.createPersistentPreference("gamepad.pointerSensitivity", DEFAULT_POINTER_SENSITIVITY);
    private static final DoubleProperty panSensitivity =
            PathPrefs.createPersistentPreference("gamepad.panSensitivity", DEFAULT_PAN_SENSITIVITY);
    private static final DoubleProperty touchpadSensitivity =
            PathPrefs.createPersistentPreference("gamepad.touchpadSensitivity", DEFAULT_TOUCHPAD_SENSITIVITY);
    private static final DoubleProperty zoomSensitivity =
            PathPrefs.createPersistentPreference("gamepad.zoomSensitivity", DEFAULT_ZOOM_SENSITIVITY);
    private final Map<String, ControllerMapping> mappings = new LinkedHashMap<>();
    private final ObservableList<ControllerMapping> observableMappings = FXCollections.observableArrayList();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public MappingStore() {
        load();
    }

    public ControllerMapping getMapping(String inputId) {
        rwLock.readLock().lock();
        try {
            return mappings.get(inputId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public ObservableList<ControllerMapping> mappings() {
        return observableMappings;
    }

    public DoubleProperty pointerSensitivityProperty() {
        return pointerSensitivity;
    }

    public DoubleProperty panSensitivityProperty() {
        return panSensitivity;
    }

    public DoubleProperty touchpadSensitivityProperty() {
        return touchpadSensitivity;
    }

    public DoubleProperty zoomSensitivityProperty() {
        return zoomSensitivity;
    }

    public void putMapping(ControllerMapping mapping) {
        rwLock.writeLock().lock();
        try {
            mappings.put(mapping.inputId(), mapping);
            notifyObservers();
            save();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void ensureInput(String inputId, String inputName) {
        rwLock.writeLock().lock();
        try {
            if (!mappings.containsKey(inputId)) {
                mappings.put(inputId, new ControllerMapping(inputId, inputName, ControllerMapping.ActionType.NONE, ""));
                notifyObservers();
                save();
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void resetDefaults() {
        rwLock.writeLock().lock();
        try {
            serializedMappings.set(defaultMappings());
            load();
            Platform.runLater(() -> {
                pointerSensitivity.set(DEFAULT_POINTER_SENSITIVITY);
                panSensitivity.set(DEFAULT_PAN_SENSITIVITY);
                touchpadSensitivity.set(DEFAULT_TOUCHPAD_SENSITIVITY);
                zoomSensitivity.set(DEFAULT_ZOOM_SENSITIVITY);
            });
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public ControllerProfile toProfile(String name) {
        rwLock.readLock().lock();
        try {
            var profileMappings = mappings.values().stream()
                    .map(ControllerProfile.ProfileMapping::from)
                    .toList();
            return new ControllerProfile(name,
                    pointerSensitivity.get(), panSensitivity.get(), zoomSensitivity.get(),
                    touchpadSensitivity.get(),
                    profileMappings);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void applyProfile(ControllerProfile profile) {
        applyProfileMappings(profile);
        Platform.runLater(() -> {
            pointerSensitivity.set(profile.pointerSensitivity());
            panSensitivity.set(profile.panSensitivity());
            zoomSensitivity.set(profile.zoomSensitivity());
            touchpadSensitivity.set(profile.touchpadSensitivity() != null ? profile.touchpadSensitivity() : 8.0);
        });
    }

    public void applyProfileMappings(ControllerProfile profile) {
        rwLock.writeLock().lock();
        try {
            mappings.clear();
            for (var pm : profile.mappings())
                mappings.put(pm.inputId(), pm.toControllerMapping());
            notifyObservers();
            save();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void notifyObservers() {
        var snapshot = java.util.List.copyOf(mappings.values());
        Platform.runLater(() -> observableMappings.setAll(snapshot));
    }

    private void load() {
        // Must be called while holding the write lock (or during construction before threads start).
        mappings.clear();
        var lines = serializedMappings.get().split("\\R");
        for (var line : lines) {
            if (line.isBlank())
                continue;
            var parts = line.split(SEPARATOR, -1);
            if (parts.length < 4)
                continue;
            try {
                var mapping = new ControllerMapping(
                        unescape(parts[0]),
                        unescape(parts[1]),
                        ControllerMapping.ActionType.valueOf(parts[2]),
                        unescape(parts[3]));
                mapping = migrateDefaultMapping(mapping);
                mappings.put(mapping.inputId(), mapping);
            } catch (IllegalArgumentException ignored) {
                // Skip stale mappings from earlier versions.
            }
        }
        addDefaultMappingsIfMissing();
        var snapshot = java.util.List.copyOf(mappings.values());
        Platform.runLater(() -> observableMappings.setAll(snapshot));
        save();
    }

    private void save() {
        serializedMappings.set(mappings.values().stream()
                .map(mapping -> String.join("|",
                        escape(mapping.inputId()),
                        escape(mapping.inputName()),
                        mapping.actionType().name(),
                        escape(mapping.actionValue())))
                .collect(Collectors.joining("\n")));
    }

    private static String defaultMappings() {
        return String.join("\n",
                "dualsense.cross|Cross button|MOUSE_BUTTON|Left",
                "dualsense.circle|Circle button|QUPATH_UNDO|",
                "dualsense.square|Square button|MOUSE_BUTTON|Right",
                "dualsense.triangle|Triangle button|MOUSE_SHIFT_RIGHT_CLICK|",
                "dualsense.l1|Left bumper (L1)|QUPATH_ZOOM_OUT|",
                "dualsense.r1|Right bumper (R1)|QUPATH_ZOOM_IN|",
                "dualsense.l2|Left trigger (L2)|QUPATH_TOOL_PREVIOUS|",
                "dualsense.r2|Right trigger (R2)|QUPATH_TOOL_NEXT|",
                "dualsense.l2_axis|L2 trigger analog|NONE|",
                "dualsense.r2_axis|R2 trigger analog|NONE|",
                "dualsense.create|Create button|QUPATH_COMMAND|File > Export snapshot... > Main window screenshot...",
                "dualsense.touchpad|Touch pad press|NONE|",
                "dualsense.touchpad_left|Touch pad left press|QUPATH_TOGGLE_SIDEBAR|",
                "dualsense.touchpad_middle|Touch pad middle press|NONE|",
                "dualsense.touchpad_bottom|Touch pad bottom press|QUPATH_COMMAND|Measure > Show detection measurements",
                "dualsense.touchpad_right|Touch pad right press|QUPATH_SLIDE_OVERVIEW|",
                "dualsense.l3|Left stick click (L3)|QUPATH_COMMAND|View > Zoom > Zoom to fit",
                "dualsense.r3|Right stick click (R3)|CONTROLLER_TOGGLE_PAN_SPEED|",
                "dualsense.mic_mute|Mute button|CONTROLLER_TOGGLE_INPUT|",
                "dualsense.options|Options button|QUPATH_COMMAND|File > Save As...",
                "dualsense.ps|PS button|NONE|",
                "dualsense.dpad_up|D-pad up|QUPATH_SHOW_ANNOTATIONS|",
                "dualsense.dpad_down|D-pad down|QUPATH_FILL_ANNOTATIONS|",
                "dualsense.dpad_left|D-pad left|QUPATH_SHOW_DETECTIONS|",
                "dualsense.dpad_right|D-pad right|QUPATH_FILL_DETECTIONS|",
                "dualsense.left_x|Left joystick horizontal|MOUSE_MOVE_X|",
                "dualsense.left_y|Left joystick vertical|MOUSE_MOVE_Y|",
                "dualsense.right_x|Right joystick horizontal|QUPATH_PAN_X|",
                "dualsense.right_y|Right joystick vertical|QUPATH_PAN_Y|",
                "dualsense.touchpad_swipe_x|Touch pad swipe horizontal|QUPATH_PAN_X|",
                "dualsense.touchpad_swipe_y|Touch pad swipe vertical|QUPATH_PAN_Y|",
                "xbox360.a|A button|MOUSE_BUTTON|Left",
                "xbox360.b|B button|QUPATH_UNDO|",
                "xbox360.x|X button|MOUSE_BUTTON|Right",
                "xbox360.y|Y button|MOUSE_SHIFT_RIGHT_CLICK|",
                "xbox360.lb|Left bumper (LB)|QUPATH_TOOL_PREVIOUS|",
                "xbox360.rb|Right bumper (RB)|QUPATH_TOOL_NEXT|",
                "xbox360.lt|Left trigger (LT)|QUPATH_ZOOM_OUT|",
                "xbox360.rt|Right trigger (RT)|QUPATH_ZOOM_IN|",
                "xbox360.lt_axis|LT trigger analog|NONE|",
                "xbox360.rt_axis|RT trigger analog|NONE|",
                "xbox360.back|Back button|QUPATH_COMMAND|File > Export snapshot... > Main window screenshot...",
                "xbox360.start|Start button|QUPATH_COMMAND|File > Save As...",
                "xbox360.guide|Guide button|CONTROLLER_TOGGLE_INPUT|",
                "xbox360.ls|Left stick click (LS)|QUPATH_COMMAND|View > Zoom > Zoom to fit",
                "xbox360.rs|Right stick click (RS)|CONTROLLER_TOGGLE_PAN_SPEED|",
                "xbox360.dpad_up|D-pad up|QUPATH_SHOW_ANNOTATIONS|",
                "xbox360.dpad_down|D-pad down|QUPATH_FILL_ANNOTATIONS|",
                "xbox360.dpad_left|D-pad left|QUPATH_SHOW_DETECTIONS|",
                "xbox360.dpad_right|D-pad right|QUPATH_FILL_DETECTIONS|",
                "xbox360.left_x|Left joystick horizontal|MOUSE_MOVE_X|",
                "xbox360.left_y|Left joystick vertical|MOUSE_MOVE_Y|",
                "xbox360.right_x|Right joystick horizontal|QUPATH_PAN_X|",
                "xbox360.right_y|Right joystick vertical|QUPATH_PAN_Y|");
    }

    private static ControllerMapping migrateDefaultMapping(ControllerMapping mapping) {
        if ("dualsense.create".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.QUPATH_SCREENSHOT)
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_COMMAND, "File > Export snapshot... > Main window screenshot...");

        if ("dualsense.touchpad_right".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.QUPATH_COMMAND
                && "View > Show slide overview".equals(mapping.actionValue()))
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_SLIDE_OVERVIEW, "");

        if ("dualsense.touchpad_left".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.QUPATH_COMMAND
                && "View > Show analysis pane".equals(mapping.actionValue()))
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_TOGGLE_SIDEBAR, "");

        if ("dualsense.cross".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.KEY
                && "space".equalsIgnoreCase(mapping.actionValue()))
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.MOUSE_BUTTON, "Left");

        if ("dualsense.circle".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.NONE)
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_UNDO, "");

        if ("dualsense.square".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.NONE)
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.MOUSE_BUTTON, "Right");

        if ("dualsense.triangle".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.KEY
                && "shift".equalsIgnoreCase(mapping.actionValue()))
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.MOUSE_SHIFT_RIGHT_CLICK, "");

        if ("dualsense.r3".equals(mapping.inputId())
                && (mapping.actionType() == ControllerMapping.ActionType.NONE
                || mapping.actionType() == ControllerMapping.ActionType.QUPATH_ZOOM_TO_FIT))
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_COMMAND, "View > Zoom > Zoom to fit");

        if ("dualsense.options".equals(mapping.inputId())
                && (mapping.actionType() == ControllerMapping.ActionType.NONE
                || mapping.actionType() == ControllerMapping.ActionType.QUPATH_SAVE_AS))
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_COMMAND, "File > Save As...");

        if ("dualsense.touchpad_bottom".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.QUPATH_DETECTION_MEASUREMENTS)
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_COMMAND, "Measure > Show detection measurements");

        if ("dualsense.mic_mute".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.NONE)
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.CONTROLLER_TOGGLE_INPUT, "");

        if ("dualsense.touchpad_right".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.NONE)
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_SLIDE_OVERVIEW, "");

        if ("dualsense.l1".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.MOUSE_WHEEL
                && "down".equalsIgnoreCase(mapping.actionValue()))
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_ZOOM_OUT, "");

        if ("dualsense.r1".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.MOUSE_WHEEL
                && "up".equalsIgnoreCase(mapping.actionValue()))
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_ZOOM_IN, "");

        if ("dualsense.l2".equals(mapping.inputId())
                && mapping.actionType() == ControllerMapping.ActionType.NONE)
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_TOOL_PREVIOUS, "");

        if ("dualsense.r2".equals(mapping.inputId())
                && (mapping.actionType() == ControllerMapping.ActionType.NONE
                || mapping.actionType() == ControllerMapping.ActionType.KEY))
            return new ControllerMapping(mapping.inputId(), mapping.inputName(), ControllerMapping.ActionType.QUPATH_TOOL_NEXT, "");

        return mapping;
    }

    private void addDefaultMappingsIfMissing() {
        for (var line : defaultMappings().split("\\R")) {
            var parts = line.split(SEPARATOR, -1);
            if (parts.length < 4 || mappings.containsKey(unescape(parts[0])))
                continue;
            mappings.put(unescape(parts[0]), new ControllerMapping(
                    unescape(parts[0]),
                    unescape(parts[1]),
                    ControllerMapping.ActionType.valueOf(parts[2]),
                    unescape(parts[3])));
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\p").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        return value.replace("\\\\", "\\").replace("\\p", "|").replace("\\n", "\n");
    }
}
