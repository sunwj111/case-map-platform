package com.casemap.hierarchy.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CaseAssetStore {

    private final ObjectMapper objectMapper;
    private final Path runtimeFile;
    private final Map<String, CaseAsset> items = new LinkedHashMap<>();
    private final Map<String, CaseAsset> runtimeItems = new LinkedHashMap<>();
    private final String seedSource;

    public CaseAssetStore(ObjectMapper objectMapper) throws IOException {
        this(objectMapper, null);
    }

    @Autowired
    public CaseAssetStore(
            ObjectMapper objectMapper,
            @Value("${case-assets.runtime-file:data/produce/case_assets.json}") String runtimeFile
    ) throws IOException {
        this.objectMapper = objectMapper;
        this.runtimeFile = runtimeFile == null ? null : Path.of(runtimeFile);
        try (InputStream input = requiredResource("/data/case_assets.json")) {
            CaseAssetFile file = objectMapper.readValue(input, CaseAssetFile.class);
            this.seedSource = file.getSource();
            for (CaseAsset item : file.getItems()) {
                items.put(item.getId(), item);
            }
        }
        if (this.runtimeFile != null && Files.exists(this.runtimeFile)) {
            CaseAssetFile runtimeAssets = objectMapper.readValue(this.runtimeFile.toFile(), CaseAssetFile.class);
            for (CaseAsset item : runtimeAssets.getItems()) {
                runtimeItems.put(item.getId(), item);
                items.put(item.getId(), item);
            }
        }
    }

    public synchronized List<CaseAsset> listAll() {
        return Collections.unmodifiableList(new ArrayList<>(items.values()));
    }

    public String getSource() {
        return runtimeItems.isEmpty() ? seedSource : seedSource + " + 评审入库";
    }

    public synchronized void publish(List<CaseAsset> assets) {
        if (runtimeFile == null) {
            throw new IllegalStateException("正式资产运行时存储未配置");
        }
        for (CaseAsset asset : assets) {
            runtimeItems.put(asset.getId(), asset);
            items.put(asset.getId(), asset);
        }
        persistRuntimeAssets();
    }

    private void persistRuntimeAssets() {
        try {
            Path parentDirectory = runtimeFile.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
            CaseAssetFile file = new CaseAssetFile();
            file.setSource("评审入库正式资产");
            file.setItems(new ArrayList<>(runtimeItems.values()));
            Path temporaryFile = runtimeFile.resolveSibling(runtimeFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporaryFile.toFile(), file);
            Files.move(temporaryFile, runtimeFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("保存正式用例资产失败", exception);
        }
    }

    private static InputStream requiredResource(String path) {
        InputStream input = CaseAssetStore.class.getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("缺少资源文件：" + path);
        }
        return input;
    }
}
