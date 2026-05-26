package qupath.ext.controller.ui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import qupath.fx.dialogs.Dialogs;
import qupath.ext.controller.input.ControllerInput;
import qupath.ext.controller.input.ControllerPoller;
import qupath.ext.controller.input.InputActionExecutor;
import qupath.ext.controller.mapping.ControllerMapping;
import qupath.ext.controller.mapping.ControllerProfile;
import qupath.ext.controller.mapping.MappingStore;
import qupath.ext.controller.mapping.ProfileStore;
import qupath.lib.gui.QuPathGUI;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public class MappingController extends TabPane {

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final ControllerPoller poller;
    private final QuPathGUI qupath;
    private final ProfileStore profileStore = new ProfileStore();
    private final Consumer<ControllerProfile> onProfileLoad;

    // ── FXML fields ───────────────────────────────────────────────────────────

    @FXML private Label deviceLabel;
    @FXML private TableView<ControllerMapping> table;
    @FXML private TableColumn<ControllerMapping, String> inputColumn;
    @FXML private TableColumn<ControllerMapping, ControllerMapping> actionColumn;
    @FXML private TableColumn<ControllerMapping, ControllerMapping> valueColumn;
    @FXML private ListView<String> profileList;
    @FXML private Button loadButton;
    @FXML private Button deleteButton;
    @FXML private TextField profileNameField;
    @FXML private Button saveProfileButton;
    @FXML private Slider pointerSlider;
    @FXML private Slider panSlider;
    @FXML private Slider touchpadSlider;
    @FXML private Slider zoomSlider;
    @FXML private Label pointerValueLabel;
    @FXML private Label panValueLabel;
    @FXML private Label touchpadValueLabel;
    @FXML private Label zoomValueLabel;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MappingController(ControllerPoller poller, QuPathGUI qupath,
                             Consumer<ControllerProfile> onProfileLoad) throws IOException {
        this.poller = poller;
        this.qupath = qupath;
        this.onProfileLoad = onProfileLoad;
        UiUtils.loadFXML(this, MappingController.class.getResource("mapping_controller.fxml"));
        configureTable();
        configureProfiles();
        configureSensitivity();
    }

    // ── Layout tab wiring ─────────────────────────────────────────────────────

    private void configureTable() {
        table.setItems(poller.mappingStore().mappings());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        poller.inputs().addListener(
                (javafx.collections.ListChangeListener<ControllerInput>) c -> table.refresh());

        inputColumn.setCellValueFactory(data -> {
            var id = data.getValue().inputId();
            var name = poller.inputs().stream()
                    .filter(i -> i.id().equals(id))
                    .map(ControllerInput::displayName)
                    .findFirst()
                    .orElse(data.getValue().inputName());
            return new SimpleStringProperty(name);
        });

        actionColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        actionColumn.setCellFactory(col -> createActionCell());

        valueColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        valueColumn.setCellFactory(col -> createValueCell());

        deviceLabel.textProperty().bind(poller.deviceNameProperty());
        deviceLabel.setStyle("-fx-opacity: 0.72;");
    }

    @FXML
    private void onRefresh() {
        poller.refreshControllers();
    }

    @FXML
    private void onReset() {
        poller.mappingStore().resetDefaults();
        var def = poller.mappingStore().toProfile("Default");
        profileStore.save(def);
        onProfileLoad.accept(def);
    }

    // ── Profiles tab wiring ───────────────────────────────────────────────────

    private void configureProfiles() {
        profileList.getItems().setAll(profileStore.listProfiles());
        profileList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            private final Label playerLabel = new Label();
            private final Label nameLabel = new Label();
            private final javafx.scene.layout.HBox cell =
                    new javafx.scene.layout.HBox(6, playerLabel, nameLabel);
            {
                cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                playerLabel.setStyle("-fx-opacity: 0.5; -fx-font-size: 10px;");
            }
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) { setGraphic(null); return; }
                nameLabel.setText(name);
                playerLabel.setText((lv.getItems().indexOf(name) + 1) + ".");
                setGraphic(cell);
            }
        });

        profileList.getSelectionModel().selectedItemProperty().addListener((obs, old, name) -> {
            boolean selected = name != null;
            loadButton.setDisable(!selected);
            deleteButton.setDisable(!selected || "Default".equals(name));
        });

        Runnable updateSaveDisable = () -> {
            var name = profileNameField.getText().trim();
            var profiles = profileStore.listProfiles();
            saveProfileButton.setDisable(name.isBlank() ||
                    (profiles.size() >= ProfileStore.MAX_PROFILES && !profiles.contains(name)));
        };
        profileNameField.textProperty().addListener((obs, o, n) -> updateSaveDisable.run());
        profileList.getItems().addListener(
                (javafx.collections.ListChangeListener<String>) c -> updateSaveDisable.run());
        updateSaveDisable.run();
    }

    @FXML
    private void onLoad() {
        var name = profileList.getSelectionModel().getSelectedItem();
        if (name == null) return;
        var profile = profileStore.load(name);
        if (profile != null) onProfileLoad.accept(profile);
    }

    @FXML
    private void onDelete() {
        var name = profileList.getSelectionModel().getSelectedItem();
        if (name == null) return;
        if (Dialogs.showConfirmDialog("Delete profile", "Delete profile \"" + name + "\"?")) {
            var wasActive = name.equals(profileStore.getActiveProfileName());
            profileStore.delete(name);
            profileList.getItems().setAll(profileStore.listProfiles());
            if (wasActive) {
                var fallback = profileStore.load("Default");
                if (fallback != null) onProfileLoad.accept(fallback);
            }
        }
    }

    @FXML
    private void onSaveProfile() {
        var name = profileNameField.getText().trim();
        if (name.isBlank()) return;
        var profile = poller.mappingStore().toProfile(name);
        profileStore.save(profile);
        profileList.getItems().setAll(profileStore.listProfiles());
        profileList.getSelectionModel().select(name);
        profileNameField.clear();
    }

    // ── Advanced tab wiring ───────────────────────────────────────────────────

    private void configureSensitivity() {
        bindSlider(pointerSlider, pointerValueLabel, poller.mappingStore().pointerSensitivityProperty());
        bindSlider(panSlider, panValueLabel, poller.mappingStore().panSensitivityProperty());
        bindSlider(touchpadSlider, touchpadValueLabel, poller.mappingStore().touchpadSensitivityProperty());
        bindSlider(zoomSlider, zoomValueLabel, poller.mappingStore().zoomSensitivityProperty());
    }

    private void bindSlider(Slider slider, Label valueLabel,
                             javafx.beans.property.DoubleProperty property) {
        slider.valueProperty().bindBidirectional(property);
        valueLabel.setText(String.valueOf((int) slider.getValue()));
        slider.valueProperty().addListener((obs, o, v) -> valueLabel.setText(String.valueOf(v.intValue())));
    }

    @FXML
    private void onResetSensitivity() {
        poller.mappingStore().pointerSensitivityProperty().set(MappingStore.DEFAULT_POINTER_SENSITIVITY);
        poller.mappingStore().panSensitivityProperty().set(MappingStore.DEFAULT_PAN_SENSITIVITY);
        poller.mappingStore().touchpadSensitivityProperty().set(MappingStore.DEFAULT_TOUCHPAD_SENSITIVITY);
        poller.mappingStore().zoomSensitivityProperty().set(MappingStore.DEFAULT_ZOOM_SENSITIVITY);
    }

    // ── Inline action cell ────────────────────────────────────────────────────

    private TableCell<ControllerMapping, ControllerMapping> createActionCell() {
        return new TableCell<>() {
            private final ComboBox<ControllerMapping.ActionType> combo = new ComboBox<>();
            private boolean updating = false;

            {
                combo.getItems().setAll(visibleActionTypes());
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setOnAction(e -> {
                    if (updating) return;
                    var mapping = table.getSelectionModel().getSelectedItem();
                    if (mapping == null || combo.getValue() == null) return;
                    var newType = combo.getValue();
                    var keepValue = valueTypeGroup(newType).equals(valueTypeGroup(mapping.actionType()));
                    poller.mappingStore().putMapping(new ControllerMapping(
                            mapping.inputId(), mapping.inputName(), newType,
                            keepValue ? mapping.actionValue() : ""));
                });
                table.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
                    if (!isEmpty() && getItem() != null) updateItem(getItem(), false);
                });
            }

            @Override
            protected void updateItem(ControllerMapping mapping, boolean empty) {
                super.updateItem(mapping, empty);
                if (empty || mapping == null) { setGraphic(null); setText(null); return; }
                boolean selected = getTableView() != null &&
                        getTableView().getSelectionModel().getSelectedIndex() == getIndex();
                if (selected) {
                    updating = true;
                    try {
                        combo.getItems().setAll(visibleActionTypes());
                        var type = mapping.actionType();
                        if (!combo.getItems().contains(type))
                            combo.getItems().add(0, type);
                        combo.setValue(type);
                    } finally {
                        updating = false;
                    }
                    setGraphic(combo);
                    setText(null);
                } else {
                    setGraphic(null);
                    var type = mapping.actionType();
                    setText(type == ControllerMapping.ActionType.NONE ? "" : type.toString());
                }
            }
        };
    }

    // ── Inline value cell ─────────────────────────────────────────────────────

    private TableCell<ControllerMapping, ControllerMapping> createValueCell() {
        return new TableCell<>() {
            private final TextField textField = new TextField();
            private final ComboBox<String> mouseButtonBox = new ComboBox<>();
            private final ComboBox<String> mouseWheelBox = new ComboBox<>();
            private final ComboBox<String> commandBox = new ComboBox<>();
            private boolean updating = false;
            private ControllerMapping editingMapping = null;

            {
                textField.setTooltip(new Tooltip(
                        "Keys: SPACE, SHIFT, A, ENTER\nHold directions: LEFT_RIGHT, UP_DOWN"));
                textField.setOnAction(e -> commitValue(textField.getText()));
                textField.focusedProperty().addListener(
                        (obs, o, focused) -> { if (!focused) commitValue(textField.getText()); });

                mouseButtonBox.getItems().setAll("Left", "Middle", "Right");
                mouseButtonBox.setMaxWidth(Double.MAX_VALUE);
                mouseButtonBox.valueProperty().addListener(
                        (obs, o, n) -> { if (!updating && n != null) commitValue(n); });

                mouseWheelBox.getItems().setAll("Up", "Down");
                mouseWheelBox.setMaxWidth(Double.MAX_VALUE);
                mouseWheelBox.valueProperty().addListener(
                        (obs, o, n) -> { if (!updating && n != null) commitValue(n); });

                commandBox.setEditable(true);
                commandBox.setMaxWidth(Double.MAX_VALUE);
                commandBox.setPromptText("Type to search commands...");
                commandBox.getEditor().setOnAction(e -> commitValue(commandBox.getValue()));
                commandBox.valueProperty().addListener(
                        (obs, o, n) -> { if (!updating && n != null && !n.isBlank()) commitValue(n); });

                table.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
                    if (!isEmpty() && getItem() != null) updateItem(getItem(), false);
                });
            }

            private void commitValue(String value) {
                var mapping = editingMapping;
                if (mapping == null || value == null) return;
                poller.mappingStore().putMapping(new ControllerMapping(
                        mapping.inputId(), mapping.inputName(), mapping.actionType(), value));
            }

            private String normalise(String raw, String fallback) {
                var s = raw == null || raw.isBlank() ? fallback : raw;
                return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
            }

            @Override
            protected void updateItem(ControllerMapping mapping, boolean empty) {
                super.updateItem(mapping, empty);
                if (empty || mapping == null) { setGraphic(null); setText(null); return; }
                boolean selected = getTableView() != null &&
                        getTableView().getSelectionModel().getSelectedIndex() == getIndex();
                if (selected) {
                    editingMapping = mapping;
                    var type = mapping.actionType();
                    updating = true;
                    try {
                        if (type == ControllerMapping.ActionType.MOUSE_BUTTON) {
                            var val = normalise(mapping.actionValue(), "left");
                            mouseButtonBox.setValue(val);
                            setGraphic(mouseButtonBox);
                            if (mapping.actionValue().isBlank()) commitValue(val);
                        } else if (type == ControllerMapping.ActionType.MOUSE_WHEEL) {
                            var val = normalise(mapping.actionValue(), "up");
                            mouseWheelBox.setValue(val);
                            setGraphic(mouseWheelBox);
                            if (mapping.actionValue().isBlank()) commitValue(val);
                        } else if (type == ControllerMapping.ActionType.QUPATH_COMMAND) {
                            if (commandBox.getItems().isEmpty()) populateCommandBox(commandBox);
                            commandBox.setValue(mapping.actionValue());
                            setGraphic(commandBox);
                            var idx = getIndex();
                            javafx.application.Platform.runLater(() -> {
                                if (getTableView() != null &&
                                        getTableView().getSelectionModel().getSelectedIndex() == idx)
                                    commandBox.getEditor().requestFocus();
                            });
                        } else if (actionNeedsValue(type)) {
                            textField.setText(mapping.actionValue());
                            setGraphic(textField);
                        } else {
                            setGraphic(null);
                        }
                    } finally {
                        updating = false;
                    }
                    setText(null);
                } else {
                    setGraphic(null);
                    setText(mapping.actionValue().isBlank() ? "" : mapping.actionValue());
                }
            }
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void populateCommandBox(ComboBox<String> commandBox) {
        var scene = qupath.getStage().getScene();
        if (scene == null || scene.getRoot() == null) return;
        var commands = FXCollections.observableArrayList(
                InputActionExecutor.enumerateMenuCommands(scene.getRoot()));
        var filtered = new javafx.collections.transformation.FilteredList<>(commands, p -> true);
        commandBox.setItems(filtered);
        commandBox.getEditor().textProperty().addListener((obs, oldVal, text) -> {
            var lower = text == null ? "" : text.toLowerCase();
            filtered.setPredicate(c -> lower.isBlank() || c.toLowerCase().contains(lower));
            // Show dropdown when user is typing (not when value is set programmatically)
            if (text != null && !text.isBlank() && !text.equals(commandBox.getValue()))
                commandBox.show();
        });
    }

    private static List<ControllerMapping.ActionType> visibleActionTypes() {
        return List.of(
                ControllerMapping.ActionType.NONE,
                ControllerMapping.ActionType.KEY,
                ControllerMapping.ActionType.MOUSE_BUTTON,
                ControllerMapping.ActionType.MOUSE_WHEEL,
                ControllerMapping.ActionType.MOUSE_SHIFT_RIGHT_CLICK,
                ControllerMapping.ActionType.MOUSE_MOVE_X,
                ControllerMapping.ActionType.MOUSE_MOVE_Y,
                ControllerMapping.ActionType.CONTROLLER_TOGGLE_INPUT,
                ControllerMapping.ActionType.CONTROLLER_TOGGLE_PAN_SPEED,
                ControllerMapping.ActionType.QUPATH_PAN_X,
                ControllerMapping.ActionType.QUPATH_PAN_Y,
                ControllerMapping.ActionType.QUPATH_ZOOM_IN,
                ControllerMapping.ActionType.QUPATH_ZOOM_OUT,
                ControllerMapping.ActionType.QUPATH_TOOL_NEXT,
                ControllerMapping.ActionType.QUPATH_TOOL_PREVIOUS,
                ControllerMapping.ActionType.QUPATH_UNDO,
                ControllerMapping.ActionType.QUPATH_SHOW_ANNOTATIONS,
                ControllerMapping.ActionType.QUPATH_FILL_ANNOTATIONS,
                ControllerMapping.ActionType.QUPATH_SHOW_ANNOTATION_NAMES,
                ControllerMapping.ActionType.QUPATH_SHOW_DETECTIONS,
                ControllerMapping.ActionType.QUPATH_FILL_DETECTIONS,
                ControllerMapping.ActionType.QUPATH_COMMAND);
    }

    private static String valueTypeGroup(ControllerMapping.ActionType type) {
        if (type == null) return "none";
        return switch (type) {
            case KEY -> "text";
            case MOUSE_BUTTON -> "mouseButton";
            case MOUSE_WHEEL -> "mouseWheel";
            case QUPATH_COMMAND -> "command";
            default -> "none";
        };
    }

    private static boolean actionNeedsValue(ControllerMapping.ActionType type) {
        return type == ControllerMapping.ActionType.KEY;
    }
}
