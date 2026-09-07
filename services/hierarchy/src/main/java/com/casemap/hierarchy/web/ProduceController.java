package com.casemap.hierarchy.web;

import com.casemap.hierarchy.produce.CreateImportBatchRequest;
import com.casemap.hierarchy.produce.CaseFileParseResult;
import com.casemap.hierarchy.produce.ImportBatch;
import com.casemap.hierarchy.produce.KnowledgeFileParseResult;
import com.casemap.hierarchy.produce.KnowledgeExtractionResult;
import com.casemap.hierarchy.produce.KnowledgeTextRequest;
import com.casemap.hierarchy.produce.ProduceBatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/produce/batches")
public class ProduceController {

    private final ProduceBatchService produceBatchService;

    public ProduceController(ProduceBatchService produceBatchService) {
        this.produceBatchService = produceBatchService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportBatch create(@Valid @RequestBody CreateImportBatchRequest request) {
        return produceBatchService.create(request);
    }

    @GetMapping("/{batchId}")
    public ImportBatch get(@PathVariable String batchId) {
        return produceBatchService.find(batchId)
                .orElseThrow(() -> new NotFoundException("导入批次不存在：" + batchId));
    }

    @PostMapping("/{batchId}/cases")
    public CaseFileParseResult importCases(
            @PathVariable String batchId,
            @RequestPart("file") MultipartFile file
    ) {
        ensureBatchExists(batchId);
        return produceBatchService.importCases(batchId, originalFileName(file), readContent(file));
    }

    @PostMapping("/{batchId}/knowledge")
    public KnowledgeFileParseResult importKnowledge(
            @PathVariable String batchId,
            @RequestPart("file") MultipartFile file
    ) {
        ensureBatchExists(batchId);
        return produceBatchService.importKnowledge(batchId, originalFileName(file), readContent(file));
    }

    @PostMapping("/{batchId}/knowledge/extract")
    public KnowledgeExtractionResult extractKnowledge(@PathVariable String batchId) {
        ensureBatchExists(batchId);
        return produceBatchService.extractKnowledge(batchId);
    }

    @PostMapping("/{batchId}/knowledge/text")
    public KnowledgeExtractionResult importKnowledgeText(
            @PathVariable String batchId,
            @Valid @RequestBody KnowledgeTextRequest request
    ) {
        ensureBatchExists(batchId);
        return produceBatchService.importKnowledgeText(
                batchId,
                request.getSourceName(),
                request.getText()
        );
    }

    private void ensureBatchExists(String batchId) {
        if (produceBatchService.find(batchId).isEmpty()) {
            throw new NotFoundException("导入批次不存在：" + batchId);
        }
    }

    private static String originalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        return originalFileName;
    }

    private static byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("读取上传文件失败", exception);
        }
    }
}
