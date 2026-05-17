#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path
from urllib.parse import urlparse


def load_registry(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def is_non_empty_string(value):
    return isinstance(value, str) and value.strip() != ""


def is_https_url(value):
    if not is_non_empty_string(value):
        return False
    parsed = urlparse(value)
    return parsed.scheme == "https" and parsed.netloc != ""


def validate_registry(path: Path):
    errors = []

    try:
        registry = load_registry(path)
    except FileNotFoundError:
        return [f"Registry file not found: {path}"]
    except json.JSONDecodeError as exc:
        return [f"Invalid JSON in {path}: {exc}"]

    if not isinstance(registry, dict):
        return ["Registry root must be a JSON object"]

    schema_version = registry.get("schema_version")
    if schema_version != 1:
        errors.append("Top-level field 'schema_version' must equal 1")

    extensions = registry.get("extensions")
    if not isinstance(extensions, list):
        return errors + ["Top-level field 'extensions' must be a list"]

    seen_ids = set()
    seen_coords = set()

    for index, extension in enumerate(extensions, start=1):
        prefix = f"Extension #{index}"

        if not isinstance(extension, dict):
            errors.append(f"{prefix} must be an object")
            continue

        for field in ("id", "name", "description", "owner"):
            if not is_non_empty_string(extension.get(field)):
                errors.append(f"{prefix} field '{field}' must be a non-empty string")

        if not is_https_url(extension.get("source_repo")):
            errors.append(f"{prefix} field 'source_repo' must be an https URL")

        maven = extension.get("maven")
        if not isinstance(maven, dict):
            errors.append(f"{prefix} field 'maven' must be an object")
            continue

        for field in ("groupId", "artifactId", "version"):
            if not is_non_empty_string(maven.get(field)):
                errors.append(f"{prefix} Maven field '{field}' must be a non-empty string")

        tags = extension.get("tags")
        if tags is not None:
            if not isinstance(tags, list) or any(not is_non_empty_string(tag) for tag in tags):
                errors.append(f"{prefix} tags must be a list of non-empty strings")

        compatibility = extension.get("compatibility")
        if compatibility is not None:
            if not isinstance(compatibility, dict):
                errors.append(f"{prefix} compatibility must be an object when present")
            elif "min_btrace_version" in compatibility and not is_non_empty_string(
                compatibility["min_btrace_version"]
            ):
                errors.append(
                    f"{prefix} compatibility field 'min_btrace_version' must be a non-empty string"
                )

        extension_id = extension.get("id")
        if is_non_empty_string(extension_id):
            if extension_id in seen_ids:
                errors.append(f"Duplicate extension id: {extension_id}")
            seen_ids.add(extension_id)

        group_id = maven.get("groupId")
        artifact_id = maven.get("artifactId")
        if is_non_empty_string(group_id) and is_non_empty_string(artifact_id):
            coordinate = f"{group_id}:{artifact_id}"
            if coordinate in seen_coords:
                errors.append(f"Duplicate Maven coordinate: {coordinate}")
            seen_coords.add(coordinate)

    return errors


def main():
    parser = argparse.ArgumentParser(description="Validate the BTrace extension registry JSON.")
    parser.add_argument(
        "registry",
        nargs="?",
        default="extension-registry/registry/extensions.json",
        help="Path to the registry JSON file",
    )
    args = parser.parse_args()

    errors = validate_registry(Path(args.registry))
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print(f"Registry is valid: {args.registry}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
