package com.sharerouter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class GroupManageActivity extends Activity {

    // Quark-style color palette — gray bg, white cards with borders
    private static final int C_BG = 0xFFF2F2F7;
    private static final int C_CARD = 0xFFFFFFFF;
    private static final int C_TEXT_MAIN = 0xFF1A1A1A;
    private static final int C_TEXT_SUB = 0xFF8E8E93;
    private static final int C_TEXT_HINT = 0xFFC4C4C4;
    private static final int C_PRIMARY = 0xFF4E6EF2;
    private static final int C_DANGER = 0xFFFF3B30;
    private static final int C_SUCCESS = 0xFF34C759;
    private static final int C_DIVIDER = 0xFFE5E5EA;

    private ArrayList<GroupManager.Group> groups;
    private LinearLayout groupsContainer;

    // Edit mode
    private String editingGroupName;
    private HashSet<String> editingTargets; // "pkg/cls" keys
    private ArrayList<TargetInfo> allTargets;
    private TextView editSearchHint;
    private EditText editSearchBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        groups = GroupManager.loadGroups(this);
        loadAllTargets();
        showListView();
    }

    /**
     * Query ALL share-capable targets via multiple strategies.
     * Each (pkg, cls) pair is a separate entry, so an app with
     * multiple share activities shows multiple rows.
     */
    private void loadAllTargets() {
        allTargets = new ArrayList<TargetInfo>();
        HashMap<String, TargetInfo> dedup = new HashMap<String, TargetInfo>();
        PackageManager pm = getPackageManager();
        String myPkg = getPackageName();

        // Strategy 1: ACTION_SEND with */*
        addShareTargets(dedup, pm, myPkg, Intent.ACTION_SEND, "*/*");
        // Strategy 2: ACTION_SEND with text/plain
        addShareTargets(dedup, pm, myPkg, Intent.ACTION_SEND, "text/plain");
        // Strategy 3: ACTION_SEND with image/*
        addShareTargets(dedup, pm, myPkg, Intent.ACTION_SEND, "image/*");
        // Strategy 4: ACTION_SEND_MULTIPLE with */*
        addShareTargets(dedup, pm, myPkg, Intent.ACTION_SEND_MULTIPLE, "*/*");
        // Strategy 5: ACTION_VIEW with */*
        addShareTargets(dedup, pm, myPkg, Intent.ACTION_VIEW, "*/*");

        allTargets = new ArrayList<TargetInfo>(dedup.values());

        // Sort by label
        Collections.sort(allTargets, new Comparator<TargetInfo>() {
            @Override
            public int compare(TargetInfo a, TargetInfo b) {
                return a.label.toLowerCase().compareTo(b.label.toLowerCase());
            }
        });
    }

    private void addShareTargets(HashMap<String, TargetInfo> map,
                                 PackageManager pm, String myPkg,
                                 String action, String mimeType) {
        try {
            Intent probe = new Intent(action);
            probe.setType(mimeType);
            probe.addCategory(Intent.CATEGORY_DEFAULT);
            List<ResolveInfo> infos = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL);
            if (infos == null) return;
            for (int i = 0; i < infos.size(); i++) {
                ResolveInfo ri = infos.get(i);
                if (ri == null || ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                String cls = ri.activityInfo.name;
                if (pkg == null || cls == null || myPkg.equals(pkg)) continue;
                String key = GroupManager.makeKey(pkg, cls);
                if (map.containsKey(key)) continue;

                TargetInfo ti = new TargetInfo();
                ti.packageName = pkg;
                ti.className = cls;
                ti.key = key;
                ti.label = String.valueOf(ri.loadLabel(pm));
                // Try to load icon — if fails, stays null (placeholder shown)
                try {
                    ti.icon = pm.getApplicationIcon(pkg);
                } catch (Throwable ignored) {}
                // Load app name for grouping display
                try {
                    ti.appName = String.valueOf(pm.getApplicationLabel(
                            pm.getApplicationInfo(pkg, 0)));
                } catch (Throwable ignored) {
                    ti.appName = pkg;
                }

                map.put(key, ti);
            }
        } catch (Throwable ignored) {}
    }

    // ──────────────────────────────────────────────
    //  List View — shows all groups
    // ──────────────────────────────────────────────
    private void showListView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(C_BG);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        // Title
        TextView title = new TextView(this);
        title.setText("分组管理");
        title.setTextColor(C_TEXT_MAIN);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(8), 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("精确到每个分享项，将常用的分享目标分组");
        subtitle.setTextColor(C_TEXT_SUB);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 0, 0, dp(20));
        root.addView(subtitle);

        // Groups container
        groupsContainer = new LinearLayout(this);
        groupsContainer.setOrientation(LinearLayout.VERTICAL);
        refreshGroupCards();
        root.addView(groupsContainer);

        // "New group" button
        TextView newBtn = new TextView(this);
        newBtn.setText("+ 新建分组");
        newBtn.setTextColor(Color.WHITE);
        newBtn.setTextSize(15);
        newBtn.setTypeface(null, Typeface.BOLD);
        newBtn.setGravity(Gravity.CENTER);
        newBtn.setPadding(0, dp(14), 0, dp(14));
        newBtn.setBackgroundResource(getResId("bg_save_btn"));
        if (Build.VERSION.SDK_INT >= 21) newBtn.setElevation(dp(2));
        LinearLayout.LayoutParams newLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        newLp.topMargin = dp(16);
        newBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateGroupDialog();
            }
        });
        root.addView(newBtn, newLp);

        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void refreshGroupCards() {
        groupsContainer.removeAllViews();
        groups = GroupManager.loadGroups(this);

        if (groups.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有分组\n点击下方按钮创建第一个分组");
            empty.setTextColor(C_TEXT_HINT);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(3), 1.0f);
            empty.setPadding(0, dp(40), 0, dp(20));
            groupsContainer.addView(empty);
            return;
        }

        for (int i = 0; i < groups.size(); i++) {
            final GroupManager.Group g = groups.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(16), dp(12), dp(16));
            card.setBackgroundResource(getResId("bg_home_card"));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = dp(10);

            // Group icon — first character in accent circle (no emoji)
            TextView icon = new TextView(this);
            icon.setText(g.name.length() > 0 ? g.name.substring(0, 1) : "#");
            icon.setTextColor(C_PRIMARY);
            icon.setTextSize(15);
            icon.setTypeface(null, Typeface.BOLD);
            icon.setGravity(Gravity.CENTER);
            LinearLayout iconWrap = new LinearLayout(this);
            iconWrap.setOrientation(LinearLayout.HORIZONTAL);
            iconWrap.setGravity(Gravity.CENTER);
            iconWrap.setBackgroundResource(getResId("bg_scenario_icon"));
            iconWrap.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
            card.addView(iconWrap, new LinearLayout.LayoutParams(dp(40), dp(40)));

            // Name + count
            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tbLp.leftMargin = dp(12);

            TextView name = new TextView(this);
            name.setText(g.name);
            name.setTextColor(C_TEXT_MAIN);
            name.setTextSize(16);
            name.setTypeface(null, Typeface.BOLD);
            textBox.addView(name);

            TextView count = new TextView(this);
            count.setText(g.targets.size() + " 个分享项");
            count.setTextColor(C_TEXT_SUB);
            count.setTextSize(12);
            LinearLayout.LayoutParams cntLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cntLp.topMargin = dp(2);
            textBox.addView(count, cntLp);
            card.addView(textBox, tbLp);

            // Edit button
            TextView editBtn = new TextView(this);
            editBtn.setText("编辑");
            editBtn.setTextColor(C_PRIMARY);
            editBtn.setTextSize(13);
            editBtn.setTypeface(null, Typeface.BOLD);
            editBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
            editBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startEditGroup(g.name);
                }
            });
            card.addView(editBtn);

            groupsContainer.addView(card, cardLp);
        }
    }

    private void showCreateGroupDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("输入分组名称");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
                .setTitle("新建分组")
                .setView(input)
                .setPositiveButton("创建", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) {
                            Toast.makeText(GroupManageActivity.this, "名称不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (name.contains("|") || name.contains(":")) {
                            Toast.makeText(GroupManageActivity.this, "名称不能包含 | 或 :", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        GroupManager.createGroup(GroupManageActivity.this, name);
                        startEditGroup(name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ──────────────────────────────────────────────
    //  Edit View — edit a specific group
    // ──────────────────────────────────────────────
    private void startEditGroup(String groupName) {
        editingGroupName = groupName;
        groups = GroupManager.loadGroups(this);
        editingTargets = new HashSet<String>();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).name.equals(groupName)) {
                editingTargets = new HashSet<String>(groups.get(i).targets);
                break;
            }
        }
        showEditView();
    }

    private ArrayList<TargetInfo> filteredTargets;
    private TargetCheckAdapter editAdapter;

    private void showEditView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // Top bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(20), dp(16), dp(20), dp(8));

        TextView backBtn = new TextView(this);
        backBtn.setText("\u2190 返回");
        backBtn.setTextColor(C_PRIMARY);
        backBtn.setTextSize(14);
        backBtn.setTypeface(null, Typeface.BOLD);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showListView();
            }
        });
        topBar.addView(backBtn);

        TextView title = new TextView(this);
        title.setText(editingGroupName);
        title.setTextColor(C_TEXT_MAIN);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(12);
        titleLp.rightMargin = dp(12);
        topBar.addView(title, titleLp);

        TextView delBtn = new TextView(this);
        delBtn.setText("删除");
        delBtn.setTextColor(C_DANGER);
        delBtn.setTextSize(14);
        delBtn.setTypeface(null, Typeface.BOLD);
        delBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(GroupManageActivity.this)
                        .setTitle("删除分组")
                        .setMessage("确定删除「" + editingGroupName + "」？")
                        .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                GroupManager.deleteGroup(GroupManageActivity.this, editingGroupName);
                                showListView();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        topBar.addView(delBtn);
        root.addView(topBar);

        // Hint
        TextView hint = new TextView(this);
        hint.setText("勾选要加入此分组的分享项（精确到每个入口）");
        hint.setTextColor(C_TEXT_SUB);
        hint.setTextSize(13);
        hint.setPadding(dp(20), 0, dp(20), dp(8));
        root.addView(hint);

        // Search box for filtering
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackgroundResource(getResId("bg_search_pill"));
        searchBar.setPadding(dp(16), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.leftMargin = dp(16);
        searchLp.rightMargin = dp(16);
        searchLp.bottomMargin = dp(8);

        editSearchBox = new EditText(this);
        editSearchBox.setHint("搜索分享项…");
        editSearchBox.setTextColor(C_TEXT_MAIN);
        editSearchBox.setHintTextColor(C_TEXT_HINT);
        editSearchBox.setTextSize(14);
        editSearchBox.setBackgroundColor(Color.TRANSPARENT);
        editSearchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        editSearchBox.setSingleLine(true);
        editSearchBox.setPadding(dp(8), 0, 0, 0);
        editSearchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyEditFilter();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        searchBar.addView(editSearchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(searchBar, searchLp);

        // Selected count
        editSearchHint = new TextView(this);
        updateSelectedCount();
        editSearchHint.setTextColor(C_TEXT_SUB);
        editSearchHint.setTextSize(12);
        editSearchHint.setPadding(dp(20), 0, dp(20), dp(4));
        root.addView(editSearchHint);

        // App list
        filteredTargets = new ArrayList<TargetInfo>(allTargets);
        ListView listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setPadding(dp(14), 0, dp(14), dp(8));
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        editAdapter = new TargetCheckAdapter();
        listView.setAdapter(editAdapter);
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Save button
        TextView saveBtn = new TextView(this);
        saveBtn.setText("保存");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(15);
        saveBtn.setTypeface(null, Typeface.BOLD);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(0, dp(14), 0, dp(14));
        saveBtn.setBackgroundResource(getResId("bg_save_btn"));
        if (Build.VERSION.SDK_INT >= 21) saveBtn.setElevation(dp(2));
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentGroup();
                Toast.makeText(GroupManageActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                showListView();
            }
        });
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.leftMargin = dp(16);
        saveLp.rightMargin = dp(16);
        saveLp.bottomMargin = dp(16);
        root.addView(saveBtn, saveLp);

        setContentView(root);
    }

    private void applyEditFilter() {
        String query = editSearchBox.getText().toString().trim().toLowerCase();
        filteredTargets.clear();
        for (int i = 0; i < allTargets.size(); i++) {
            TargetInfo t = allTargets.get(i);
            if (query.length() == 0
                    || t.label.toLowerCase().contains(query)
                    || t.appName.toLowerCase().contains(query)
                    || t.packageName.toLowerCase().contains(query)) {
                filteredTargets.add(t);
            }
        }
        if (editAdapter != null) editAdapter.notifyDataSetChanged();
    }

    private void updateSelectedCount() {
        if (editSearchHint != null) {
            editSearchHint.setText("已选 " + editingTargets.size() + " 项 · 共 " + allTargets.size() + " 个分享项");
        }
    }

    private void saveCurrentGroup() {
        groups = GroupManager.loadGroups(this);
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).name.equals(editingGroupName)) {
                groups.get(i).targets = new HashSet<String>(editingTargets);
                break;
            }
        }
        GroupManager.saveGroups(this, groups);
    }

    // ──────────────────────────────────────────────
    //  Target checkbox adapter
    // ──────────────────────────────────────────────
    private class TargetCheckAdapter extends BaseAdapter {
        @Override
        public int getCount() { return filteredTargets.size(); }
        @Override
        public Object getItem(int pos) { return filteredTargets.get(pos); }
        @Override
        public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final TargetInfo t = filteredTargets.get(position);
            final boolean checked = editingTargets.contains(t.key);

            LinearLayout row = new LinearLayout(GroupManageActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackgroundResource(checked
                    ? getResId("bg_list_item_pinned")
                    : getResId("bg_list_item"));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);

            // App icon
            LinearLayout iconWrap = new LinearLayout(GroupManageActivity.this);
            iconWrap.setOrientation(LinearLayout.HORIZONTAL);
            iconWrap.setGravity(Gravity.CENTER);
            iconWrap.setBackgroundResource(getResId("bg_icon_circle"));
            if (t.icon != null) {
                ImageView icon = new ImageView(GroupManageActivity.this);
                icon.setImageDrawable(t.icon);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iconWrap.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
            } else {
                TextView placeholder = new TextView(GroupManageActivity.this);
                String firstChar = t.label.length() > 0
                        ? t.label.substring(0, 1).toUpperCase() : "?";
                placeholder.setText(firstChar);
                placeholder.setTextColor(C_TEXT_HINT);
                placeholder.setTextSize(14);
                placeholder.setTypeface(null, Typeface.BOLD);
                placeholder.setGravity(Gravity.CENTER);
                iconWrap.addView(placeholder, new LinearLayout.LayoutParams(dp(28), dp(28)));
            }
            row.addView(iconWrap, new LinearLayout.LayoutParams(dp(38), dp(38)));

            // Label + sub-label
            LinearLayout textBox = new LinearLayout(GroupManageActivity.this);
            textBox.setOrientation(LinearLayout.VERTICAL);

            TextView label = new TextView(GroupManageActivity.this);
            label.setText(t.label);
            label.setTextColor(checked ? C_PRIMARY : C_TEXT_MAIN);
            label.setTextSize(14);
            label.setTypeface(null, checked ? Typeface.BOLD : Typeface.NORMAL);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textBox.addView(label);

            // Show app name + class name as sub-label
            TextView sub = new TextView(GroupManageActivity.this);
            sub.setText(t.appName + " · " + t.className);
            sub.setTextColor(C_TEXT_HINT);
            sub.setTextSize(10);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.topMargin = dp(1);
            textBox.addView(sub, subLp);

            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textLp.leftMargin = dp(10);
            row.addView(textBox, textLp);

            // Checkbox indicator
            TextView check = new TextView(GroupManageActivity.this);
            check.setText(checked ? "\u2714" : "\u25CB"); // check or circle
            check.setTextColor(checked ? C_SUCCESS : C_TEXT_HINT);
            check.setTextSize(18);
            check.setGravity(Gravity.CENTER);
            row.addView(check, new LinearLayout.LayoutParams(dp(30), dp(30)));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (editingTargets.contains(t.key)) {
                        editingTargets.remove(t.key);
                    } else {
                        editingTargets.add(t.key);
                    }
                    notifyDataSetChanged();
                    updateSelectedCount();
                }
            });

            return row;
        }
    }

    // ──────────────────────────────────────────────
    //  Data + helpers
    // ──────────────────────────────────────────────
    private static class TargetInfo {
        String packageName;
        String className;
        String key;   // "pkg/cls"
        String label;
        String appName;
        Drawable icon;
    }

    private int getResId(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
