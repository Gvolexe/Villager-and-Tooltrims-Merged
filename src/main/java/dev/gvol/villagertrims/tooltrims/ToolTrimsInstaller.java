package dev.gvol.villagertrims.tooltrims;

import dev.gvol.villagertrims.PluginSettings;
import dev.gvol.villagertrims.VillagerToolTrimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class ToolTrimsInstaller {
    private final VillagerToolTrimsPlugin plugin;
    private final PluginSettings.ToolTrims settings;

    public ToolTrimsInstaller(VillagerToolTrimsPlugin plugin, PluginSettings.ToolTrims settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public InstallResult ensureInstalled(boolean force) {
        if (!this.settings.autoInstallDatapack()) {
            return InstallResult.DISABLED;
        }

        if (this.settings.datapackUrl().isBlank() || this.settings.datapackSha1().isBlank()) {
            this.plugin.getLogger().warning("Tool Trims datapack URL or SHA-1 is missing in config.yml.");
            return InstallResult.FAILED;
        }

        try {
            Path datapackPath = datapackDirectory().resolve(this.settings.datapackFilename());
            Files.createDirectories(datapackPath.getParent());

            if (!force && Files.exists(datapackPath) && sha1(datapackPath).equalsIgnoreCase(this.settings.datapackSha1())) {
                return InstallResult.ALREADY_CURRENT;
            }

            Path tempFile = Files.createTempFile("tool-trims-", ".zip");
            try {
                download(this.settings.datapackUrl(), tempFile);
                String actualSha1 = sha1(tempFile);
                if (!actualSha1.equalsIgnoreCase(this.settings.datapackSha1())) {
                    this.plugin.getLogger().warning("Downloaded Tool Trims datapack SHA-1 mismatch. Expected " + this.settings.datapackSha1() + " but got " + actualSha1 + ".");
                    return InstallResult.FAILED;
                }
                Files.move(tempFile, datapackPath, StandardCopyOption.REPLACE_EXISTING);
                return InstallResult.INSTALLED;
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException | NoSuchAlgorithmException exception) {
            this.plugin.getLogger().warning("Unable to install Tool Trims datapack: " + exception.getMessage());
            return InstallResult.FAILED;
        }
    }

    private Path datapackDirectory() {
        List<World> worlds = Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            return worlds.get(0).getWorldFolder().toPath().resolve("datapacks");
        }
        return Bukkit.getWorldContainer().toPath().resolve("world").resolve("datapacks");
    }

    private void download(String url, Path target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "VillagerToolTrims/" + this.plugin.getPluginMeta().getVersion());

        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new IOException("HTTP " + status + " from " + url);
        }

        try (InputStream inputStream = connection.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }
    }

    private String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream inputStream = Files.newInputStream(file);
             DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
            digestInputStream.transferTo(OutputStreamSink.INSTANCE);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public enum InstallResult {
        DISABLED,
        ALREADY_CURRENT,
        INSTALLED,
        FAILED
    }
}
