package com.example.android.notepad;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;

/**
 * 笔记导出、备份（JSON）与恢复工具。使用 Android 自带 org.json，无需额外依赖。
 * 导出与备份默认只包含正常笔记（trashed=0），回收站里的内容不计入。
 */
public class BackupUtils {

    private static final String[] NOTE_COLS = {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_CREATE_DATE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_CATEGORY,
    };

    /** 导出全部正常笔记为可读纯文本 */
    public static String buildExportText(ContentResolver cr) {
        Cursor c = cr.query(NotePad.Notes.CONTENT_URI, NOTE_COLS,
                NotePad.Notes.COLUMN_NAME_TRASHED + "=0", null,
                NotePad.Notes.DEFAULT_SORT_ORDER);
        return toText(c);
    }

    /** 导出指定 id 的笔记为可读纯文本（单条/多条通用） */
    public static String buildExportText(ContentResolver cr, long[] ids) {
        if (ids == null || ids.length == 0) {
            return "";
        }
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                in.append(",");
            }
            in.append(ids[i]);
        }
        String selection = NotePad.Notes._ID + " IN (" + in + ") AND "
                + NotePad.Notes.COLUMN_NAME_TRASHED + "=0";
        Cursor c = cr.query(NotePad.Notes.CONTENT_URI, NOTE_COLS, selection, null,
                NotePad.Notes.DEFAULT_SORT_ORDER);
        return toText(c);
    }

    // 把游标里的笔记统一格式化为可读文本
    private static String toText(Cursor c) {
        StringBuilder sb = new StringBuilder();
        sb.append("笔记导出（共 ").append(c.getCount()).append(" 条）\n")
          .append("导出时间：").append(new Date()).append("\n\n");
        int n = 0;
        while (c.moveToNext()) {
            String title = c.getString(1);
            String note = c.getString(2);
            String category = c.getString(5);
            sb.append("===== ").append(++n).append(". ").append(title);
            if (category != null) {
                sb.append(" [").append(category).append("]");
            }
            sb.append(" =====\n");
            sb.append(note == null ? "" : note).append("\n\n");
        }
        c.close();
        return sb.toString();
    }

    /** 备份全部正常笔记为 JSON 字符串 */
    public static String buildBackupJson(ContentResolver cr) throws Exception {
        Cursor c = cr.query(NotePad.Notes.CONTENT_URI, NOTE_COLS,
                NotePad.Notes.COLUMN_NAME_TRASHED + "=0", null,
                NotePad.Notes.DEFAULT_SORT_ORDER);
        JSONArray arr = new JSONArray();
        while (c.moveToNext()) {
            JSONObject o = new JSONObject();
            o.put(NotePad.Notes.COLUMN_NAME_TITLE, c.getString(1));
            o.put(NotePad.Notes.COLUMN_NAME_NOTE, c.getString(2));
            o.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, c.getLong(3));
            o.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, c.getLong(4));
            if (!c.isNull(5)) {
                o.put(NotePad.Notes.COLUMN_NAME_CATEGORY, c.getString(5));
            }
            arr.put(o);
        }
        c.close();
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("backup_time", System.currentTimeMillis());
        root.put("notes", arr);
        return root.toString(2);
    }

    /** 从备份 JSON 恢复，返回成功导入的笔记条数 */
    public static int restoreFromJson(ContentResolver cr, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray arr = root.getJSONArray("notes");
        int count = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            ContentValues v = new ContentValues();
            if (o.has(NotePad.Notes.COLUMN_NAME_TITLE)) {
                v.put(NotePad.Notes.COLUMN_NAME_TITLE,
                        o.getString(NotePad.Notes.COLUMN_NAME_TITLE));
            }
            if (o.has(NotePad.Notes.COLUMN_NAME_NOTE)) {
                v.put(NotePad.Notes.COLUMN_NAME_NOTE,
                        o.getString(NotePad.Notes.COLUMN_NAME_NOTE));
            }
            if (o.has(NotePad.Notes.COLUMN_NAME_CREATE_DATE)) {
                v.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE,
                        o.getLong(NotePad.Notes.COLUMN_NAME_CREATE_DATE));
            }
            if (o.has(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE)) {
                v.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                        o.getLong(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
            }
            if (o.has(NotePad.Notes.COLUMN_NAME_CATEGORY)) {
                v.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                        o.getString(NotePad.Notes.COLUMN_NAME_CATEGORY));
            }
            // 恢复的笔记默认是正常状态，不进回收站
            v.put(NotePad.Notes.COLUMN_NAME_TRASHED, 0);
            cr.insert(NotePad.Notes.CONTENT_URI, v);
            count++;
        }
        return count;
    }
}
