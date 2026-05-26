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
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private static final StringProperty activeProfilePref =
            PathPrefs.createPersistentPreference("controller.activeProfile", null);
    private static final StringProperty profileNamesPref =
            PathPrefs.createPersistentPreference("controller.profileNames", "[]");
    private static final List<StringProperty> profileSlots = List.of(
            PathPrefs.createPersistentPreference("controller.profile.0", null),
            PathPrefs.createPersistentPreference("controller.profile.1", null),
            PathPrefs.createPersistentPreference("controller.profile.2", null),
            PathPrefs.createPersistentPreference("controller.profile.3", null));

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
            var names = mapper.readValue(profileNamesPref.get(), STRING_LIST_TYPE);
            var result = new ArrayList<ControllerProfile>();
            for (int i = 0; i < names.size() && i < profileSlots.size(); i++) {
                var json = profileSlots.get(i).get();
                if (json != null) {
                    try {
                        result.add(mapper.readValue(json, ControllerProfile.class));
                    } catch (Exception e) {
                        logger.warn("Cannot parse stored profile '{}', skipping", names.get(i));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            logger.debug("Cannot read profiles, treating as empty", e);
            return List.of();
        }
    }

    private static void writeAll(List<ControllerProfile> profiles) {
        try {
            for (int i = 0; i < profileSlots.size(); i++)
                profileSlots.get(i).set(i < profiles.size() ? mapper.writeValueAsString(profiles.get(i)) : null);
            profileNamesPref.set(mapper.writeValueAsString(
                    profiles.stream().map(ControllerProfile::name).toList()));
        } catch (Exception e) {
            logger.error("Failed to save profiles", e);
        }
    }

    private static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.equals(name.strip());
    }
}
