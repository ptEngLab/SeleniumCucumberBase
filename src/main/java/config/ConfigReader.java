package config;

import lombok.Getter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Getter
public class ConfigReader {
    private static final Logger logger = LogManager.getLogger(ConfigReader.class);

    private final Properties properties;

    // Private constructor for singleton
    private ConfigReader() {
        properties = new Properties();
        loadProperties();
    }

    private static class Holder {
        private static final ConfigReader INSTANCE = new ConfigReader();
    }

    public static ConfigReader getInstance() {
        return Holder.INSTANCE;
    }

    // Load properties from a file
    private void loadProperties() {
        // Allow system property to override for flexibility
        String configFilePath = System.getProperty("config.file",
                System.getProperty("user.dir") + File.separator + "lib" +
                        File.separator + "config" + File.separator + "config.properties");

        try (FileInputStream ip = new FileInputStream(configFilePath)) {
            properties.load(ip);
            logger.info("Configuration loaded successfully from: {}", configFilePath);
        } catch (IOException e) {
            logger.warn("Could not load config file: {} - Using empty/default properties", configFilePath);
        }
    }

    // Convenience getters with defaults
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Property {} is not a valid integer: {}. Using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }
}
