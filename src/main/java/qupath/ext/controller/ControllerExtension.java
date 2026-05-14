package qupath.ext.controller;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.controller.input.ControllerPoller;
import qupath.ext.controller.input.InputActionExecutor;
import qupath.ext.controller.mapping.MappingStore;
import qupath.ext.controller.mapping.ProfileStore;
import qupath.ext.controller.ui.MappingController;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.common.ColorTools;
import qupath.lib.common.Version;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionListener;
import qupath.lib.objects.hierarchy.events.PathObjectSelectionModel;

import java.awt.image.BufferedImage;
import java.util.Collection;

/**
 * QuPath extension that maps DualSense HID events to keyboard and mouse input.
 */
public class ControllerExtension implements QuPathExtension {

    private static final Logger logger = LoggerFactory.getLogger(ControllerExtension.class);
    private static final String EXTENSION_NAME = "Controller";
    private static final String EXTENSION_DESCRIPTION = "Map a game controller to QuPath actions and viewer controls.";
    private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.7.0");
    private static final BooleanProperty enabled = PathPrefs.createPersistentPreference(
            "gamepad.enabled", true);

    private boolean installed;
    private Stage stage;
    private ControllerPoller poller;
    private PathObjectSelectionModel selectionModel;
    private PathObjectSelectionListener selectionListener;
    private Integer lastLightbarColor;
    private int defaultLightbarColor = ColorTools.makeRGB(0, 0, 128);

    @Override
    public void installExtension(QuPathGUI qupath) {
        if (installed) {
            logger.debug("{} is already installed", getName());
            return;
        }
        installed = true;

        addPreference(qupath);
        addMenu(qupath);
        startPolling(qupath);
        installAnnotationColorSync(qupath);
    }

    private void addPreference(QuPathGUI qupath) {
        var propertyItem = new PropertyItemBuilder<>(enabled, Boolean.class)
                .name("Enable controller")
                .category(EXTENSION_NAME)
                .description("Poll connected HID controllers and apply the configured mappings")
                .build();
        qupath.getPreferencePane().getPropertySheet().getItems().add(propertyItem);
    }

    private void addMenu(QuPathGUI qupath) {
        var menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);

        var configureItem = new MenuItem("Controller layout...");
        configureItem.setOnAction(e -> showMappingWindow(qupath));
        menu.getItems().add(configureItem);
    }

    private ProfileStore profileStore;

    private void startPolling(QuPathGUI qupath) {
        profileStore = new ProfileStore();
        var mappingStore = new MappingStore();
        var executor = new InputActionExecutor(
                qupath,
                mappingStore.pointerSensitivityProperty(),
                mappingStore.panSensitivityProperty(),
                mappingStore.touchpadSensitivityProperty(),
                mappingStore.zoomSensitivityProperty());
        poller = new ControllerPoller(mappingStore, executor);
        poller.runningProperty().bind(enabled);
        poller.start();
        poller.deviceNameProperty().addListener((obs, old, name) -> {
            if (!name.equals("No controller detected")) {
                lastLightbarColor = null;
                updateLightbarForSelection(selectionModel == null ? null : selectionModel.getSelectedObject());
            }
        });
        observeUndoAvailability(qupath);
        autoLoadProfile(mappingStore);
    }

    private void autoLoadProfile(MappingStore mappingStore) {
        ensureDefaultProfile(mappingStore);
        var name = profileStore.getActiveProfileName();
        if (name == null)
            name = "Default";
        var profile = profileStore.load(name);
        if (profile == null) return;
        mappingStore.applyProfileMappings(profile);
        profileStore.setActiveProfileName(name);
        var finalName = name;
        Platform.runLater(() -> {
            var idx = profileStore.listProfiles().indexOf(finalName) + 1;
            poller.setPlayerLeds(idx > 0 ? idx : 0);
        });
    }

    private void ensureDefaultProfile(MappingStore mappingStore) {
        if (profileStore.load("Default") != null) return;
        profileStore.save(mappingStore.toProfile("Default"));
    }

    private void installAnnotationColorSync(QuPathGUI qupath) {
        selectionListener = this::selectedPathObjectChanged;
        updateSelectionModel(qupath.getImageData());
        qupath.imageDataProperty().addListener((observable, oldValue, newValue) -> updateSelectionModel(newValue));
    }

    private void updateSelectionModel(ImageData<BufferedImage> imageData) {
        if (selectionModel != null && selectionListener != null)
            selectionModel.removePathObjectSelectionListener(selectionListener);

        selectionModel = imageData == null ? null : imageData.getHierarchy().getSelectionModel();
        if (selectionModel != null) {
            selectionModel.addPathObjectSelectionListener(selectionListener);
            updateLightbarForSelection(selectionModel.getSelectedObject());
        } else {
            resetLightbarColor();
        }
    }

    private void selectedPathObjectChanged(PathObject pathObjectSelected, PathObject previousObject, Collection<PathObject> allSelected) {
        updateLightbarForSelection(pathObjectSelected);
    }

    private void updateLightbarForSelection(PathObject selectedObject) {
        if (poller == null)
            return;

        var color = getSelectionColor(selectedObject);
        if (color == null) {
            resetLightbarColor();
            return;
        }

        setLightbarColor(color, "selected annotation class " + selectedObject.getPathClass());
    }

    private void resetLightbarColor() {
        setLightbarColor(defaultLightbarColor, "default");
    }

    private void setLightbarColor(int color, String reason) {
        if (poller == null || (lastLightbarColor != null && lastLightbarColor == color))
            return;
        lastLightbarColor = color;
        var ok = poller.setLightbarColor(ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color));
        if (ok)
            logger.debug("Synced DualSense lightbar to {} color #{}", reason, Integer.toHexString(color));
    }

    private Integer getSelectionColor(PathObject selectedObject) {
        if (selectedObject == null || !selectedObject.isAnnotation())
            return null;

        var pathClass = selectedObject.getPathClass();
        if (pathClass != null && pathClass.getColor() != null)
            return pathClass.getColor();
        return null;
    }

    private void showMappingWindow(QuPathGUI qupath) {
        if (stage == null) {
            try {
                var controller = new MappingController(poller, qupath,
                        profile -> {
                            poller.mappingStore().applyProfile(profile);
                            profileStore.setActiveProfileName(profile.name());
                            var idx = profileStore.listProfiles().indexOf(profile.name()) + 1;
                            poller.setPlayerLeds(idx > 0 ? idx : 0);
                        });
                stage = new Stage();
                stage.initOwner(qupath.getStage());
                stage.setTitle("Controller layout");
                var scene = new Scene(controller, 1100, 620);
                var parentStylesheets = qupath.getStage().getScene().getStylesheets();
                scene.getStylesheets().addAll(parentStylesheets);
                stage.setScene(scene);
                stage.setMinWidth(960);
                stage.setMinHeight(520);
                stage.setOnCloseRequest(e -> Platform.runLater(() -> stage.hide()));
            } catch (java.io.IOException e) {
                logger.error("Failed to load controller layout window", e);
                return;
            }
        }
        stage.show();
        stage.toFront();
    }

    private void observeUndoAvailability(QuPathGUI qupath) {
        Platform.runLater(() -> {
            var scene = qupath.getStage().getScene();
            if (scene == null || scene.getRoot() == null)
                return;
            findMenuItem(scene.getRoot(), "Edit", "Undo").ifPresent(item -> {
                poller.setUndoAvailable(!item.isDisable());
                item.disableProperty().addListener((obs, old, disabled) ->
                        poller.setUndoAvailable(!disabled));
            });
        });
    }

    private java.util.Optional<MenuItem> findMenuItem(Parent root, String... path) {
        for (var child : root.getChildrenUnmodifiable()) {
            if (child instanceof MenuBar menuBar) {
                for (var menu : menuBar.getMenus()) {
                    var result = findMenuItem(menu, path, 0);
                    if (result.isPresent())
                        return result;
                }
            }
            if (child instanceof Parent parent) {
                var result = findMenuItem(parent, path);
                if (result.isPresent())
                    return result;
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<MenuItem> findMenuItem(MenuItem item, String[] path, int index) {
        var text = item.getText() == null ? "" : item.getText().replace("_", "").trim();
        var matches = text.equalsIgnoreCase(path[index]);
        if (matches && index == path.length - 1)
            return java.util.Optional.of(item);
        if (item instanceof Menu menu) {
            for (var child : menu.getItems()) {
                var result = matches
                        ? findMenuItem(child, path, index + 1)
                        : findMenuItem(child, path, index);
                if (result.isPresent())
                    return result;
            }
        }
        return java.util.Optional.empty();
    }



    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public String getDescription() {
        return EXTENSION_DESCRIPTION;
    }

    @Override
    public Version getQuPathVersion() {
        return EXTENSION_QUPATH_VERSION;
    }
}
