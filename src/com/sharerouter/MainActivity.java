package com.sharerouter;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    // Quark-style palette — gray bg, white cards with borders
    private static final int C_BG          = 0xFFF2F2F7;
    private static final int C_CARD        = 0xFFFFFFFF;
    private static final int C_TEXT_MAIN    = 0xFF1A1A1A;
    private static final int C_TEXT_SUB     = 0xFF8E8E93;
    private static final int C_TEXT_HINT    = 0xFFC4C4C4;
    private static final int C_PRIMARY      = 0xFF4E6EF2;
    private static final int C_SUCCESS      = 0xFF34C759;
    private static final int C_DANGER       = 0xFFFF3B30;
    private static final int C_DIVIDER      = 0xFFE5E5EA;
    private static final int C_SECTION_TITLE= 0xFF8E8E93;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_BG);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(20);
        root.setPadding(pad, dp(44), pad, dp(28));

        // ── App icon ──
        ImageView icon = new ImageView(this);
        icon.setImageResource(getResId("ic_launcher"));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(72), dp(72));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconLp);

        // ── Title ──
        TextView title = new TextView(this);
        title.setText("分享助手");
        title.setTextColor(C_TEXT_MAIN);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(16), 0, 0);
        root.addView(title);

        // ── Subtitle ──
        TextView subtitle = new TextView(this);
        subtitle.setText("智能分享中转站");
        subtitle.setTextColor(C_TEXT_SUB);
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, dp(28));
        root.addView(subtitle);

        // ── Section: permission ──
        root.addView(sectionLabel("权限"));
        root.addView(makeCard(permissionStatusContent(), dp(0)));

        // ── Section: groups ──
        root.addView(sectionLabel("分组"));
        root.addView(makeCard(groupManageContent(), dp(8)));

        // ── Section: usage ──
        root.addView(sectionLabel("使用方法"));
        root.addView(makeCard(howToUseContent(), dp(8)));

        // ── Section: scenarios ──
        root.addView(sectionLabel("支持场景"));
        root.addView(makeCard(scenariosContent(), dp(8)));

        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    /** Small uppercase section label above each card */
    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(C_SECTION_TITLE);
        label.setTextSize(12);
        label.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(4);
        lp.topMargin = dp(20);
        lp.bottomMargin = dp(6);
        label.setLayoutParams(lp);
        return label;
    }

    /** White card with border — no elevation needed */
    private LinearLayout makeCard(View content, int topMargin) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackgroundResource(getResId("bg_home_card"));
        card.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        wrapLp.topMargin = topMargin;
        wrapper.setLayoutParams(wrapLp);
        wrapper.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }

    /** Thin divider line inside cards */
    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(C_DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        d.setLayoutParams(lp);
        return d;
    }

    // ──────────────────────────────────────────────
    //  Permission status card
    // ──────────────────────────────────────────────
    private View permissionStatusContent() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        final boolean hasPermission = checkQueryAllPackagesEffective();

        // Row: title + badge
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView heading = new TextView(this);
        heading.setText("QUERY_ALL_PACKAGES");
        heading.setTextColor(C_TEXT_MAIN);
        heading.setTextSize(15);
        heading.setTypeface(null, Typeface.BOLD);
        row.addView(heading, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(this);
        badge.setText(hasPermission ? "正常" : "需开启");
        badge.setTextColor(hasPermission ? C_SUCCESS : C_DANGER);
        badge.setTextSize(11);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackgroundResource(hasPermission
                ? getResId("bg_success_badge")
                : getResId("bg_danger_badge"));
        row.addView(badge);
        box.addView(row);

        box.addView(dividerWithMargin(dp(12)));

        // Description
        TextView status = new TextView(this);
        status.setText(hasPermission
                ? "权限已生效，可查询所有已安装应用。"
                : "未检测到权限，目标应用列表可能不完整。");
        status.setTextColor(hasPermission ? C_SUCCESS : C_DANGER);
        status.setTextSize(13);
        status.setLineSpacing(dp(3), 1.0f);
        box.addView(status);

        if (!hasPermission) {
            TextView explain = new TextView(this);
            explain.setText("Android 11+ 限制了应用间可见性，请开启权限以获取完整列表。");
            explain.setTextColor(C_TEXT_SUB);
            explain.setTextSize(13);
            explain.setLineSpacing(dp(3), 1.0f);
            LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            exLp.topMargin = dp(8);
            box.addView(explain, exLp);

            TextView btn = new TextView(this);
            btn.setText("去开启");
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(15);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(0, dp(13), 0, dp(13));
            btn.setBackgroundResource(getResId("bg_btn_primary"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(16);
            box.addView(btn, lp);
        }

        return box;
    }

    // ──────────────────────────────────────────────
    //  Group management card
    // ──────────────────────────────────────────────
    private View groupManageContent() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        ArrayList<GroupManager.Group> groups = GroupManager.loadGroups(this);

        // Row: title + count
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView heading = new TextView(this);
        heading.setText("分组管理");
        heading.setTextColor(C_TEXT_MAIN);
        heading.setTextSize(15);
        heading.setTypeface(null, Typeface.BOLD);
        row.addView(heading, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView count = new TextView(this);
        count.setText(groups.size() + " 个分组");
        count.setTextColor(C_TEXT_SUB);
        count.setTextSize(12);
        row.addView(count);
        box.addView(row);

        box.addView(dividerWithMargin(dp(12)));

        // Description
        TextView desc = new TextView(this);
        desc.setText("精确到每个分享入口，分享时快速筛选");
        desc.setTextColor(C_TEXT_SUB);
        desc.setTextSize(13);
        desc.setLineSpacing(dp(3), 1.0f);
        box.addView(desc);

        // Group chips
        if (!groups.isEmpty()) {
            LinearLayout chipsRow = new LinearLayout(this);
            chipsRow.setOrientation(LinearLayout.HORIZONTAL);
            chipsRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipsLp.topMargin = dp(12);

            for (int i = 0; i < groups.size() && i < 4; i++) {
                TextView chip = new TextView(this);
                chip.setText(groups.get(i).name);
                chip.setTextColor(C_PRIMARY);
                chip.setTextSize(12);
                chip.setTypeface(null, Typeface.BOLD);
                chip.setPadding(dp(12), dp(6), dp(12), dp(6));
                chip.setBackgroundResource(getResId("bg_tab_unselected"));
                LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) chipLp.leftMargin = dp(6);
                chipsRow.addView(chip, chipLp);
            }
            if (groups.size() > 4) {
                TextView more = new TextView(this);
                more.setText("+" + (groups.size() - 4));
                more.setTextColor(C_TEXT_HINT);
                more.setTextSize(12);
                LinearLayout.LayoutParams moreLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                moreLp.leftMargin = dp(6);
                chipsRow.addView(more, moreLp);
            }
            box.addView(chipsRow, chipsLp);
        }

        // Button
        TextView btn = new TextView(this);
        btn.setText(groups.isEmpty() ? "创建分组" : "管理分组");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dp(13), 0, dp(13));
        btn.setBackgroundResource(getResId("bg_save_btn"));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, GroupManageActivity.class));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        box.addView(btn, lp);

        return box;
    }

    // ──────────────────────────────────────────────
    //  How to use card
    // ──────────────────────────────────────────────
    private View howToUseContent() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        String[] steps = {
                "在任意 App 中点击「分享」",
                "在系统分享列表中选择分享助手",
                "查看分享来源和内容摘要",
                "点击目标 App 即可转发",
                "点击右侧按钮可置顶常用应用"
        };
        for (int i = 0; i < steps.length; i++) {
            if (i > 0) {
                box.addView(dividerWithMargin(dp(2)));
            }

            LinearLayout stepRow = new LinearLayout(this);
            stepRow.setOrientation(LinearLayout.HORIZONTAL);
            stepRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            srLp.topMargin = i == 0 ? dp(0) : dp(10);
            srLp.bottomMargin = i == steps.length - 1 ? dp(0) : dp(10);

            // Number circle
            TextView num = new TextView(this);
            num.setText(String.valueOf(i + 1));
            num.setTextColor(Color.WHITE);
            num.setTextSize(11);
            num.setTypeface(null, Typeface.BOLD);
            num.setGravity(Gravity.CENTER);
            num.setBackgroundResource(getResId("bg_num_circle"));
            stepRow.addView(num, new LinearLayout.LayoutParams(dp(22), dp(22)));

            TextView stepText = new TextView(this);
            stepText.setText(steps[i]);
            stepText.setTextColor(C_TEXT_MAIN);
            stepText.setTextSize(14);
            stepText.setLineSpacing(dp(2), 1.0f);
            LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            stLp.leftMargin = dp(12);
            stepRow.addView(stepText, stLp);

            box.addView(stepRow, srLp);
        }

        return box;
    }

    // ──────────────────────────────────────────────
    //  Supported scenarios card
    // ──────────────────────────────────────────────
    private View scenariosContent() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        String[][] items = {
                {"链接", "浏览器、聊天软件中的链接"},
                {"图片视频", "相册、相机中的媒体文件"},
                {"文档", "PDF、Word、Excel 等任意格式"},
                {"多文件", "多个文件同时转发"},
                {"混合内容", "带文本和文件的组合内容"}
        };
        int[] accentColors = {
                0xFF4E6EF2, 0xFF34C759, 0xFFFF9500, 0xFFAF52DE, 0xFFFF2D55
        };

        for (int i = 0; i < items.length; i++) {
            if (i > 0) {
                box.addView(dividerWithMargin(dp(2)));
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.topMargin = i == 0 ? dp(0) : dp(10);
            rLp.bottomMargin = i == items.length - 1 ? dp(0) : dp(10);

            // Colored circle with first character
            LinearLayout iconWrap = new LinearLayout(this);
            iconWrap.setOrientation(LinearLayout.HORIZONTAL);
            iconWrap.setGravity(Gravity.CENTER);
            ShapeDrawable circleBg = new ShapeDrawable(new OvalShape());
            circleBg.getPaint().setColor(accentColors[i]);
            if (Build.VERSION.SDK_INT >= 16) {
                iconWrap.setBackground(circleBg);
            }
            TextView iconT = new TextView(this);
            iconT.setText(items[i][0].substring(0, 1));
            iconT.setTextColor(Color.WHITE);
            iconT.setTextSize(13);
            iconT.setTypeface(null, Typeface.BOLD);
            iconT.setGravity(Gravity.CENTER);
            iconWrap.addView(iconT, new LinearLayout.LayoutParams(dp(24), dp(24)));
            row.addView(iconWrap, new LinearLayout.LayoutParams(dp(32), dp(32)));

            // Text
            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tbLp.leftMargin = dp(12);

            TextView name = new TextView(this);
            name.setText(items[i][0]);
            name.setTextColor(C_TEXT_MAIN);
            name.setTextSize(14);
            name.setTypeface(null, Typeface.BOLD);
            textBox.addView(name);

            TextView desc = new TextView(this);
            desc.setText(items[i][1]);
            desc.setTextColor(C_TEXT_SUB);
            desc.setTextSize(12);
            textBox.addView(desc);

            row.addView(textBox, tbLp);
            box.addView(row, rLp);
        }

        return box;
    }

    // ──────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────
    private View dividerWithMargin(int topMargin) {
        View d = new View(this);
        d.setBackgroundColor(C_DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = topMargin;
        d.setLayoutParams(lp);
        return d;
    }

    private boolean checkQueryAllPackagesEffective() {
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            return apps != null && apps.size() > 15;
        } catch (Throwable t) {
            return false;
        }
    }

    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "请找到「权限」并开启「查询所有应用」", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show();
        }
    }

    private int getResId(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
