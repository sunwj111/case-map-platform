package com.casemap.hierarchy.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class CaseAssetStore {

    private final List<CaseAsset> items;
    private final String source;

    public CaseAssetStore(ObjectMapper objectMapper) throws IOException {
        try (InputStream input = requiredResource("/data/case_assets.json")) {
            CaseAssetFile file = objectMapper.readValue(input, CaseAssetFile.class);
            this.source = file.getSource();
            this.items = Collections.unmodifiableList(new ArrayList<>(file.getItems()));
        }
    }

    public List<CaseAsset> listAll() {
        return items;
    }

    public String getSource() {
        return source;
    }

    private static InputStream requiredResource(String path) {
        InputStream input = CaseAssetStore.class.getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("缺少资源文件：" + path);
        }
        return input;
    }
}
