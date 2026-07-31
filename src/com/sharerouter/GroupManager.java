package com.sharerouter;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Manages app groups stored in SharedPreferences.
 * Stores target keys in format "packageName/className" to support
 * per-component (sub-share-target) grouping, not just per-app.
 *
 * Serialization format: name1:::key1||key2|||name2:::key3
 */
public class GroupManager {

    private static final String PREFS_NAME = "sharerouter_prefs";
    private static final String KEY_GROUPS = "groups_data";
    private static final String SEP_GROUP = "|||";
    private static final String SEP_NAME = ":::";
    private static final String SEP_PKG = "||";

    public static class Group {
        public String name;
        /** Stores "pkg/cls" target keys */
        public HashSet<String> targets = new HashSet<String>();

        public Group() {}
        public Group(String name) { this.name = name; }
    }

    // ── Key helpers ──
    public static String makeKey(String pkg, String cls) {
        return pkg + "/" + cls;
    }

    public static String keyToPkg(String key) {
        if (key == null) return "";
        int idx = key.indexOf('/');
        return idx >= 0 ? key.substring(0, idx) : key;
    }

    public static String keyToCls(String key) {
        if (key == null) return "";
        int idx = key.indexOf('/');
        return idx >= 0 ? key.substring(idx + 1) : "";
    }

    // ── CRUD ──
    public static ArrayList<Group> loadGroups(Context ctx) {
        ArrayList<Group> groups = new ArrayList<Group>();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String data = prefs.getString(KEY_GROUPS, "");
        if (data == null || data.length() == 0) return groups;

        String[] groupParts = data.split("\\|\\|\\|");
        for (int i = 0; i < groupParts.length; i++) {
            String part = groupParts[i];
            if (part.length() == 0) continue;
            String[] kv = part.split(":::", 2);
            if (kv.length < 1) continue;
            Group g = new Group(kv[0]);
            if (kv.length == 2 && kv[1].length() > 0) {
                String[] keys = kv[1].split("\\|\\|");
                for (int j = 0; j < keys.length; j++) {
                    if (keys[j].length() > 0) g.targets.add(keys[j]);
                }
            }
            groups.add(g);
        }
        return groups;
    }

    public static void saveGroups(Context ctx, ArrayList<Group> groups) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            Group g = groups.get(i);
            if (i > 0) sb.append(SEP_GROUP);
            sb.append(g.name).append(SEP_NAME);
            boolean first = true;
            for (String key : g.targets) {
                if (!first) sb.append(SEP_PKG);
                sb.append(key);
                first = false;
            }
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_GROUPS, sb.toString()).apply();
    }

    public static void toggleTargetInGroup(Context ctx, String groupName, String targetKey) {
        ArrayList<Group> groups = loadGroups(ctx);
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).name.equals(groupName)) {
                if (groups.get(i).targets.contains(targetKey)) {
                    groups.get(i).targets.remove(targetKey);
                } else {
                    groups.get(i).targets.add(targetKey);
                }
                break;
            }
        }
        saveGroups(ctx, groups);
    }

    public static void createGroup(Context ctx, String name) {
        ArrayList<Group> groups = loadGroups(ctx);
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).name.equals(name)) return;
        }
        groups.add(new Group(name));
        saveGroups(ctx, groups);
    }

    public static void deleteGroup(Context ctx, String name) {
        ArrayList<Group> groups = loadGroups(ctx);
        for (int i = groups.size() - 1; i >= 0; i--) {
            if (groups.get(i).name.equals(name)) {
                groups.remove(i);
                break;
            }
        }
        saveGroups(ctx, groups);
    }

    public static void renameGroup(Context ctx, String oldName, String newName) {
        ArrayList<Group> groups = loadGroups(ctx);
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).name.equals(oldName)) {
                groups.get(i).name = newName;
                break;
            }
        }
        saveGroups(ctx, groups);
    }

    /** Returns group names that contain the given target key */
    public static ArrayList<String> getGroupsForTarget(Context ctx, String targetKey) {
        ArrayList<Group> groups = loadGroups(ctx);
        ArrayList<String> result = new ArrayList<String>();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).targets.contains(targetKey)) {
                result.add(groups.get(i).name);
            }
        }
        return result;
    }

    /** Backward-compatible: returns group names whose targets match pkg OR exact pkg/cls */
    public static ArrayList<String> getGroupsForPackage(Context ctx, String pkg, String cls) {
        ArrayList<Group> groups = loadGroups(ctx);
        ArrayList<String> result = new ArrayList<String>();
        String exactKey = makeKey(pkg, cls);
        for (int i = 0; i < groups.size(); i++) {
            HashSet<String> targets = groups.get(i).targets;
            if (targets.contains(exactKey)) {
                result.add(groups.get(i).name);
            }
        }
        return result;
    }
}
