package com.casemap.hierarchy.featurekey;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * featureKey 规则：领域/系统/场景/功能点
 */
public final class FeatureKey {

    public static final String SEPARATOR = "/";

    private final String domain;
    private final String system;
    private final String scene;
    private final String feature;

    private FeatureKey(String domain, String system, String scene, String feature) {
        this.domain = domain;
        this.system = system;
        this.scene = scene;
        this.feature = feature;
    }

    public String domain() {
        return domain;
    }

    public String system() {
        return system;
    }

    public String scene() {
        return scene;
    }

    public String feature() {
        return feature;
    }

    public List<String> asList() {
        return List.of(domain, system, scene, feature);
    }

    public String toKey() {
        return String.join(SEPARATOR, domain, system, scene, feature);
    }

    public static FeatureKey of(String domain, String system, String scene, String feature) {
        return new FeatureKey(
                normalize(domain, "领域"),
                normalize(system, "系统"),
                normalize(scene, "场景"),
                normalize(feature, "功能点")
        );
    }

    public static FeatureKey parse(String featureKey) {
        String raw = featureKey == null ? "" : featureKey.trim();
        if (raw.isEmpty()) {
            throw new FeatureKeyException("featureKey 不能为空");
        }
        String[] segments = raw.split(SEPARATOR, -1);
        if (segments.length != 4) {
            throw new FeatureKeyException(
                    "featureKey 必须为「领域/系统/场景/功能点」四级，当前 "
                            + segments.length + " 段：" + featureKey);
        }
        return of(segments[0], segments[1], segments[2], segments[3]);
    }

    public static String join(String domain, String system, String scene, String feature) {
        return of(domain, system, scene, feature).toKey();
    }

    public static String encodeForUrl(String featureKey) {
        return URLEncoder.encode(parse(featureKey).toKey(), StandardCharsets.UTF_8);
    }

    public static String decodeFromUrl(String encoded) {
        String decoded = URLDecoder.decode(Objects.requireNonNullElse(encoded, ""), StandardCharsets.UTF_8);
        return parse(decoded).toKey();
    }

    /**
     * 解析路径变量中的 featureKey。
     * 兼容未编码的四级路径、整段百分号编码，以及 Spring {@code {*featureKey}} 可能带上的前导斜杠。
     */
    public static String fromPathVariable(String featureKey) {
        String raw = featureKey == null ? "" : featureKey.trim();
        if (raw.startsWith(SEPARATOR)) {
            raw = raw.substring(1);
        }
        if (raw.contains("%")) {
            return decodeFromUrl(raw);
        }
        return parse(raw).toKey();
    }

    public static List<String> exampleQuoteKeys() {
        return Arrays.asList(
                "家装/报价/金额计算与汇总/数量价汇总",
                "家装/报价/金额计算与汇总/固定价汇总",
                "家装/报价/造价提交与审核/自动审核判定"
        );
    }

    public static boolean isValid(String featureKey) {
        try {
            parse(featureKey);
            return true;
        } catch (FeatureKeyException ex) {
            return false;
        }
    }

    private static String normalize(String segment, String levelLabel) {
        String value = segment == null ? "" : segment.trim();
        if (value.isEmpty()) {
            throw new FeatureKeyException(levelLabel + "不能为空");
        }
        if (value.contains(SEPARATOR)) {
            throw new FeatureKeyException(levelLabel + "不能包含分隔符「/」，原始值：" + segment);
        }
        if (value.contains("\n") || value.contains("\r")) {
            throw new FeatureKeyException(levelLabel + "不能包含换行");
        }
        return value;
    }
}
