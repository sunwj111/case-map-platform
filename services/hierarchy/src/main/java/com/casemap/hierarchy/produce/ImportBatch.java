package com.casemap.hierarchy.produce;

import java.util.ArrayList;
import java.util.List;

public class ImportBatch {

    private String id;
    private String domain;
    private String system;
    private ProductionMode mode;
    private ImportBatchStatus status;
    private boolean casesRequired;
    private boolean knowledgeRequired;
    private boolean casesImported;
    private boolean knowledgeImported;
    private boolean keywordsConfirmed;
    private boolean mapDraftGenerated;
    private boolean readyForKeywordCalibration;
    private boolean readyForMapDraft;
    private List<String> missingInputs = new ArrayList<>();
    private CaseFileParseResult caseImport;
    private KnowledgeFileParseResult knowledgeImport;
    private KnowledgeExtractionResult keywordExtraction;
    private String createdAt;
    private String updatedAt;

    public void refreshGate() {
        casesRequired = true;
        knowledgeRequired = mode == ProductionMode.STANDARD;
        readyForKeywordCalibration = casesImported && (!knowledgeRequired || knowledgeImported);
        readyForMapDraft = readyForKeywordCalibration && keywordsConfirmed;
        missingInputs = new ArrayList<>();
        if (!casesImported) {
            missingInputs.add("cases");
        }
        if (knowledgeRequired && !knowledgeImported) {
            missingInputs.add("knowledge");
        }

        if (mapDraftGenerated) {
            status = ImportBatchStatus.MAP_DRAFT_GENERATED;
        } else if (readyForMapDraft) {
            status = ImportBatchStatus.READY_FOR_MAP_DRAFT;
        } else if (readyForKeywordCalibration) {
            status = ImportBatchStatus.READY_FOR_KEYWORD_CALIBRATION;
        } else if (!casesImported && knowledgeRequired && !knowledgeImported) {
            status = ImportBatchStatus.WAITING_FOR_CASES_AND_KNOWLEDGE;
        } else if (!casesImported) {
            status = ImportBatchStatus.WAITING_FOR_CASES;
        } else {
            status = ImportBatchStatus.WAITING_FOR_KNOWLEDGE;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public ProductionMode getMode() {
        return mode;
    }

    public void setMode(ProductionMode mode) {
        this.mode = mode;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public void setStatus(ImportBatchStatus status) {
        this.status = status;
    }

    public boolean isCasesRequired() {
        return casesRequired;
    }

    public void setCasesRequired(boolean casesRequired) {
        this.casesRequired = casesRequired;
    }

    public boolean isKnowledgeRequired() {
        return knowledgeRequired;
    }

    public void setKnowledgeRequired(boolean knowledgeRequired) {
        this.knowledgeRequired = knowledgeRequired;
    }

    public boolean isCasesImported() {
        return casesImported;
    }

    public void setCasesImported(boolean casesImported) {
        this.casesImported = casesImported;
    }

    public boolean isKnowledgeImported() {
        return knowledgeImported;
    }

    public void setKnowledgeImported(boolean knowledgeImported) {
        this.knowledgeImported = knowledgeImported;
    }

    public boolean isKeywordsConfirmed() {
        return keywordsConfirmed;
    }

    public void setKeywordsConfirmed(boolean keywordsConfirmed) {
        this.keywordsConfirmed = keywordsConfirmed;
    }

    public boolean isMapDraftGenerated() {
        return mapDraftGenerated;
    }

    public void setMapDraftGenerated(boolean mapDraftGenerated) {
        this.mapDraftGenerated = mapDraftGenerated;
    }

    public boolean isReadyForKeywordCalibration() {
        return readyForKeywordCalibration;
    }

    public void setReadyForKeywordCalibration(boolean readyForKeywordCalibration) {
        this.readyForKeywordCalibration = readyForKeywordCalibration;
    }

    public boolean isReadyForMapDraft() {
        return readyForMapDraft;
    }

    public void setReadyForMapDraft(boolean readyForMapDraft) {
        this.readyForMapDraft = readyForMapDraft;
    }

    public List<String> getMissingInputs() {
        return missingInputs;
    }

    public void setMissingInputs(List<String> missingInputs) {
        this.missingInputs = missingInputs == null ? new ArrayList<>() : new ArrayList<>(missingInputs);
    }

    public CaseFileParseResult getCaseImport() {
        return caseImport;
    }

    public void setCaseImport(CaseFileParseResult caseImport) {
        this.caseImport = caseImport;
    }

    public KnowledgeFileParseResult getKnowledgeImport() {
        return knowledgeImport;
    }

    public void setKnowledgeImport(KnowledgeFileParseResult knowledgeImport) {
        this.knowledgeImport = knowledgeImport;
    }

    public KnowledgeExtractionResult getKeywordExtraction() {
        return keywordExtraction;
    }

    public void setKeywordExtraction(KnowledgeExtractionResult keywordExtraction) {
        this.keywordExtraction = keywordExtraction;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
