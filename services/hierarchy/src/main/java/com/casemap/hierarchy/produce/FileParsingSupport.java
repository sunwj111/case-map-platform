package com.casemap.hierarchy.produce;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class FileParsingSupport {

    private FileParsingSupport() {
    }

    static String extension(String fileName) {
        String normalizedName = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        int separatorIndex = normalizedName.lastIndexOf('.');
        return separatorIndex < 0 ? "" : normalizedName.substring(separatorIndex + 1);
    }

    static DecodedText decodeText(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return new DecodedText(removeBom(text), null);
        } catch (CharacterCodingException exception) {
            Charset fallbackCharset = Charset.forName("GB18030");
            String text = fallbackCharset.decode(ByteBuffer.wrap(content)).toString();
            ImportRisk risk = new ImportRisk(
                    "ENCODING_FALLBACK",
                    "文件不是有效 UTF-8，已按 GB18030 解析",
                    null
            );
            return new DecodedText(removeBom(text), risk);
        }
    }

    static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    static String compact(Object value) {
        return value == null ? "" : String.valueOf(value).replaceAll("\\s+", " ").trim();
    }

    private static String removeBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    record DecodedText(String text, ImportRisk risk) {
    }
}
