package org.ghostos.securitycenter;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * First Android UI shell for GhostOS Security Center.
 * Every status shown here is read from a public Android API or a guarded platform
 * probe, or explicitly labelled unknown/unverified. This app does not control or embed a VPN client.
 */
public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(10, 14, 24);
    private static final int SURFACE = Color.rgb(22, 29, 44);
    private static final int ACCENT = Color.rgb(111, 170, 255);
    private static final int PRIMARY = Color.rgb(241, 245, 252);
    private static final int MUTED = Color.rgb(164, 178, 199);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recreate the snapshot after returning from Android VPN settings.
        setContentView(buildContent());
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(32));
        scroll.addView(root);

        TextView eyebrow = text("GHOSTOS  /  DEVICE PROTECTION", 11, ACCENT, true);
        root.addView(eyebrow);
        TextView title = text("Security Center", 30, PRIMARY, true);
        LinearLayout.LayoutParams titleParams = params(-1, -2);
        titleParams.topMargin = dp(7);
        root.addView(title, titleParams);
        TextView intro = text("A live overview of the security state reported by Android. Unknown means not yet verified.", 14, MUTED, false);
        LinearLayout.LayoutParams introParams = params(-1, -2);
        introParams.topMargin = dp(8);
        introParams.bottomMargin = dp(22);
        root.addView(intro, introParams);

        addCard(root, "NETWORK PROTECTION", vpnStatus(), "A VPN transport does not prove all-app coverage, provider identity or lockdown is active.");
        addCard(root, "VPN LOCKDOWN", "Always-on / lockdown: not verified", "Configure these OS-managed settings in Android VPN settings. Lockdown can block all network traffic when the VPN is unavailable.");
        addCard(root, "DEVICE INTEGRITY", bootIntegrityStatus(), "Platform-reported boot properties only. Green/locked is not independent vbmeta signature verification.");
        addCard(root, "SELINUX", selinuxStatus(), "Enforcement mode from Android SELinux framework; policy quality and rule coverage are not assessed here.");
        addCard(root, "SECURITY UPDATES", "Security patch: " + safe(Build.VERSION.SECURITY_PATCH), "Android release: " + safe(Build.VERSION.RELEASE) + "  ·  Device: " + safe(Build.DEVICE));
        addCard(root, "SCREEN LOCK", screenLockStatus(), "Read from Android KeyguardManager. This does not verify every lock-screen policy.");
        addCard(root, "DEBUGGING", adbStatus(), "Read from Android global settings. Additional developer options are not assessed here.");
        addCard(root, "PRIVACY & PERMISSIONS", "Permission audit: not yet connected", "Camera, microphone, location and clipboard events require framework-backed providers before they can be reported.");

        Button protonVpn = new Button(this);
        protonVpn.setText("Open Proton VPN");
        protonVpn.setAllCaps(false);
        protonVpn.setTextColor(PRIMARY);
        protonVpn.setBackground(tint(SURFACE, 18));
        LinearLayout.LayoutParams protonParams = params(-1, dp(50));
        protonParams.bottomMargin = dp(8);
        root.addView(protonVpn, protonParams);
        protonVpn.setOnClickListener(v -> openProtonVpn());

        Button vpnSettings = new Button(this);
        vpnSettings.setText("Open Android VPN settings");
        vpnSettings.setAllCaps(false);
        vpnSettings.setTextColor(Color.rgb(8, 18, 34));
        vpnSettings.setBackground(tint(ACCENT, 100));
        LinearLayout.LayoutParams buttonParams = params(-1, dp(52));
        buttonParams.topMargin = dp(8);
        root.addView(vpnSettings, buttonParams);
        vpnSettings.setOnClickListener(v -> openVpnSettings());

        TextView footer = text("Proton VPN is supported as a separate official Android app. GhostOS does not contain a VPN client or Proton credentials.", 12, MUTED, false);
        LinearLayout.LayoutParams footerParams = params(-1, -2);
        footerParams.topMargin = dp(16);
        root.addView(footer, footerParams);
        return scroll;
    }

    private String vpnStatus() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return "Default network: unknown";
        try {
            Network activeNetwork = manager.getActiveNetwork();
            if (activeNetwork == null) return "Default network: unavailable";

            NetworkCapabilities capabilities = manager.getNetworkCapabilities(activeNetwork);
            if (capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return "Default network uses VPN transport · provider/coverage unverified";
            }
            return "Default network does not report VPN transport";
        } catch (RuntimeException error) {
            return "VPN status: unavailable";
        }
    }

    private String bootIntegrityStatus() {
        try {
            String state = readSystemProperty("ro.boot.verifiedbootstate", "unknown");
            String locked = readSystemProperty("ro.boot.flash.locked", "unknown");
            if ("red".equals(state)) return "Platform reports Verified Boot: red";
            if ("orange".equals(state) || "0".equals(locked)) return "Platform reports bootloader unlocked";
            if ("green".equals(state) && "1".equals(locked)) {
                return "Platform reports green state · bootloader locked · AVB not independently verified";
            }
            if ("yellow".equals(state) && "1".equals(locked)) return "Platform reports yellow state · custom trust root";
            return "Verified Boot: unknown";
        } catch (RuntimeException error) {
            return "Verified Boot: unknown";
        }
    }

    private String selinuxStatus() {
        try {
            Boolean enabled = readSelinuxFlag("isSELinuxEnabled");
            if (enabled == null) return "SELinux: unknown";
            if (!enabled) return "SELinux: disabled";
            Boolean enforced = readSelinuxFlag("isSELinuxEnforced");
            if (enforced == null) return "SELinux: unknown";
            return enforced ? "SELinux: enforcing" : "SELinux: permissive";
        } catch (RuntimeException error) {
            return "SELinux: unknown";
        }
    }

    // Platform-only APIs are guarded so SDK-only CI compilation remains possible.
    // Runtime access may be restricted; unavailable evidence is always shown as unknown.
    private String readSystemProperty(String key, String fallback) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Object value = properties.getMethod("get", String.class, String.class)
                    .invoke(null, key, fallback);
            return value instanceof String ? (String) value : fallback;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return fallback;
        }
    }

    private Boolean readSelinuxFlag(String methodName) {
        try {
            Class<?> selinux = Class.forName("android.os.SELinux");
            Object value = selinux.getMethod(methodName).invoke(null);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    private String screenLockStatus() {
        try {
            KeyguardManager keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguard == null) return "Secure screen lock: unknown";
            return keyguard.isDeviceSecure() ? "Secure screen lock: enabled" : "Secure screen lock: not detected";
        } catch (RuntimeException error) {
            return "Secure screen lock: unknown";
        }
    }

    private String adbStatus() {
        try {
            int enabled = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0);
            return enabled == 1 ? "USB debugging: enabled" : "USB debugging: disabled";
        } catch (RuntimeException error) {
            return "USB debugging: unknown";
        }
    }

    private void openProtonVpn() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage("ch.protonvpn.android");
            if (launch != null) {
                startActivity(launch);
            } else {
                Intent releases = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/ProtonVPN/android-app/releases"));
                startActivity(releases);
            }
        } catch (RuntimeException error) {
            // Do not claim Proton VPN is installed or connected when launch is unavailable.
        }
    }

    private void openVpnSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));
        } catch (RuntimeException error) {
            // Keep the app usable if the device build does not expose this settings page.
        }
    }

    private void addCard(LinearLayout root, String heading, String value, String detail) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(15), dp(17), dp(16));
        card.setBackground(tint(SURFACE, 18));
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.bottomMargin = dp(12);
        root.addView(card, cardParams);
        card.addView(text(heading, 10, ACCENT, true));
        TextView valueView = text(value, 17, PRIMARY, true);
        LinearLayout.LayoutParams valueParams = params(-1, -2);
        valueParams.topMargin = dp(8);
        card.addView(valueView, valueParams);
        TextView detailView = text(detail, 12, MUTED, false);
        LinearLayout.LayoutParams detailParams = params(-1, -2);
        detailParams.topMargin = dp(7);
        card.addView(detailView, detailParams);
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable tint(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams params(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
    }
}
