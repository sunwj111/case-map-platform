from app.errors import FeatureKeyError
from app.feature_key import decode_from_url, encode_for_url, from_path_variable, java_hex, java_string_hash, join, parse


def test_join_and_parse():
    key = join("家装", "报价", "金额计算与汇总", "数量价汇总")
    assert key == "家装/报价/金额计算与汇总/数量价汇总"
    assert parse(key).domain == "家装"


def test_reject_slash_in_segment():
    try:
        join("家装", "报/价", "场景", "功能点")
    except FeatureKeyError as error:
        assert "分隔符" in str(error)
    else:
        raise AssertionError("expected FeatureKeyError")


def test_url_round_trip():
    key = "家装/报价/金额计算与汇总/数量价汇总"
    encoded = encode_for_url(key)
    assert "/" not in encoded
    assert decode_from_url(encoded) == key
    assert from_path_variable(key) == key
    assert from_path_variable("/" + key) == key
    assert from_path_variable(encoded) == key


def test_java_hash_hex_matches_string_hashcode():
    key = "家装/报价/金额计算与汇总/数量价汇总"
    assert java_hex(java_string_hash(key)) == format(java_string_hash(key) & 0xFFFFFFFF, "x")
