package com.jpm.hrms.document.storage;

import com.jpm.hrms.config.AppProperties;
import com.jpm.hrms.document.CandidateDocument.Storage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Files in a private folder on the server (UPLOAD_DIR), e.g. {@code 65/3f2c...e1.enc}. The folder must not be
 * served by the web server, must be persistent, and must be included in backups (backup-db.sh archives it).
 */
@Component
public class LocalFileStorage implements FileStorage {
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");
    private final Path root;

    public LocalFileStorage(AppProperties props) {
        this.root = Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    @Override
    public Storage kind() {
        return Storage.LOCAL;
    }

    @Override
    public String store(String folder, String suffix, byte[] content) throws IOException {
        if (!SAFE.matcher(folder).matches() || !SAFE.matcher(suffix).matches()) throw new IOException("Invalid storage name");
        String key = folder + "/" + UUID.randomUUID() + "." + suffix;
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return key;
    }

    @Override
    public byte[] read(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    @Override
    public boolean exists(String key) {
        try {
            return Files.isReadable(resolve(key));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    /** Rejects any key that would leave the storage folder (e.g. "../"). */
    private Path resolve(String key) throws IOException {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IOException("Invalid storage key");
        return p;
    }
}
