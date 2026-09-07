package com.casemap.hierarchy.produce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class XMindFileParser {

    private static final int MAX_CONTENT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_NODES = 10000;
    private static final int MAX_DEPTH = 100;

    private final ObjectMapper objectMapper;

    public XMindFileParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public XMindParseResult parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("XMind 文件为空");
        }
        XMindArchiveContent archiveContent = readArchive(content);
        List<XMindNode> nodes;
        String format;
        if (archiveContent.contentJson() != null) {
            nodes = parseContentJson(archiveContent.contentJson());
            format = "xmind-json";
        } else if (archiveContent.contentXml() != null) {
            nodes = parseContentXml(archiveContent.contentXml());
            format = "xmind-xml";
        } else {
            throw new IllegalArgumentException("XMind 包中未找到 content.json 或 content.xml");
        }
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("XMind 中没有可解析的主题");
        }
        return new XMindParseResult(
                format,
                nodes.size(),
                nodesToCaseRows(nodes),
                nodesToKnowledgeMarkdown(nodes)
        );
    }

    private XMindArchiveContent readArchive(byte[] content) {
        byte[] contentJson = null;
        byte[] contentXml = null;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory()) {
                    continue;
                }
                String entryName = zipEntry.getName().replace('\\', '/');
                if ("content.json".equals(entryName)) {
                    contentJson = readEntry(zipInputStream);
                } else if ("content.xml".equals(entryName)) {
                    contentXml = readEntry(zipInputStream);
                }
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("无法读取 XMind 文件：" + exception.getMessage(), exception);
        }
        return new XMindArchiveContent(contentJson, contentXml);
    }

    private static byte[] readEntry(ZipInputStream zipInputStream) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int totalBytes = 0;
        int bytesRead;
        while ((bytesRead = zipInputStream.read(buffer)) >= 0) {
            totalBytes += bytesRead;
            if (totalBytes > MAX_CONTENT_BYTES) {
                throw new IllegalArgumentException("XMind 内容超过 10MB 限制");
            }
            outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toByteArray();
    }

    private List<XMindNode> parseContentJson(byte[] content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            List<JsonNode> sheets = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(sheets::add);
            } else if (root.path("sheets").isArray()) {
                root.path("sheets").forEach(sheets::add);
            } else if (root.has("rootTopic")) {
                sheets.add(root);
            } else {
                throw new IllegalArgumentException("无法识别 XMind content.json 结构");
            }

            List<XMindNode> nodes = new ArrayList<>();
            for (JsonNode sheet : sheets) {
                JsonNode rootTopic = sheet.path("rootTopic");
                if (rootTopic.isMissingNode() || rootTopic.isNull()) {
                    continue;
                }
                List<String> initialPath = new ArrayList<>();
                String sheetTitle = compact(sheet.path("title").asText(""));
                if (!sheetTitle.isEmpty()) {
                    initialPath.add(sheetTitle);
                }
                walkJsonTopic(rootTopic, initialPath, nodes);
            }
            return nodes;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("XMind content.json 解析失败：" + exception.getMessage(), exception);
        }
    }

    private void walkJsonTopic(JsonNode topic, List<String> parentPath, List<XMindNode> nodes) {
        ensureNodeLimit(nodes, parentPath.size());
        String title = jsonTitle(topic);
        List<String> currentPath = new ArrayList<>(parentPath);
        if (!title.isEmpty()) {
            currentPath.add(title);
        }
        List<JsonNode> children = jsonChildren(topic);
        nodes.add(new XMindNode(
                title,
                jsonNotes(topic),
                List.copyOf(currentPath),
                currentPath.size(),
                children.isEmpty()
        ));
        for (JsonNode child : children) {
            walkJsonTopic(child, currentPath, nodes);
        }
    }

    private static String jsonTitle(JsonNode topic) {
        JsonNode title = topic.path("title");
        if (title.isTextual()) {
            return compact(title.asText());
        }
        if (title.isObject()) {
            String text = title.path("text").asText("");
            return compact(text.isEmpty() ? title.path("content").asText("") : text);
        }
        return compact(topic.path("@title").asText(""));
    }

    private static String jsonNotes(JsonNode topic) {
        JsonNode notes = topic.path("notes");
        if (notes.isTextual()) {
            return compact(notes.asText());
        }
        JsonNode plain = notes.path("plain");
        if (plain.isTextual()) {
            return compact(plain.asText());
        }
        if (plain.path("content").isTextual()) {
            return compact(plain.path("content").asText());
        }
        return compact(notes.path("content").asText(""));
    }

    private static List<JsonNode> jsonChildren(JsonNode topic) {
        List<JsonNode> children = new ArrayList<>();
        JsonNode childrenNode = topic.path("children");
        for (String groupName : List.of("attached", "detached", "summary")) {
            JsonNode group = childrenNode.path(groupName);
            if (group.isArray()) {
                group.forEach(children::add);
            }
        }
        return children;
    }

    private List<XMindNode> parseContentXml(byte[] content) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var documentBuilder = factory.newDocumentBuilder();
            documentBuilder.setErrorHandler(new StrictXmlErrorHandler());
            Document document = documentBuilder.parse(new ByteArrayInputStream(content));
            List<Element> rootTopics = new ArrayList<>();
            NodeList allElements = document.getElementsByTagNameNS("*", "topic");
            for (int elementIndex = 0; elementIndex < allElements.getLength(); elementIndex++) {
                Element topic = (Element) allElements.item(elementIndex);
                if (!hasTopicAncestor(topic)) {
                    rootTopics.add(topic);
                }
            }

            List<XMindNode> nodes = new ArrayList<>();
            for (Element rootTopic : rootTopics) {
                walkXmlTopic(rootTopic, List.of(), nodes);
            }
            return nodes;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("XMind content.xml 解析失败：" + exception.getMessage(), exception);
        }
    }

    private void walkXmlTopic(Element topic, List<String> parentPath, List<XMindNode> nodes) {
        ensureNodeLimit(nodes, parentPath.size());
        String title = xmlTitle(topic);
        List<String> currentPath = new ArrayList<>(parentPath);
        if (!title.isEmpty()) {
            currentPath.add(title);
        }
        List<Element> children = xmlChildren(topic);
        nodes.add(new XMindNode(
                title,
                xmlNotes(topic),
                List.copyOf(currentPath),
                currentPath.size(),
                children.isEmpty()
        ));
        for (Element child : children) {
            walkXmlTopic(child, currentPath, nodes);
        }
    }

    private static boolean hasTopicAncestor(Element topic) {
        Node parent = topic.getParentNode();
        while (parent != null) {
            if (parent instanceof Element parentElement && "topic".equals(localName(parentElement))) {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    private static List<Element> xmlChildren(Element topic) {
        List<Element> children = new ArrayList<>();
        for (Element child : directChildren(topic)) {
            if (!"children".equals(localName(child))) {
                continue;
            }
            for (Element topicsElement : directChildren(child)) {
                if (!"topics".equals(localName(topicsElement))) {
                    continue;
                }
                for (Element childTopic : directChildren(topicsElement)) {
                    if ("topic".equals(localName(childTopic))) {
                        children.add(childTopic);
                    }
                }
            }
        }
        return children;
    }

    private static String xmlTitle(Element topic) {
        String titleAttribute = compact(topic.getAttribute("title"));
        if (!titleAttribute.isEmpty()) {
            return titleAttribute;
        }
        for (Element child : directChildren(topic)) {
            if ("title".equals(localName(child))) {
                return compact(child.getTextContent());
            }
        }
        return "";
    }

    private static String xmlNotes(Element topic) {
        for (Element child : directChildren(topic)) {
            if ("notes".equals(localName(child))) {
                return compact(child.getTextContent());
            }
        }
        return "";
    }

    private static List<Element> directChildren(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList childNodes = parent.getChildNodes();
        for (int childIndex = 0; childIndex < childNodes.getLength(); childIndex++) {
            Node child = childNodes.item(childIndex);
            if (child instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static String localName(Element element) {
        String localName = element.getLocalName();
        if (localName != null) {
            return localName.toLowerCase();
        }
        return element.getNodeName().replaceFirst("^.*:", "").toLowerCase();
    }

    private static List<ParsedCaseRow> nodesToCaseRows(List<XMindNode> nodes) {
        List<XMindNode> candidates = nodes.stream()
                .filter(node -> node.leaf() && !node.title().isEmpty() && node.depth() >= 2)
                .toList();
        if (candidates.isEmpty()) {
            candidates = nodes.stream()
                    .filter(node -> !node.title().isEmpty() && node.depth() >= 2)
                    .toList();
        }

        List<ParsedCaseRow> rows = new ArrayList<>();
        for (int nodeIndex = 0; nodeIndex < candidates.size(); nodeIndex++) {
            XMindNode node = candidates.get(nodeIndex);
            List<String> path = node.path();
            String feature = path.size() >= 2 ? path.get(path.size() - 2) : "";
            String scene = path.size() >= 3 ? path.get(path.size() - 3) : path.get(0);
            String[] stepAndExpected = splitNotes(node.notes());
            rows.add(new ParsedCaseRow(
                    "XMIND-ROW-" + (nodeIndex + 1),
                    node.title(),
                    stepAndExpected[0],
                    stepAndExpected[1],
                    scene,
                    feature,
                    path.get(0),
                    nodeIndex + 1
            ));
        }
        return rows;
    }

    private static String nodesToKnowledgeMarkdown(List<XMindNode> nodes) {
        Map<String, Set<String>> featuresByScene = new LinkedHashMap<>();
        Map<String, Set<String>> rulesByScene = new LinkedHashMap<>();
        for (XMindNode node : nodes) {
            if (node.title().isEmpty() || node.depth() < 2) {
                continue;
            }
            String scene = node.path().get(0);
            featuresByScene.computeIfAbsent(scene, ignored -> new LinkedHashSet<>());
            rulesByScene.computeIfAbsent(scene, ignored -> new LinkedHashSet<>());
            if (isRule(node.title())) {
                rulesByScene.get(scene).add(node.title());
            } else if (node.depth() >= 2) {
                featuresByScene.get(scene).add(node.title());
            }
            if (isRule(node.notes())) {
                rulesByScene.get(scene).add(compact(node.notes()));
            }
        }

        LinkedHashSet<String> features = new LinkedHashSet<>();
        LinkedHashSet<String> rules = new LinkedHashSet<>();
        featuresByScene.values().forEach(features::addAll);
        rulesByScene.values().forEach(rules::addAll);
        return String.join("\n",
                "# 从 XMind 导入的知识骨架",
                "",
                "## 业务场景",
                bulletLines(featuresByScene.keySet()),
                "",
                "## 功能点",
                bulletLines(features),
                "",
                "## 规则",
                bulletLines(rules)
        );
    }

    private static String bulletLines(Set<String> values) {
        return values.stream().map(value -> "- " + value).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private static String[] splitNotes(String notes) {
        String compactNotes = compact(notes);
        if (compactNotes.isEmpty()) {
            return new String[]{"", ""};
        }
        String[] parts = compactNotes.split("(?:预期|期望|结果)[:：]", 2);
        return new String[]{compact(parts[0]), parts.length > 1 ? compact(parts[1]) : ""};
    }

    private static boolean isRule(String text) {
        return text != null && text.matches(".*(?:必须|不可|不能|应当|禁止|校验|拦截).*");
    }

    private static String compact(String text) {
        return FileParsingSupport.compact(text);
    }

    private static void ensureNodeLimit(List<XMindNode> nodes, int depth) {
        if (nodes.size() >= MAX_NODES) {
            throw new IllegalArgumentException("XMind 主题数不能超过 " + MAX_NODES);
        }
        if (depth >= MAX_DEPTH) {
            throw new IllegalArgumentException("XMind 层级不能超过 " + MAX_DEPTH);
        }
    }

    public record XMindParseResult(
            String format,
            int nodeCount,
            List<ParsedCaseRow> caseRows,
            String knowledgeText
    ) {
    }

    private record XMindArchiveContent(byte[] contentJson, byte[] contentXml) {
    }

    private record XMindNode(
            String title,
            String notes,
            List<String> path,
            int depth,
            boolean leaf
    ) {
    }

    private static final class StrictXmlErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) {
            // 警告不影响安全解析。
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
