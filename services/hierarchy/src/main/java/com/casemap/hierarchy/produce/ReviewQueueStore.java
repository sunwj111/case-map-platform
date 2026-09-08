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
import java.util.function.Consumer;

@Repository
public class ReviewQueueStore {

    private final ObjectMapper objectMapper;
    private final Path dataFile;
    private final Map<String, ReviewItem> items = new LinkedHashMap<>();

    public ReviewQueueStore(
            ObjectMapper objectMapper,
            @Value("${produce.review-file:data/produce/review_queue.json}") String dataFile
    ) {
        this.objectMapper = objectMapper;
        this.dataFile = Path.of(dataFile);
    }

    @PostConstruct
    public synchronized void init() throws IOException {
        if (!Files.exists(dataFile)) {
            return;
        }
        Map<String, Object> payload = objectMapper.readValue(dataFile.toFile(), new TypeReference<>() {
        });
        List<ReviewItem> loadedItems = objectMapper.convertValue(
                payload.get("items"),
                new TypeReference<>() {
                }
        );
        items.clear();
        if (loadedItems != null) {
            for (ReviewItem item : loadedItems) {
                items.put(item.getId(), item);
            }
        }
    }

    public synchronized List<ReviewItem> listAll() {
        return new ArrayList<>(items.values());
    }

    public synchronized Optional<ReviewItem> get(String reviewId) {
        return Optional.ofNullable(items.get(reviewId));
    }

    public synchronized void replaceBatchDraft(String batchId, List<ReviewItem> draftItems) {
        boolean hasReviewedItems = items.values().stream()
                .anyMatch(item -> batchId.equals(item.getBatchId())
                        && item.getStatus() != ReviewStatus.PENDING);
        if (hasReviewedItems) {
            throw new IllegalArgumentException("当前批次已有评审操作，不能重新生成评审队列");
        }
        items.values().removeIf(item -> batchId.equals(item.getBatchId()));
        for (ReviewItem item : draftItems) {
            items.put(item.getId(), item);
        }
        persist();
    }

    public synchronized ReviewItem update(String reviewId, Consumer<ReviewItem> updater) {
        ReviewItem item = items.get(reviewId);
        if (item == null) {
            throw new IllegalArgumentException("评审项不存在：" + reviewId);
        }
        updater.accept(item);
        item.setUpdatedAt(Instant.now().toString());
        items.put(reviewId, item);
        persist();
        return item;
    }

    public synchronized void updateMany(List<String> reviewIds, Consumer<ReviewItem> updater) {
        for (String reviewId : reviewIds) {
            ReviewItem item = items.get(reviewId);
            if (item == null) {
                throw new IllegalArgumentException("评审项不存在：" + reviewId);
            }
            updater.accept(item);
            item.setUpdatedAt(Instant.now().toString());
            items.put(reviewId, item);
        }
        persist();
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
            payload.put("items", new ArrayList<>(items.values()));
            Path temporaryFile = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), payload);
            Files.move(temporaryFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("保存评审队列失败", exception);
        }
    }
}
