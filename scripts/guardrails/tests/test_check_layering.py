"""Hermetic tests for check_layering.py: each builds a throw-away source tree and runs the script."""

import json
import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_layering.py")

PROJECT = "implementation project(':%s')"

RULES = {
    "separate_builds": ["btrace-gradle-plugin"],
    "edges": [
        ["btrace-agent", "btrace-core"],
        ["btrace-client", "btrace-core"],
        ["btrace-runtime", "btrace-core"],
        ["btrace-extensions:btrace-otel", "btrace-core"],
    ],
    "imports": [
        {"module": "btrace-boot", "forbidden": ["io.btrace.core", "io.btrace.agent"]},
        {"module": "btrace-core", "forbidden": ["io.btrace.agent", "io.btrace.runtime", "io.btrace.instr"]},
        {"module": "btrace-agent", "package": "io.btrace.instr", "forbidden": ["io.btrace.agent"]},
        {"module": "btrace-client", "forbidden": ["io.btrace.agent"]},
        {
            "module": "btrace-client",
            "forbidden": ["io.btrace.instr"],
            "exceptions": {"btrace-client/src/main/java/io/btrace/client/ProbePrinter.java": ["io.btrace.instr"]},
        },
        {
            "module": "btrace-extensions",
            "forbidden": ["io.btrace.core", "io.btrace.agent"],
            "allow": ["io.btrace.core.extensions"],
        },
    ],
    "upper_layer_literals": [{"module": "btrace-runtime", "forbidden": ["io.btrace.agent"]}],
}

BUILD_FILES = {
    "btrace-agent": [":btrace-core"],
    "btrace-client": [":btrace-core"],
    "btrace-runtime": [":btrace-core"],
    "btrace-extensions/btrace-otel": [":btrace-core"],
    "btrace-core": [],
}


class LayeringCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.baseline = os.path.join(self.root, "baseline.txt")
        self.write("settings.gradle", "rootProject.name = 'demo'\n")
        for module, deps in BUILD_FILES.items():
            self.write_build(module, deps)
        self.write_rules(RULES)
        # the one file allowed to import io.btrace.instr; an unused exception is itself an error
        self.java("btrace-client", "io.btrace.client", "ProbePrinter", "import io.btrace.instr.Printer;")

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, rel, text):
        path = os.path.join(self.root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)

    def write_build(self, module, deps, extra=""):
        body = "".join("    implementation project('%s')\n" % d for d in deps)
        self.write(module + "/build.gradle", "dependencies {\n" + body + extra + "}\n")

    def write_rules(self, rules):
        self.write("rules.json", json.dumps(rules))

    def java(self, module, package, name, body, source_set="main/java"):
        text = "package %s;\n\n%s\n\nclass %s {}\n" % (package, body, name)
        rel = "%s/src/%s/%s/%s.java" % (module, source_set, package.replace(".", "/"), name)
        self.write(rel, text)
        return rel

    def run_check(self, isolated=True):
        cmd = [sys.executable] + (["-I"] if isolated else []) + [SCRIPT, self.root]
        cmd += ["--rules", os.path.join(self.root, "rules.json"), "--baseline", self.baseline]
        r = subprocess.run(cmd, capture_output=True, text=True)
        return r.returncode, r.stdout + r.stderr

    def assertFails(self, *fragments):
        code, out = self.run_check()
        self.assertEqual(1, code, out)
        for fragment in fragments:
            self.assertIn(fragment, out)
        return out

    def assertPasses(self):
        code, out = self.run_check()
        self.assertEqual(0, code, out)
        self.assertIn("layering: OK", out)

    def test_clean_tree_passes(self):
        self.java("btrace-client", "io.btrace.client", "X", "import io.btrace.core.ArgsMap;")
        self.assertPasses()

    def test_runs_under_isolated_mode(self):
        code, out = self.run_check(isolated=True)
        self.assertEqual(0, code, out)

    def test_client_importing_agent_fails(self):
        self.java("btrace-client", "io.btrace.client", "X", "import io.btrace.agent.Main;")
        self.assertFails("import btrace-client/", "io.btrace.agent")

    def test_core_importing_runtime_module_fails(self):
        self.java("btrace-core", "io.btrace.core", "X", "import io.btrace.runtime.BTraceRuntimes;")
        self.assertFails("import btrace-core/", "io.btrace.runtime")

    def test_boot_importing_core_fails(self):
        self.java("btrace-boot", "io.btrace.boot", "X", "import io.btrace.core.ArgsMap;")
        self.assertFails("import btrace-boot/")

    def test_instr_importing_agent_fails(self):
        self.java("btrace-agent", "io.btrace.instr", "X", "import io.btrace.agent.Main;")
        self.assertFails("import btrace-agent/")

    def test_agent_package_may_import_agent(self):
        self.java("btrace-agent", "io.btrace.agent", "X", "import io.btrace.agent.Main;")
        self.assertPasses()

    def test_extension_importing_core_internal_fails(self):
        self.java("btrace-extensions/btrace-otel", "io.btrace.otel", "X", "import io.btrace.core.ArgsMap;")
        self.assertFails("import btrace-extensions/btrace-otel/", "io.btrace.core")

    def test_extension_importing_extension_api_passes(self):
        self.java(
            "btrace-extensions/btrace-otel",
            "io.btrace.otel",
            "X",
            "import io.btrace.core.extensions.Extension;\nimport java.util.List;",
        )
        self.assertPasses()

    def test_extension_impl_source_set_is_checked(self):
        self.java(
            "btrace-extensions/btrace-otel", "io.btrace.otel", "X", "import io.btrace.core.ArgsMap;", "impl/java"
        )
        self.assertFails("import btrace-extensions/btrace-otel/src/impl/java/")

    def test_new_project_edge_fails(self):
        self.write_build("btrace-core", [":btrace-agent"])
        self.assertFails("edge btrace-core/build.gradle:btrace-core->btrace-agent")

    def test_edge_forms_are_parsed(self):
        self.write_build(
            "btrace-client",
            [":btrace-core"],
            '    compileOnly(project(":btrace-core"))\n'
            "    runtimeOnly project(path: ':btrace-core', configuration: 'shadow')\n"
            "    testImplementation project(path: \":btrace-core\")\n",
        )
        self.assertPasses()

    def test_nested_project_edge_fails(self):
        self.write_build("btrace-core", [":btrace-extensions:btrace-otel"])
        self.assertFails("btrace-core->btrace-extensions:btrace-otel")

    def test_project_reference_outside_dependencies_is_not_an_edge(self):
        self.write(
            "btrace-core/build.gradle",
            "def dist = project(':btrace-dist')\ntasks.jar.dependsOn project(':btrace-boot').tasks.jar\n",
        )
        self.assertPasses()

    def test_unparseable_project_reference_is_an_error(self):
        self.write_build("btrace-client", [":btrace-core"], "    implementation project(somePath)\n")
        self.assertFails("cannot parse", "btrace-client/build.gradle")

    def test_allowed_edge_that_no_longer_exists_fails(self):
        self.write_build("btrace-client", [])
        self.assertFails("allowed edge", "btrace-client->btrace-core")

    def test_separate_build_is_ignored(self):
        self.write_build("btrace-gradle-plugin", [":btrace-core", ":btrace-agent"])
        self.assertPasses()

    def test_upper_layer_class_literal_fails(self):
        self.java(
            "btrace-runtime", "io.btrace.runtime", "X", 'String n = "io.btrace.agent.PerfReaderImpl";'
        )
        self.assertFails("literal btrace-runtime/", "io.btrace.agent.PerfReaderImpl")

    def test_slash_form_literal_fails(self):
        self.java("btrace-runtime", "io.btrace.runtime", "X", 'String n = "io/btrace/agent/PerfReaderImpl";')
        self.assertFails("literal btrace-runtime/")

    def test_unrelated_literal_passes(self):
        self.java("btrace-runtime", "io.btrace.runtime", "X", 'String n = "io.btrace.agentless.Foo";')
        self.assertPasses()

    def test_split_package_fails(self):
        self.java("btrace-core", "io.btrace.runtime", "A", "")
        self.java("btrace-runtime", "io.btrace.runtime", "B", "")
        self.assertFails("split-package btrace-core/src/main/java/io/btrace/runtime:btrace-runtime")

    def test_package_in_two_source_sets_of_one_module_passes(self):
        self.java("btrace-runtime", "io.btrace.runtime", "A", "")
        self.java("btrace-runtime", "io.btrace.runtime", "B", "", "main/java9")
        self.assertPasses()

    def test_test_source_sets_do_not_count_as_split_packages(self):
        self.java("btrace-core", "io.btrace.runtime", "A", "")
        self.java("btrace-runtime", "io.btrace.runtime", "B", "", "test/java")
        self.assertPasses()

    def test_static_and_wildcard_imports_are_checked(self):
        self.java("btrace-client", "io.btrace.client", "A", "import static io.btrace.agent.Main.x;")
        self.java("btrace-client", "io.btrace.client", "B", "import io.btrace.agent.*;")
        out = self.assertFails()
        self.assertIn("import btrace-client/src/main/java/io/btrace/client/A.java", out)
        self.assertIn("import btrace-client/src/main/java/io/btrace/client/B.java", out)

    def test_name_in_comment_or_javadoc_passes(self):
        self.java(
            "btrace-client",
            "io.btrace.client",
            "X",
            "// import io.btrace.agent.Main;\n"
            "/* import io.btrace.agent.Main; */\n"
            "/**\n * See import io.btrace.agent.Main;\n * {@link io.btrace.agent.Main}\n */\n"
            'String s = "import io.btrace.agent.Main;";',
        )
        self.java(
            "btrace-runtime", "io.btrace.runtime", "Y", '// "io.btrace.agent.PerfReaderImpl"\n/* "io.btrace.agent.Z" */'
        )
        self.assertPasses()

    def test_java24_source_set_is_checked(self):
        self.java("btrace-client", "io.btrace.client", "X", "import io.btrace.agent.Main;", "main/java24")
        self.assertFails("import btrace-client/src/main/java24/")

    def test_java9_and_java11_source_sets_are_checked(self):
        self.java("btrace-client", "io.btrace.client", "A", "import io.btrace.agent.Main;", "main/java9")
        self.java("btrace-client", "io.btrace.client", "B", "import io.btrace.agent.Main;", "main/java11")
        out = self.assertFails()
        self.assertIn("src/main/java9/", out)
        self.assertIn("src/main/java11/", out)

    def test_baselined_violation_passes(self):
        rel = self.java("btrace-client", "io.btrace.client", "X", "import io.btrace.agent.Main;")
        self.write("baseline.txt", "import %s:io.btrace.agent  # known\n" % rel)
        self.assertPasses()

    def test_fixed_but_still_baselined_fails(self):
        rel = self.java("btrace-client", "io.btrace.client", "X", "import java.util.List;")
        self.write("baseline.txt", "import %s:io.btrace.agent\n" % rel)
        self.assertFails("stale baseline entry")

    def test_baseline_entry_for_deleted_file_reports_stale(self):
        self.write("baseline.txt", "import btrace-client/src/main/java/io/btrace/client/Gone.java:io.btrace.agent\n")
        self.assertFails("stale baseline entry (remove it): import btrace-client/src/main/java/io/btrace/client/Gone.java")

    def test_allowed_exception_file_passes(self):
        self.assertPasses()

    def test_exception_that_suppresses_nothing_is_stale(self):
        self.java("btrace-client", "io.btrace.client", "ProbePrinter", "import java.util.List;")
        self.assertFails("stale exception", "ProbePrinter.java")

    def test_exception_does_not_cover_other_files(self):
        self.java("btrace-client", "io.btrace.client", "Other", "import io.btrace.instr.Printer;")
        self.assertFails("import btrace-client/src/main/java/io/btrace/client/Other.java:io.btrace.instr")

    def test_missing_rules_file_is_an_error(self):
        os.remove(os.path.join(self.root, "rules.json"))
        code, out = self.run_check()
        self.assertEqual(1, code, out)
        self.assertIn("layering:", out)


if __name__ == "__main__":
    unittest.main()
