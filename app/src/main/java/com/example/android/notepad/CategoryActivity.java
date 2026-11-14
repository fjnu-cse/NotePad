package com.example.android.notepad;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * 分类导航页：网格卡片，点击进入对应分类的笔记列表或待办清单。
 */
public class CategoryActivity extends Activity {

    /** 传给 NotesList 的分类过滤 extra key */
    public static final String EXTRA_CATEGORY = "category_filter";
    public static final String ALL = "全部";
    public static final String TODO = "待办清单";

    // 分类名称与对应图标
    private static final String[] NAMES = {
            "全部笔记", "工作", "学习", "生活", "其他", TODO
    };
    private static final int[] ICONS = {
            R.drawable.app_notes,
            R.drawable.ic_menu_edit,
            R.drawable.ic_menu_save,
            R.drawable.app_notes,
            R.drawable.ic_menu_compose,
            R.drawable.ic_menu_revert
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category);
        setTitle("笔记分类");

        GridView grid = (GridView) findViewById(R.id.category_grid);
        grid.setAdapter(new CategoryAdapter());

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String name = NAMES[position];
                if (TODO.equals(name)) {
                    startActivity(new Intent(CategoryActivity.this, TodoActivity.class));
                } else {
                    Intent intent = new Intent(CategoryActivity.this, NotesList.class);
                    // 全部笔记传"全部"，其余传具体分类名
                    intent.putExtra(EXTRA_CATEGORY,
                            "全部笔记".equals(name) ? ALL : name);
                    startActivity(intent);
                }
            }
        });
    }

    private class CategoryAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return NAMES.length;
        }

        @Override
        public Object getItem(int position) {
            return NAMES[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(CategoryActivity.this)
                        .inflate(R.layout.item_category, parent, false);
            }
            ImageView icon = (ImageView) convertView.findViewById(R.id.category_icon);
            TextView name = (TextView) convertView.findViewById(R.id.category_name);
            icon.setImageResource(ICONS[position]);
            name.setText(NAMES[position]);
            return convertView;
        }
    }
}
