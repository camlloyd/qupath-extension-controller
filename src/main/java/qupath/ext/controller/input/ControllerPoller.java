package qupath.ext.controller.input;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.controller.mapping.ControllerMapping;
import qupath.ext.controller.mapping.MappingStore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ControllerPoller {

    private static final Logger logger = LoggerFactory.getLogger(ControllerPoller.class);
    private static final float BUTTON_THRESHOLD = 0.5f;
    private static final long CONTINUOUS_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(16);
    private static final long TRIGGER_REPEAT_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(300);
    private static final long UNDO_HOLD_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long UNDO_RUMBLE_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(400);

    private final List<HidControllerDriver> drivers = List.of(new DualSenseDriver(), new Xbox360Driver(), new SpaceMouseDriver());
    private final MappingStore mappingStore;
    private final InputActionExecutor executor;
    private final ObservableList<ControllerInput> inputs = FXCollections.observableArrayList();
    private volatile boolean runningVolatile = true;
    private final BooleanProperty running = new SimpleBooleanProperty(true);
    private final StringProperty deviceName = new SimpleStringProperty("No controller detected");
    private final Map<String, Float> lastValues = new HashMap<>();
    private final Map<String, Long> lastContinuousNanos = new HashMap<>();
    private final Map<String, Long> holdStartNanos = new HashMap<>();
    private final Set<String> holdFired = new HashSet<>();
    private final Set<String> rumbleFired = new HashSet<>();
    private Set<String> analogInputIds = Set.of();
    private static final long RECONNECT_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(5);
    private volatile long lastReconnectNanos = 0;
    private final Object deviceLock = new Object();
    private volatile boolean inputFrozen;
    private volatile boolean finePanMode;
    private volatile boolean undoAvailable = true;
    private volatile Map<String, Float> cachedValues = Map.of();
    private final AtomicBoolean newDataPending = new AtomicBoolean();

    private volatile HidServices hidServices;
    private volatile HidDevice activeDevice;
    private volatile HidControllerDriver activeDriver;

    public ControllerPoller(MappingStore mappingStore, InputActionExecutor executor) {
        this.mappingStore = mappingStore;
        this.executor = executor;
        running.addListener((obs, oldVal, newVal) -> runningVolatile = newVal);
    }

    public void start() {
        refreshControllers();
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                pollSafely();
            }
        }.start();
    }

    public void refreshControllers() {
        try {
            var services = getHidServices();
            services.scan();

            HidDevice selected = null;
            HidControllerDriver selectedDriver = null;
            int total = 0;

            for (var device : services.getAttachedHidDevices()) {
                total++;
                for (var driver : drivers) {
                    if (driver.matches(device)) {
                        if (selected == null || driver.isPreferred(device)) {
                            selected = device;
                            selectedDriver = driver;
                        }
                        break;
                    }
                }
            }

            activateDevice(selected, selectedDriver);
            updateInputs(selected, selectedDriver);

            var name = selected == null ? "No controller detected" : selected.getProduct();
            Platform.runLater(() -> deviceName.set(name));
            logger.info("HID refresh found {} device(s); active device is {}",
                    total, selected == null ? "none" : selected.getProduct());
        } catch (Throwable t) {
            activateDevice(null, null);
            updateInputs(null, null);
            logger.warn("Unable to refresh HID controllers", t);
        }
    }

    private synchronized HidServices getHidServices() {
        var services = hidServices;
        if (services != null)
            return services;

        var specification = new HidServicesSpecification();
        specification.setAutoStart(false);
        specification.setAutoShutdown(true);
        specification.setAutoDataRead(false);
        services = HidManager.getHidServices(specification);
        services.start();
        hidServices = services;
        return services;
    }

    private void activateDevice(HidDevice selected, HidControllerDriver driver) {
        HidDevice deviceToStart = null;
        synchronized (deviceLock) {
            var current = activeDevice;
            if (current != null && current != selected)
                current.close();

            if (activeDriver != null)
                activeDriver.reset();

            activeDevice = selected;
            activeDriver = driver;
            lastValues.clear();
            lastContinuousNanos.clear();
            cachedValues = Map.of();
            newDataPending.set(false);
            inputFrozen = false;
            finePanMode = false;

            if (selected == null) {
                executor.releaseAllHeldKeys();
            }

            if (selected != null && !selected.isOpen()) {
                var opened = selected.open();
                logger.info("Opening HID device {} returned {} ({})",
                        selected.getProduct(), opened, selected.getLastErrorMessage());
                if (opened)
                    deviceToStart = selected;
                else
                    activeDevice = null;
            }
        }
        if (deviceToStart != null)
            startHidPoller(deviceToStart, driver);
    }

    private void startHidPoller(HidDevice device, HidControllerDriver driver) {
        var thread = new Thread(() -> {
            var buffer = new byte[128];
            var errorExit = false;
            while (device == activeDevice) {
                try {
                    var bytesRead = device.read(buffer, 50);
                    if (device != activeDevice) break;
                    if (bytesRead < 0) {
                        // Negative return signals a device error (e.g. cable pulled or driver conflict).
                        logger.warn("HID read error on {}: {}", device.getProduct(), device.getLastErrorMessage());
                        errorExit = true;
                        break;
                    }
                    if (bytesRead >= driver.minReportLength()) {
                        var parsed = driver.parseReport(buffer, bytesRead);
                        if (!parsed.isEmpty()) {
                            cachedValues = parsed;
                            newDataPending.set(true);
                        }
                    }
                } catch (Exception e) {
                    if (device == activeDevice) {
                        logger.warn("Error reading from HID device", e);
                        errorExit = true;
                    }
                    break;
                }
            }
            if (errorExit && device == activeDevice) {
                var now = System.nanoTime();
                if (now - lastReconnectNanos >= RECONNECT_COOLDOWN_NANOS) {
                    lastReconnectNanos = now;
                    logger.info("Scheduling controller reconnect after device error");
                    javafx.application.Platform.runLater(this::refreshControllers);
                } else {
                    logger.info("Suppressing reconnect attempt (cooldown active)");
                }
            }
        }, "controller-hid-poller");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateInputs(HidDevice device, HidControllerDriver driver) {
        var discovered = FXCollections.<ControllerInput>observableArrayList();
        if (device != null && driver != null)
            discovered.addAll(driver.inputs());
        Platform.runLater(() -> {
            inputs.setAll(discovered);
            analogInputIds = discovered.stream()
                    .filter(ControllerInput::analog)
                    .map(ControllerInput::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (var input : discovered)
                mappingStore.ensureInput(input.id(), input.displayName());
        });
    }

    private void pollSafely() {
        try {
            if (!runningVolatile)
                return;
            poll();
        } catch (Exception e) {
            logger.warn("Error while polling HID controller", e);
        }
    }

    private void poll() {
        var driver = activeDriver;
        if (driver == null)
            return;

        var values = cachedValues;
        if (values.isEmpty())
            return;

        var newData = newDataPending.getAndSet(false);

        double pointerX = 0, pointerY = 0; var hasPointer = false;
        double panX = 0, panY = 0;         var hasPan = false;
        double touchPanX = 0, touchPanY = 0; var hasTouchPan = false;

        for (var entry : values.entrySet()) {
            var inputId = entry.getKey();
            var value = entry.getValue();
            var mapping = mappingStore.getMapping(inputId);
            if (mapping == null || mapping.actionType() == ControllerMapping.ActionType.NONE)
                mapping = driver.builtInMapping(inputId);
            if (mapping == null || mapping.actionType() == ControllerMapping.ActionType.NONE)
                continue;

            var type = mapping.actionType();

            if (type == ControllerMapping.ActionType.CONTROLLER_TOGGLE_INPUT) {
                if (newData) {
                    var lastValue = lastValues.getOrDefault(inputId, 0f);
                    lastValues.put(inputId, value);
                    handleFreezeToggle(inputId, value, lastValue);
                }
                continue;
            }
            if (inputFrozen)
                continue;

            var touchpadSwipe = driver.isTouchpadSwipe(inputId);

            if (type == ControllerMapping.ActionType.MOUSE_MOVE_X)                  { pointerX += value; hasPointer = true; continue; }
            if (type == ControllerMapping.ActionType.MOUSE_MOVE_Y)                  { pointerY += value; hasPointer = true; continue; }
            if (type == ControllerMapping.ActionType.QUPATH_PAN_X && touchpadSwipe) { touchPanX += value; hasTouchPan = true; continue; }
            if (type == ControllerMapping.ActionType.QUPATH_PAN_Y && touchpadSwipe) { touchPanY += value; hasTouchPan = true; continue; }
            if (type == ControllerMapping.ActionType.QUPATH_PAN_X)                  { panX += value; hasPan = true; continue; }
            if (type == ControllerMapping.ActionType.QUPATH_PAN_Y)                  { panY += value; hasPan = true; continue; }

            var lastValue = lastValues.getOrDefault(inputId, 0f);
            lastValues.put(inputId, value);

            if (type == ControllerMapping.ActionType.CONTROLLER_TOGGLE_PAN_SPEED) {
                if (newData) handleFinePanToggle(inputId, value, lastValue);
                continue;
            }
            if (isAnalog(inputId)) {
                if (newData) handleAnalog(mapping, value);
                continue;
            }
            handleButton(inputId, mapping, value, lastValue, driver);
        }

        var panScale = finePanMode ? 0.2 : 1.0;
        if (hasPointer)   executor.movePointer(pointerX, pointerY);
        if (hasPan)       executor.panViewer(panX * panScale, panY * panScale);
        if (hasTouchPan)  executor.panTouchpad(touchPanX * panScale, touchPanY * panScale);
    }

    private boolean isAnalog(String inputId) {
        return analogInputIds.contains(inputId);
    }

    private void handleAnalog(ControllerMapping mapping, float value) {
        if (Math.abs(value) < 0.001f)
            return;
        executor.execute(mapping, value);
    }

    private void handleButton(String inputId, ControllerMapping mapping,
                               float value, float lastValue, HidControllerDriver driver) {
        var pressed = value >= BUTTON_THRESHOLD;
        var wasPressed = lastValue >= BUTTON_THRESHOLD;

        if (mapping.actionType() == ControllerMapping.ActionType.QUPATH_UNDO) {
            handleDelayedUndo(inputId, mapping, pressed);
            return;
        }

        if (driver.isTriggerRepeat(inputId) && isTriggerRepeatAction(mapping)) {
            handleRepeatingButton(inputId, mapping, pressed, wasPressed, TRIGGER_REPEAT_INTERVAL_NANOS);
            return;
        }

        if (isContinuous(mapping)) {
            if (pressed && shouldFireContinuous(inputId))
                executor.execute(mapping, 1f);
            return;
        }

        if (pressed && !wasPressed) {
            executor.execute(mapping, 1f);
        } else if (!pressed && wasPressed) {
            executor.release(mapping);
        }
    }

    private void handleRepeatingButton(String inputId, ControllerMapping mapping,
                                        boolean pressed, boolean wasPressed, long interval) {
        if (!pressed) {
            lastContinuousNanos.remove(inputId);
            if (wasPressed) executor.release(mapping);
            return;
        }
        var now = System.nanoTime();
        if (!wasPressed) {
            lastContinuousNanos.put(inputId, now);
            executor.execute(mapping, 1f);
        } else if (shouldFireContinuous(inputId, interval)) {
            executor.execute(mapping, 1f);
        }
    }

    private void handleDelayedUndo(String inputId, ControllerMapping mapping, boolean pressed) {
        if (!pressed) {
            if (holdStartNanos.containsKey(inputId) && !holdFired.contains(inputId)) {
                executor.execute(new ControllerMapping(inputId, mapping.inputName(),
                        ControllerMapping.ActionType.QUPATH_CLOSE_DIALOG, ""), 1f);
            }
            holdStartNanos.remove(inputId);
            holdFired.remove(inputId);
            if (rumbleFired.remove(inputId)) setRumble(0, 0);
            return;
        }

        var now = System.nanoTime();
        var start = holdStartNanos.computeIfAbsent(inputId, ignored -> now);
        var elapsed = now - start;

        if (elapsed >= UNDO_RUMBLE_DELAY_NANOS && !rumbleFired.contains(inputId) && undoAvailable) {
            rumbleFired.add(inputId);
            setRumble(0, 20);
        }
        if (!holdFired.contains(inputId) && elapsed >= UNDO_HOLD_NANOS) {
            holdFired.add(inputId);
            setRumble(0, 0);
            executor.execute(mapping, 1f);
        }
    }

    private void handleFreezeToggle(String inputId, float value, float lastValue) {
        if (value < BUTTON_THRESHOLD || lastValue >= BUTTON_THRESHOLD) return;
        inputFrozen = !inputFrozen;
        if (inputFrozen) {
            executor.releaseAllHeldKeys();
            finePanMode = false;
            setMicMuteLed(false);
        }
        lastContinuousNanos.clear();
        logger.info("Controller input {}", inputFrozen ? "frozen" : "active");
    }

    private void handleFinePanToggle(String inputId, float value, float lastValue) {
        if (value < BUTTON_THRESHOLD || lastValue >= BUTTON_THRESHOLD) return;
        finePanMode = !finePanMode;
        setMicMuteLed(finePanMode);
        logger.info("Fine pan mode {}", finePanMode ? "enabled" : "disabled");
    }

    private boolean isContinuous(ControllerMapping mapping) {
        return mapping.actionType() == ControllerMapping.ActionType.MOUSE_WHEEL
                || mapping.actionType() == ControllerMapping.ActionType.QUPATH_ZOOM_IN
                || mapping.actionType() == ControllerMapping.ActionType.QUPATH_ZOOM_OUT;
    }

    private boolean isTriggerRepeatAction(ControllerMapping mapping) {
        return mapping.actionType() == ControllerMapping.ActionType.KEY
                || mapping.actionType() == ControllerMapping.ActionType.QUPATH_TOOL_NEXT
                || mapping.actionType() == ControllerMapping.ActionType.QUPATH_TOOL_PREVIOUS;
    }

    private boolean shouldFireContinuous(String inputId) {
        return shouldFireContinuous(inputId, CONTINUOUS_INTERVAL_NANOS);
    }

    private boolean shouldFireContinuous(String inputId, long intervalNanos) {
        var now = System.nanoTime();
        var previous = lastContinuousNanos.getOrDefault(inputId, 0L);
        if (now - previous < intervalNanos) return false;
        lastContinuousNanos.put(inputId, now);
        return true;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public ObservableList<ControllerInput> inputs() { return inputs; }
    public StringProperty deviceNameProperty()      { return deviceName; }
    public MappingStore mappingStore()               { return mappingStore; }
    public BooleanProperty runningProperty()         { return running; }
    public void setUndoAvailable(boolean available)  { this.undoAvailable = available; }

    public boolean setLightbarColor(int r, int g, int b) {
        synchronized (deviceLock) {
            var d = activeDriver; var dev = activeDevice;
            return d != null && d.setLightbarColor(dev, r, g, b);
        }
    }

    public boolean setMicMuteLed(boolean muted) {
        synchronized (deviceLock) {
            var d = activeDriver; var dev = activeDevice;
            return d != null && d.setMicMuteLed(dev, muted);
        }
    }

    public boolean setRumble(int left, int right) {
        synchronized (deviceLock) {
            var d = activeDriver; var dev = activeDevice;
            return d != null && d.setRumble(dev, left, right);
        }
    }

    public boolean setPlayerLeds(int player) {
        synchronized (deviceLock) {
            var d = activeDriver; var dev = activeDevice;
            return d != null && d.setPlayerLeds(dev, player);
        }
    }
}
