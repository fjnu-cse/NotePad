package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.util.HashSet;

/**
 * 批量管理：勾选笔记后可一键导出（单条/多条/全部）或批量删除（移入回收站）。
 */
public class BatchActivity extends Activity {

    private static final String[] PROJECTION = {
            NotePad.Notes._ID,                          // 0
            NotePad.Notes.COLUMN_NAME_TITLE,            // 1
            NotePad.Notes.COLUMN_NAME_CATEGORY,         // 2
    };
    private static final int COL_ID = 0;
    private static final int COL_TITLE = 1;
    private static final int COL_CAT = 2;

    private static final int REQ_BATCH_EXPORT = 2001;

    private BatchAdapter mAdapter;
    private final HashSet<Long> mSelected = new HashSet<Long>();
    private Button mSelectAllBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch);
        setTitle("批量管理");

        ListView list = (ListView) findViewById(R.id.batch_list);
        mSelectAllBtn = (Button) findViewById(R.id.batch_select_all);

        mAdapter = new BatchAdapter(queryNotes());
        list.setAdapter(mAdapter);

        // 全选 / 取消全选（已全部选中时再点则清空）
        mSelectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Cursor cur = mAdapter.getCursor();
                if (cur.getCount() > 0 && mSelected.size() == cur.getCount()) {
                    mSelected.clear();
                } else {
                    cur.moveToPosition(-1);
                    while (cur.moveToNext()) {
                        mSelected.add(cur.getLong(COL_ID));
                    }
                }
                mAdapter.notifyDataSetChanged();
                updateSelectAllText();
            }
        });

        // 导出选中：勾 1 条即单条导出，勾多条即批量导出
        findViewById(R.id.batch_export).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelected.isEmpty()) {
                    toast("请先勾选要导出的笔记");
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "notepad_export.txt");
                startActivityForResult(intent, REQ_BATCH_EXPORT);
            }
        });

        // 删除选中：统一移入回收站，可在回收站找回
        findViewById(R.id.batch_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelected.isEmpty()) {
                    toast("请先勾选要删除的笔记");
                    return;
                }
                int n = 0;
                for (Long id : mSelected) {
                    Uri itemUri = ContentUris.withAppendedId(
                            NotePad.Notes.CONTENT_ID_URI_BASE, id);
                    ContentValues cv = new ContentValues();
                    cv.put(NotePad.Notes.COLUMN_NAME_TRASHED, 1);
                    getContentResolver().update(itemUri, cv, null, null);
                    n++;
                }
                mSelected.clear();
                refresh();
                toast("已把 " + n + " 条笔记移入回收站");
            }
        });
    }

    private Cursor queryNotes() {
        return getContentResolver().query(NotePad.Notes.CONTENT_URI, PROJECTION,
                NotePad.Notes.COLUMN_NAME_TRASHED + "=0", null,
                NotePad.Notes.DEFAULT_SORT_ORDER);
    }

    private void refresh() {
        mAdapter.changeCursor(queryNotes());
        updateSelectAllText();
    }

    private void updateSelectAllText() {
        Cursor cur = mAdapter.getCursor();
        boolean all = cur.getCount() > 0 && mSelected.size() == cur.getCount();
        mSelectAllBtn.setText(all ? "取消全选" : "全选");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) {
            refresh();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == REQ_BATCH_EXPORT) {
            try {
                long[] ids = new long[mSelected.size()];
                int i = 0;
                for (Long id : mSelected) {
                    ids[i++] = id;
                }
                String text = BackupUtils.buildExportText(getContentResolver(), ids);
                OutputStream os = getContentResolver().openOutputStream(data.getData());
                os.write(text.getBytes("UTF-8"));
                os.flush();
                os.close();
                toast("导出成功，共 " + ids.length + " 条");
            } catch (Exception e) {
                toast("导出失败：" + e.getMessage());
            }
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private class BatchAdapter extends CursorAdapter {
        BatchAdapter(Cursor c) {
            super(BatchActivity.this, c, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context).inflate(R.layout.item_batch, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final long id = cursor.getLong(COL_ID);
            String title = cursor.getString(COL_TITLE);
            String cat = cursor.getString(COL_CAT);

            ((TextView) view.findViewById(R.id.batch_title)).setText(title);
            ((TextView) view.findViewById(R.id.batch_cat))
                    .setText(cat == null ? "" : "[" + cat + "]");

            CheckBox check = (CheckBox) view.findViewById(R.id.batch_check);
            check.setOnCheckedChangeListener(null);
            check.setChecked(mSelected.contains(id));
            check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    if (checked) {
                        mSelected.add(id);
                    } else {
                        mSelected.remove(id);
                    }
                    updateSelectAllText();
                }
            });
        }
    }
}
