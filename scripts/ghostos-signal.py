#!/usr/bin/env python3
"""Verify a pinned APK before staging the optional, unprivileged ROM app."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "packages/apps/GhostWebSignal/release.json"
INHERIT = "$(call inherit-product, vendor/ghostos/signal/product.mk)"


def run(*args):
    return subprocess.run(args, check=True, text=True, capture_output=True).stdout


def load_release():
    release = json.loads(CONFIG.read_text())
    if release["package"] != "org.ghostweb.signal":
        raise ValueError("Only the dedicated GhostWeb Signal package is allowed")
    for field in ("sha256", "certificate_sha256"):
        if not re.fullmatch(r"[0-9a-f]{64}", release[field]):
            raise ValueError("Missing or invalid pin: " + field)
    prefix = "https://github.com/GhostWebEnterprise/ghost-web-signal/releases/download/"
    if not release["url"].startswith(prefix + release["tag"] + "/"):
        raise ValueError("Unexpected APK source")
    return release


def verify_apk(apk, release, apksigner, aapt):
    with apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    if digest != release["sha256"]:
        raise ValueError("APK SHA-256 mismatch")
    # apksigner verifies the signature and content digests, not just certificate presence.
    signature = run(apksigner, "verify", "--verbose", "--print-certs", str(apk))
    certs = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]+)$",
                       signature, re.MULTILINE)
    if [cert.lower() for cert in certs] != [release["certificate_sha256"]]:
        raise ValueError("APK signer mismatch (exactly one pinned signer required)")
    badging = run(aapt, "dump", "badging", str(apk))
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",
                        badging, re.MULTILINE)
    expected = (release["package"], str(release["version_code"]), release["version_name"])
    if not package or package.groups() != expected:
        raise ValueError("APK package/version mismatch")
    if "application-debuggable" in badging or "arm64-v8a" not in badging:
        raise ValueError("APK must be a non-debuggable ARM64 release")
    manifest = run(aapt, "dump", "xmltree", str(apk), "AndroidManifest.xml")
    if not re.search(r"android:extractNativeLibs\([^)]*\)=\(type 0x12\)0xffffffff", manifest):
        raise ValueError("Preserved compressed JNI requires extractNativeLibs=true")
    return {"sha256": digest, "certificate_sha256": certs[0].lower(),
            "package": package[1], "version_name": package[3]}


def product_path(android, device):
    if device not in ("larry", "oscaro"):
        raise ValueError("Unsupported device")
    product = android / "device/oneplus" / device / ("lineage_" + device + ".mk")
    if not product.is_file() or product.is_symlink():
        raise ValueError("Missing regular upstream product: " + str(product))
    return product


def stage(android, device, apk=None):
    product = product_path(android, device)
    destination = android / "vendor/ghostos/signal"
    if destination.is_symlink() or any(p.is_symlink() for p in destination.parents):
        raise ValueError("Refusing a symlinked destination")
    for filename in ("GhostWebSignal.apk", "Android.bp", "product.mk"):
        if (destination / filename).is_symlink():
            raise ValueError("Refusing a symlinked staged file")
    text = product.read_text()
    if apk is None and INHERIT not in text:
        return
    lines = [line for line in text.splitlines() if line.strip() != INHERIT]
    if apk is not None:
        destination.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(apk, destination / "GhostWebSignal.apk")
        templates = ROOT / "packages/apps/GhostWebSignal"
        for source, target in (("Android.bp.in", "Android.bp"), ("product.mk", "product.mk")):
            shutil.copyfile(templates / source, destination / target)
        lines += [INHERIT]
    # Removing the inherited package on an opt-out prevents stale APK inclusion.
    product.write_text("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--android-root", type=Path)
    parser.add_argument("--device", default="larry")
    parser.add_argument("--disable", action="store_true")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--apksigner", default=os.environ.get("APKSIGNER", "apksigner"))
    parser.add_argument("--aapt", default=os.environ.get("AAPT", "aapt"))
    args = parser.parse_args()
    if args.disable:
        if not args.android_root:
            parser.error("--disable requires --android-root")
        stage(args.android_root.resolve(), args.device)
        return
    release = load_release()
    with tempfile.TemporaryDirectory(prefix="ghostos-signal-") as directory:
        apk = Path(directory) / "GhostWebSignal.apk"
        if args.apk:
            shutil.copyfile(args.apk, apk)
        else:
            with urllib.request.urlopen(release["url"], timeout=120) as source, apk.open("wb") as target:
                shutil.copyfileobj(source, target)
        report = verify_apk(apk, release, args.apksigner, args.aapt)
        if args.android_root:
            stage(args.android_root.resolve(), args.device, apk)
        print(json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    main()
