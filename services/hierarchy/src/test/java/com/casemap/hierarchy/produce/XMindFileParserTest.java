package com.casemap.hierarchy.produce;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XMindFileParserTest {

    private final XMindFileParser xMindFileParser = new XMindFileParser(new ObjectMapper());

    @Test
    void parsesModernContentJson() throws Exception {
        String contentJson = """
                [
                  {
                    "title": "报价",
                    "rootTopic": {
                      "title": "金额计算与汇总",
                      "children": {
                        "attached": [
                          {
                            "title": "数量价汇总",
                            "children": {
                              "attached": [
                                {
                                  "title": "数量价正常汇总",
                                  "notes": {
                                    "plain": {
                                      "content": "输入数量和价格 预期：金额正确"
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                ]
                """;

        XMindFileParser.XMindParseResult result = xMindFileParser.parse(
                zip("content.json", contentJson)
        );

        assertEquals("xmind-json", result.format());
        assertEquals(3, result.nodeCount());
        assertEquals(1, result.caseRows().size());
        ParsedCaseRow parsedCase = result.caseRows().get(0);
        assertEquals("数量价正常汇总", parsedCase.caseName());
        assertEquals("金额计算与汇总", parsedCase.scene());
        assertEquals("数量价汇总", parsedCase.feature());
        assertEquals("金额正确", parsedCase.expected());
        assertTrue(result.knowledgeText().contains("## 功能点"));
    }

    @Test
    void parsesLegacyContentXml() throws Exception {
        String contentXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmap-content xmlns="urn:xmind:xmap:xmlns:content:2.0">
                  <sheet>
                    <topic title="造价提交与审核">
                      <children>
                        <topics type="attached">
                          <topic title="自动审核判定">
                            <children>
                              <topics type="attached">
                                <topic title="金额超限转人工">
                                  <notes><plain>提交报价 预期：进入人工审核</plain></notes>
                                </topic>
                              </topics>
                            </children>
                          </topic>
                        </topics>
                      </children>
                    </topic>
                  </sheet>
                </xmap-content>
                """;

        XMindFileParser.XMindParseResult result = xMindFileParser.parse(
                zip("content.xml", contentXml)
        );

        assertEquals("xmind-xml", result.format());
        assertEquals(1, result.caseRows().size());
        assertEquals("造价提交与审核", result.caseRows().get(0).scene());
        assertEquals("自动审核判定", result.caseRows().get(0).feature());
    }

    @Test
    void rejectsInvalidArchiveAndExternalEntityXml() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> xMindFileParser.parse(zip("metadata.json", "{}"))
        );

        String unsafeXml = """
                <?xml version="1.0"?>
                <!DOCTYPE topic [<!ENTITY external SYSTEM "file:///etc/passwd">]>
                <xmap-content><sheet><topic title="&external;"/></sheet></xmap-content>
                """;
        assertThrows(
                IllegalArgumentException.class,
                () -> xMindFileParser.parse(zip("content.xml", unsafeXml))
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
