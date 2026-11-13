package org.ghostos.auditor;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Read-only, platform-backed device audit screen. */
public final class MainActivity extends Activity {
    private static final int BACKGROUND = Color.rgb(10, 14, 24);
    private static final int SURFACE = Color.rgb(22, 29, 44);
    private static final int PRIMARY = Color.rgb(241, 245, 252);
    private static final int MUTED = Color.rgb(164, 178, 199);
    private static final int ACCENT = Color.rgb(111, 170, 255);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(32));
        scroll.addView(root);

        root.addView(text("GHOSTOS  /  READ-ONLY AUDIT", 11, ACCENT, true));
        TextView title = text("Device Auditor", 30, PRIMARY, true);
        LinearLayout.LayoutParams titleParams = params(-1, -2);
        titleParams.topMargin = dp(7);
        root.addView(title, titleParams);
        TextView intro = text("Platform-reported observations for this boot. Unknown means evidence was unavailable.",
                14, MUTED, false);
        LinearLayout.LayoutParams introParams = params(-1, -2);
        introParams.topMargin = dp(8);
        introParams.bottomMargin = dp(20);
        root.addView(intro, introParams);

        for (AuditSnapshot.Finding finding : AuditSnapshot.collect(new AndroidSource(this))) {
            addFinding(root, finding);
        }

        Button refresh = new Button(this);
        refresh.setText("Refresh audit snapshot");
        refresh.setAllCaps(false);
        refresh.setTextColor(PRIMARY);
        refresh.setBackground(tint(SURFACE, 18));
        LinearLayout.LayoutParams refreshParams = params(-1, dp(50));
        refreshParams.topMargin = dp(8);
        root.addView(refresh, refreshParams);
        refresh.setOnClickListener((View view) -> render());

        TextView footer = text("Read-only only. No policy, VPN, bootloader, SELinux or update settings are changed. "
                + "This screen is not remote attestation or a complete security certification.", 12, MUTED, false);
        LinearLayout.LayoutParams footerParams = params(-1, -2);
        footerParams.topMargin = dp(18);
        root.addView(footer, footerParams);
        setContentView(scroll);
    }

    private void addFinding(LinearLayout root, AuditSnapshot.Finding finding) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(15));
        card.setBackground(tint(SURFACE, 18));
        LinearLayout.LayoutParams cardParams = params(-1, -2);
        cardParams.bottomMargin = dp(11);
        root.addView(card, cardParams);
        card.addView(text(finding.title.toUpperCase(), 10, ACCENT, true));
        TextView state = text(finding.state.name(), 11, stateColor(finding.state), true);
        LinearLayout.LayoutParams stateParams = params(-1, -2);
        stateParams.topMargin = dp(7);
        card.addView(state, stateParams);
        TextView value = text(finding.value, 17, PRIMARY, true);
        LinearLayout.LayoutParams valueParams = params(-1, -2);
        valueParams.topMargin = dp(5);
        card.addView(value, valueParams);
        TextView evidence = text("Source: " + finding.source + "\n" + finding.limitation, 12, MUTED, false);
        LinearLayout.LayoutParams evidenceParams = params(-1, -2);
        evidenceParams.topMargin = dp(7);
        card.addView(evidence, evidenceParams);
    }

    private int stateColor(AuditSnapshot.State state) {
        switch (state) {
            case PASS: return Color.rgb(105, 210, 155);
            case WARNING: return Color.rgb(255, 190, 95);
            case FAIL: return Color.rgb(255, 112, 112);
            case OBSERVED: return ACCENT;
            default: return MUTED;
        }
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

    private static final class AndroidSource implements AuditSnapshot.Source {
        private final Context context;

        AndroidSource(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public String property(String key) {
            try {
                Class<?> properties = Class.forName("android.os.SystemProperties");
                Object value = properties.getMethod("get", String.class, String.class).invoke(null, key, "");
                return value instanceof String ? (String) value : "";
            } catch (ReflectiveOperationException | RuntimeException error) {
                return "";
            }
        }

        @Override public Boolean selinuxEnabled() { return selinuxFlag("isSELinuxEnabled"); }
        @Override public Boolean selinuxEnforced() { return selinuxFlag("isSELinuxEnforced"); }

        private Boolean selinuxFlag(String method) {
            try {
                Class<?> selinux = Class.forName("android.os.SELinux");
                Object value = selinux.getMethod(method).invoke(null);
                return value instanceof Boolean ? (Boolean) value : null;
            } catch (ReflectiveOperationException | RuntimeException error) {
                return null;
            }
        }

        @Override
        public Boolean secureScreenLock() {
            try {
                KeyguardManager manager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                return manager == null ? null : manager.isDeviceSecure();
            } catch (RuntimeException error) {
                return null;
            }
        }

        @Override
        public Boolean adbEnabled() {
            try {
                return Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED) == 1;
            } catch (Settings.SettingNotFoundException | RuntimeException error) {
                return null;
            }
        }

        @Override public String securityPatch() { return android.os.Build.VERSION.SECURITY_PATCH; }
        @Override public String androidRelease() { return android.os.Build.VERSION.RELEASE; }
        @Override public String device() { return android.os.Build.DEVICE; }
        @Override public int sdkInt() { return android.os.Build.VERSION.SDK_INT; }
    }
}
