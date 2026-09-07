package com.casemap.hierarchy.produce;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class KnowledgeFileParser {

    private final XMindFileParser xMindFileParser;

    public KnowledgeFileParser(XMindFileParser xMindFileParser) {
        this.xMindFileParser = xMindFileParser;
    }

    public KnowledgeFileParseResult parse(String fileName, byte[] content) {
        validateFile(fileName, content);
        String extension = FileParsingSupport.extension(fileName);
        if ("txt".equals(extension) || "md".equals(extension) || "markdown".equals(extension)) {
            return parseText(fileName, content, extension);
        }
        if ("xlsx".equals(extension) || "xls".equals(extension)) {
            return parseWorkbook(fileName, content, extension);
        }
        if ("xmind".equals(extension)) {
            return parseXMind(fileName, content);
        }
        throw new IllegalArgumentException("暂不支持该知识库文件格式：" + fileName);
    }

    private KnowledgeFileParseResult parseXMind(String fileName, byte[] content) {
        XMindFileParser.XMindParseResult parsed = xMindFileParser.parse(content);
        ImportFileMetadata metadata = new ImportFileMetadata(
                fileName,
                parsed.format(),
                content.length,
                FileParsingSupport.sha256(content),
                parsed.nodeCount(),
                null,
                null
        );
        return new KnowledgeFileParseResult(
                metadata,
                parsed.knowledgeText(),
                countLines(parsed.knowledgeText()),
                List.of()
        );
    }

    private KnowledgeFileParseResult parseText(String fileName, byte[] content, String extension) {
        FileParsingSupport.DecodedText decodedText = FileParsingSupport.decodeText(content);
        String text = decodedText.text().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("知识库文件为空");
        }
        List<ImportRisk> risks = decodedText.risk() == null
                ? List.of()
                : List.of(decodedText.risk());
        ImportFileMetadata metadata = new ImportFileMetadata(
                fileName,
                extension,
                content.length,
                FileParsingSupport.sha256(content),
                null,
                null,
                null
        );
        return new KnowledgeFileParseResult(metadata, text, countLines(text), risks);
    }

    private KnowledgeFileParseResult parseWorkbook(String fileName, byte[] content, String extension) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("知识库 Excel 没有工作表");
            }
            Sheet firstSheet = workbook.getSheetAt(0);
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);
            List<String> knowledgeLines = new ArrayList<>();
            for (int rowIndex = 0; rowIndex <= firstSheet.getLastRowNum(); rowIndex++) {
                Row row = firstSheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                List<String> cellValues = new ArrayList<>();
                int lastCellNumber = Math.max(row.getLastCellNum(), 0);
                for (int cellIndex = 0; cellIndex < lastCellNumber; cellIndex++) {
                    Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : FileParsingSupport.compact(
                            dataFormatter.formatCellValue(cell, formulaEvaluator)
                    );
                    if (!value.isEmpty()) {
                        cellValues.add(value);
                    }
                }
                if (!cellValues.isEmpty()) {
                    knowledgeLines.add("- " + String.join("、", cellValues));
                }
            }
            if (knowledgeLines.isEmpty()) {
                throw new IllegalArgumentException("知识库 Excel 中没有可读取内容");
            }

            String text = "# 从 Excel 导入的知识草稿\n\n" + String.join("\n", knowledgeLines);
            ImportFileMetadata metadata = new ImportFileMetadata(
                    fileName,
                    extension,
                    content.length,
                    FileParsingSupport.sha256(content),
                    knowledgeLines.size(),
                    firstSheet.getSheetName(),
                    workbook.getNumberOfSheets()
            );
            return new KnowledgeFileParseResult(
                    metadata,
                    text,
                    countLines(text),
                    List.of()
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法解析知识库 Excel：" + exception.getMessage(), exception);
        }
    }

    private static int countLines(String text) {
        return text.split("\\R", -1).length;
    }

    private static void validateFile(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("知识库文件为空");
        }
    }
}
