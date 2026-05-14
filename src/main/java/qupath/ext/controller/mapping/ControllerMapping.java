package qupath.ext.controller.mapping;

import java.util.Objects;

public record ControllerMapping(String inputId, String inputName, ActionType actionType, String actionValue) {

    public ControllerMapping {
        inputId = Objects.requireNonNullElse(inputId, "");
        inputName = Objects.requireNonNullElse(inputName, inputId);
        actionType = Objects.requireNonNullElse(actionType, ActionType.NONE);
        actionValue = Objects.requireNonNullElse(actionValue, "");
    }

    public enum ActionType {
        // Shown in the mapping dropdown
        NONE("Disabled"),
        KEY("Keyboard key"),
        MOUSE_BUTTON("Mouse button"),
        MOUSE_WHEEL("Mouse wheel"),
        MOUSE_SHIFT_RIGHT_CLICK("Shift + right click"),
        MOUSE_MOVE_X("Mouse move X"),
        MOUSE_MOVE_Y("Mouse move Y"),
        CONTROLLER_TOGGLE_INPUT("Toggle controller input *"),
        CONTROLLER_TOGGLE_PAN_SPEED("Toggle fast/precise pan *"),
        QUPATH_PAN_X("QuPath pan X *"),
        QUPATH_PAN_Y("QuPath pan Y *"),
        QUPATH_ZOOM_IN("QuPath zoom in *"),
        QUPATH_ZOOM_OUT("QuPath zoom out *"),
        QUPATH_TOOL_NEXT("QuPath next tool *"),
        QUPATH_TOOL_PREVIOUS("QuPath previous tool *"),
        QUPATH_UNDO("QuPath undo *"),
        QUPATH_SHOW_ANNOTATIONS("QuPath show annotations *"),
        QUPATH_FILL_ANNOTATIONS("QuPath fill annotations *"),
        QUPATH_SHOW_ANNOTATION_NAMES("QuPath show annotation names *"),
        QUPATH_SHOW_DETECTIONS("QuPath show detections *"),
        QUPATH_FILL_DETECTIONS("QuPath fill detections *"),
        QUPATH_COMMAND("QuPath command..."),
        // Hidden from dropdown — achievable via QuPath command...
        QUPATH_CLOSE_DIALOG("QuPath close dialog *"),
        QUPATH_SAVE_AS("QuPath save as"),
        QUPATH_SCREENSHOT("QuPath screenshot"),
        QUPATH_SLIDE_OVERVIEW("QuPath slide overview *"),
        QUPATH_TOGGLE_SIDEBAR("QuPath show analysis pane *"),
        QUPATH_ZOOM_TO_FIT("QuPath zoom to fit"),
        QUPATH_COMMAND_LIST("QuPath command list"),
        QUPATH_DETECTION_MEASUREMENTS("QuPath detection measurements");

        private final String label;

        ActionType(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
