package com.earmo.onlyoffice.integration.storage.local;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.storage.DocumentStorageStrategy;
import com.earmo.onlyoffice.integration.storage.StorageProvider;
import com.earmo.onlyoffice.integration.storage.StorageWriteRequest;
import com.earmo.onlyoffice.integration.storage.StoredObjectResource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;

/**
 * local provider 兼容实现。
 *
 * <p>虽然 Phase 2 之后 local 不再是正式默认策略，但它仍然保留给 dev/test 和迁移过渡场景使用。
 */
@Component
@RequiredArgsConstructor
public class LocalDocumentStorageStrategy implements DocumentStorageStrategy {

    private final OnlyofficeIntegrationProperties onlyofficeIntegrationProperties;

    @Override
    public StorageProvider provider() {
        return StorageProvider.LOCAL;
    }

    @Override
    public boolean exists(String storageKey) throws IOException {
        return Files.exists(resolvePath(storageKey));
    }

    @Override
    public StoredObjectResource read(String storageKey) throws IOException {
        Path path = resolvePath(storageKey);
        if (!Files.exists(path)) {
            throw new IOException("存储对象不存在：" + storageKey);
        }
        return toResource(storageKey, path);
    }

    @Override
    public StoredObjectResource writeNew(StorageWriteRequest request) throws IOException {
        Path path = resolvePath(request.storageKey());
        Files.createDirectories(path.getParent());
        Files.write(path, request.body(), StandardOpenOption.CREATE_NEW);
        return toResource(request.storageKey(), path);
    }

    @Override
    public StoredObjectResource overwrite(StorageWriteRequest request) throws IOException {
        Path path = resolvePath(request.storageKey());
        Files.createDirectories(path.getParent());
        Path tempFile = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Files.write(tempFile, request.body(), StandardOpenOption.CREATE_NEW);
        try {
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
        }
        return toResource(request.storageKey(), path);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolvePath(storageKey));
    }

    private StoredObjectResource toResource(String storageKey, Path path) throws IOException {
        byte[] body = Files.readAllBytes(path);
        String contentType = Files.probeContentType(path);
        Instant lastModified = Files.getLastModifiedTime(path).toInstant();
        return new StoredObjectResource(storageKey, contentType, body, body.length, lastModified, path);
    }

    private Path resolvePath(String storageKey) throws IOException {
        Path root = onlyofficeIntegrationProperties.getStorage().getLocal().getRoot();
        Files.createDirectories(root);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("非法存储路径：" + storageKey);
        }
        return resolved;
    }
}
