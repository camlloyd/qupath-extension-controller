package qupath.ext.controller.mapping;

import java.util.List;

public record ControllerProfile(
        String name,
        double pointerSensitivity,
        double panSensitivity,
        double zoomSensitivity,
        Double touchpadSensitivity,
        List<ProfileMapping> mappings) {

    public record ProfileMapping(String inputId, String inputName, String actionType, String actionValue) {

        public static ProfileMapping from(ControllerMapping m) {
            return new ProfileMapping(m.inputId(), m.inputName(), m.actionType().name(), m.actionValue());
        }

        public ControllerMapping toControllerMapping() {
            try {
                return new ControllerMapping(inputId, inputName,
                        ControllerMapping.ActionType.valueOf(actionType), actionValue);
            } catch (IllegalArgumentException e) {
                return new ControllerMapping(inputId, inputName, ControllerMapping.ActionType.NONE, "");
            }
        }
    }
}
