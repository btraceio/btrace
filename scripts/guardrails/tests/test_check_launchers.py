"""Hermetic tests for check_launchers.py: each builds a throw-away bin directory and runs the script."""

import os
import subprocess
import sys
import tempfile
import unittest

SCRIPT = os.path.join(os.path.dirname(__file__), "..", "check_launchers.py")
BIN = "btrace-dist/src/main/resources/bin"

SH = """#! /bin/sh
JAVA_ARGS="-XX:+IgnoreUnrecognizedVMOptions"
%s
"${JAVA_HOME}/bin/java" ${JAVA_ARGS} -Dbtrace.client.main=io.btrace.X -cp "${CLIENT_JAR}" %s "$@"
"""
BAT = """@echo off\r
set "JAVA_ARGS=-XX:+IgnoreUnrecognizedVMOptions"\r
%s\r
"%%JAVA_HOME%%\\bin\\java" %%JAVA_ARGS%% -Dbtrace.client.main=io.btrace.X -cp "%%CLIENT_JAR%%" %s %%*\r
"""
ADD = '--add-exports jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED'


def sh(extra="", main="io.btrace.boot.Loader"):
    return SH % (extra, main)


def bat(extra="", main="io.btrace.boot.Loader"):
    return BAT % (extra, main)


class LauncherCheckTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = self.tmp.name
        self.baseline = os.path.join(self.root, "baseline.txt")

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, name, text):
        path = os.path.join(self.root, BIN, name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="") as f:
            f.write(text)

    def pair(self, sh_text, bat_text, name="tool"):
        self.write(name, sh_text)
        self.write(name + ".bat", bat_text)

    def run_check(self):
        cmd = [sys.executable, "-I", SCRIPT, self.root, "--baseline", self.baseline]
        r = subprocess.run(cmd, capture_output=True, text=True)
        return r.returncode, r.stdout + r.stderr

    def assertFails(self, *fragments):
        code, out = self.run_check()
        self.assertEqual(1, code, out)
        for fragment in fragments:
            self.assertIn(fragment, out)

    def assertPasses(self):
        code, out = self.run_check()
        self.assertEqual(0, code, out)
        self.assertIn("launchers: OK", out)

    def test_matching_pair_passes(self):
        self.pair(sh("JAVA_ARGS=\"$JAVA_ARGS %s\"" % ADD), bat('set "JAVA_ARGS=%%JAVA_ARGS%% %s"' % ADD))
        self.assertPasses()

    def test_flag_only_in_bat_fails(self):
        self.pair(sh(), bat('set "JAVA_ARGS=%JAVA_ARGS% -XX:+AllowRedefinitionToAddDeleteMethods"'))
        self.assertFails("launcher-flag bin/tool:-XX:+AllowRedefinitionToAddDeleteMethods:missing-in-sh")

    def test_flag_only_in_shell_fails(self):
        self.pair(sh('JAVA_ARGS="$JAVA_ARGS -Xshare:off"'), bat())
        self.assertFails("launcher-flag bin/tool:-Xshare:off:missing-in-bat")

    def test_add_exports_argument_is_part_of_the_flag(self):
        self.pair(
            sh('JAVA_ARGS="$JAVA_ARGS --add-exports a/b=ALL-UNNAMED"'),
            bat('set "JAVA_ARGS=%JAVA_ARGS% --add-exports a/c=ALL-UNNAMED"'),
        )
        self.assertFails(
            "launcher-flag bin/tool:--add-exports a/b=ALL-UNNAMED:missing-in-bat",
            "launcher-flag bin/tool:--add-exports a/c=ALL-UNNAMED:missing-in-sh",
        )

    def test_add_exports_with_equals_form_matches_space_form(self):
        self.pair(
            sh('JAVA_ARGS="$JAVA_ARGS --add-opens=a/b=ALL-UNNAMED"'),
            bat('set "JAVA_ARGS=%JAVA_ARGS% --add-opens a/b=ALL-UNNAMED"'),
        )
        self.assertPasses()

    def test_different_main_class_fails(self):
        self.pair(sh(), bat(main="io.btrace.other.Loader"))
        self.assertFails("launcher-main bin/tool:sh=io.btrace.boot.Loader:bat=io.btrace.other.Loader")

    def test_system_property_value_difference_fails(self):
        self.pair(sh(), bat().replace("io.btrace.X", "io.btrace.Y"))
        self.assertFails("-Dbtrace.client.main=io.btrace.X:missing-in-bat", "-Dbtrace.client.main=io.btrace.Y:missing-in-sh")

    def test_script_without_batch_twin_fails(self):
        self.write("tool", sh())
        self.assertFails("bin/tool has no batch twin")

    def test_batch_without_shell_twin_fails(self):
        self.write("tool.bat", bat())
        self.assertFails("bin/tool.bat has no shell twin")

    def test_missing_bin_directory_fails(self):
        self.assertFails("no launchers to compare")

    def test_empty_bin_directory_fails(self):
        os.makedirs(os.path.join(self.root, BIN))
        self.assertFails("holds no launcher scripts")

    def test_crlf_and_caret_continuation_parse_like_shell(self):
        shell = sh('JAVA_ARGS="$JAVA_ARGS -XX:+A \\\n    --add-exports x/y=ALL-UNNAMED \\\n    -Xmx1g"')
        batch = (
            "@echo off\r\n"
            'set "JAVA_ARGS=-XX:+IgnoreUnrecognizedVMOptions"\r\n'
            "set \"JAVA_ARGS=%JAVA_ARGS% -XX:+A ^\r\n"
            "    --add-exports x/y=ALL-UNNAMED ^\r\n"
            '    -Xmx1g"\r\n'
            '"%JAVA_HOME%\\bin\\java" %JAVA_ARGS% -Dbtrace.client.main=io.btrace.X -cp "%CLIENT_JAR%" io.btrace.boot.Loader %*\r\n'
        )
        self.pair(shell, batch)
        self.assertPasses()

    def test_crlf_does_not_leak_into_flag_names(self):
        self.pair(sh('JAVA_ARGS="$JAVA_ARGS -Xmx1g"'), bat("set JAVA_ARGS=%JAVA_ARGS% -Xmx1g"))
        self.assertPasses()

    def test_flags_in_a_java_opts_style_variable_are_found(self):
        self.pair(sh('JAVA_OPTS="-Xss512k -XX:+Foo"'), bat('set "JAVA_OPTS=-Xss512k"'))
        self.assertFails("launcher-flag bin/tool:-XX:+Foo:missing-in-bat")

    def test_comments_and_echo_lines_are_ignored(self):
        self.pair(
            sh('# -Xcomment\necho "use -Xecho"'),
            bat("rem -Xcomment\r\n:: -Xother\r\necho use -Xecho"),
        )
        self.assertPasses()

    def test_javaagent_launch_without_main_class_matches(self):
        shell = '#!/bin/sh\nJAVA_ARGS="-Xshare:off"\n"${JAVA_HOME}/bin/java" ${JAVA_ARGS} -javaagent:${J}=x,script="$@"\n'
        batch = '@echo off\r\nset "JAVA_ARGS=-Xshare:off"\r\n"%JAVA_HOME%\\bin\\java" %JAVA_ARGS% "-javaagent:%J%=x,script=%~1"\r\n'
        self.pair(shell, batch)
        self.assertPasses()

    def test_no_main_class_on_either_side_fails_instead_of_comparing_equal(self):
        shell = '#!/bin/sh\nJAVA_ARGS="-Xshare:off"\n"${JAVA_HOME}/bin/java" ${JAVA_ARGS} -jar "${J}" "$@"\n'
        batch = '@echo off\r\nset "JAVA_ARGS=-Xshare:off"\r\n"%JAVA_HOME%\\bin\\java" %JAVA_ARGS% -jar "%J%" %*\r\n'
        self.pair(shell, batch)
        self.assertFails(
            "bin/tool (bat): no main class or -javaagent launch found",
            "bin/tool (sh): no main class or -javaagent launch found",
        )

    def test_script_with_no_flags_fails_instead_of_passing(self):
        self.pair(
            '#!/bin/sh\n"${JAVA_HOME}/bin/java" -cp x io.btrace.boot.Loader\n',
            bat(),
        )
        self.assertFails("bin/tool (sh): no JVM flag found")

    def test_script_without_java_command_fails(self):
        self.pair("#!/bin/sh\nexec \"$@\"\n", bat())
        self.assertFails("bin/tool (sh): no java command line found")

    def test_undecodable_script_fails(self):
        self.write("tool.bat", bat())
        with open(os.path.join(self.root, BIN, "tool"), "wb") as f:
            f.write(b"#!/bin/sh\n\xff\xfe\n")
        self.assertFails("bin/tool cannot be read")

    def test_baselined_difference_passes(self):
        self.pair(sh(), bat("set JAVA_ARGS=%JAVA_ARGS% -Xshare:off"))
        with open(self.baseline, "w") as f:
            f.write("launcher-flag bin/tool:-Xshare:off:missing-in-sh  # sh never needed it\n")
        self.assertPasses()

    def test_stale_baseline_entry_fails(self):
        self.pair(sh(), bat())
        with open(self.baseline, "w") as f:
            f.write("launcher-flag bin/tool:-Xshare:off:missing-in-sh  # fixed since\n")
        self.assertFails("stale baseline entry (remove it): launcher-flag bin/tool:-Xshare:off:missing-in-sh")

    def test_new_difference_beside_baselined_one_fails(self):
        self.pair(sh(), bat("set JAVA_ARGS=%JAVA_ARGS% -Xshare:off -Xmx1g"))
        with open(self.baseline, "w") as f:
            f.write("launcher-flag bin/tool:-Xshare:off:missing-in-sh  # reason\n")
        self.assertFails("new violation: launcher-flag bin/tool:-Xmx1g:missing-in-sh")

    def test_baseline_entry_without_reason_fails(self):
        self.pair(sh(), bat("set JAVA_ARGS=%JAVA_ARGS% -Xshare:off"))
        with open(self.baseline, "w") as f:
            f.write("# header\nlauncher-flag bin/tool:-Xshare:off:missing-in-sh\n")
        self.assertFails("baseline.txt:2: baseline entry has no '# reason': launcher-flag bin/tool:-Xshare:off:missing-in-sh")


if __name__ == "__main__":
    unittest.main()
