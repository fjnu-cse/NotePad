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

import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * 本实验在原有代码基础上扩展了以下功能：
 * 1. 列表条目显示笔记的最后修改时间；
 * 2. 支持按标题或内容实时搜索笔记；
 * 3. 支持按修改时间、创建时间、标题排序（排序方式会被记住）；
 * 4. 支持把所有笔记导出为文本文件；
 * 5. 美化了列表界面（搜索栏、圆角卡片、时间戳样式）。
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NotesList extends ListActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    /**
     * The columns needed by the cursor adapter
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,                        // 0
            NotePad.Notes.COLUMN_NAME_TITLE,          // 1
            NotePad.Notes.COLUMN_NAME_NOTE,           // 2
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 3
    };

    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 1;

    /** 用于保存排序方式等偏好设置 */
    private static final String PREFS_NAME = "notepad_settings";
    private static final String PREF_SORT_ORDER = "sort_order";

    /** 三种排序方式 */
    private static final String SORT_BY_MODIFIED =
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " DESC";
    private static final String SORT_BY_CREATED =
            NotePad.Notes.COLUMN_NAME_CREATE_DATE + " DESC";
    private static final String SORT_BY_TITLE =
            NotePad.Notes.COLUMN_NAME_TITLE + " COLLATE LOCALIZED ASC";

    /** 列表摘要最多显示的字符数 */
    private static final int SNIPPET_LENGTH = 60;

    /** 导出笔记的请求码 */
    private static final int REQUEST_CODE_EXPORT = 1;

    /** 列表适配器 */
    private SimpleCursorAdapter mAdapter;

    /** 搜索框 */
    private EditText mSearchBox;

    /** 当前排序方式 */
    private String mSortOrder;

    /** 列表中时间戳的显示格式 */
    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    /** 笔记数据变化时自动刷新列表（例如长按删除笔记后） */
    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            requery();
        }
    };

    /**
     * onCreate is called when Android starts this Activity from scratch.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // Gets the intent that started this Activity.
        Intent intent = getIntent();

        // If there is no data associated with the Intent, sets the data to the default URI, which
        // accesses a list of notes.
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        // 使用自定义布局：顶部搜索栏 + 笔记列表
        setContentView(R.layout.notes_list);

        /*
         * Sets the callback for context menu activation for the ListView. The listener is set
         * to be this Activity. The effect is that context menus are enabled for items in the
         * ListView, and the context menu is handled by a method in NotesList.
         */
        getListView().setOnCreateContextMenuListener(this);

        // 没有笔记时显示提示文字
        getListView().setEmptyView(findViewById(R.id.empty_view));

        // 读取上一次选择的排序方式，默认为按修改时间倒序
        mSortOrder = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_SORT_ORDER, SORT_BY_MODIFIED);

        // 搜索框内容变化时立即过滤列表
        mSearchBox = (EditText) findViewById(R.id.search_box);
        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                requery();
            }
        });

        /*
         * The following two arrays create a "map" between columns in the cursor and view IDs
         * for items in the ListView. Each element in the dataColumns array represents
         * a column name; each element in the viewID array represents the ID of a View.
         * The SimpleCursorAdapter maps them in ascending order to determine where each column
         * value will appear in the ListView.
         */

        // The names of the cursor columns to display in the view
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_NOTE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        };

        // The view IDs that will display the cursor columns
        int[] viewIDs = { R.id.note_title, R.id.note_snippet, R.id.note_time };

        // Creates the backing adapter for the ListView.
        mAdapter = new SimpleCursorAdapter(
                this,                         // The Context for the ListView
                R.layout.noteslist_item,      // Points to the XML for a list item
                null,                         // The cursor is set in onResume()
                dataColumns,
                viewIDs,
                0
        );

        // 自定义时间戳和摘要的显示方式
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.note_time) {
                    long time = cursor.getLong(columnIndex);
                    ((TextView) view).setText(mDateFormat.format(new Date(time)));
                    return true;
                }
                if (view.getId() == R.id.note_snippet) {
                    String note = cursor.getString(columnIndex);
                    if (note == null) {
                        note = "";
                    }
                    // 摘要只显示一行，去掉换行并截断
                    note = note.replace('\n', ' ').trim();
                    if (note.length() > SNIPPET_LENGTH) {
                        note = note.substring(0, SNIPPET_LENGTH) + "…";
                    }
                    ((TextView) view).setText(note);
                    return true;
                }
                if (view.getId() == R.id.note_title) {
                    String title = cursor.getString(columnIndex);
                    if (TextUtils.isEmpty(title)) {
                        title = getString(R.string.untitled_note);
                    }
                    ((TextView) view).setText(title);
                    return true;
                }
                return false;
            }
        });

        // Sets the ListView's adapter to be the cursor adapter that was just created.
        setListAdapter(mAdapter);
    }

    /**
     * 根据搜索框中的关键字和当前排序方式重新查询笔记列表。
     */
    private void requery() {
        if (mAdapter == null || mSearchBox == null) {
            return;
        }

        String keyword = mSearchBox.getText().toString().trim();

        String selection = null;
        String[] selectionArgs = null;
        if (!TextUtils.isEmpty(keyword)) {
            // LIKE 中 % 和 _ 是通配符，先把用户输入转义，避免搜索 "%" 时匹配全部笔记。
            String escaped = keyword
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            // 同时匹配标题和内容
            selection = "(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? ESCAPE '!' OR "
                    + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ? ESCAPE '!')";
            String like = "%" + escaped + "%";
            selectionArgs = new String[] { like, like };
        }

        Cursor cursor = getContentResolver().query(
                getIntent().getData(),   // The URI of the provider
                PROJECTION,
                selection,
                selectionArgs,
                mSortOrder
        );

        mAdapter.changeCursor(cursor);

        // 根据搜索状态更新空列表提示
        TextView emptyView = (TextView) findViewById(R.id.empty_view);
        emptyView.setText(TextUtils.isEmpty(keyword)
                ? R.string.empty_notes : R.string.empty_search);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 监听笔记数据的变化（新增、修改、删除）
        getContentResolver().registerContentObserver(
                getIntent().getData(), true, mObserver);
        // 从编辑界面返回后刷新列表
        requery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭查询使用的 Cursor，避免资源泄漏
        if (mAdapter != null) {
            mAdapter.changeCursor(null);
        }
    }

    /**
     * Called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Sets up a menu that provides the Insert option plus a list of alternative actions for
     * this Activity. Other applications that want to handle notes can "register" themselves in
     * Android by providing an intent filter that includes the category ALTERNATIVE and the
     * mimeTYpe NotePad.Notes.CONTENT_TYPE. If they do this, the code in onCreateOptionsMenu()
     * will add the Activity that contains the intent filter to its list of options. In effect,
     * the menu will offer the user other applications that can handle notes.
     * @param menu A Menu object, to which menu items should be added.
     * @return True, always. The menu should be displayed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // Generate any additional actions that can be performed on the
        // overall list.  In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // The paste menu item is enabled if there is data on the clipboard.
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);


        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        // If the clipboard contains an item, enables the Paste option on the menu.
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            // If the clipboard is empty, disables the menu's Paste option.
            mPasteItem.setEnabled(false);
        }

        // 勾选当前的排序方式
        MenuItem sortModified = menu.findItem(R.id.menu_sort_modified);
        MenuItem sortCreated = menu.findItem(R.id.menu_sort_created);
        MenuItem sortTitle = menu.findItem(R.id.menu_sort_title);
        if (sortModified != null) {
            sortModified.setChecked(SORT_BY_MODIFIED.equals(mSortOrder));
        }
        if (sortCreated != null) {
            sortCreated.setChecked(SORT_BY_CREATED.equals(mSortOrder));
        }
        if (sortTitle != null) {
            sortTitle.setChecked(SORT_BY_TITLE.equals(mSortOrder));
        }

        // Gets the number of notes currently being displayed.
        final boolean haveItems =
                getListAdapter() != null && getListAdapter().getCount() > 0;

        // If there are any notes in the list (which implies that one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.  This will be a combination
        // of our own specific actions along with any extensions that can be
        // found.
        if (haveItems) {

            // This is the selected item.
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // Creates an array of Intents with one element. This will be used to send an Intent
            // based on the selected menu item.
            Intent[] specifics = new Intent[1];

            // Sets the Intent in the array to be an EDIT action on the URI of the selected note.
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // Creates an array of menu items with one element. This will contain the EDIT option.
            MenuItem[] items = new MenuItem[1];

            // Creates an Intent with no specific action, using the URI of the selected note.
            Intent intent = new Intent(null, uri);

            /* Adds the category ALTERNATIVE to the Intent, with the note ID URI as its
             * data. This prepares the Intent as a place to group alternative options in the
             * menu.
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * Add alternatives to the menu
             */
            menu.addIntentOptions(
                Menu.CATEGORY_ALTERNATIVE,  // Add the Intents as options in the alternatives group.
                Menu.NONE,                  // A unique item ID is not required.
                Menu.NONE,                  // The alternatives don't need to be in order.
                null,                       // The caller's name is not excluded from the group.
                specifics,                  // These specific options must appear first.
                intent,                     // These Intent objects map to the options in specifics.
                Menu.NONE,                  // No flags are required.
                items                       // The menu items generated from the specifics-to-
                                            // Intents mapping
            );
                // If the Edit menu item exists, adds shortcuts for it.
                if (items[0] != null) {

                    // Sets the Edit menu item shortcut to numeric "1", letter "e"
                    items[0].setShortcut('1', 'e');
                }
            } else {
                // If the list is empty, removes any existing alternative actions from the menu
                menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
            }

        // Displays the menu
        return true;
    }

    /**
     * This method is called when the user selects an option from the menu, but no item
     * in the list is selected. If the option was INSERT, then a new Intent is sent out with action
     * ACTION_INSERT. The data from the incoming Intent is put into the new Intent. In effect,
     * this triggers the NoteEditor activity in the NotePad application.
     *
     * If the item was not INSERT, then most likely it was an alternative option from another
     * application. The parent method is called to process the item.
     * @param item The menu item that was selected by the user
     * @return True, if the INSERT menu item was selected; otherwise, the result of calling
     * the parent method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_add) {
            /*
             * Launches a new Activity using an Intent. The intent filter for the Activity
             * has to have action ACTION_INSERT. No category is set, so DEFAULT is assumed.
             * In effect, this starts the NoteEditor Activity in NotePad.
             */
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_paste) {
            /*
             * Launches a new Activity using an Intent. The intent filter for the Activity
             * has to have action ACTION_PASTE. No category is set, so DEFAULT is assumed.
             * In effect, this starts the NoteEditor Activity in NotePad.
             */
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_sort_modified) {
            setSortOrder(SORT_BY_MODIFIED);
            return true;
        } else if (id == R.id.menu_sort_created) {
            setSortOrder(SORT_BY_CREATED);
            return true;
        } else if (id == R.id.menu_sort_title) {
            setSortOrder(SORT_BY_TITLE);
            return true;
        } else if (id == R.id.menu_export) {
            exportAllNotes();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 切换排序方式并保存到偏好设置中。
     */
    private void setSortOrder(String sortOrder) {
        mSortOrder = sortOrder;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_SORT_ORDER, sortOrder)
                .apply();
        requery();
    }

    /**
     * 通过系统文件选择器把所有笔记导出为 UTF-8 文本文件。
     */
    private void exportAllNotes() {
        Cursor cursor = getContentResolver().query(
                getIntent().getData(),
                new String[] { NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_NOTE },
                null,
                null,
                mSortOrder);

        if (cursor == null || cursor.getCount() == 0) {
            if (cursor != null) {
                cursor.close();
            }
            Toast.makeText(this, R.string.export_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        cursor.close();

        String fileName = getString(R.string.export_file_prefix) + "_"
                + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date())
                + ".txt";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        try {
            startActivityForResult(intent, REQUEST_CODE_EXPORT);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No activity can handle ACTION_CREATE_DOCUMENT", e);
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_EXPORT && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            writeNotesToUri(data.getData());
        }
    }

    /**
     * 在后台线程中把笔记写入用户选择的文件。
     */
    private void writeNotesToUri(final Uri targetUri) {
        final Uri notesUri = getIntent().getData();
        final String sortOrder = mSortOrder;

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success = false;
                Cursor cursor = null;
                OutputStream outputStream = null;
                Writer writer = null;
                SimpleDateFormat format =
                        new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

                try {
                    cursor = getContentResolver().query(
                            notesUri,
                            new String[] {
                                    NotePad.Notes.COLUMN_NAME_TITLE,
                                    NotePad.Notes.COLUMN_NAME_NOTE,
                                    NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                            },
                            null,
                            null,
                            sortOrder);

                    outputStream = getContentResolver().openOutputStream(targetUri);
                    if (cursor != null && outputStream != null) {
                        writer = new OutputStreamWriter(outputStream, "UTF-8");

                        writer.write("NotePad 笔记导出\n");
                        writer.write("导出时间：" + format.format(new Date()) + "\n");
                        writer.write("笔记数量：" + cursor.getCount() + "\n");

                        while (cursor.moveToNext()) {
                            String title = cursor.getString(0);
                            String note = cursor.getString(1);
                            writer.write("\n========================================\n");
                            writer.write("标题：" + (title == null ? "" : title) + "\n");
                            writer.write("修改时间："
                                    + format.format(new Date(cursor.getLong(2))) + "\n");
                            writer.write("----------------------------------------\n");
                            writer.write((note == null ? "" : note) + "\n");
                        }
                        success = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to export notes", e);
                } finally {
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (IOException ignored) {
                        }
                    } else if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (IOException ignored) {
                        }
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                final boolean result = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(NotesList.this,
                                result ? R.string.export_success : R.string.export_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     *
     * The only available options are COPY and DELETE.
     *
     * Context-click is equivalent to long-press.
     *
     * @param menu A ContexMenu object to which items should be added.
     * @param view The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                                        Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        /*
         * Gets the extra info from the menu item. When an note in the Notes list is long-pressed, a
         * context menu appears. The menu items for the menu automatically get the data
         * associated with the note that was long-pressed. The data comes from the provider that
         * backs the list.
         *
         * The note's data is passed to the context menu creation routine in a ContextMenuInfo
         * object.
         *
         * When one of the context menu items is clicked, the same data is passed, along with the
         * note ID, to onContextItemSelected() via the item parameter.
         */
        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);

            // Triggers default processing of the menu item.
            return false;
        }
        // Some items (for example the ones added by addIntentOptions) may not carry
        // an AdapterContextMenuInfo; they must be left to the default handling.
        if (info == null) {
            return false;
        }
        // Appends the selected note's ID to the URI sent with the incoming Intent.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * Gets the menu item's ID and compares it to known actions.
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            // Gets a handle to the clipboard service.
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            // Copies the notes URI to the clipboard. In effect, this copies the note itself
            clipboard.setPrimaryClip(ClipData.newUri(   // new clipboard item holding a URI
                    getContentResolver(),               // resolver to retrieve URI info
                    "Note",                             // label for the clip
                    noteUri));                          // the URI

            // Returns to the caller and skips further processing.
            return true;
            //END_INCLUDE(copy)
        } else if (id == R.id.context_delete) {
            // Deletes the note from the provider by passing in a URI in note ID format.
            // Please see the introductory note about performing provider operations on the
            // UI thread.
            getContentResolver().delete(
                    noteUri,  // The URI of the provider
                    null,     // No where clause is needed, since only a single note ID is being
                    // passed in.
                    null      // No where clause is used, so no where clause arguments are needed.
            );

            // Returns to the caller and skips further processing.
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * This method is called when the user clicks a note in the displayed list.
     *
     * This method handles incoming actions of either PICK (get data from the provider) or
     * GET_CONTENT (get or create data). If the incoming action is EDIT, this method sends a
     * new Intent to start NoteEditor.
     * @param l The ListView that contains the clicked item
     * @param v The View of the individual item
     * @param position The position of v in the displayed list
     * @param id The row ID of the clicked item
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // Constructs a new URI from the incoming URI and the row ID
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // Gets the action from the incoming Intent
        String action = getIntent().getAction();

        // Handles requests for note data
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // Sets the result to return to the component that called this Activity. The
            // result contains the new URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {

            // Sends out an Intent to start an Activity that can handle ACTION_EDIT. The
            // Intent's data is the note ID URI. The effect is to call NoteEdit.
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }
}
