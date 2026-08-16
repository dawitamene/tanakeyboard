#!/usr/bin/env python3

import argparse
import hashlib
import pickle
import struct
import sys
import zlib
from pathlib import Path


MAGIC = 0x41485246
VERSION = 1
HEADER_SIZE = 112
STATE_SIZE = 8
ARC_SIZE = 10
WEIGHT_INDEX_SIZE = 8
PAGE_INDEX_SIZE = 12
PAGE_LIMIT = 60_000


def split_top_level(text, separator):
    parts = []
    start = 0
    depth = 0
    for index, character in enumerate(text):
        if character == "[":
            depth += 1
        elif character == "]":
            depth -= 1
        elif character == separator and depth == 0:
            parts.append(text[start:index])
            start = index + 1
    parts.append(text[start:])
    return parts


def parse_feature_structure(text, prefix=""):
    if not text.startswith("[") or not text.endswith("]"):
        raise ValueError(f"invalid HornMorpho feature structure: {text}")
    body = text[1:-1]
    if not body:
        return ()
    assignments = []
    for item in split_top_level(body, ","):
        if item[0] in "+-":
            assignments.append((prefix + item[1:], item[0]))
            continue
        feature, value = item.split("=", 1)
        if value.startswith("["):
            assignments.extend(parse_feature_structure(value, prefix + feature + "."))
        else:
            assignments.append((prefix + feature, value))
    return tuple(sorted(assignments))


def parse_weight(text):
    return tuple(
        parse_feature_structure(alternative)
        for alternative in split_top_level(text, ";")
    )


def encode_constraint(alternatives, feature_ids, value_ids):
    if len(alternatives) > 255:
        raise ValueError("constraint has more than 255 alternatives")
    encoded = bytearray((len(alternatives),))
    for assignments in alternatives:
        if len(assignments) > 255:
            raise ValueError("feature structure has more than 255 assignments")
        encoded.append(len(assignments))
        for feature, value in assignments:
            encoded.extend(struct.pack(">BH", feature_ids[feature], value_ids[value]))
    return bytes(encoded)


def encode_string_table(strings):
    index = bytearray()
    data = bytearray()
    for value in strings:
        encoded = value.encode("utf-8")
        if len(encoded) > 0xFFFF:
            raise ValueError(f"string is too long: {value[:80]}")
        index.extend(struct.pack(">IH", len(data), len(encoded)))
        data.extend(encoded)
    return bytes(index), bytes(data)


def compress_pages(records):
    pages = []
    indexes = []
    current = bytearray()
    current_records = []

    def finish_page():
        if not current:
            return
        compressor = zlib.compressobj(level=9, wbits=-15)
        compressed = compressor.compress(bytes(current)) + compressor.flush()
        page_id = len(pages)
        pages.append((compressed, len(current)))
        indexes.extend(
            (page_id, offset, length)
            for offset, length in current_records
        )
        current.clear()
        current_records.clear()

    for record in records:
        if len(record) > PAGE_LIMIT:
            raise ValueError("one constraint exceeds the page size")
        if current and len(current) + len(record) > PAGE_LIMIT:
            finish_page()
        current_records.append((len(current), len(record)))
        current.extend(record)
    finish_page()
    return pages, indexes


def state_ids(fst):
    ids = sorted(int(state) for state in fst._outgoing)
    if ids != list(range(len(ids))):
        raise ValueError("HornMorpho runtime export requires contiguous numeric states")
    return ids


def read_root_frequencies(path):
    frequencies = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        key, separator, raw_frequency = line.rpartition("\t")
        if not separator or ":" not in key:
            continue
        try:
            frequency = int(raw_frequency)
        except ValueError:
            continue
        root = key.split(":", 1)[0]
        frequencies[root] = max(frequencies.get(root, 0), frequency)
    return frequencies


def export(pickle_path, root_frequency_path, output_path, manifest_path):
    sys.path.insert(0, str(pickle_path.parents[4]))
    with pickle_path.open("rb") as source:
        fst = pickle.load(source)

    states = state_ids(fst)
    weight_texts = sorted(set(fst._weight.values()))
    parsed_weights = [parse_weight(text) for text in weight_texts]
    features = sorted({feature for weight in parsed_weights for fs in weight for feature, _ in fs})
    values = sorted({value for weight in parsed_weights for fs in weight for _, value in fs})
    if len(features) > 255:
        raise ValueError("feature identifiers no longer fit in one byte")
    if len(values) > 0xFFFF:
        raise ValueError("feature values no longer fit in two bytes")
    if len(weight_texts) + 1 > 0xFFFF:
        raise ValueError("weight identifiers no longer fit in two bytes")

    feature_ids = {value: index for index, value in enumerate(features)}
    value_ids = {value: index for index, value in enumerate(values)}
    root_frequencies = read_root_frequencies(root_frequency_path)
    weight_ids = {value: index + 1 for index, value in enumerate(weight_texts)}
    constraint_records = [b"\x01\x00"] + [
        encode_constraint(weight, feature_ids, value_ids)
        for weight in parsed_weights
    ]
    pages, weight_indexes = compress_pages(constraint_records)
    if len(pages) > 0xFFFF:
        raise ValueError("constraint page identifiers no longer fit in two bytes")

    state_data = bytearray()
    arc_data = bytearray()
    arc_count = 0
    final_count = 0
    for state in states:
        outgoing = fst._outgoing[str(state)]
        final = bool(fst._is_final[str(state)])
        if len(outgoing) > 0xFFFF:
            raise ValueError(f"state {state} has too many outgoing arcs")
        state_data.extend(struct.pack(">IHH", arc_count, len(outgoing), 1 if final else 0))
        final_count += int(final)
        for arc in outgoing:
            input_label = fst._in_string[arc]
            output_label = fst._out_string[arc]
            if len(input_label) > 1 or len(output_label) > 1:
                raise ValueError(f"arc {arc} has a multi-character label")
            input_code = ord(input_label) if input_label else 0
            output_code = ord(output_label) if output_label else 0
            weight_id = weight_ids.get(fst._weight.get(arc, ""), 0)
            arc_data.extend(
                struct.pack(">IHHH", int(fst._dst[arc]), input_code, output_code, weight_id)
            )
            arc_count += 1

    if arc_count != len(fst._src):
        raise ValueError("not every HornMorpho arc was exported")

    weight_index_data = b"".join(
        struct.pack(">HHHH", page, offset, length, 0)
        for page, offset, length in weight_indexes
    )
    page_data = bytearray()
    page_index_data = bytearray()
    for compressed, raw_length in pages:
        page_index_data.extend(
            struct.pack(">IIHH", len(page_data), len(compressed), raw_length, 0)
        )
        page_data.extend(compressed)

    feature_index, feature_strings = encode_string_table(features)
    value_index, value_strings = encode_string_table(values)
    root_frequency_data = b"".join(
        struct.pack(">I", root_frequencies.get(value, 0))
        for value in values
    )
    strings = feature_strings + value_strings
    value_index = bytearray(value_index)
    for index in range(len(values)):
        offset = struct.unpack_from(">I", value_index, index * 6)[0]
        struct.pack_into(">I", value_index, index * 6, offset + len(feature_strings))

    state_offset = HEADER_SIZE
    arc_offset = state_offset + len(state_data)
    weight_index_offset = arc_offset + len(arc_data)
    page_index_offset = weight_index_offset + len(weight_index_data)
    page_data_offset = page_index_offset + len(page_index_data)
    feature_index_offset = page_data_offset + len(page_data)
    value_index_offset = feature_index_offset + len(feature_index)
    root_frequency_offset = value_index_offset + len(value_index)
    string_offset = root_frequency_offset + len(root_frequency_data)
    file_length = string_offset + len(strings)
    uncompressed_constraints = sum(raw for _, raw in pages)
    compressed_constraints = len(page_data)

    header = bytearray(HEADER_SIZE)
    struct.pack_into(
        ">IHH" + "I" * 22,
        header,
        0,
        MAGIC,
        VERSION,
        HEADER_SIZE,
        len(states),
        arc_count,
        len(constraint_records),
        len(pages),
        len(features),
        len(values),
        state_offset,
        arc_offset,
        weight_index_offset,
        page_index_offset,
        page_data_offset,
        feature_index_offset,
        value_index_offset,
        string_offset,
        file_length,
        0,
        int(fst._initial_state),
        final_count,
        uncompressed_constraints,
        compressed_constraints,
        50306,
        1,
    )
    struct.pack_into(">I", header, 96, root_frequency_offset)
    body = b"".join(
        (
            bytes(state_data),
            bytes(arc_data),
            weight_index_data,
            bytes(page_index_data),
            bytes(page_data),
            feature_index,
            bytes(value_index),
            root_frequency_data,
            strings,
        )
    )
    struct.pack_into(">I", header, 68, zlib.crc32(body) & 0xFFFFFFFF)
    artifact = bytes(header) + body
    if len(artifact) != file_length:
        raise ValueError("computed artifact length does not match encoded length")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_bytes(artifact)
    digest = hashlib.sha256(artifact).hexdigest()
    manifest_path.write_text(
        "\n".join(
            (
                f"schemaVersion={VERSION}",
                f"asset={output_path.name}",
                f"length={len(artifact)}",
                f"sha256={digest}",
                "hornMorphoVersion=5.3.6",
                "upstreamCommit=7e3d93af760e27ea6dbc3b7a078d2d9c3335f618",
                "representation=weighted-runtime-fst",
                f"states={len(states)}",
                f"arcs={arc_count}",
                f"roots=1832",
                f"rankedRoots={len(root_frequencies)}",
                f"constraintPages={len(pages)}",
                f"constraintBytes={compressed_constraints}",
                "generatedSurfaces=0",
                "",
            )
        ),
        encoding="utf-8",
    )
    print(
        f"exported {len(states)} states, {arc_count} arcs, {len(weight_texts)} rule weights, "
        f"{len(pages)} pages, {len(artifact)} bytes"
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pickle", type=Path, required=True)
    parser.add_argument("--root-frequencies", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()
    export(
        args.pickle.resolve(),
        args.root_frequencies.resolve(),
        args.output.resolve(),
        args.manifest.resolve(),
    )


if __name__ == "__main__":
    main()
