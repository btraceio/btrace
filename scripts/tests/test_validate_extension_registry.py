import json
import tempfile
import unittest
from pathlib import Path

from scripts.validate_extension_registry import validate_registry


def write_registry(payload):
    tmp = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False)
    with tmp:
        json.dump(payload, tmp)
    return Path(tmp.name)


class ValidateExtensionRegistryTest(unittest.TestCase):
    def test_accepts_valid_registry(self):
        registry = {
            "schema_version": 1,
            "extensions": [
                {
                    "id": "btrace-metrics",
                    "name": "BTrace HDR Histogram",
                    "description": "Latency histogram helpers for BTrace scripts",
                    "owner": "btraceio",
                    "source_repo": "https://github.com/btraceio/btrace",
                    "maven": {
                        "groupId": "io.btrace",
                        "artifactId": "btrace-metrics",
                        "version": "2.3.0",
                    },
                    "tags": ["metrics", "observability"],
                }
            ],
        }

        path = write_registry(registry)

        self.assertEqual([], validate_registry(path))

    def test_rejects_duplicate_ids_and_coordinates(self):
        registry = {
            "schema_version": 1,
            "extensions": [
                {
                    "id": "btrace-utils",
                    "name": "BTrace Utilities",
                    "description": "Utility extension",
                    "owner": "btraceio",
                    "source_repo": "https://github.com/btraceio/btrace",
                    "maven": {
                        "groupId": "io.btrace",
                        "artifactId": "btrace-utils",
                        "version": "2.3.0",
                    },
                },
                {
                    "id": "btrace-utils",
                    "name": "BTrace Utils Mirror",
                    "description": "Duplicate entry",
                    "owner": "community",
                    "source_repo": "https://github.com/example/ext",
                    "maven": {
                        "groupId": "io.btrace",
                        "artifactId": "btrace-utils",
                        "version": "2.3.1",
                    },
                },
            ],
        }

        path = write_registry(registry)

        errors = validate_registry(path)

        self.assertIn("Duplicate extension id: btrace-utils", errors)
        self.assertIn("Duplicate Maven coordinate: io.btrace:btrace-utils", errors)

    def test_rejects_missing_required_fields(self):
        registry = {
            "schema_version": 1,
            "extensions": [
                {
                    "id": "",
                    "name": "Broken",
                    "description": "",
                    "owner": "btraceio",
                    "source_repo": "not-a-url",
                    "maven": {
                        "groupId": "io.btrace",
                        "artifactId": "",
                        "version": "",
                    },
                    "tags": ["ok", 1],
                }
            ],
        }

        path = write_registry(registry)

        errors = validate_registry(path)

        self.assertIn("Extension #1 field 'id' must be a non-empty string", errors)
        self.assertIn("Extension #1 field 'description' must be a non-empty string", errors)
        self.assertIn("Extension #1 field 'source_repo' must be an https URL", errors)
        self.assertIn("Extension #1 Maven field 'artifactId' must be a non-empty string", errors)
        self.assertIn("Extension #1 Maven field 'version' must be a non-empty string", errors)
        self.assertIn("Extension #1 tags must be a list of non-empty strings", errors)


if __name__ == "__main__":
    unittest.main()
