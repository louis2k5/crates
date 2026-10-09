package org.bcp.crates;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Writes the plugin's data files: the text is produced on the server thread, the disk write happens in the background. */
public final class Storage {
    private final CratesPlugin plugin;
    private final Object lock = new Object();
    private final Map<String, Long> newest = new HashMap<>();
    private final Map<String, Long> written = new HashMap<>();

    public Storage(CratesPlugin plugin) {
        this.plugin = plugin;
    }

    public File file(String name) {
        return new File(this.plugin.getDataFolder(), name);
    }

    /**
     * Reads a data file.
     *
     * @return null when the file does not exist yet
     * @throws InvalidConfigurationException when the file exists but cannot be read. The caller must then leave
     *         everything alone: carrying on with "no data" would overwrite the file with an empty one later.
     */
    public YamlConfiguration read(String name) throws InvalidConfigurationException {
        File file = file(name);
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException | RuntimeException ex) {
            throw new InvalidConfigurationException(name + " could not be read: " + ex.getMessage(), ex);
        }
        return yaml;
    }

    /** @param now true while the plugin shuts down: write before returning */
    public void write(String name, String content, boolean now) {
        long seq;
        synchronized (this.lock) {
            seq = this.newest.merge(name, 1L, Long::sum);
        }
        // a plugin that is shutting down may not start background tasks any more: write directly then
        if (now || !this.plugin.isEnabled()) {
            doWrite(name, content, seq);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> doWrite(name, content, seq));
        }
    }

    private void doWrite(String name, String content, long seq) {
        synchronized (this.lock) {
            if (seq < this.written.getOrDefault(name, 0L)) {
                return; // a newer version is already on disk
            }
            this.written.put(name, seq);
            try {
                File folder = this.plugin.getDataFolder();
                if (!folder.exists() && !folder.mkdirs()) {
                    this.plugin.getLogger().warning("Could not create the plugin data folder.");
                    return;
                }
                Path target = file(name).toPath();
                Path tmp = target.resolveSibling(name + ".tmp");
                Files.writeString(tmp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                this.plugin.getLogger().severe("Could not save " + name + ": " + ex.getMessage());
            }
        }
    }
}
