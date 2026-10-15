import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("signal_preinstall", ROOT / "scripts/ghostos-signal.py")
signal = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signal)


class SignalPreinstallTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.android = Path(self.directory.name)
        self.apk = self.android / "candidate.apk"
        self.apk.write_bytes(b"test fixture")
        self.release = signal.load_release()
        self.release["sha256"] = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        self.signature = "Signer #1 certificate SHA-256 digest: " + self.release["certificate_sha256"]
        self.badging = ("package: name='org.ghostweb.signal' versionCode='172007' versionName='8.19.2-7'\n"
                        "native-code: 'arm64-v8a'\n")

    def verify(self, signature=None, badging=None):
        manifest = "A: android:extractNativeLibs(0x010104ea)=(type 0x12)0xffffffff"
        with patch.object(signal, "run", side_effect=[signature or self.signature, badging or self.badging, manifest]):
            return signal.verify_apk(self.apk, self.release, "apksigner", "aapt")

    def test_pinned_identity(self):
        self.assertEqual(self.verify()["package"], "org.ghostweb.signal")

    def test_reject_tampering_before_invoking_tools(self):
        self.apk.write_bytes(b"tampered")
        with patch.object(signal, "run") as run, self.assertRaisesRegex(ValueError, "SHA-256"):
            self.verify()
        run.assert_not_called()

    def test_reject_wrong_or_multiple_signers(self):
        for signature in ("Signer #1 certificate SHA-256 digest: " + "0" * 64,
                          self.signature + "\nSigner #2 certificate SHA-256 digest: " + "1" * 64):
            with self.subTest(signature=signature), self.assertRaisesRegex(ValueError, "signer"):
                self.verify(signature=signature)

    def test_signature_tool_failure_propagates(self):
        with patch.object(signal, "run", side_effect=RuntimeError("invalid signature")):
            with self.assertRaises(RuntimeError):
                signal.verify_apk(self.apk, self.release, "apksigner", "aapt")

    def test_native_extraction_must_be_enabled(self):
        with patch.object(signal, "run", side_effect=[self.signature, self.badging, ""]):
            with self.assertRaisesRegex(ValueError, "extractNativeLibs"):
                signal.verify_apk(self.apk, self.release, "apksigner", "aapt")

    def test_reject_wrong_package_version_debug_or_arch(self):
        for badging in (self.badging.replace("org.ghostweb.signal", "org.ghostweb.app"),
                        self.badging.replace("172007", "172006"),
                        self.badging + "application-debuggable\n",
                        self.badging.replace("arm64-v8a", "x86_64")):
            with self.subTest(badging=badging), self.assertRaises(ValueError):
                self.verify(badging=badging)

    def test_stage_is_idempotent_and_opt_out_removes_package(self):
        product = self.android / "device/oneplus/larry/lineage_larry.mk"
        product.parent.mkdir(parents=True)
        product.write_text("PRODUCT_NAME := lineage_larry\n")
        for _ in range(2):
            signal.stage(self.android, "larry", self.apk)
        self.assertEqual(product.read_text().count(signal.INHERIT), 1)
        staged = self.android / "vendor/ghostos/signal/GhostWebSignal.apk"
        self.assertEqual(staged.read_bytes(), self.apk.read_bytes())
        signal.stage(self.android, "larry")
        self.assertNotIn(signal.INHERIT, product.read_text())
        self.assertEqual(product.read_text(), "PRODUCT_NAME := lineage_larry\n")

    def test_missing_or_unknown_product_fails(self):
        for device in ("larry", "../escape"):
            with self.subTest(device=device), self.assertRaises(ValueError):
                signal.stage(self.android, device, self.apk)


if __name__ == "__main__":
    unittest.main()
