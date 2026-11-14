package com.example.android.notepad;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;

/**
 * 待办清单：添加、勾选完成（加删除线）、删除，数据通过 ContentProvider 持久化。
 */
public class TodoActivity extends Activity {

    private static final String[] PROJECTION = {
            NotePad.Todos._ID,
            NotePad.Todos.COLUMN_NAME_CONTENT,
            NotePad.Todos.COLUMN_NAME_DONE,
    };
    private static final int COL_ID = 0;
    private static final int COL_CONTENT = 1;
    private static final int COL_DONE = 2;

    private EditText mInput;
    private TodoAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo);
        setTitle("待办清单");

        mInput = (EditText) findViewById(R.id.todo_input);
        ListView list = (ListView) findViewById(R.id.todo_list);

        Cursor cursor = getContentResolver().query(NotePad.Todos.CONTENT_URI,
                PROJECTION, null, null, NotePad.Todos.DEFAULT_SORT_ORDER);
        mAdapter = new TodoAdapter(cursor);
        list.setAdapter(mAdapter);

        findViewById(R.id.todo_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = mInput.getText().toString().trim();
                if (TextUtils.isEmpty(text)) {
                    return;
                }
                ContentValues values = new ContentValues();
                values.put(NotePad.Todos.COLUMN_NAME_CONTENT, text);
                getContentResolver().insert(NotePad.Todos.CONTENT_URI, values);
                mInput.setText("");
                refresh();
            }
        });
    }

    private void refresh() {
        Cursor c = getContentResolver().query(NotePad.Todos.CONTENT_URI,
                PROJECTION, null, null, NotePad.Todos.DEFAULT_SORT_ORDER);
        mAdapter.changeCursor(c);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) {
            refresh();
        }
    }

    private class TodoAdapter extends CursorAdapter {
        TodoAdapter(Cursor c) {
            super(TodoActivity.this, c, 0);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context).inflate(R.layout.item_todo, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final long id = cursor.getLong(COL_ID);
            String content = cursor.getString(COL_CONTENT);
            int done = cursor.getInt(COL_DONE);

            final CheckBox check = (CheckBox) view.findViewById(R.id.todo_check);
            // 复用时先移除旧监听，防止错误触发
            check.setOnCheckedChangeListener(null);
            check.setText(content);
            boolean isDone = done == 1;
            check.setChecked(isDone);
            if (isDone) {
                check.setPaintFlags(check.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                check.setTextColor(getResources().getColor(R.color.text_done));
            } else {
                check.setPaintFlags(check.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                check.setTextColor(getResources().getColor(R.color.text_title));
            }
            check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    Uri itemUri = ContentUris.withAppendedId(
                            NotePad.Todos.CONTENT_ID_URI_BASE, id);
                    ContentValues values = new ContentValues();
                    values.put(NotePad.Todos.COLUMN_NAME_DONE, checked ? 1 : 0);
                    getContentResolver().update(itemUri, values, null, null);
                    refresh();
                }
            });

            ImageButton del = (ImageButton) view.findViewById(R.id.todo_delete);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri itemUri = ContentUris.withAppendedId(
                            NotePad.Todos.CONTENT_ID_URI_BASE, id);
                    getContentResolver().delete(itemUri, null, null);
                    refresh();
                }
            });
        }
    }
}
