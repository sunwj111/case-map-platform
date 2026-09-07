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

class CaseFileParserTest {

    private final XMindFileParser xMindFileParser = new XMindFileParser(new ObjectMapper());
    private final CaseFileParser caseFileParser = new CaseFileParser(10000, xMindFileParser);

    @Test
    void parsesCsvWithFuzzyHeadersAndQuotedContent() {
        String csv = """
                用例编号,用例名称,操作步骤,预期结果,业务场景,功能点,所属模块
                TC-001,"数量,价格汇总","输入数量
                并提交",金额正确,金额计算与汇总,数量价汇总,报价
                """;

        CaseFileParseResult result = caseFileParser.parse(
                "cases.csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals(1, result.rows().size());
        ParsedCaseRow parsedCase = result.rows().get(0);
        assertEquals("TC-001", parsedCase.originalCaseId());
        assertEquals("数量,价格汇总", parsedCase.caseName());
        assertEquals("输入数量 并提交", parsedCase.step());
        assertEquals("数量价汇总", parsedCase.feature());
        assertEquals(0, result.risks().size());
        assertEquals(64, result.metadata().sha256().length());
    }

    @Test
    void reportsMissingColumnsAndGeneratesTemporaryCaseId() {
        String csv = """
                用例名称,预期结果
                固定价汇总,金额正确
                """;

        CaseFileParseResult result = caseFileParser.parse(
                "cases.csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("IMPORT-ROW-2", result.rows().get(0).originalCaseId());
        assertTrue(result.risks().stream().anyMatch(risk -> "MISSING_CASE_ID_COLUMN".equals(risk.code())));
        assertTrue(result.risks().stream().anyMatch(risk -> "MISSING_STEP_COLUMN".equals(risk.code())));
        assertTrue(result.risks().stream().anyMatch(risk -> "MISSING_ORIGINAL_CASE_ID".equals(risk.code())));
    }

    @Test
    void parsesFirstExcelSheet() throws Exception {
        byte[] workbookContent;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("历史用例");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("CaseName");
            header.createCell(1).setCellValue("Step");
            header.createCell(2).setCellValue("Expected");
            var dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("优惠后汇总");
            dataRow.createCell(1).setCellValue("提交报价");
            dataRow.createCell(2).setCellValue("结果正确");
            workbook.createSheet("忽略工作表");
            workbook.write(outputStream);
            workbookContent = outputStream.toByteArray();
        }

        CaseFileParseResult result = caseFileParser.parse("cases.xlsx", workbookContent);

        assertEquals(1, result.rows().size());
        assertEquals("优惠后汇总", result.rows().get(0).caseName());
        assertEquals("历史用例", result.metadata().sheetName());
        assertEquals(2, result.metadata().sheetCount());
    }

    @Test
    void parsesXMindAsHistoricalCases() throws Exception {
        String contentJson = """
                [{
                  "rootTopic": {
                    "title": "金额计算",
                    "children": {"attached": [{
                      "title": "数量价汇总",
                      "children": {"attached": [{"title": "正常汇总"}]}
                    }]}
                  }
                }]
                """;

        CaseFileParseResult result = caseFileParser.parse(
                "cases.xmind",
                zip("content.json", contentJson)
        );

        assertEquals("xmind-json", result.metadata().format());
        assertEquals(1, result.rows().size());
        assertEquals("正常汇总", result.rows().get(0).caseName());
        assertTrue(result.risks().stream().anyMatch(risk -> "GENERATED_XMIND_CASE_IDS".equals(risk.code())));
    }

    @Test
    void rejectsEmptyUnsupportedAndMalformedFiles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> caseFileParser.parse("cases.csv", new byte[0])
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> caseFileParser.parse("cases.json", "{}".getBytes(StandardCharsets.UTF_8))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> caseFileParser.parse(
                        "cases.csv",
                        "用例名称\n\"未闭合".getBytes(StandardCharsets.UTF_8)
                )
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
