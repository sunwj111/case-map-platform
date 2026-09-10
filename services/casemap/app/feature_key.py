from __future__ import annotations

from dataclasses import dataclass
from urllib.parse import quote_plus, unquote_plus

from app.errors import FeatureKeyError

SEPARATOR = "/"
EXAMPLE_QUOTE_KEYS = [
    "家装/报价/金额计算与汇总/数量价汇总",
    "家装/报价/金额计算与汇总/固定价汇总",
    "家装/报价/造价提交与审核/自动审核判定",
]


@dataclass(frozen=True)
class FeatureKey:
    domain: str
    system: str
    scene: str
    feature: str

    def as_list(self) -> list[str]:
        return [self.domain, self.system, self.scene, self.feature]

    def to_key(self) -> str:
        return SEPARATOR.join(self.as_list())


def _normalize(segment: str | None, level_label: str) -> str:
    value = "" if segment is None else segment.strip()
    if not value:
        raise FeatureKeyError(level_label + "不能为空")
    if SEPARATOR in value:
        raise FeatureKeyError(level_label + "不能包含分隔符「/」，原始值：" + str(segment))
    if "\n" in value or "\r" in value:
        raise FeatureKeyError(level_label + "不能包含换行")
    return value


def of(domain: str, system: str, scene: str, feature: str) -> FeatureKey:
    return FeatureKey(
        _normalize(domain, "领域"),
        _normalize(system, "系统"),
        _normalize(scene, "场景"),
        _normalize(feature, "功能点"),
    )


def parse(feature_key: str | None) -> FeatureKey:
    raw = "" if feature_key is None else feature_key.strip()
    if not raw:
        raise FeatureKeyError("featureKey 不能为空")
    segments = raw.split(SEPARATOR)
    if len(segments) != 4:
        raise FeatureKeyError(
            "featureKey 必须为「领域/系统/场景/功能点」四级，当前 "
            + str(len(segments))
            + " 段："
            + str(feature_key)
        )
    return of(segments[0], segments[1], segments[2], segments[3])


def join(domain: str, system: str, scene: str, feature: str) -> str:
    return of(domain, system, scene, feature).to_key()


def encode_for_url(feature_key: str) -> str:
    return quote_plus(parse(feature_key).to_key(), safe="")


def decode_from_url(encoded: str | None) -> str:
    decoded = unquote_plus("" if encoded is None else encoded)
    return parse(decoded).to_key()


def from_path_variable(feature_key: str | None) -> str:
    raw = "" if feature_key is None else feature_key.strip()
    if raw.startswith(SEPARATOR):
        raw = raw[1:]
    if "%" in raw:
        return decode_from_url(raw)
    return parse(raw).to_key()


def is_valid(feature_key: str) -> bool:
    try:
        parse(feature_key)
        return True
    except FeatureKeyError:
        return False


def java_string_hash(text: str) -> int:
    hash_value = 0
    for character in text:
        hash_value = ((hash_value * 31) + ord(character)) & 0xFFFFFFFF
    if hash_value >= 0x80000000:
        hash_value -= 0x100000000
    return hash_value


def java_hex(value: int) -> str:
    return format(value & 0xFFFFFFFF, "x")
