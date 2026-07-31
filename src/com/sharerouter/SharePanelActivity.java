package com.sharerouter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class SharePanelActivity extends Activity {

    private static final String PREFS_NAME = "sharerouter_prefs";
    private static final String KEY_PINNED = "pinned_packages";
    private static final String KEY_SORT_MODE = "sort_mode";

    // Sort modes
    private static final int SORT_ALPHA = 0;
    private static final int SORT_FREQ = 1;
    private int sortMode = SORT_ALPHA;

    // Quark-style color palette — gray bg, white items with borders
    private static final int C_BG          = 0xFFF2F2F7;
    private static final int C_TEXT_MAIN    = 0xFF1A1A1A;
    private static final int C_TEXT_SUB     = 0xFF8E8E93;
    private static final int C_TEXT_HINT    = 0xFFC4C4C4;
    private static final int C_PRIMARY      = 0xFF4E6EF2;
    private static final int C_PINNED_ACCENT= 0xFF4E6EF2;
    private static final int C_SUMMARY_TEXT = 0xFF4E6EF2;
    private static final int C_ERROR        = 0xFFFF3B30;
    private static final int C_WARNING      = 0xFFFF9500;
    private static final int C_DIVIDER      = 0xFFE5E5EA;

    private ParsedShare parsedShare;
    private final ArrayList<ShareTarget> allTargets = new ArrayList<ShareTarget>();
    private final ArrayList<ShareTarget> displayTargets = new ArrayList<ShareTarget>();
    private final HashSet<String> pinnedPackages = new HashSet<String>();
    private TargetAdapter adapter;
    private TextView sourceText;
    private TextView summaryText;
    private LinearLayout summaryCard;
    private TextView statusText;
    private View continueButton;
    private TextView permissionHint;
    private EditText searchBox;
    private TextView clearBtn;
    private TextView sortBtn;
    private TextView sortFreqTab;
    private LinearLayout pinnedSection;
    private ListView listView;
    private SharedPreferences prefs;

    // Groups
    private ArrayList<GroupManager.Group> groups = new ArrayList<GroupManager.Group>();
    private LinearLayout tabBar;
    private int selectedTabIndex = 0; // 0 = all, 1+ = group index
    private ArrayList<TextView> tabViews = new ArrayList<TextView>();

    // Icon loading
    private volatile boolean iconsLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sortMode = prefs.getInt(KEY_SORT_MODE, SORT_ALPHA);
        loadPinned();
        loadGroups();
        buildUi();
        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShareIntent(intent);
    }

    private void loadPinned() {
        pinnedPackages.clear();
        String saved = prefs.getString(KEY_PINNED, "");
        if (saved != null && saved.length() > 0) {
            String[] arr = saved.split(",");
            for (int i = 0; i < arr.length; i++) {
                if (arr[i].trim().length() > 0) {
                    pinnedPackages.add(arr[i].trim());
                }
            }
        }
    }

    private void loadGroups() {
        groups = GroupManager.loadGroups(this);
    }

    private void savePinned() {
        StringBuilder sb = new StringBuilder();
        for (String pkg : pinnedPackages) {
            if (sb.length() > 0) sb.append(",");
            sb.append(pkg);
        }
        prefs.edit().putString(KEY_PINNED, sb.toString()).apply();
    }

    // ──────────────────────────────────────────────
    //  Usage frequency tracking
    // ──────────────────────────────────────────────
    private int getUsage(String pkg, String cls) {
        return prefs.getInt("usage_" + pkg + "/" + cls, 0);
    }

    private void incrementUsage(String pkg, String cls) {
        String key = "usage_" + pkg + "/" + cls;
        int count = prefs.getInt(key, 0);
        prefs.edit().putInt(key, count + 1).apply();
    }

    // ──────────────────────────────────────────────
    //  Sort comparator — respects sort mode + pinned
    // ──────────────────────────────────────────────
    private Comparator<ShareTarget> makeComparator() {
        final int mode = sortMode;
        return new Comparator<ShareTarget>() {
            @Override
            public int compare(ShareTarget a, ShareTarget b) {
                boolean aP = pinnedPackages.contains(GroupManager.makeKey(a.packageName, a.className));
                boolean bP = pinnedPackages.contains(GroupManager.makeKey(b.packageName, b.className));
                if (aP && !bP) return -1;
                if (!aP && bP) return 1;
                if (mode == SORT_FREQ) {
                    int aF = getUsage(a.packageName, a.className);
                    int bF = getUsage(b.packageName, b.className);
                    if (aF != bF) return bF - aF;
                }
                return a.label.toLowerCase().compareTo(b.label.toLowerCase());
            }
        };
    }

    // ──────────────────────────────────────────────
    //  Fuzzy search matching — checks label, appName, and package name
    // ──────────────────────────────────────────────
    private boolean fuzzyMatch(String query, String label, String appName, String pkg) {
        String q = query.toLowerCase();
        if (q.length() == 0) return true;

        // Match against share target label (e.g. "发送给朋友")
        if (label != null && singleFieldMatch(q, label.toLowerCase())) return true;
        // Match against application name (e.g. "微信") — this is what users actually search for
        if (appName != null && singleFieldMatch(q, appName.toLowerCase())) return true;
        // Match against package name
        if (pkg != null && pkg.toLowerCase().contains(q)) return true;
        return false;
    }

    /** Checks if query matches a field via contains, word-prefix, or subsequence */
    private boolean singleFieldMatch(String q, String field) {
        if (field == null || field.length() == 0) return false;
        // 1. Direct contains
        if (field.contains(q)) return true;
        // 2. Word boundary — query matches start of any word
        String[] words = field.split("[\\s_\\-.·]+");
        for (int i = 0; i < words.length; i++) {
            if (words[i].startsWith(q)) return true;
        }
        // 3. Subsequence match — all chars of query appear in order
        int qi = 0;
        for (int ti = 0; ti < field.length() && qi < q.length(); ti++) {
            if (q.charAt(qi) == field.charAt(ti)) qi++;
        }
        if (qi == q.length()) return true;
        return false;
    }

    /** Updates the visual style of the two sort tabs based on current sortMode */
    private void updateSortTabStyles(TextView alphaTab, TextView freqTab) {
        boolean alpha = (sortMode == SORT_ALPHA);
        alphaTab.setBackgroundResource(alpha
                ? getResId("bg_tab_selected") : getResId("bg_tab_unselected"));
        alphaTab.setTextColor(alpha ? Color.WHITE : C_TEXT_SUB);
        freqTab.setBackgroundResource(!alpha
                ? getResId("bg_tab_selected") : getResId("bg_tab_unselected"));
        freqTab.setTextColor(!alpha ? Color.WHITE : C_TEXT_SUB);
    }

    private void togglePin(String packageName, String className) {
        String key = GroupManager.makeKey(packageName, className);
        if (pinnedPackages.contains(key)) {
            pinnedPackages.remove(key);
            Toast.makeText(this, "已取消置顶", Toast.LENGTH_SHORT).show();
        } else {
            pinnedPackages.add(key);
            Toast.makeText(this, "已置顶", Toast.LENGTH_SHORT).show();
        }
        savePinned();
        filterAndRefresh();
    }

    // ──────────────────────────────────────────────
    //  UI Construction
    // ──────────────────────────────────────────────
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // Grab handle
        View grabHandle = new View(this);
        grabHandle.setBackgroundResource(getResId("bg_grab_handle"));
        LinearLayout.LayoutParams grabLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        grabLp.gravity = Gravity.CENTER_HORIZONTAL;
        grabLp.topMargin = dp(10);
        grabLp.bottomMargin = dp(6);
        root.addView(grabHandle, grabLp);

        // Top border line — makes the bottom sheet edge clearly visible
        View topBorder = new View(this);
        topBorder.setBackgroundColor(C_DIVIDER);
        root.addView(topBorder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(4), dp(12), dp(4));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("分享到");
        title.setTextColor(C_TEXT_MAIN);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        titleBox.addView(title);

        sourceText = new TextView(this);
        sourceText.setTextColor(C_TEXT_SUB);
        sourceText.setTextSize(12);
        sourceText.setSingleLine(true);
        sourceText.setVisibility(View.GONE);
        LinearLayout.LayoutParams srcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srcLp.topMargin = dp(2);
        titleBox.addView(sourceText, srcLp);

        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Close button
        LinearLayout closeWrap = new LinearLayout(this);
        closeWrap.setOrientation(LinearLayout.HORIZONTAL);
        closeWrap.setGravity(Gravity.CENTER);
        closeWrap.setBackgroundResource(getResId("bg_close_btn"));
        TextView closeText = new TextView(this);
        closeText.setText("\u2715");
        closeText.setTextColor(0xFF999999);
        closeText.setTextSize(16);
        closeText.setGravity(Gravity.CENTER);
        closeWrap.addView(closeText, new LinearLayout.LayoutParams(dp(32), dp(32)));
        closeWrap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        header.addView(closeWrap, new LinearLayout.LayoutParams(dp(36), dp(36)));
        root.addView(header);

        // Summary card
        summaryCard = new LinearLayout(this);
        summaryCard.setOrientation(LinearLayout.HORIZONTAL);
        summaryCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        summaryCard.setGravity(Gravity.CENTER_VERTICAL);
        summaryCard.setBackgroundResource(getResId("bg_summary_card"));
        LinearLayout.LayoutParams summaryLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryLp.leftMargin = dp(16);
        summaryLp.rightMargin = dp(16);
        summaryLp.topMargin = dp(4);
        summaryLp.bottomMargin = dp(4);

        summaryText = new TextView(this);
        summaryText.setTextColor(C_SUMMARY_TEXT);
        summaryText.setTextSize(13);
        summaryText.setLineSpacing(dp(2), 1.0f);
        summaryText.setMaxLines(3);
        summaryText.setSingleLine(false);
        LinearLayout.LayoutParams sumTextLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        summaryCard.addView(summaryText, sumTextLp);
        root.addView(summaryCard, summaryLp);

        // Search bar
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackgroundResource(getResId("bg_search_pill"));
        searchBar.setPadding(dp(16), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.leftMargin = dp(16);
        searchLp.rightMargin = dp(16);
        searchLp.topMargin = dp(4);
        searchLp.bottomMargin = dp(2);

        searchBox = new EditText(this);
        searchBox.setHint("搜索应用…");
        searchBox.setTextColor(C_TEXT_MAIN);
        searchBox.setHintTextColor(C_TEXT_HINT);
        searchBox.setTextSize(14);
        searchBox.setBackgroundColor(Color.TRANSPARENT);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchBox.setSingleLine(true);
        searchBox.setPadding(dp(4), 0, 0, 0);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterAndRefresh();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                return true;
            }
        });
        searchBar.addView(searchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        clearBtn = new TextView(this);
        clearBtn.setText("\u2715");
        clearBtn.setTextColor(C_TEXT_HINT);
        clearBtn.setTextSize(13);
        clearBtn.setPadding(dp(6), 0, 0, 0);
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchBox.setText("");
            }
        });
        searchBar.addView(clearBtn);
        root.addView(searchBar, searchLp);

        // ── Sort selection bar ── A dedicated row with two toggle pills
        LinearLayout sortBar = new LinearLayout(this);
        sortBar.setOrientation(LinearLayout.HORIZONTAL);
        sortBar.setGravity(Gravity.CENTER);
        sortBar.setPadding(dp(16), dp(6), dp(16), dp(2));

        final TextView alphaTab = new TextView(this);
        alphaTab.setText("A-Z 字母排序");
        alphaTab.setTextSize(12);
        alphaTab.setTypeface(null, Typeface.BOLD);
        alphaTab.setGravity(Gravity.CENTER);
        alphaTab.setPadding(dp(16), dp(7), dp(16), dp(7));
        alphaTab.setBackgroundResource(sortMode == SORT_ALPHA
                ? getResId("bg_tab_selected") : getResId("bg_tab_unselected"));
        alphaTab.setTextColor(sortMode == SORT_ALPHA ? Color.WHITE : C_TEXT_SUB);

        final TextView freqTab = new TextView(this);
        freqTab.setText("按使用频率");
        freqTab.setTextSize(12);
        freqTab.setTypeface(null, Typeface.BOLD);
        freqTab.setGravity(Gravity.CENTER);
        freqTab.setPadding(dp(16), dp(7), dp(16), dp(7));
        freqTab.setBackgroundResource(sortMode == SORT_FREQ
                ? getResId("bg_tab_selected") : getResId("bg_tab_unselected"));
        freqTab.setTextColor(sortMode == SORT_FREQ ? Color.WHITE : C_TEXT_SUB);

        alphaTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sortMode == SORT_ALPHA) return;
                sortMode = SORT_ALPHA;
                prefs.edit().putInt(KEY_SORT_MODE, sortMode).apply();
                updateSortTabStyles(alphaTab, freqTab);
                Collections.sort(allTargets, makeComparator());
                filterAndRefresh();
            }
        });
        LinearLayout.LayoutParams alphaLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        alphaLp.rightMargin = dp(6);
        sortBar.addView(alphaTab, alphaLp);

        freqTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sortMode == SORT_FREQ) return;
                sortMode = SORT_FREQ;
                prefs.edit().putInt(KEY_SORT_MODE, sortMode).apply();
                updateSortTabStyles(alphaTab, freqTab);
                Collections.sort(allTargets, makeComparator());
                filterAndRefresh();
            }
        });
        LinearLayout.LayoutParams freqLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        freqLp.leftMargin = dp(6);
        sortBar.addView(freqTab, freqLp);

        // Keep references for external refresh
        sortBtn = alphaTab;
        sortFreqTab = freqTab;
        root.addView(sortBar);

        // Group tab bar (horizontal scroll)
        if (!groups.isEmpty()) {
            HorizontalScrollView tabScroll = new HorizontalScrollView(this);
            tabScroll.setHorizontalScrollBarEnabled(false);
            tabScroll.setFillViewport(true);

            tabBar = new LinearLayout(this);
            tabBar.setOrientation(LinearLayout.HORIZONTAL);
            tabBar.setPadding(dp(14), dp(6), dp(14), dp(2));
            rebuildTabBar();
            tabScroll.addView(tabBar);
            root.addView(tabScroll);
        }

        // Divider before list area
        View listDivider = new View(this);
        listDivider.setBackgroundColor(C_DIVIDER);
        root.addView(listDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // Pinned section label
        pinnedSection = new LinearLayout(this);
        pinnedSection.setOrientation(LinearLayout.HORIZONTAL);
        pinnedSection.setGravity(Gravity.CENTER_VERTICAL);
        pinnedSection.setPadding(dp(20), dp(8), dp(20), dp(2));
        pinnedSection.setVisibility(View.GONE);

        TextView pinnedLabel = new TextView(this);
        pinnedLabel.setText("置顶");
        pinnedLabel.setTextColor(C_PINNED_ACCENT);
        pinnedLabel.setTextSize(11);
        pinnedLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams pinLabelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pinnedSection.addView(pinnedLabel, pinLabelLp);
        root.addView(pinnedSection);

        // Status text
        statusText = new TextView(this);
        statusText.setText("正在查找应用…");
        statusText.setTextColor(C_TEXT_SUB);
        statusText.setTextSize(12);
        statusText.setPadding(dp(20), dp(6), dp(20), dp(2));
        root.addView(statusText);

        // Permission hint
        permissionHint = new TextView(this);
        permissionHint.setTextColor(C_WARNING);
        permissionHint.setTextSize(12);
        permissionHint.setPadding(dp(20), 0, dp(20), dp(2));
        permissionHint.setVisibility(View.GONE);
        root.addView(permissionHint);

        // App list
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setPadding(dp(14), dp(4), dp(14), dp(8));
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        adapter = new TargetAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ShareTarget target = displayTargets.get(position);
                try {
                    forwardTo(target);
                    Toast.makeText(SharePanelActivity.this,
                            "已转发到 " + target.label, Toast.LENGTH_SHORT).show();
                    finish();
                } catch (Throwable t) {
                    showStatus("转发失败：" + safeMessage(t), true);
                }
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom bar — with top border
        View bottomBorder = new View(this);
        bottomBorder.setBackgroundColor(C_DIVIDER);
        root.addView(bottomBorder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(16), dp(10), dp(16), dp(12));

        TextView continueText = new TextView(this);
        continueText.setText("系统分享器");
        continueText.setTextColor(C_PRIMARY);
        continueText.setTextSize(13);
        continueText.setTypeface(null, Typeface.BOLD);
        continueText.setGravity(Gravity.CENTER);
        continueText.setBackgroundResource(getResId("bg_btn_secondary"));
        continueText.setVisibility(View.GONE);
        continueText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent chooser = Intent.createChooser(buildForwardIntent(null), "选择应用");
                    startActivity(chooser);
                } catch (Throwable t) {
                    showStatus("无法打开系统分享器：" + safeMessage(t), true);
                }
            }
        });
        bottomBar.addView(continueText, new LinearLayout.LayoutParams(dp(160), dp(40)));
        continueButton = continueText;
        root.addView(bottomBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    // ──────────────────────────────────────────────
    //  Group tab bar
    // ──────────────────────────────────────────────
    private void rebuildTabBar() {
        if (tabBar == null) return;
        tabBar.removeAllViews();
        tabViews.clear();

        // "全部" tab
        TextView allTab = createTab("全部", selectedTabIndex == 0);
        allTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedTabIndex = 0;
                updateTabSelections();
                filterAndRefresh();
            }
        });
        tabBar.addView(allTab);
        tabViews.add(allTab);

        // Group tabs
        for (int i = 0; i < groups.size(); i++) {
            final int idx = i + 1;
            final GroupManager.Group g = groups.get(i);
            TextView tab = createTab(g.name, selectedTabIndex == idx);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedTabIndex = idx;
                    updateTabSelections();
                    filterAndRefresh();
                }
            });
            tabBar.addView(tab);
            tabViews.add(tab);
        }
    }

    private TextView createTab(String text, boolean selected) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setTextColor(selected ? Color.WHITE : C_TEXT_SUB);
        tab.setTextSize(13);
        tab.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(dp(16), dp(8), dp(16), dp(8));
        tab.setBackgroundResource(selected
                ? getResId("bg_tab_selected")
                : getResId("bg_tab_unselected"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        tab.setLayoutParams(lp);
        return tab;
    }

    private void updateTabSelections() {
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tab = tabViews.get(i);
            boolean sel = (i == selectedTabIndex);
            tab.setTextColor(sel ? Color.WHITE : C_TEXT_SUB);
            tab.setTypeface(null, sel ? Typeface.BOLD : Typeface.NORMAL);
            tab.setBackgroundResource(sel
                    ? getResId("bg_tab_selected")
                    : getResId("bg_tab_unselected"));
        }
    }

    private GroupManager.Group getSelectedGroup() {
        if (selectedTabIndex == 0 || groups.isEmpty()) return null;
        int idx = selectedTabIndex - 1;
        if (idx < 0 || idx >= groups.size()) return null;
        return groups.get(idx);
    }

    // ──────────────────────────────────────────────
    //  Intent handling
    // ──────────────────────────────────────────────
    private void handleShareIntent(Intent intent) {
        showStatus("正在查找应用…", false);
        try {
            parsedShare = parseIntent(intent);
            String src = parsedShare.sourceAppName;
            if (src != null && !src.equals("未知来源")) {
                sourceText.setText("来自 " + src);
                sourceText.setVisibility(View.VISIBLE);
            } else {
                sourceText.setVisibility(View.GONE);
            }

            String summary = buildSummary(parsedShare);
            if (summary != null && summary.length() > 0) {
                summaryText.setText(summary);
                summaryText.setVisibility(View.VISIBLE);
                summaryCard.setVisibility(View.VISIBLE);
            } else {
                summaryText.setVisibility(View.GONE);
                summaryCard.setVisibility(View.GONE);
            }

            // ── FAST PATH: query targets WITHOUT loading icons ──
            allTargets.clear();
            allTargets.addAll(resolveTargetsFast(parsedShare));
            filterAndRefresh();

            if (allTargets.isEmpty()) {
                showStatus("正在搜索更多应用…", false);
            } else {
                showStatus(allTargets.size() + " 个应用 · 搜索中…", false);
            }
            continueButton.setVisibility(View.VISIBLE);

            // ── Background: load icons for fast-path targets ──
            startIconLoader();

            // ── Background: full enumeration ──
            final ParsedShare parsedRef = parsedShare;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final ArrayList<ShareTarget> extra = resolveTargetsFull(parsedRef);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            HashSet<String> existing = new HashSet<String>();
                            for (int i = 0; i < allTargets.size(); i++) {
                                existing.add(allTargets.get(i).packageName + "/" + allTargets.get(i).className);
                            }
                            for (int i = 0; i < extra.size(); i++) {
                                ShareTarget t = extra.get(i);
                                String key = t.packageName + "/" + t.className;
                                if (!existing.contains(key)) {
                                    allTargets.add(t);
                                    existing.add(key);
                                }
                            }
                            Collections.sort(allTargets, makeComparator());
                            filterAndRefresh();
                            if (allTargets.isEmpty()) {
                                showStatus("没有找到可接收该内容的应用", true);
                            } else {
                                showStatus(allTargets.size() + " 个应用", false);
                            }
                            // Load icons for newly added targets
                            startIconLoader();
                        }
                    });
                }
            }).start();
        } catch (Throwable t) {
            parsedShare = safeFallbackShare(intent);
            sourceText.setVisibility(View.GONE);
            summaryText.setText("分享内容读取异常");
            showStatus("解析失败：" + safeMessage(t), true);
            continueButton.setVisibility(View.VISIBLE);
        }
    }

    // ──────────────────────────────────────────────
    //  Background icon loader — loads icons in batches
    // ──────────────────────────────────────────────
    private void startIconLoader() {
        if (iconsLoading) return;
        iconsLoading = true;
        final PackageManager pm = getPackageManager();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int loaded = 0;
                for (int i = 0; i < allTargets.size(); i++) {
                    final ShareTarget t = allTargets.get(i);
                    if (t.icon != null) continue;
                    try {
                        t.icon = pm.getApplicationIcon(t.packageName);
                        loaded++;
                    } catch (Throwable ignored) {}

                    // Update UI every 5 icons
                    if (loaded > 0 && loaded % 5 == 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                adapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
                if (loaded > 0) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
                iconsLoading = false;
            }
        }).start();
    }

    // ──────────────────────────────────────────────
    //  Filtering
    // ──────────────────────────────────────────────
    private void filterAndRefresh() {
        displayTargets.clear();
        String query = searchBox.getText().toString().trim().toLowerCase();
        clearBtn.setVisibility(query.length() > 0 ? View.VISIBLE : View.GONE);

        GroupManager.Group selectedGroup = getSelectedGroup();

        ArrayList<ShareTarget> pinnedList = new ArrayList<ShareTarget>();
        ArrayList<ShareTarget> normalList = new ArrayList<ShareTarget>();

        for (int i = 0; i < allTargets.size(); i++) {
            ShareTarget t = allTargets.get(i);
            // Fuzzy search filter — matches label, appName, and package name
            boolean match = query.length() == 0 || fuzzyMatch(query, t.label, t.appName, t.packageName);
            if (!match) continue;

            // Group filter — match exact pkg/cls target key
            if (selectedGroup != null && !selectedGroup.targets.contains(
                    GroupManager.makeKey(t.packageName, t.className))) {
                continue;
            }

            if (pinnedPackages.contains(GroupManager.makeKey(t.packageName, t.className))) {
                pinnedList.add(t);
            } else {
                normalList.add(t);
            }
        }

        boolean hasPinned = !pinnedList.isEmpty() && query.length() == 0 && selectedGroup == null;
        pinnedSection.setVisibility(hasPinned ? View.VISIBLE : View.GONE);

        displayTargets.addAll(pinnedList);
        displayTargets.addAll(normalList);
        adapter.notifyDataSetChanged();

        // Update status if group filter is active
        if (selectedGroup != null && displayTargets.isEmpty() && query.length() == 0) {
            showStatus("「" + selectedGroup.name + "」分组中没有可接收该内容的应用", false);
        }
    }

    // ──────────────────────────────────────────────
    //  Long-press: app options dialog
    // ──────────────────────────────────────────────
    private void showAppOptions(final ShareTarget target) {
        ArrayList<String> items = new ArrayList<String>();
        final ArrayList<Integer> actions = new ArrayList<Integer>();

        // Pin/unpin
        boolean isPinned = pinnedPackages.contains(
                GroupManager.makeKey(target.packageName, target.className));
        items.add(isPinned ? "取消置顶" : "置顶");
        actions.add(0); // 0 = toggle pin

        // Add to group
        if (!groups.isEmpty()) {
            items.add("添加到分组…");
            actions.add(1);
        }

        // Remove from group
        ArrayList<String> appGroups = GroupManager.getGroupsForPackage(
                this, target.packageName, target.className);
        if (!appGroups.isEmpty()) {
            items.add("从分组移除…");
            actions.add(2);
        }

        final String[] itemsArr = items.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(target.label)
                .setItems(itemsArr, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int action = actions.get(which);
                        switch (action) {
                            case 0:
                                togglePin(target.packageName, target.className);
                                break;
                            case 1:
                                showAddToGroupDialog(target);
                                break;
                            case 2:
                                showRemoveFromGroupDialog(target);
                                break;
                        }
                    }
                })
                .show();
    }

    private void showAddToGroupDialog(final ShareTarget target) {
        String[] groupNames = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) groupNames[i] = groups.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle("添加到分组")
                .setItems(groupNames, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        GroupManager.Group g = groups.get(which);
                        String targetKey = GroupManager.makeKey(target.packageName, target.className);
                        if (g.targets.contains(targetKey)) {
                            Toast.makeText(SharePanelActivity.this,
                                    "已在「" + g.name + "」中", Toast.LENGTH_SHORT).show();
                        } else {
                            GroupManager.toggleTargetInGroup(SharePanelActivity.this, g.name,
                                    GroupManager.makeKey(target.packageName, target.className));
                            Toast.makeText(SharePanelActivity.this,
                                    "已添加到「" + g.name + "」", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void showRemoveFromGroupDialog(final ShareTarget target) {
        final ArrayList<String> appGroups = GroupManager.getGroupsForPackage(
                this, target.packageName, target.className);
        String[] names = appGroups.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("从分组移除")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String groupName = appGroups.get(which);
                        GroupManager.toggleTargetInGroup(SharePanelActivity.this, groupName,
                                GroupManager.makeKey(target.packageName, target.className));
                        Toast.makeText(SharePanelActivity.this,
                                "已从「" + groupName + "」移除", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    // ──────────────────────────────────────────────
    //  Intent parsing
    // ──────────────────────────────────────────────
    private ParsedShare parseIntent(Intent intent) {
        ParsedShare parsed = new ParsedShare();
        parsed.originalAction = intent.getAction();
        parsed.mimeType = intent.getType() == null ? "*/*" : intent.getType();
        parsed.text = intent.getStringExtra(Intent.EXTRA_TEXT);
        parsed.subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        parsed.uris = extractUris(intent);
        parsed.sourcePackage = resolveSourcePackage();
        parsed.sourceAppName = resolveAppName(parsed.sourcePackage);
        return parsed;
    }

    private ParsedShare safeFallbackShare(Intent intent) {
        ParsedShare parsed = new ParsedShare();
        parsed.originalAction = intent == null ? Intent.ACTION_SEND : intent.getAction();
        parsed.mimeType = intent == null || intent.getType() == null ? "*/*" : intent.getType();
        parsed.text = null;
        parsed.subject = null;
        parsed.uris = new ArrayList<Uri>();
        parsed.sourcePackage = null;
        parsed.sourceAppName = "未知来源";
        return parsed;
    }

    @SuppressWarnings("deprecation")
    private ArrayList<Uri> extractUris(Intent intent) {
        ArrayList<Uri> result = new ArrayList<Uri>();
        ArrayList<Uri> multiple = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (multiple != null) result.addAll(multiple);
        if (result.isEmpty()) {
            Uri single = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (single != null) result.add(single);
        }
        if (result.isEmpty() && intent.getData() != null) {
            result.add(intent.getData());
        }
        if (result.isEmpty() && intent.getClipData() != null) {
            ClipData clip = intent.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) {
                ClipData.Item item = clip.getItemAt(i);
                if (item.getUri() != null) result.add(item.getUri());
            }
        }
        return result;
    }

    private String resolveSourcePackage() {
        String cp = getCallingPackage();
        if (cp != null) return cp;
        Uri ref = getReferrer();
        if (ref != null && "android-app".equals(ref.getScheme())) return ref.getHost();
        return null;
    }

    private String resolveAppName(String pkg) {
        if (pkg == null) return "未知来源";
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable ignored) { return pkg; }
    }

    // ──────────────────────────────────────────────
    //  Target resolution — FAST PATH (no icon loading)
    // ──────────────────────────────────────────────
    private ArrayList<ShareTarget> resolveTargetsFast(ParsedShare parsed) {
        HashMap<String, ShareTarget> resultMap = new HashMap<String, ShareTarget>();
        boolean isMultiple = parsed.uris.size() > 1;
        String mimeType = parsed.mimeType == null ? "*/*" : parsed.mimeType;
        PackageManager pm = getPackageManager();

        // Strategy 1: exact mimeType
        try {
            Intent probe = new Intent(isMultiple ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
            probe.setType(mimeType);
            probe.addCategory(Intent.CATEGORY_DEFAULT);
            addResolveInfosFast(resultMap, pm.queryIntentActivities(probe, PackageManager.MATCH_ALL));
        } catch (Throwable ignored) {}

        // Strategy 2: wildcard */*
        if (!"*/*".equals(mimeType)) {
            try {
                Intent probe = new Intent(isMultiple ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
                probe.setType("*/*");
                probe.addCategory(Intent.CATEGORY_DEFAULT);
                addResolveInfosFast(resultMap, pm.queryIntentActivities(probe, PackageManager.MATCH_ALL));
            } catch (Throwable ignored) {}
        }

        // Strategy 3: ACTION_VIEW
        try {
            Intent probe = new Intent(Intent.ACTION_VIEW);
            probe.setType(mimeType);
            probe.addCategory(Intent.CATEGORY_DEFAULT);
            addResolveInfosFast(resultMap, pm.queryIntentActivities(probe, PackageManager.MATCH_ALL));
        } catch (Throwable ignored) {}

        // Strategy 4: text/plain
        if (parsed.uris.isEmpty() && parsed.text != null) {
            try {
                Intent probe = new Intent(Intent.ACTION_SEND);
                probe.setType("text/plain");
                probe.addCategory(Intent.CATEGORY_DEFAULT);
                addResolveInfosFast(resultMap, pm.queryIntentActivities(probe, PackageManager.MATCH_ALL));
            } catch (Throwable ignored) {}
        }

        return sortTargets(new ArrayList<ShareTarget>(resultMap.values()));
    }

    /** Fast version: loads label only, skips icon loading for speed */
    private void addResolveInfosFast(HashMap<String, ShareTarget> map, List<ResolveInfo> infos) {
        if (infos == null) return;
        PackageManager pm = getPackageManager();
        for (int i = 0; i < infos.size(); i++) {
            ResolveInfo info = infos.get(i);
            if (info == null || info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            if (pkg == null || cls == null || getPackageName().equals(pkg)) continue;
            String key = pkg + "/" + cls;
            if (!map.containsKey(key)) {
                ShareTarget t = new ShareTarget();
                t.label = String.valueOf(info.loadLabel(pm));
                t.packageName = pkg;
                t.className = cls;
                t.icon = null; // Icons loaded in background
                t.appName = loadAppName(pm, pkg);
                map.put(key, t);
            }
        }
    }

    // ──────────────────────────────────────────────
    //  Full enumeration (background)
    // ──────────────────────────────────────────────
    private ArrayList<ShareTarget> resolveTargetsFull(ParsedShare parsed) {
        HashMap<String, ShareTarget> resultMap = new HashMap<String, ShareTarget>();
        boolean isMultiple = parsed.uris.size() > 1;
        String mimeType = parsed.mimeType == null ? "*/*" : parsed.mimeType;
        PackageManager pm = getPackageManager();

        try {
            List<ApplicationInfo> allApps = pm.getInstalledApplications(0);
            Intent test = new Intent(isMultiple ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
            test.setType(mimeType);
            test.addCategory(Intent.CATEGORY_DEFAULT);
            for (int i = 0; i < allApps.size(); i++) {
                ApplicationInfo ai = allApps.get(i);
                if (ai == null || getPackageName().equals(ai.packageName)) continue;
                // Pre-load app name once per package for efficiency
                String cachedAppName = String.valueOf(ai.loadLabel(pm));
                try {
                    test.setPackage(ai.packageName);
                    List<ResolveInfo> infos = pm.queryIntentActivities(test, PackageManager.MATCH_ALL);
                    if (infos != null) {
                        for (int j = 0; j < infos.size(); j++) {
                            ResolveInfo ri = infos.get(j);
                            if (ri == null || ri.activityInfo == null) continue;
                            String p = ri.activityInfo.packageName;
                            String c = ri.activityInfo.name;
                            if (p == null || c == null || getPackageName().equals(p)) continue;
                            String key = p + "/" + c;
                            if (!resultMap.containsKey(key)) {
                                ShareTarget st = new ShareTarget();
                                st.label = String.valueOf(ri.loadLabel(pm));
                                st.packageName = p;
                                st.className = c;
                                st.icon = null; // Icons loaded in background
                                st.appName = cachedAppName;
                                resultMap.put(key, st);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        return new ArrayList<ShareTarget>(resultMap.values());
    }

    private ArrayList<ShareTarget> sortTargets(ArrayList<ShareTarget> result) {
        Collections.sort(result, makeComparator());
        return result;
    }

    private void addResolveInfos(HashMap<String, ShareTarget> map, List<ResolveInfo> infos) {
        if (infos == null) return;
        PackageManager pm = getPackageManager();
        for (int i = 0; i < infos.size(); i++) {
            ResolveInfo info = infos.get(i);
            if (info == null || info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            String cls = info.activityInfo.name;
            if (pkg == null || cls == null || getPackageName().equals(pkg)) continue;
            String key = pkg + "/" + cls;
            if (!map.containsKey(key)) {
                ShareTarget t = new ShareTarget();
                t.label = String.valueOf(info.loadLabel(pm));
                t.packageName = pkg;
                t.className = cls;
                t.icon = null;
                t.appName = loadAppName(pm, pkg);
                map.put(key, t);
            }
        }
    }

    /** Loads the application label (e.g. "微信") for a package */
    private String loadAppName(PackageManager pm, String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (Throwable ignored) {
            return pkg;
        }
    }

    private String buildSummary(ParsedShare parsed) {
        if (!parsed.uris.isEmpty() && parsed.text != null && parsed.text.trim().length() > 0) {
            return parsed.uris.size() + " 个文件 · " + parsed.text;
        }
        if (!parsed.uris.isEmpty()) {
            return parsed.uris.size() + " 个文件 · " + parsed.mimeType;
        }
        if (parsed.text != null && parsed.text.trim().length() > 0) {
            String t = parsed.text;
            if (t.length() > 80) t = t.substring(0, 80) + "…";
            return t;
        }
        return parsed.mimeType;
    }

    private Intent buildForwardIntent(ShareTarget target) {
        ArrayList<Uri> uris = parsedShare == null ? new ArrayList<Uri>() : parsedShare.uris;
        Intent forward = new Intent(uris.size() > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        forward.setType(parsedShare == null ? "*/*" : parsedShare.mimeType);
        if (target != null) {
            forward.setComponent(new ComponentName(target.packageName, target.className));
        }
        if (parsedShare != null && parsedShare.text != null) {
            forward.putExtra(Intent.EXTRA_TEXT, parsedShare.text);
        }
        if (parsedShare != null && parsedShare.subject != null) {
            forward.putExtra(Intent.EXTRA_SUBJECT, parsedShare.subject);
        }
        if (uris.size() == 1) {
            forward.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else if (uris.size() > 1) {
            forward.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        if (!uris.isEmpty()) {
            forward.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            forward.setClipData(buildClipData(uris));
        }
        return forward;
    }

    private void forwardTo(ShareTarget target) {
        Intent forward = buildForwardIntent(target);
        for (int i = 0; parsedShare != null && i < parsedShare.uris.size(); i++) {
            try {
                grantUriPermission(target.packageName, parsedShare.uris.get(i), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignored) {}
        }
        incrementUsage(target.packageName, target.className);
        startActivity(forward);
    }

    private ClipData buildClipData(ArrayList<Uri> uris) {
        ClipData data = ClipData.newUri(getContentResolver(), "shared_content", uris.get(0));
        for (int i = 1; i < uris.size(); i++) {
            data.addItem(new ClipData.Item(uris.get(i)));
        }
        return data;
    }

    private void showStatus(String msg, boolean isError) {
        statusText.setText(msg);
        statusText.setTextColor(isError ? C_ERROR : C_TEXT_SUB);
    }

    private String safeMessage(Throwable t) {
        if (t == null || t.getMessage() == null || t.getMessage().length() == 0) {
            return t == null ? "未知错误" : t.getClass().getSimpleName();
        }
        return t.getMessage();
    }

    private int getResId(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ──────────────────────────────────────────────
    //  Data classes
    // ──────────────────────────────────────────────
    private static class ParsedShare {
        String originalAction;
        String mimeType;
        String text;
        String subject;
        String sourcePackage;
        String sourceAppName;
        ArrayList<Uri> uris;
    }

    static class ShareTarget {
        String label;       // share target label, e.g. "发送给朋友"
        String appName;     // application label, e.g. "微信"
        String packageName;
        String className;
        Drawable icon;
    }

    // ──────────────────────────────────────────────
    //  Adapter
    // ──────────────────────────────────────────────
    private class TargetAdapter extends BaseAdapter {
        @Override
        public int getCount() { return displayTargets.size(); }
        @Override
        public Object getItem(int pos) { return displayTargets.get(pos); }
        @Override
        public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ShareTarget target = displayTargets.get(position);
            boolean isPinned = pinnedPackages.contains(
                    GroupManager.makeKey(target.packageName, target.className));

            // Outer container
            LinearLayout outer = new LinearLayout(SharePanelActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams outerLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            outerLp.bottomMargin = dp(8);
            outerLp.leftMargin = dp(0);
            outerLp.rightMargin = dp(0);

            // Row card
            LinearLayout row = new LinearLayout(SharePanelActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(10), dp(12));
            row.setBackgroundResource(isPinned
                    ? getResId("bg_list_item_pinned")
                    : getResId("bg_list_item"));

            // App icon (with placeholder if not loaded yet)
            LinearLayout iconWrap = new LinearLayout(SharePanelActivity.this);
            iconWrap.setOrientation(LinearLayout.HORIZONTAL);
            iconWrap.setGravity(Gravity.CENTER);
            iconWrap.setBackgroundResource(getResId("bg_icon_circle"));
            if (target.icon != null) {
                ImageView icon = new ImageView(SharePanelActivity.this);
                icon.setImageDrawable(target.icon);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iconWrap.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
            } else {
                // Placeholder: first letter of app name
                TextView placeholder = new TextView(SharePanelActivity.this);
                String firstChar = target.label.length() > 0
                        ? target.label.substring(0, 1).toUpperCase()
                        : "?";
                placeholder.setText(firstChar);
                placeholder.setTextColor(C_TEXT_HINT);
                placeholder.setTextSize(16);
                placeholder.setTypeface(null, Typeface.BOLD);
                placeholder.setGravity(Gravity.CENTER);
                iconWrap.addView(placeholder, new LinearLayout.LayoutParams(dp(32), dp(32)));
            }
            row.addView(iconWrap, new LinearLayout.LayoutParams(dp(44), dp(44)));

            // Label + pin tag
            LinearLayout textBox = new LinearLayout(SharePanelActivity.this);
            textBox.setOrientation(LinearLayout.VERTICAL);

            TextView label = new TextView(SharePanelActivity.this);
            label.setText(target.label);
            label.setTextColor(isPinned ? C_PINNED_ACCENT : C_TEXT_MAIN);
            label.setTextSize(15);
            label.setTypeface(null, isPinned ? Typeface.BOLD : Typeface.NORMAL);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            textBox.addView(label);

            // Show the app name as a subtitle so users can identify which app this belongs to
            if (target.appName != null && target.appName.length() > 0
                    && !target.appName.equals(target.label)) {
                TextView appSub = new TextView(SharePanelActivity.this);
                appSub.setText(target.appName);
                appSub.setTextColor(C_TEXT_SUB);
                appSub.setTextSize(11);
                appSub.setSingleLine(true);
                appSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams appLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                appLp.topMargin = dp(1);
                textBox.addView(appSub, appLp);
            }

            if (isPinned) {
                TextView pinTag = new TextView(SharePanelActivity.this);
                pinTag.setText("已置顶");
                pinTag.setTextColor(C_PINNED_ACCENT);
                pinTag.setTextSize(10);
                LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tagLp.topMargin = dp(1);
                textBox.addView(pinTag, tagLp);
            }

            // Show usage count when in frequency sort mode
            if (sortMode == SORT_FREQ) {
                int usage = getUsage(target.packageName, target.className);
                if (usage > 0) {
                    TextView usageTag = new TextView(SharePanelActivity.this);
                    usageTag.setText("使用 " + usage + " 次");
                    usageTag.setTextColor(C_TEXT_HINT);
                    usageTag.setTextSize(10);
                    LinearLayout.LayoutParams uLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    uLp.topMargin = dp(1);
                    textBox.addView(usageTag, uLp);
                }
            }

            // Show which groups this target belongs to
            if (!groups.isEmpty()) {
                ArrayList<String> appGroups = GroupManager.getGroupsForPackage(
                        SharePanelActivity.this, target.packageName, target.className);
                if (!appGroups.isEmpty()) {
                    StringBuilder gs = new StringBuilder();
                    for (int g = 0; g < appGroups.size() && g < 3; g++) {
                        if (gs.length() > 0) gs.append(" · ");
                        gs.append(appGroups.get(g));
                    }
                    if (appGroups.size() > 3) gs.append("…");
                    TextView groupTag = new TextView(SharePanelActivity.this);
                    groupTag.setText(gs.toString());
                    groupTag.setTextColor(C_TEXT_HINT);
                    groupTag.setTextSize(10);
                    LinearLayout.LayoutParams gtagLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    gtagLp.topMargin = dp(1);
                    textBox.addView(groupTag, gtagLp);
                }
            }

            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textLp.leftMargin = dp(12);
            row.addView(textBox, textLp);

            // Pin button
            LinearLayout pinWrap = new LinearLayout(SharePanelActivity.this);
            pinWrap.setOrientation(LinearLayout.HORIZONTAL);
            pinWrap.setGravity(Gravity.CENTER);
            pinWrap.setBackgroundResource(isPinned
                    ? getResId("bg_pin_circle")
                    : getResId("bg_icon_circle"));
            TextView pinBtn = new TextView(SharePanelActivity.this);
            pinBtn.setText(isPinned ? "\u25CF" : "\u25CB");
            pinBtn.setTextSize(13);
            pinBtn.setGravity(Gravity.CENTER);
            pinWrap.addView(pinBtn, new LinearLayout.LayoutParams(dp(28), dp(28)));
            pinWrap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    togglePin(target.packageName, target.className);
                }
            });
            row.addView(pinWrap, new LinearLayout.LayoutParams(dp(36), dp(36)));

            // Direct click on row — more reliable than ListView OnItemClickListener
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        forwardTo(target);
                        Toast.makeText(SharePanelActivity.this,
                                "已转发到 " + target.label, Toast.LENGTH_SHORT).show();
                        finish();
                    } catch (Throwable t) {
                        showStatus("转发失败：" + safeMessage(t), true);
                    }
                }
            });

            // Long-press for group options
            row.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showAppOptions(target);
                    return true;
                }
            });

            outer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return outer;
        }
    }
}
