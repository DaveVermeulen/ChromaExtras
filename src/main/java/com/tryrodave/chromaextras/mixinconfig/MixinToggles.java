package com.tryrodave.chromaextras.mixinconfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * On/off switches for every ChromaExtras mixin, backed by {@code config/chromaextras-mixins.properties}.
 *
 * <p>
 * Keys default to {@code true} (all fixes enabled) and are written to the file the first time they are seen, so after
 * one launch the file lists every available toggle. Multi-class features share one key (disabling half of a feature
 * would break the other half), via {@link #FEATURE_KEYS}; accessor mixins are always applied - they only expose
 * members and change no behaviour, and the code of enabled mixins depends on them.
 *
 * <p>
 * Loaded during the mixin bootstrap (before Minecraft classes exist), so this class must only use plain Java,
 * launchwrapper and log4j.
 */
public final class MixinToggles {

    static final Logger LOG = LogManager.getLogger("ChromaExtras");

    /** Master switch: when false, every ChromaExtras mixin is skipped regardless of individual keys. */
    private static final String MASTER_KEY = "enable-all-mixins";

    /** Mixin short name -> shared feature key, for features spanning several mixin classes. */
    private static final Map<String, String> FEATURE_KEYS = new HashMap<>();

    static {
        FEATURE_KEYS.put("MixinMeteoriteSpawn", "ae2-surface-meteorites");
        FEATURE_KEYS.put("MixinMeteoritePlacer", "ae2-surface-meteorites");
        FEATURE_KEYS.put("MixinReikaImageLoader", "dragonapi-resourcepack-crash-fix");
        FEATURE_KEYS.put("MixinReikaTextureHelper", "dragonapi-resourcepack-crash-fix");
    }

    private static final Properties PROPS = new Properties();
    private static File file;
    private static boolean loaded;

    private MixinToggles() {}

    /** The config key controlling a mixin class, e.g. {@code MixinMeteoriteSpawn -> ae2-surface-meteorites}. */
    public static String keyFor(String mixinShortName) {
        String feature = FEATURE_KEYS.get(mixinShortName);
        return feature != null ? feature : mixinShortName;
    }

    /**
     * Whether the given mixin class (short name, e.g. {@code MixinMeteoriteSpawn}) should apply. Unknown keys are
     * registered as {@code true} and persisted so the config file self-documents.
     */
    public static synchronized boolean isEnabled(String mixinShortName) {
        load();
        // Accessors expose members only; enabled mixins' code links against them, so they are not toggleable.
        if (mixinShortName.startsWith("Accessor") || mixinShortName.endsWith("Accessor")) {
            return true;
        }
        if (!Boolean.parseBoolean(PROPS.getProperty(MASTER_KEY, "true"))) {
            return false;
        }
        String key = keyFor(mixinShortName);
        String value = PROPS.getProperty(key);
        if (value == null) {
            PROPS.setProperty(key, "true");
            save();
            return true;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File home = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
        File configDir = new File(home, "config");
        // noinspection ResultOfMethodCallIgnored
        configDir.mkdirs();
        file = new File(configDir, "chromaextras-mixins.properties");
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                PROPS.load(in);
            } catch (IOException e) {
                LOG.error("Could not read " + file + "; all mixins stay enabled", e);
            }
        }
        if (PROPS.getProperty(MASTER_KEY) == null) {
            PROPS.setProperty(MASTER_KEY, "true");
            save();
        }
        LOG.info(
            "Mixin toggle config: {} ({} keys, {}={})",
            file,
            PROPS.size(),
            MASTER_KEY,
            PROPS.getProperty(MASTER_KEY));
    }

    private static void save() {
        if (file == null) {
            return;
        }
        // Properties has no stable ordering; write through a TreeMap-sorted copy for a diff-friendly file.
        Properties sorted = new Properties() {

            @Override
            public synchronized java.util.Enumeration<Object> keys() {
                return java.util.Collections.enumeration(new TreeMap<>(MixinToggles.PROPS).keySet());
            }
        };
        sorted.putAll(PROPS);
        try (OutputStream out = new FileOutputStream(file)) {
            sorted.store(
                out,
                "ChromaExtras mixin toggles. true = fix enabled (default), false = fix disabled.\n"
                    + "'enable-all-mixins' is the master switch. Multi-class features share one key\n"
                    + "(e.g. ae2-surface-meteorites). Keys appear here after the first launch that uses them.");
        } catch (IOException e) {
            LOG.error("Could not write " + file, e);
        }
    }
}
