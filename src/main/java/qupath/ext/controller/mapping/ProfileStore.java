package qupath.ext.controller.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProfileStore {

    private static final Logger logger = LoggerFactory.getLogger(ProfileStore.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final TypeReference<List<ControllerProfile>> PROFILE_LIST_TYPE = new TypeReference<>() {};

    private static final StringProperty profilesPref =
            PathPrefs.createPersistentPreference("controller.profiles", "[]");
    private static final StringProperty activeProfilePref =
            PathPrefs.createPersistentPreference("controller.activeProfile", null);

    public String getActiveProfileName() {
        return activeProfilePref.get();
    }

    public void setActiveProfileName(String name) {
        activeProfilePref.set(name);
    }

    public List<String> listProfiles() {
        var names = new ArrayList<>(readAll().stream()
                .map(ControllerProfile::name)
                .toList());
        if (names.remove("Default"))
            names.add(0, "Default");
        return Collections.unmodifiableList(names);
    }

    public static final int MAX_PROFILES = 4;

    public void save(ControllerProfile profile) {
        if (!isValidName(profile.name())) {
            logger.warn("Refusing to save profile with invalid name '{}'", profile.name());
            return;
        }
        var profiles = new ArrayList<>(readAll());
        profiles.removeIf(p -> p.name().equals(profile.name()));
        if (profiles.size() >= MAX_PROFILES) {
            logger.warn("Maximum of {} profiles reached", MAX_PROFILES);
            return;
        }
        profiles.add(profile);
        writeAll(profiles);
    }

    public ControllerProfile load(String name) {
        if (!isValidName(name)) {
            logger.warn("Refusing to load profile with invalid name '{}'", name);
            return null;
        }
        return readAll().stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public void delete(String name) {
        if (!isValidName(name) || "Default".equals(name)) {
            logger.warn("Refusing to delete profile with invalid name '{}'", name);
            return;
        }
        var profiles = new ArrayList<>(readAll());
        profiles.removeIf(p -> p.name().equals(name));
        writeAll(profiles);
        if (name.equals(getActiveProfileName()))
            setActiveProfileName("Default");
    }

    private static List<ControllerProfile> readAll() {
        try {
            return mapper.readValue(profilesPref.get(), PROFILE_LIST_TYPE);
        } catch (Exception e) {
            logger.debug("Cannot read profiles from preferences, treating as empty", e);
            return List.of();
        }
    }

    private static void writeAll(List<ControllerProfile> profiles) {
        try {
            profilesPref.set(mapper.writeValueAsString(profiles));
        } catch (Exception e) {
            logger.error("Failed to save profiles", e);
        }
    }

    private static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.equals(name.strip());
    }
}
