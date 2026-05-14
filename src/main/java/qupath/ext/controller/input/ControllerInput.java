package qupath.ext.controller.input;

import java.util.Objects;

public final class ControllerInput {

    private final String id;
    private final String displayName;
    private final boolean analog;

    public ControllerInput(String id, String displayName, boolean analog) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.analog = analog;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean analog() {
        return analog;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
