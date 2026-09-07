package com.casemap.hierarchy.tech;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class FlowNodeCatalog {

    private final QuoteFlowNodeFile file;

    public FlowNodeCatalog(ObjectMapper objectMapper) throws IOException {
        try (InputStream input = requiredResource("/data/quote_flow_nodes.json")) {
            this.file = objectMapper.readValue(input, QuoteFlowNodeFile.class);
        }
    }

    public List<QuoteFlowNode> listAll() {
        return Collections.unmodifiableList(file.getFlowNodes());
    }

    public String getSource() {
        return file.getSource();
    }

    public Optional<QuoteFlowNode> findByName(String nodeName) {
        if (nodeName == null || nodeName.isBlank()) {
            return Optional.empty();
        }
        return file.getFlowNodes().stream()
                .filter(node -> nodeName.equals(node.getNode()))
                .findFirst();
    }

    private static InputStream requiredResource(String path) {
        InputStream input = FlowNodeCatalog.class.getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("缺少资源文件：" + path);
        }
        return input;
    }
}
