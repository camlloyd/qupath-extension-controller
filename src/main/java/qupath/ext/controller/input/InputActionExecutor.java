package qupath.ext.controller.input;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.controller.mapping.ControllerMapping;
import qupath.lib.gui.QuPathGUI;

import java.awt.AWTException;
import java.awt.MouseInfo;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

public class InputActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(InputActionExecutor.class);
    private final QuPathGUI qupath;
    private final DoubleProperty pointerSensitivity;
    private final DoubleProperty panSensitivity;
    private final DoubleProperty touchpadSensitivity;
    private final DoubleProperty zoomSensitivity;
    private final Robot robot;
    private final Set<Integer> heldKeys = new HashSet<>();
    private double fractionalMouseX;
    private double fractionalMouseY;
    private double fractionalPanX;
    private double fractionalPanY;
    private int lastToolIndex = -1;

    public InputActionExecutor(
            QuPathGUI qupath,
            DoubleProperty pointerSensitivity,
            DoubleProperty panSensitivity,
            DoubleProperty touchpadSensitivity,
            DoubleProperty zoomSensitivity) {
        this.qupath = qupath;
        this.pointerSensitivity = pointerSensitivity;
        this.panSensitivity = panSensitivity;
        this.touchpadSensitivity = touchpadSensitivity;
        this.zoomSensitivity = zoomSensitivity;
        this.robot = createRobot();
    }

    private Robot createRobot() {
        try {
            var created = new Robot();
            created.setAutoDelay(0);
            return created;
        } catch (AWTException e) {
            logger.error("Unable to create java.awt.Robot; gamepad mappings cannot emit input", e);
            return null;
        }
    }

    public void execute(ControllerMapping mapping, float value) {
        switch (mapping.actionType()) {
            case QUPATH_PAN_X -> panViewer(value, 0);
            case QUPATH_PAN_Y -> panViewer(0, value);

            case QUPATH_ZOOM_IN -> zoomViewer(true);
            case QUPATH_ZOOM_OUT -> zoomViewer(false);

            case QUPATH_TOOL_NEXT -> cycleTool(true);
            case QUPATH_TOOL_PREVIOUS -> cycleTool(false);
            case QUPATH_SHOW_ANNOTATIONS -> Platform.runLater(() -> {
                var o = qupath.getOverlayOptions(); if (o != null) o.showAnnotationsProperty().set(!o.showAnnotationsProperty().get()); });
            case QUPATH_FILL_ANNOTATIONS -> Platform.runLater(() -> {
                var o = qupath.getOverlayOptions(); if (o != null) o.fillAnnotationsProperty().set(!o.fillAnnotationsProperty().get()); });
            case QUPATH_SHOW_ANNOTATION_NAMES -> Platform.runLater(() -> {
                var o = qupath.getOverlayOptions(); if (o != null) o.showNamesProperty().set(!o.showNamesProperty().get()); });
            case QUPATH_SHOW_DETECTIONS -> Platform.runLater(() -> {
                var o = qupath.getOverlayOptions(); if (o != null) o.showDetectionsProperty().set(!o.showDetectionsProperty().get()); });
            case QUPATH_FILL_DETECTIONS -> Platform.runLater(() -> {
                var o = qupath.getOverlayOptions(); if (o != null) o.fillDetectionsProperty().set(!o.fillDetectionsProperty().get()); });
            case QUPATH_COMMAND -> Platform.runLater(() -> fireCommand(mapping.actionValue()));
            case QUPATH_CLOSE_DIALOG -> closeTopDialog();
            case QUPATH_UNDO -> Platform.runLater(() -> fireCommand("Edit > Undo"));
            case QUPATH_SLIDE_OVERVIEW -> Platform.runLater(() -> {
                var vm = qupath.getViewerManager();
                if (vm != null) vm.showOverviewProperty().set(!vm.showOverviewProperty().get());
            });
            case QUPATH_TOGGLE_SIDEBAR -> Platform.runLater(() ->
                    qupath.showAnalysisPaneProperty().set(!qupath.showAnalysisPaneProperty().get()));
            case NONE -> {
            }
            default -> executeRobotAction(mapping, value);
        }
    }

    private void executeRobotAction(ControllerMapping mapping, float value) {
        if (robot == null) return;
        switch (mapping.actionType()) {
            case KEY -> pressKey(mapping.actionValue());
            case MOUSE_BUTTON -> pressMouse(mapping.actionValue());
            case MOUSE_SHIFT_RIGHT_CLICK -> pressShiftRightClick();
            case MOUSE_WHEEL -> robot.mouseWheel(scaledWheel(mapping.actionValue()));
            case MOUSE_MOVE_X -> movePointer(value, 0);
            case MOUSE_MOVE_Y -> movePointer(0, value);
            default -> {
            }
        }
    }

    public void release(ControllerMapping mapping) {
        if (robot == null)
            return;

        switch (mapping.actionType()) {
            case KEY -> releaseKey(mapping.actionValue());
            case MOUSE_BUTTON -> releaseMouse(mapping.actionValue());
            default -> {
            }
        }
    }

    private void pressKey(String value) {
        var keyCode = parseKeyCode(value);
        if (keyCode != 0) {
            focusQuPathStage();
            robot.keyPress(keyCode);
            if (keyCode == KeyEvent.VK_SPACE)
                robot.keyRelease(keyCode);
        }
    }

    private void releaseKey(String value) {
        var keyCode = parseKeyCode(value);
        if (keyCode != 0)
            robot.keyRelease(keyCode);
    }

    public void releaseAllHeldKeys() {
        if (robot == null)
            return;
        var keys = new java.util.ArrayList<>(heldKeys);
        heldKeys.clear();
        for (var keyCode : keys) {
            try {
                robot.keyRelease(keyCode);
            } catch (Exception ignored) {
            }
        }
    }

    private void holdKey(int keyCode) {
        if (keyCode == 0 || heldKeys.contains(keyCode))
            return;
        focusQuPathStage();
        robot.keyPress(keyCode);
        heldKeys.add(keyCode);
    }

    private void releaseHeldKey(int keyCode) {
        if (keyCode == 0 || !heldKeys.remove(keyCode))
            return;
        robot.keyRelease(keyCode);
    }

    private void pressShiftRightClick() {
        focusQuPathStage();
        robot.keyPress(KeyEvent.VK_SHIFT);
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        robot.keyRelease(KeyEvent.VK_SHIFT);
    }


    private int parseKeyCode(String value) {
        if (value == null || value.isBlank())
            return 0;
        var normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() == 1)
            return KeyEvent.getExtendedKeyCodeForChar(normalized.charAt(0));
        try {
            var keyCode = KeyCode.valueOf(normalized);
            return KeyEvent.class.getField("VK_" + keyCode.getName().toUpperCase(Locale.ROOT).replace(" ", "_")).getInt(null);
        } catch (Exception ignored) {
            try {
                return KeyEvent.class.getField("VK_" + normalized).getInt(null);
            } catch (Exception e) {
                logger.warn("Unknown key mapping '{}'", value);
                return 0;
            }
        }
    }

    private void pressMouse(String value) {
        robot.mousePress(mouseMask(value));
    }

    private void releaseMouse(String value) {
        robot.mouseRelease(mouseMask(value));
    }

    private int mouseMask(String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "middle", "button2" -> InputEvent.BUTTON2_DOWN_MASK;
            case "right", "secondary", "button3" -> InputEvent.BUTTON3_DOWN_MASK;
            default -> InputEvent.BUTTON1_DOWN_MASK;
        };
    }

    private int scaledWheel(String configured) {
        return "up".equalsIgnoreCase(configured) ? -1 : 1;
    }

    private void moveMouse(int dx, int dy) {
        var pointer = MouseInfo.getPointerInfo();
        if (pointer == null)
            return;
        var location = pointer.getLocation();
        robot.mouseMove(location.x + dx, location.y + dy);
    }

    public void movePointer(double x, double y) {
        if (robot == null)
            return;

        var magnitude = Math.sqrt(x * x + y * y);
        if (magnitude < 0.001) {
            fractionalMouseX = 0;
            fractionalMouseY = 0;
            return;
        }

        var curvedMagnitude = Math.pow(Math.min(1.0, magnitude), 1.45);
        var distance = pointerSpeed() * curvedMagnitude;
        var dx = x / magnitude * distance + fractionalMouseX;
        var dy = y / magnitude * distance + fractionalMouseY;

        var moveX = roundTowardZero(dx);
        var moveY = roundTowardZero(dy);
        fractionalMouseX = dx - moveX;
        fractionalMouseY = dy - moveY;

        if (moveX != 0 || moveY != 0)
            moveMouse(moveX, moveY);
    }

    public void panViewer(double x, double y) {
        panViewerWithSensitivity(x, y, panSensitivity.get());
    }

    public void panTouchpad(double x, double y) {
        panViewerWithSensitivity(x, y, touchpadSensitivity.get());
    }

    private void panViewerWithSensitivity(double x, double y, double sensitivity) {
        var magnitude = Math.sqrt(x * x + y * y);
        if (magnitude < 0.001) {
            fractionalPanX = 0;
            fractionalPanY = 0;
            return;
        }

        var curvedMagnitude = Math.pow(Math.min(1.0, magnitude), 1.35);
        var distance = Math.max(1, sensitivity * 3.5) * curvedMagnitude;
        var dx = x / magnitude * distance + fractionalPanX;
        var dy = y / magnitude * distance + fractionalPanY;

        var moveX = roundTowardZero(dx);
        var moveY = roundTowardZero(dy);
        fractionalPanX = dx - moveX;
        fractionalPanY = dy - moveY;

        if (moveX == 0 && moveY == 0)
            return;

        Platform.runLater(() -> {
            var viewer = qupath.getViewer();
            if (viewer == null || !viewer.hasServer())
                return;
            var downsample = viewer.getDownsampleFactor();
            viewer.setCenterPixelLocation(
                    viewer.getCenterPixelX() + moveX * downsample,
                    viewer.getCenterPixelY() + moveY * downsample);
        });
    }

    public void zoomViewer(boolean zoomIn) {
        Platform.runLater(() -> {
            var viewer = qupath.getViewer();
            if (viewer == null || !viewer.hasServer())
                return;

            var current = viewer.getDownsampleFactor();
            var step = Math.max(0.001, Math.min(0.08, zoomSensitivity.get() * 0.002));
            var factor = zoomIn ? Math.exp(-step) : Math.exp(step);
            var requested = current * factor;
            var clamped = Math.max(viewer.getMinDownsample(), Math.min(viewer.getMaxDownsample(), requested));
            if (Double.isFinite(clamped) && Math.abs(clamped - current) > 0.0001)
                viewer.setDownsampleFactor(clamped);
        });
    }

    private void cycleTool(boolean forward) {
        Platform.runLater(() -> {
            var toolManager = qupath.getToolManager();
            var tools = toolManager.getTools();
            if (tools.isEmpty())
                return;

            var selected = toolManager.getSelectedTool();
            var index = tools.indexOf(selected);
            if (index < 0)
                index = lastToolIndex >= 0 ? lastToolIndex : 0;
            index = Math.floorMod(index + (forward ? 1 : -1), tools.size());
            lastToolIndex = index;
            toolManager.setSelectedTool(tools.get(index));
        });
    }

    private void closeTopDialog() {
        Platform.runLater(() -> {
            Stage candidate = null;
            for (var window : Window.getWindows()) {
                if (!(window instanceof Stage stage) || !stage.isShowing() || stage == qupath.getStage())
                    continue;
                if (candidate == null || stage.isFocused())
                    candidate = stage;
            }
            if (candidate != null)
                candidate.close();
        });
    }

    private int roundTowardZero(double value) {
        return value < 0 ? (int)Math.ceil(value) : (int)Math.floor(value);
    }

    private int pointerSpeed() {
        return Math.max(1, (int)Math.round(pointerSensitivity.get()));
    }

    private void fireCommand(String value) {
        if (value == null || value.isBlank())
            return;
        var scene = qupath.getStage().getScene();
        if (scene == null || scene.getRoot() == null)
            return;
        var parts = value.split(" > ");
        if (!fireMenuPath(scene.getRoot(), parts))
            logger.warn("Could not find QuPath command: {}", value);
    }

    public static List<String> enumerateMenuCommands(javafx.scene.Parent root) {
        var commands = new java.util.ArrayList<String>();
        collectMenuCommands(root, new java.util.ArrayDeque<>(), commands);
        commands.sort(String::compareToIgnoreCase);
        return commands;
    }

    private static void collectMenuCommands(
            javafx.scene.Parent root,
            java.util.Deque<String> path,
            java.util.List<String> commands) {
        for (var child : root.getChildrenUnmodifiable()) {
            if (child instanceof javafx.scene.control.MenuBar menuBar) {
                for (var menu : menuBar.getMenus())
                    collectMenuCommands(menu, path, commands);
            } else if (child instanceof javafx.scene.Parent parent) {
                collectMenuCommands(parent, path, commands);
            }
        }
    }

    private static void collectMenuCommands(
            javafx.scene.control.MenuItem item,
            java.util.Deque<String> path,
            java.util.List<String> commands) {
        if (item.getText() == null || item.getText().isBlank())
            return;
        path.addLast(item.getText());
        if (item instanceof javafx.scene.control.Menu menu) {
            for (var child : menu.getItems())
                collectMenuCommands(child, path, commands);
        } else {
            commands.add(String.join(" > ", path));
        }
        path.removeLast();
    }

    private boolean fireMenuPath(javafx.scene.Parent root, String... path) {
        for (var child : root.getChildrenUnmodifiable()) {
            if (child instanceof javafx.scene.control.MenuBar menuBar) {
                for (var menu : menuBar.getMenus()) {
                    if (fireMenuPath(menu, path, 0))
                        return true;
                }
            }
            if (child instanceof javafx.scene.Parent parent && fireMenuPath(parent, path))
                return true;
        }
        return false;
    }

    private boolean fireMenuPath(javafx.scene.control.MenuItem item, String[] path, int index) {
        var text = item.getText() == null ? "" : item.getText().replace("_", "").replace("…", "...").trim();
        var matches = text.equalsIgnoreCase(path[index].trim());
        if (matches && index == path.length - 1) {
            if (!item.isDisable())
                item.fire();
            return true;
        }
        if (item instanceof javafx.scene.control.Menu menu) {
            for (var child : menu.getItems()) {
                if (matches && fireMenuPath(child, path, index + 1))
                    return true;
                if (!matches && fireMenuPath(child, path, index))
                    return true;
            }
        }
        return false;
    }

    private void focusQuPathStage() {
        qupath.getStage().requestFocus();
    }
}
