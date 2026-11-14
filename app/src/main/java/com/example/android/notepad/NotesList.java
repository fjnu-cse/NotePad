/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.app.AlertDialog;              // ★ 新增：分类筛选对话框
import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.ContentValues;
import android.content.DialogInterface;        // ★ 新增
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;                 // 判断字符串是否为空（查询用）
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SearchView;                // 标题栏搜索框控件（查询用）
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;                     // ★ 新增：收集筛选参数
import java.util.Date;
import java.util.List;                          // ★ 新增
import java.util.Locale;

public class NotesList extends ListActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    /**
     * The columns needed by the cursor adapter
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,                            // 0 行号
            NotePad.Notes.COLUMN_NAME_TITLE,              // 1 标题
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,  // 2 修改时间（毫秒时间戳）
            NotePad.Notes.COLUMN_NAME_CATEGORY,           // 3 ★ 分类
    };

    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 1;

    /** 时间格式化器：把毫秒时间戳转成"年-月-日 时:分:秒"可读格式 */
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    /** 保存列表适配器和当前数据游标（搜索刷新、退出释放用） */
    private SimpleCursorAdapter mAdapter;
    private Cursor mCursor;

    /* ★ 新增：当前分类筛选条件（null = 全部）；当前搜索关键字 */
    private String mCategoryFilter = null;
    private String mSearchKeyword = null;

    // SAF 文件选择请求码
    private static final int REQ_EXPORT = 1001;
    private static final int REQ_BACKUP = 1002;
    private static final int REQ_RESTORE = 1003;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        getListView().setOnCreateContextMenuListener(this);

        // 使用含悬浮按钮的列表布局
        setContentView(R.layout.activity_notes_list);

        // 读取分类导航页传入的过滤条件（"全部"或无 extra 表示不过滤）
        String cat = getIntent().getStringExtra(CategoryActivity.EXTRA_CATEGORY);
        if (cat != null && !CategoryActivity.ALL.equals(cat)) {
            mCategoryFilter = cat;
        }

        // 悬浮按钮：新建笔记
        findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            }
        });

        // 查询数据库，只返回正常笔记（trashed=0），回收站里的不在此显示
        mCursor = getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                NotePad.Notes.COLUMN_NAME_TRASHED + "=0",
                null,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        // 列 → 控件 的映射（★ 加了分类列）
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_CATEGORY
        };
        int[] viewIDs = {
                android.R.id.text1,
                R.id.text2,
                R.id.text3
        };

        // 创建适配器并保存到成员变量 mAdapter
        mAdapter = new SimpleCursorAdapter(
                this,
                R.layout.noteslist_item,
                mCursor,
                dataColumns,
                viewIDs
        );

        // ViewBinder：★ 同时处理时间（text2）和分类标签（text3）
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.text2) {
                    // 毫秒时间戳 → 可读字符串
                    long timestamp = cursor.getLong(columnIndex);
                    ((TextView) view).setText(DATE_FORMAT.format(new Date(timestamp)));

                    // 给同一行的删除按钮绑定点击事件（ViewBinder 每行绑定都会执行，覆盖旧监听）
                    final long noteId = cursor.getLong(
                            cursor.getColumnIndexOrThrow(NotePad.Notes._ID));
                    View row = (View) view.getParent();
                    ImageButton delBtn = (ImageButton) row.findViewById(R.id.item_delete);
                    delBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Uri itemUri = ContentUris.withAppendedId(
                                    NotePad.Notes.CONTENT_ID_URI_BASE, noteId);
                            ContentValues cv = new ContentValues();
                            cv.put(NotePad.Notes.COLUMN_NAME_TRASHED, 1);
                            getContentResolver().update(itemUri, cv, null, null);
                            Toast.makeText(NotesList.this, "已移入回收站，可在回收站恢复",
                                    Toast.LENGTH_SHORT).show();
                            performQuery(mSearchKeyword);
                        }
                    });
                    return true;
                }
                if (view.getId() == R.id.text3) {
                    // 分类：显示成 [工作] 这样的粉色标签；旧笔记没分类时为空
                    String category = cursor.getString(columnIndex);
                    ((TextView) view).setText(category == null ? "" : "[" + category + "]");
                    return true;
                }
                return false;
            }
        });

        setListAdapter(mAdapter);
    }

    /* ★ 新增：回到前台时刷新列表
     * 从编辑页返回时，笔记的标题/内容/分类可能已经改动，必须重新查询才能看到新分类标签 */
    @Override
    protected void onResume() {
        super.onResume();
        performQuery(mSearchKeyword);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // 取出菜单里的搜索项，配置 SearchView（查询功能）
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("搜索标题或内容");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performQuery(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                performQuery(newText);
                return true;
            }
        });

        // 原有逻辑：允许其他应用为笔记列表提供扩展菜单
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
        return super.onCreateOptionsMenu(menu);
    }

    /* ★ 修改：核心查询方法 —— 把"关键字"和"分类"两个条件组合起来
     * 例如：在工作分类下搜索"apple"，就是 (标题含apple OR 内容含apple) AND 分类=工作 */
    private void performQuery(String keyword) {
        // 记住当前关键字，onResume 刷新时沿用
        mSearchKeyword = keyword;

        // 用 StringBuilder 动态拼 WHERE 条件；用 List 收集占位符（?）的参数
        StringBuilder selection = new StringBuilder();
        List<String> args = new ArrayList<String>();

        // 基础条件：只显示正常笔记，排除回收站
        selection.append(NotePad.Notes.COLUMN_NAME_TRASHED).append("=0");

        // 条件1：关键字模糊匹配（标题 OR 内容），外面加括号保证 AND 优先级正确
        if (!TextUtils.isEmpty(keyword)) {
            selection.append(" AND (")
                    .append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ? OR ")
                    .append(NotePad.Notes.COLUMN_NAME_NOTE).append(" LIKE ?)");
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
        }

        // 条件2：分类精确匹配
        if (mCategoryFilter != null) {
            selection.append(" AND ")
                    .append(NotePad.Notes.COLUMN_NAME_CATEGORY).append(" = ?");
            args.add(mCategoryFilter);
        }

        Cursor newCursor = getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                selection.length() > 0 ? selection.toString() : null,   // 无条件是查全部
                args.isEmpty() ? null : args.toArray(new String[0]),
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        // 换到适配器上，列表立即刷新（changeCursor 会自动关闭旧游标）
        mAdapter.changeCursor(newCursor);
        mCursor = newCursor;
    }

    /* ★ 新增：弹出"按分类筛选"单选对话框 */
    private void showCategoryFilterDialog() {
        final String[] options = { "全部", "工作", "学习", "生活", "其他" };

        // 定位当前筛选在选项中的位置，让对话框默认选中它
        int checked = 0;
        if (mCategoryFilter != null) {
            for (int i = 1; i < options.length; i++) {
                if (options[i].equals(mCategoryFilter)) {
                    checked = i;
                    break;
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("按分类筛选")
                .setSingleChoiceItems(options, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 选"全部"→ null（不过滤）；否则记为所选分类名
                        mCategoryFilter = (which == 0) ? null : options[which];
                        dialog.dismiss();
                        performQuery(mSearchKeyword);   // 立刻按新分类重新查询
                    }
                })
                .show();
    }

    // 弹出"创建文档"选择器（导出/备份）
    private void createDocument(String mime, String defaultName, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, defaultName);
        startActivityForResult(intent, requestCode);
    }

    // 弹出"打开文档"选择器（恢复）
    private void openDocument(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri docUri = data.getData();
        try {
            if (requestCode == REQ_EXPORT) {
                writeText(docUri, BackupUtils.buildExportText(getContentResolver()));
                toast("导出成功");
            } else if (requestCode == REQ_BACKUP) {
                writeText(docUri, BackupUtils.buildBackupJson(getContentResolver()));
                toast("备份成功");
            } else if (requestCode == REQ_RESTORE) {
                int count = BackupUtils.restoreFromJson(getContentResolver(),
                        readText(docUri));
                toast("恢复成功，导入 " + count + " 条笔记");
                performQuery(mSearchKeyword);
            }
        } catch (Exception e) {
            Log.e(TAG, "document operation failed", e);
            toast("操作失败：" + e.getMessage());
        }
    }

    private void writeText(Uri uri, String text) throws Exception {
        OutputStream os = getContentResolver().openOutputStream(uri);
        os.write(text.getBytes("UTF-8"));
        os.flush();
        os.close();
    }

    private String readText(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) {
            bos.write(buf, 0, len);
        }
        is.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // 页面销毁时关闭游标，释放数据库资源
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCursor != null) {
            mCursor.close();
        }
    }

    /* ---------- 以下方法保持原样 ---------- */

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            mPasteItem.setEnabled(false);
        }

        final boolean haveItems = getListAdapter().getCount() > 0;
        if (haveItems) {
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());
            Intent[] specifics = new Intent[1];
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);
            MenuItem[] items = new MenuItem[1];
            Intent intent = new Intent(null, uri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,
                    Menu.NONE,
                    Menu.NONE,
                    null,
                    specifics,
                    intent,
                    Menu.NONE,
                    items
            );
            if (items[0] != null) {
                items[0].setShortcut('1', 'e');
            }
        } else {
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;
        } else if (item.getItemId() == R.id.menu_paste) {
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        } else if (item.getItemId() == R.id.menu_filter) {
            showCategoryFilterDialog();
            return true;
        } else if (item.getItemId() == R.id.menu_batch) {
            startActivity(new Intent(this, BatchActivity.class));
            return true;
        } else if (item.getItemId() == R.id.menu_categories) {
            startActivity(new Intent(this, CategoryActivity.class));
            return true;
        } else if (item.getItemId() == R.id.menu_todo) {
            startActivity(new Intent(this, TodoActivity.class));
            return true;
        } else if (item.getItemId() == R.id.menu_backup) {
            createDocument("application/json", "notepad_backup.json", REQ_BACKUP);
            return true;
        } else if (item.getItemId() == R.id.menu_restore) {
            openDocument(REQ_RESTORE);
            return true;
        } else if (item.getItemId() == R.id.menu_trash) {
            startActivity(new Intent(this, TrashActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            return;
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);
        int id = item.getItemId();
        if (id == R.id.context_open) {
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(
                    getContentResolver(),
                    "Note",
                    noteUri));
            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(noteUri, null, null);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }
}
