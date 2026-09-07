package com.casemap.hierarchy.produce;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CaseFileParser {

    private static final List<String> CASE_ID_KEYS = List.of(
            "caseid", "用例id", "用例编号", "编号", "id"
    );
    private static final List<String> CASE_NAME_KEYS = List.of(
            "casename", "用例名称", "用例名", "name", "title", "标题", "用例"
    );
    private static final List<String> STEP_KEYS = List.of(
            "step", "steps", "步骤", "操作步骤", "测试步骤"
    );
    private static final List<String> EXPECTED_KEYS = List.of(
            "expectedresults", "expected", "预期结果", "期望结果", "预期", "期望"
    );
    private static final List<String> SCENE_KEYS = List.of(
            "scene", "场景", "业务场景", "流程节点", "节点"
    );
    private static final List<String> FEATURE_KEYS = List.of(
            "functionpoint", "feature", "功能点", "featurename", "功能", "模块功能"
    );
    private static final List<String> MODULE_KEYS = List.of(
            "module", "belongplatform", "模块", "平台", "所属模块", "belongmodule"
    );

    private final int maxCasesPerBatch;
    private final XMindFileParser xMindFileParser;

    public CaseFileParser(
            @Value("${produce.max-cases-per-batch:10000}") int maxCasesPerBatch,
            XMindFileParser xMindFileParser
    ) {
        this.maxCasesPerBatch = maxCasesPerBatch;
        this.xMindFileParser = xMindFileParser;
    }

    public CaseFileParseResult parse(String fileName, byte[] content) {
        validateFile(fileName, content);
        String extension = FileParsingSupport.extension(fileName);
        if ("csv".equals(extension) || "txt".equals(extension)) {
            return parseCsv(fileName, content);
        }
        if ("xlsx".equals(extension) || "xls".equals(extension)) {
            return parseWorkbook(fileName, content, extension);
        }
        if ("xmind".equals(extension)) {
            return parseXMind(fileName, content);
        }
        throw new IllegalArgumentException("暂不支持该用例文件格式：" + fileName);
    }

    private CaseFileParseResult parseXMind(String fileName, byte[] content) {
        XMindFileParser.XMindParseResult parsed = xMindFileParser.parse(content);
        if (parsed.caseRows().isEmpty()) {
            throw new IllegalArgumentException("XMind 中没有可导入的用例主题");
        }
        if (parsed.caseRows().size() > maxCasesPerBatch) {
            throw new IllegalArgumentException("单批次用例数不能超过 " + maxCasesPerBatch);
        }
        ImportFileMetadata metadata = new ImportFileMetadata(
                fileName,
                parsed.format(),
                content.length,
                FileParsingSupport.sha256(content),
                parsed.caseRows().size(),
                null,
                null
        );
        List<ImportRisk> risks = List.of(new ImportRisk(
                "GENERATED_XMIND_CASE_IDS",
                "XMind 不含标准用例 ID，已按主题顺序生成批次内临时 ID",
                null
        ));
        return new CaseFileParseResult(metadata, parsed.caseRows(), risks);
    }

    private CaseFileParseResult parseCsv(String fileName, byte[] content) {
        FileParsingSupport.DecodedText decodedText = FileParsingSupport.decodeText(content);
        List<ImportRisk> risks = new ArrayList<>();
        if (decodedText.risk() != null) {
            risks.add(decodedText.risk());
        }
        List<List<String>> matrix = parseCsvMatrix(decodedText.text());
        return parseMatrix(fileName, content, "csv", matrix, null, null, risks);
    }

    private CaseFileParseResult parseWorkbook(String fileName, byte[] content, String extension) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Excel 文件没有工作表");
            }
            Sheet firstSheet = workbook.getSheetAt(0);
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);
            List<List<String>> matrix = new ArrayList<>();
            for (int rowIndex = 0; rowIndex <= firstSheet.getLastRowNum(); rowIndex++) {
                Row row = firstSheet.getRow(rowIndex);
                List<String> values = new ArrayList<>();
                if (row != null) {
                    int lastCellNumber = Math.max(row.getLastCellNum(), 0);
                    for (int cellIndex = 0; cellIndex < lastCellNumber; cellIndex++) {
                        Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        values.add(cell == null ? "" : dataFormatter.formatCellValue(cell, formulaEvaluator));
                    }
                }
                matrix.add(values);
            }
            return parseMatrix(
                    fileName,
                    content,
                    extension,
                    matrix,
                    firstSheet.getSheetName(),
                    workbook.getNumberOfSheets(),
                    new ArrayList<>()
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析用例 Excel：" + exception.getMessage(), exception);
        }
    }

    private CaseFileParseResult parseMatrix(
            String fileName,
            byte[] content,
            String format,
            List<List<String>> matrix,
            String sheetName,
            Integer sheetCount,
            List<ImportRisk> risks
    ) {
        int firstContentRowIndex = -1;
        for (int rowIndex = 0; rowIndex < matrix.size(); rowIndex++) {
            if (hasContent(matrix.get(rowIndex))) {
                firstContentRowIndex = rowIndex;
                break;
            }
        }
        if (firstContentRowIndex < 0) {
            throw new IllegalArgumentException("用例文件为空");
        }

        List<String> firstRow = matrix.get(firstContentRowIndex);
        HeaderMapping mapping = buildHeaderMapping(firstRow);
        if (!mapping.hasHeader()) {
            risks.add(new ImportRisk(
                    "HEADER_NOT_DETECTED",
                    "未识别到表头，已按用例名称、步骤、预期、场景的默认列顺序解析",
                    firstContentRowIndex + 1
            ));
        } else {
            appendMissingColumnRisks(mapping, risks, firstContentRowIndex + 1);
        }

        int startIndex = mapping.hasHeader() ? firstContentRowIndex + 1 : firstContentRowIndex;
        List<ParsedCaseRow> parsedRows = new ArrayList<>();
        Set<String> originalCaseIds = new HashSet<>();
        for (int matrixIndex = startIndex; matrixIndex < matrix.size(); matrixIndex++) {
            if (parsedRows.size() >= maxCasesPerBatch) {
                throw new IllegalArgumentException("单批次用例数不能超过 " + maxCasesPerBatch);
            }
            List<String> cells = matrix.get(matrixIndex);
            if (!hasContent(cells)) {
                continue;
            }
            int sourceRowNumber = matrixIndex + 1;
            String caseName = cell(cells, mapping.caseNameIndex());
            if (caseName.isEmpty()) {
                risks.add(new ImportRisk("EMPTY_CASE_NAME", "已跳过用例名称为空的行", sourceRowNumber));
                continue;
            }

            String originalCaseId = cell(cells, mapping.caseIdIndex());
            if (originalCaseId.isEmpty()) {
                originalCaseId = "IMPORT-ROW-" + sourceRowNumber;
                risks.add(new ImportRisk(
                        "MISSING_ORIGINAL_CASE_ID",
                        "缺少原始用例 ID，已生成批次内临时 ID",
                        sourceRowNumber
                ));
            }
            if (!originalCaseIds.add(originalCaseId)) {
                risks.add(new ImportRisk(
                        "DUPLICATE_ORIGINAL_CASE_ID",
                        "原始用例 ID 重复：" + originalCaseId,
                        sourceRowNumber
                ));
            }

            parsedRows.add(new ParsedCaseRow(
                    originalCaseId,
                    caseName,
                    cell(cells, mapping.stepIndex()),
                    cell(cells, mapping.expectedIndex()),
                    cell(cells, mapping.sceneIndex()),
                    cell(cells, mapping.featureIndex()),
                    cell(cells, mapping.moduleIndex()),
                    sourceRowNumber
            ));
        }
        if (parsedRows.isEmpty()) {
            throw new IllegalArgumentException("用例文件中没有可导入的用例");
        }

        ImportFileMetadata metadata = new ImportFileMetadata(
                fileName,
                format,
                content.length,
                FileParsingSupport.sha256(content),
                parsedRows.size(),
                sheetName,
                sheetCount
        );
        return new CaseFileParseResult(metadata, List.copyOf(parsedRows), List.copyOf(risks));
    }

    private static HeaderMapping buildHeaderMapping(List<String> headers) {
        int caseNameIndex = findHeaderIndex(headers, CASE_NAME_KEYS);
        boolean hasHeader = caseNameIndex >= 0;
        if (!hasHeader) {
            return new HeaderMapping(false, -1, 0, 1, 2, 3, -1, -1);
        }
        return new HeaderMapping(
                true,
                findHeaderIndex(headers, CASE_ID_KEYS),
                caseNameIndex,
                findHeaderIndex(headers, STEP_KEYS),
                findHeaderIndex(headers, EXPECTED_KEYS),
                findHeaderIndex(headers, SCENE_KEYS),
                findHeaderIndex(headers, FEATURE_KEYS),
                findHeaderIndex(headers, MODULE_KEYS)
        );
    }

    private static int findHeaderIndex(List<String> headers, List<String> acceptedKeys) {
        List<String> normalizedKeys = acceptedKeys.stream().map(CaseFileParser::normalizeHeader).toList();
        for (int headerIndex = 0; headerIndex < headers.size(); headerIndex++) {
            String normalizedHeader = normalizeHeader(headers.get(headerIndex));
            if (normalizedKeys.contains(normalizedHeader)) {
                return headerIndex;
            }
        }
        for (int headerIndex = 0; headerIndex < headers.size(); headerIndex++) {
            String normalizedHeader = normalizeHeader(headers.get(headerIndex));
            if (normalizedHeader.length() < 2) {
                continue;
            }
            if (normalizedKeys.stream().anyMatch(key ->
                    key.length() >= 2 && (normalizedHeader.contains(key) || key.contains(normalizedHeader)))) {
                return headerIndex;
            }
        }
        return -1;
    }

    private static void appendMissingColumnRisks(
            HeaderMapping mapping,
            List<ImportRisk> risks,
            int headerRowNumber
    ) {
        if (mapping.caseIdIndex() < 0) {
            risks.add(new ImportRisk("MISSING_CASE_ID_COLUMN", "未识别原始用例 ID 列", headerRowNumber));
        }
        if (mapping.stepIndex() < 0) {
            risks.add(new ImportRisk("MISSING_STEP_COLUMN", "未识别测试步骤列", headerRowNumber));
        }
        if (mapping.expectedIndex() < 0) {
            risks.add(new ImportRisk("MISSING_EXPECTED_COLUMN", "未识别预期结果列", headerRowNumber));
        }
        if (mapping.sceneIndex() < 0) {
            risks.add(new ImportRisk("MISSING_SCENE_COLUMN", "未识别场景列", headerRowNumber));
        }
        if (mapping.featureIndex() < 0) {
            risks.add(new ImportRisk("MISSING_FEATURE_COLUMN", "未识别功能点列", headerRowNumber));
        }
    }

    private static List<List<String>> parseCsvMatrix(String text) {
        List<List<String>> matrix = new ArrayList<>();
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean insideQuotes = false;

        for (int characterIndex = 0; characterIndex < text.length(); characterIndex++) {
            char character = text.charAt(characterIndex);
            if (character == '"') {
                if (insideQuotes && characterIndex + 1 < text.length() && text.charAt(characterIndex + 1) == '"') {
                    currentCell.append('"');
                    characterIndex++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (character == ',' && !insideQuotes) {
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
            } else if ((character == '\n' || character == '\r') && !insideQuotes) {
                if (character == '\r' && characterIndex + 1 < text.length() && text.charAt(characterIndex + 1) == '\n') {
                    characterIndex++;
                }
                currentRow.add(currentCell.toString());
                currentCell.setLength(0);
                matrix.add(currentRow);
                currentRow = new ArrayList<>();
            } else {
                currentCell.append(character);
            }
        }
        if (insideQuotes) {
            throw new IllegalArgumentException("CSV 文件存在未闭合的引号");
        }
        if (currentCell.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentCell.toString());
            matrix.add(currentRow);
        }
        return matrix;
    }

    private static String cell(List<String> cells, int index) {
        return index < 0 || index >= cells.size() ? "" : FileParsingSupport.compact(cells.get(index));
    }

    private static boolean hasContent(List<String> cells) {
        return cells.stream().anyMatch(value -> !FileParsingSupport.compact(value).isEmpty());
    }

    private static String normalizeHeader(String value) {
        return FileParsingSupport.compact(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-]", "");
    }

    private static void validateFile(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("用例文件为空");
        }
    }

    private record HeaderMapping(
            boolean hasHeader,
            int caseIdIndex,
            int caseNameIndex,
            int stepIndex,
            int expectedIndex,
            int sceneIndex,
            int featureIndex,
            int moduleIndex
    ) {
    }
}
