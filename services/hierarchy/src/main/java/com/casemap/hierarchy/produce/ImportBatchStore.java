package com.casemap.hierarchy.produce;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Repository
public class ImportBatchStore {

    private final ObjectMapper objectMapper;
    private final Path dataFile;
    private final Map<String, ImportBatch> batches = new ConcurrentHashMap<>();

    public ImportBatchStore(
            ObjectMapper objectMapper,
            @Value("${produce.batch-file:data/produce/import_batches.json}") String dataFile
    ) {
        this.objectMapper = objectMapper;
        this.dataFile = Path.of(dataFile);
    }

    @PostConstruct
    public void init() throws IOException {
        if (Files.exists(dataFile)) {
            load();
        }
    }

    public Optional<ImportBatch> get(String batchId) {
        return Optional.ofNullable(batches.get(batchId));
    }

    public synchronized ImportBatch save(ImportBatch batch) {
        batches.put(batch.getId(), batch);
        persist();
        return batch;
    }

    public synchronized ImportBatch update(String batchId, Consumer<ImportBatch> updater) {
        ImportBatch batch = batches.get(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("导入批次不存在：" + batchId);
        }
        updater.accept(batch);
        batch.setUpdatedAt(Instant.now().toString());
        batch.refreshGate();
        batches.put(batchId, batch);
        persist();
        return batch;
    }

    private void load() throws IOException {
        Map<String, Object> payload = objectMapper.readValue(dataFile.toFile(), new TypeReference<>() {
        });
        List<ImportBatch> loadedBatches = objectMapper.convertValue(
                payload.get("batches"),
                new TypeReference<>() {
                }
        );
        batches.clear();
        if (loadedBatches != null) {
            for (ImportBatch batch : loadedBatches) {
                batch.refreshGate();
                batches.put(batch.getId(), batch);
            }
        }
    }

    private void persist() {
        try {
            Path parentDirectory = dataFile.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", 1);
            payload.put("updatedAt", Instant.now().toString());
            payload.put("batches", new ArrayList<>(batches.values()));
            Path temporaryFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), payload);
            Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("保存导入批次失败", exception);
        }
    }
}
