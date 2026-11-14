package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 回收站：列出被删除（trashed=1）的笔记，可单条恢复、彻底删除，或一键清空。
 */
public class TrashActivity extends Activity {

    private static final String[] PROJECTION = {
            NotePad.Notes._ID,                          // 0
            NotePad.Notes.COLUMN_NAME_TITLE,            // 1
            NotePad.Notes.COLUMN_NAME_CATEGORY,         // 2
    };
    private static final int COL_ID = 0;
    private static final int COL_TITLE = 1;
    private static final int COL_CAT = 2;

    private TrashAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash);
        setTitle("回收站");

        ListView list = (ListView) findViewById(R.id.trash_list);
        mAdapter = new TrashAdapter(queryTrash());
        list.setAdapter(mAdapter);

        // 清空回收站：二次确认后物理删除全部 trashed=1 的笔记
        findViewById(R.id.trash_empty).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(TrashActivity.this)
                        .setTitle("清空回收站")
                        .setMessage("将永久删除回收站里的全部笔记，无法恢复，确定吗？")
                        .setPositiveButton("清空", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                int n = getContentResolver().delete(
                                        NotePad.Notes.CONTENT_URI,
                                        NotePad.Notes.COLUMN_NAME_TRASHED + "=1", null);
                                refresh();
                                toast("已彻底删除 " + n + " 条");
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
    }

    private Cursor queryTrash() {
        return getContentResolver().query(NotePad.Notes.CONTENT_URI, PROJECTION,
                NotePad.Notes.COLUMN_NAME_TRASHED + "=1", null,
                NotePad.Notes.DEFAULT_SORT_ORDER);
    }

    private void refresh() {
        mAdapter.changeCursor(queryTrash());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) {
            refresh();
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private class TrashAdapter extends CursorAdapter {
        TrashAdapter(Cursor c) {
            super(TrashActivity.this, c, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context).inflate(R.layout.item_trash, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final long id = cursor.getLong(COL_ID);
            String title = cursor.getString(COL_TITLE);
            String cat = cursor.getString(COL_CAT);

            ((TextView) view.findViewById(R.id.trash_title)).setText(title);
            ((TextView) view.findViewById(R.id.trash_cat))
                    .setText(cat == null ? "" : "[" + cat + "]");

            // 恢复：标记回正常笔记
            view.findViewById(R.id.trash_restore).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri itemUri = ContentUris.withAppendedId(
                            NotePad.Notes.CONTENT_ID_URI_BASE, id);
                    ContentValues cv = new ContentValues();
                    cv.put(NotePad.Notes.COLUMN_NAME_TRASHED, 0);
                    getContentResolver().update(itemUri, cv, null, null);
                    refresh();
                    toast("已恢复");
                }
            });

            // 彻底删除：物理删除单条，无法恢复
            view.findViewById(R.id.trash_delete_forever).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Uri itemUri = ContentUris.withAppendedId(
                                    NotePad.Notes.CONTENT_ID_URI_BASE, id);
                            getContentResolver().delete(itemUri, null, null);
                            refresh();
                            toast("已彻底删除");
                        }
                    });
        }
    }
}
