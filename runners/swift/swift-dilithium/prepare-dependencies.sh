#!/usr/bin/env bash
set -euo pipefail

runner_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
checkout_dir="$runner_dir/.build/checkouts"
patch_dir="$runner_dir/patches"

apply_patch_if_needed() {
    local package_name="$1"
    local checkout_name="$2"
    local expected_revision="$3"
    local source_path="$4"
    local patch_name="$5"
    local marker="$6"
    local checkout="$checkout_dir/$checkout_name"
    if [[ ! -d "$checkout" && -d "$checkout_dir/$package_name" ]]; then
        checkout="$checkout_dir/$package_name"
    fi

    if [[ ! -d "$checkout" ]]; then
        printf 'missing SwiftPM checkout: %s\n' "$checkout" >&2
        exit 1
    fi

    local actual_revision
    actual_revision="$(git -C "$checkout" rev-parse HEAD)"
    if [[ "$actual_revision" != "$expected_revision" ]]; then
        printf 'unexpected %s revision: %s (expected %s)\n' \
            "$package_name" "$actual_revision" "$expected_revision" >&2
        exit 1
    fi

    local source="$checkout/$source_path"
    if [[ ! -f "$source" ]]; then
        printf 'missing source file for %s: %s\n' "$package_name" "$source" >&2
        exit 1
    fi

    if ! grep -Fq "$marker" "$source"; then
        patch -d "$checkout" -p1 < "$patch_dir/$patch_name"
    fi

    if grep -Fq "SecRandomCopyBytes" "$source"; then
        printf 'Apple Security RNG call remains in %s\n' "$source" >&2
        exit 1
    fi
}

apply_patch_if_needed \
    "SwiftDilithium" \
    "swiftdilithium" \
    "452e507c68879a4a584502e1ef55605efb224e79" \
    "Sources/SwiftDilithium/Dilithium.swift" \
    "SwiftDilithium-rng.patch" \
    "SystemRandomNumberGenerator()"

apply_patch_if_needed \
    "BigInt" \
    "bigint" \
    "c2f49bee454760e433bafff17bfdc5b96f75d7c8" \
    "Sources/BigInt/BigInt.swift" \
    "BigInt-rng.patch" \
    "SystemRandomNumberGenerator()"
