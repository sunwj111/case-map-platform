package com.casemap.hierarchy.produce;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeFileParserTest {

    private final KnowledgeFileParser knowledgeFileParser = new KnowledgeFileParser(
            new XMindFileParser(new ObjectMapper())
    );

    @Test
    void parsesMarkdownAndKeepsSourceMetadata() {
        String markdown = """
                # 金额计算与汇总

                - 功能点：数量价汇总
                - 规则：数量必须大于零
                """;

        KnowledgeFileParseResult result = knowledgeFileParser.parse(
                "quote.md",
                markdown.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("md", result.metadata().format());
        assertTrue(result.text().contains("数量价汇总"));
        assertEquals(4, result.lineCount());
        assertEquals(64, result.metadata().sha256().length());
    }

    @Test
    void convertsFirstExcelSheetToMarkdownDraft() throws Exception {
        byte[] workbookContent;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("规则");
            var firstRow = sheet.createRow(0);
            firstRow.createCell(0).setCellValue("功能点");
            firstRow.createCell(1).setCellValue("规则");
            var secondRow = sheet.createRow(1);
            secondRow.createCell(0).setCellValue("固定价汇总");
            secondRow.createCell(1).setCellValue("固定价不能为空");
            workbook.write(outputStream);
            workbookContent = outputStream.toByteArray();
        }

        KnowledgeFileParseResult result = knowledgeFileParser.parse("knowledge.xlsx", workbookContent);

        assertEquals("规则", result.metadata().sheetName());
        assertEquals(2, result.metadata().rowCount());
        assertTrue(result.text().contains("- 固定价汇总、固定价不能为空"));
    }

    @Test
    void convertsXMindToKnowledgeDraft() throws Exception {
        String contentJson = """
                [{
                  "rootTopic": {
                    "title": "造价审核",
                    "children": {"attached": [{"title": "自动审核判定"}]}
                  }
                }]
                """;

        KnowledgeFileParseResult result = knowledgeFileParser.parse(
                "knowledge.xmind",
                zip("content.json", contentJson)
        );

        assertEquals("xmind-json", result.metadata().format());
        assertTrue(result.text().contains("自动审核判定"));
    }

    @Test
    void rejectsUnsupportedAndEmptyKnowledgeFiles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> knowledgeFileParser.parse("knowledge.pdf", "content".getBytes(StandardCharsets.UTF_8))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> knowledgeFileParser.parse("knowledge.md", "  ".getBytes(StandardCharsets.UTF_8))
        );
    }

    private static byte[] zip(String entryName, String content) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }
}
