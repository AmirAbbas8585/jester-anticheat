package ac.jester.anticheat.manager.config;

import ac.jester.anticheat.GrimAPI;
import ac.grim.grimac.api.common.BasicReloadable;
import ac.grim.grimac.api.config.ConfigManager;
import ac.jester.anticheat.utils.anticheat.LogUtil;
import github.scarsz.configuralize.DynamicConfig;
import github.scarsz.configuralize.Language;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ConfigManagerFileImpl implements ConfigManager, BasicReloadable {
    private final DynamicConfig config;
    private boolean initialized = false;

    public ConfigManagerFileImpl() {
        config = new DynamicConfig();
    }

    private File getConfigFile(String path) {
        return new File(GrimAPI.INSTANCE.getGrimPlugin().getDataFolder(), path);
    }

    @Override
    public void reload() {
        GrimAPI.INSTANCE.getGrimPlugin().getDataFolder().mkdirs();
        if (!initialized) {
            initialized = true;
            config.addSource(GrimAPI.class, "config", getConfigFile("config.yml"));
            config.addSource(GrimAPI.class, "messages", getConfigFile("messages.yml"));
        }

        String languageCode = System.getProperty("user.language").toUpperCase();

        try {
            config.setLanguage(Language.valueOf(languageCode));
        } catch (IllegalArgumentException ignored) {
        }

        if (!config.isLanguageAvailable(config.getLanguage())) {
            String lang = languageCode.toUpperCase();
            LogUtil.info("Unknown user language " + lang + ".");
            LogUtil.info("If you fluently speak " + lang + " as well as English, see the GitHub repo to translate it!");
            config.setLanguage(Language.EN);
        }

        try {
            config.saveAllDefaults(false);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save default config files", e);
        }

        java.util.Map<String, Object> movedToMessages = ConfigMigrator.collectRelocated(
                getConfigFile("config.yml"), "messages.yml");
        ConfigMigrator.migrate(getConfigFile("config.yml"), "config/en.yml");
        ConfigMigrator.migrate(getConfigFile("messages.yml"), "messages/en.yml", movedToMessages);

        try {
            config.loadAll();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    @Override
    public String getStringElse(String key, String otherwise) {
        return config.getStringElse(key, otherwise);
    }

    @Override
    public @Nullable String getString(String key) {
        return config.getString(key);
    }

    @Override
    public List<String> getStringList(String key) {
        return config.getStringList(key);
    }

    @Override
    public List<String> getStringListElse(String key, List<String> otherwise) {
        return config.getStringListElse(key, otherwise);
    }

    @Override
    public int getIntElse(String key, int other) {
        return config.getIntElse(key, other);
    }

    @Override
    public long getLongElse(String key, long otherwise) {
        return config.getLongElse(key, otherwise);
    }

    @Override
    public double getDoubleElse(String key, double otherwise) {
        return config.getDoubleElse(key, otherwise);
    }

    @Override
    public boolean getBooleanElse(String key, boolean otherwise) {
        return config.getBooleanElse(key, otherwise);
    }

    @Override
    public <T> T get(String key) {
        return config.get(key);
    }

    @Override
    public <T> @Nullable T getElse(String key, T otherwise) {
        return config.getElse(key, otherwise);
    }

    @Override
    public <K, V> Map<K, V> getMap(String key) {
        return config.getMap(key);
    }

    @Override
    public @Nullable <K, V> Map<K, V> getMapElse(String s, Map<K, V> map) {
        return config.getMapElse(s, map);
    }

    @Override
    public @Nullable <T> List<T> getList(String path) {
        return config.getList(path);
    }

    @Override
    public @Nullable <T> List<T> getListElse(String path, List<T> otherwise) {
        return config.getListElse(path, otherwise);
    }

    @Override
    public boolean hasLoaded() {
        return initialized;
    }
}
