package org.ghostos.auditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, read-only audit observations. No aggregate "device is secure" claim is made. */
public final class AuditSnapshot {
    public enum State { OBSERVED, PASS, WARNING, FAIL, UNKNOWN }

    public static final class Finding {
        public final String title;
        public final State state;
        public final String value;
        public final String source;
        public final String limitation;

        Finding(String title, State state, String value, String source, String limitation) {
            this.title = title;
            this.state = state;
            this.value = value;
            this.source = source;
            this.limitation = limitation;
        }
    }

    public interface Source {
        String property(String key);
        Boolean selinuxEnabled();
        Boolean selinuxEnforced();
        Boolean secureScreenLock();
        Boolean adbEnabled();
        String securityPatch();
        String androidRelease();
        String device();
        int sdkInt();
        Boolean hasSystemFeature(String feature);
        String otaStatus();
    }

    private AuditSnapshot() { }

    public static List<Finding> collect(Source source) {
        if (source == null) throw new IllegalArgumentException("source is required");
        List<Finding> findings = new ArrayList<>();
        findings.add(verifiedBoot(source.property("ro.boot.verifiedbootstate"),
                source.property("ro.boot.flash.locked")));
        findings.add(selinux(source.selinuxEnabled(), source.selinuxEnforced()));
        findings.add(screenLock(source.secureScreenLock()));
        findings.add(adb(source.adbEnabled()));
        findings.add(observed("VBMeta device state", clean(source.property("ro.boot.vbmeta.device_state")),
                "ro.boot.vbmeta.device_state",
                "A boot property only; it is not an independent vbmeta signature verification."));
        findings.add(observed("VBMeta digest", clean(source.property("ro.boot.vbmeta.digest")),
                "ro.boot.vbmeta.digest",
                "Platform-reported digest only; Auditor does not recompute it or verify a vbmeta signature."));
        findings.add(observed("AVB version", clean(source.property("ro.boot.vbmeta.avb_version")),
                "ro.boot.vbmeta.avb_version",
                "Reported boot metadata only; it does not prove that AVB verification succeeded."));
        findings.add(observed("Active boot slot", clean(source.property("ro.boot.slot_suffix")),
                "ro.boot.slot_suffix",
                "Reported slot suffix only; it does not prove the slot is bootable or successful."));
        findings.add(observed("Boot slot successful flag", clean(source.property("ro.boot.slot_successful")),
                "ro.boot.slot_successful",
                "Platform-reported boot-control metadata; no boot-control HAL query is performed."));
        findings.add(observed("Boot slot unbootable flag", clean(source.property("ro.boot.slot_unbootable")),
                "ro.boot.slot_unbootable",
                "Platform-reported boot-control metadata; no boot-control HAL query is performed."));
        findings.add(observed("Boot slot retry count", clean(source.property("ro.boot.retry_count")),
                "ro.boot.retry_count",
                "Platform-reported retry metadata only; it is not a prediction of boot success."));
        findings.add(observed("dm-verity mode", clean(source.property("ro.boot.veritymode")),
                "ro.boot.veritymode",
                "Reported boot property only; it does not independently validate dm-verity or AVB."));
        findings.add(observed("A/B update capability", clean(source.property("ro.build.ab_update")),
                "ro.build.ab_update",
                "Build metadata only; it does not prove an updater is installed or an OTA is available."));
        findings.add(observed("Build fingerprint", clean(source.property("ro.build.fingerprint")),
                "ro.build.fingerprint",
                "Build identity metadata only; it is not a trusted release or provenance verification."));
        findings.add(observed("Build incremental", clean(source.property("ro.build.version.incremental")),
                "ro.build.version.incremental",
                "Build metadata only; it does not prove update freshness or successful installation."));
        findings.add(observed("OTA updater status", clean(source.otaStatus()),
                "SystemUpdateManager.retrieveSystemUpdateInfo",
                "The system updater may publish pending-update status and target metadata. Missing status remains unknown; this does not independently prove payload signature verification, installation success or boot success."));
        String patch = clean(source.securityPatch());
        State patchState = patch.matches("\\d{4}-\\d{2}-\\d{2}") ? State.OBSERVED : State.UNKNOWN;
        findings.add(new Finding("Security patch", patchState,
                patchState == State.UNKNOWN ? "Unknown" : patch,
                "Build.VERSION.SECURITY_PATCH",
                "The reported date is not checked against an external patch-level policy."));
        findings.add(observed("Android release", clean(source.androidRelease()),
                "Build.VERSION.RELEASE", "Build metadata does not prove that the build is current or uncompromised."));
        findings.add(observed("Device codename", clean(source.device()),
                "Build.DEVICE", "Build metadata does not verify the physical handset identity."));
        findings.add(observed("Android API level", source.sdkInt() > 0 ? Integer.toString(source.sdkInt()) : "",
                "Build.VERSION.SDK_INT", "The API level is informational only."));
        String[][] features = {
                {"Fingerprint", "android.hardware.fingerprint"},
                {"Face biometrics", "android.hardware.biometrics.face"},
                {"Camera", "android.hardware.camera.any"},
                {"Microphone", "android.hardware.microphone"},
                {"GPS", "android.hardware.location.gps"},
                {"Wi-Fi", "android.hardware.wifi"},
                {"Bluetooth", "android.hardware.bluetooth"},
                {"NFC", "android.hardware.nfc"},
                {"Telephony", "android.hardware.telephony"},
                {"USB host", "android.hardware.usb.host"}
        };
        for (String[] feature : features) {
            Boolean present = source.hasSystemFeature(feature[1]);
            State capabilityState = present == null ? State.UNKNOWN : State.OBSERVED;
            String capabilityValue = present == null ? "Unknown"
                    : present ? "Declared by PackageManager" : "Not declared by PackageManager";
            findings.add(new Finding("Capability: " + feature[0], capabilityState, capabilityValue,
                    "PackageManager.hasSystemFeature(" + feature[1] + ")",
                    "Feature declaration only; it does not prove runtime hardware health, driver support or secure configuration."));
        }
        return Collections.unmodifiableList(findings);
    }

    static Finding verifiedBoot(String stateValue, String lockedValue) {
        String state = clean(stateValue).toLowerCase();
        String locked = clean(lockedValue);
        if ("red".equals(state)) {
            return new Finding("Verified Boot", State.FAIL, "Platform reports red state",
                    "ro.boot.verifiedbootstate", "Platform property evidence; inspect boot logs and vbmeta separately.");
        }
        if ("orange".equals(state) || "yellow".equals(state) || "0".equals(locked)) {
            String detail = "yellow".equals(state) ? "Platform reports a custom trust root"
                    : "Platform reports an unlocked boot state";
            return new Finding("Verified Boot", State.WARNING, detail,
                    "ro.boot.verifiedbootstate / ro.boot.flash.locked",
                    "This is not independent cryptographic verification of the boot chain.");
        }
        if ("green".equals(state) && "1".equals(locked)) {
            return new Finding("Verified Boot", State.OBSERVED, "Platform reports green state and locked bootloader",
                    "ro.boot.verifiedbootstate / ro.boot.flash.locked",
                    "Platform-reported properties only; no independent AVB/vbmeta verification was performed.");
        }
        return new Finding("Verified Boot", State.UNKNOWN, "Unknown",
                "ro.boot.verifiedbootstate / ro.boot.flash.locked",
                "Required boot properties were missing, inconsistent or inaccessible.");
    }

    static Finding selinux(Boolean enabled, Boolean enforced) {
        if (Boolean.FALSE.equals(enabled)) {
            return new Finding("SELinux", State.FAIL, "SELinux disabled",
                    "android.os.SELinux", "Read-only framework API observation.");
        }
        if (enabled == null) {
            return new Finding("SELinux", State.UNKNOWN, "Unknown",
                    "android.os.SELinux", "Framework evidence was unavailable.");
        }
        if (Boolean.TRUE.equals(enforced)) {
            return new Finding("SELinux", State.PASS, "Enforcing",
                    "android.os.SELinux", "Enforcement mode alone does not validate policy quality or denials.");
        }
        if (Boolean.FALSE.equals(enforced)) {
            return new Finding("SELinux", State.WARNING, "Permissive",
                    "android.os.SELinux", "Permissive mode does not enforce SELinux policy.");
        }
        return new Finding("SELinux", State.UNKNOWN, "Unknown",
                "android.os.SELinux", "Enforcement state was unavailable.");
    }

    static Finding screenLock(Boolean secure) {
        if (Boolean.TRUE.equals(secure)) return new Finding("Screen lock", State.PASS, "Secure lock detected",
                "KeyguardManager.isDeviceSecure", "This does not assess lock strength or biometric configuration.");
        if (Boolean.FALSE.equals(secure)) return new Finding("Screen lock", State.WARNING, "No secure lock detected",
                "KeyguardManager.isDeviceSecure", "No lock-screen policy was changed.");
        return new Finding("Screen lock", State.UNKNOWN, "Unknown",
                "KeyguardManager.isDeviceSecure", "Keyguard state was unavailable.");
    }

    static Finding adb(Boolean enabled) {
        if (Boolean.TRUE.equals(enabled)) return new Finding("ADB debugging", State.WARNING, "Enabled",
                "Settings.Global.ADB_ENABLED", "Other developer and USB policies are not assessed.");
        if (Boolean.FALSE.equals(enabled)) return new Finding("ADB debugging", State.PASS, "Disabled",
                "Settings.Global.ADB_ENABLED", "This checks ADB only, not every debugging interface.");
        return new Finding("ADB debugging", State.UNKNOWN, "Unknown",
                "Settings.Global.ADB_ENABLED", "The setting could not be read.");
    }

    private static Finding observed(String title, String value, String source, String limitation) {
        boolean available = value != null && !value.isEmpty();
        return new Finding(title, available ? State.OBSERVED : State.UNKNOWN,
                available ? value : "Unknown", source, limitation);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
