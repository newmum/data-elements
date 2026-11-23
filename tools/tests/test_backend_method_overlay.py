import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BackendMethodOverlayTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.asm = os.environ.get("BACKEND_OVERLAY_ASM_CLASSPATH")
        if not cls.asm or not shutil.which("javac") or not shutil.which("java"):
            raise unittest.SkipTest("Java21 and BACKEND_OVERLAY_ASM_CLASSPATH (ASM plus ASM-tree) are required")
        cls.temporary = tempfile.TemporaryDirectory()
        cls.root = Path(cls.temporary.name)
        tool = Path(__file__).parents[1] / "BackendMethodOverlay.java"
        subprocess.run(["javac", "-cp", cls.asm, "-d", str(cls.root), str(tool)], check=True, capture_output=True)
        cls.tool_cp = cls.asm + os.pathsep + str(cls.root)

    @classmethod
    def tearDownClass(cls):
        cls.temporary.cleanup()

    def fixture(self, replacement="return \"new-selected\";", extra="", access="public"):
        root = self.root / self.id().split(".")[-1]
        for name, body, other in [("base", 'return "old-selected";', "old-other"),
                                  ("donor", replacement, "unpublished-other")]:
            folder = root / name
            folder.mkdir(parents=True)
            source = folder / "ValidationMethodSubject.java"
            visibility = access if name == "donor" else "public"
            source.write_text('public class ValidationMethodSubject {'
                              f'{visibility} static String selected(){{{body}}}'
                              f'public static String other(){{return "{other}";}}'
                              'public static void main(String[] args){System.out.println(other()+"/"+selected());}'
                              + (extra if name == "donor" else "") + '}', encoding="utf8")
            subprocess.run(["javac", "-d", str(folder), str(source)], check=True, capture_output=True)
        return root

    def invoke(self, root, bad_hash=False):
        inputs = [root / name / "ValidationMethodSubject.class" for name in ("base", "donor")]
        output = root / "result" / "ValidationMethodSubject.class"
        hashes = [hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs]
        if bad_hash:
            hashes[0] = "0" * 64
        result = subprocess.run(["java", "-cp", self.tool_cp, "BackendMethodOverlay", *map(str, inputs), str(output),
                                 "selected", "()Ljava/lang/String;", *hashes], capture_output=True)
        return result, output

    def test_changes_only_the_selected_method_and_keeps_the_parent_member(self):
        root = self.fixture()
        result, output = self.invoke(root)
        self.assertEqual(0, result.returncode, result.stderr)
        execution = subprocess.run(["java", "-cp", str(output.parent), "ValidationMethodSubject"], capture_output=True, check=True)
        self.assertEqual("old-other/new-selected", execution.stdout.decode().strip())

    def test_rejects_a_changed_parent_hash(self):
        result, output = self.invoke(self.fixture(), bad_hash=True)
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(output.exists())

    def test_rejects_a_dependency_on_an_unpublished_method(self):
        root = self.fixture("return unpublished();", 'private static String unpublished(){return "pending";}')
        result, output = self.invoke(root)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(b"Unpublished method dependency", result.stderr)
        self.assertFalse(output.exists())

    def test_rejects_a_visibility_change(self):
        result, output = self.invoke(self.fixture(access="private"))
        self.assertNotEqual(0, result.returncode)
        self.assertIn(b"contract changed", result.stderr)
        self.assertFalse(output.exists())

    def test_never_overwrites_a_staged_result(self):
        root = self.fixture()
        result, output = self.invoke(root)
        self.assertEqual(0, result.returncode)
        before = output.read_bytes()
        result, _ = self.invoke(root)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(before, output.read_bytes())


if __name__ == "__main__":
    unittest.main()
