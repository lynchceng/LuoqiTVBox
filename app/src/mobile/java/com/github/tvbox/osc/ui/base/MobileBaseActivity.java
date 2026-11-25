package com.github.tvbox.osc.ui.base;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 手机竖屏版公共基类。
 *
 * 继承 AppCompatActivity（全局初始化在 App.onCreate 已完成：Hawk/OkGo/数据库/播放器/QuickJS），
 * 不继承 TV 的 BaseActivity（那是横屏沉浸式 + AutoSize MM 单位，不适合手机）。
 *
 * 提供深色主题（深蓝黑 #0F1B2D / 卡片 #16243A / 强调橙色）的统一 UI 构件，
 * 所有手机页面复用，保证与参考图一致的视觉风格。
 */
public abstract class MobileBaseActivity extends AppCompatActivity {

    // ===== 深色主题配色（参考目标 UI）=====
    public static final String C_BG = "#0E1929";        // 页面背景（深蓝黑）
    public static final String C_CARD = "#182739";      // 分组卡片背景
    public static final String C_CARD_2 = "#1E3046";    // 次级卡片 / 选中
    public static final String C_TEXT = "#F2F5F9";      // 主文字
    public static final String C_TEXT_2 = "#9FB0C6";    // 次要文字
    public static final String C_ACCENT = "#FF7043";    // 强调色（橙红）
    public static final String C_BLUE = "#4A90E2";      // 辅助蓝（悬浮按钮）
    public static final String C_DIVIDER = "#26374D";   // 分割线

    protected int pad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pad = dp(16);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
    }

    // ===== 单位 / 颜色工具 =====
    public int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics());
    }

    public int sp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                value, getResources().getDisplayMetrics());
    }

    /** 创建圆角矩形背景。 */
    public static GradientDrawable roundBg(String color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(radiusDp); // 注意：调用方传入的是 px
        return d;
    }

    public static GradientDrawable roundBg(String color, int radiusDp, String strokeColor, int strokePx) {
        GradientDrawable d = roundBg(color, radiusDp);
        d.setStroke(strokePx, Color.parseColor(strokeColor));
        return d;
    }

    // ===== 通用文本 =====
    public TextView text(String content, int sizeSp, String color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(Color.parseColor(color));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    /** 重载：接受 int 颜色值（如 Color.parseColor 的返回）。 */
    public TextView text(String content, int sizeSp, int colorInt, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(colorInt);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    // ===== 分组标题（如「配置」「设置」）=====
    public TextView sectionTitle(String title) {
        TextView tv = text(title, 15, C_TEXT_2, true);
        tv.setPadding(0, dp(18), 0, dp(8));
        return tv;
    }

    /** 分组卡片容器（圆角、深蓝灰背景、纵向）。 */
    public LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(roundBg(C_CARD, dp(12)));
        return c;
    }

    /**
     * 设置条目行：左侧名称（+可选副标题），右侧值 + 箭头，整行可点击。
     * 与参考图「配置地址  线路合集 >」一致。
     */
    public LinearLayout settingRow(String name, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(13), dp(14), dp(13));
        row.setBackground(roundBg(C_CARD, 0));
        row.setClickable(true);
        row.setFocusable(true);

        TextView nameTv = text(name, 15, C_TEXT, false);
        row.addView(nameTv, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (value != null && !value.isEmpty()) {
            TextView valueTv = text(value, 14, C_TEXT_2, false);
            valueTv.setGravity(Gravity.END);
            row.addView(valueTv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView arrow = text("›", 20, C_TEXT_2, false);
        arrow.setPadding(dp(8), 0, 0, 0);
        row.addView(arrow);

        if (click != null) row.setOnClickListener(click);
        return row;
    }

    /** 在分组卡片中加入一条分割线。 */
    public void addDivider(LinearLayout card) {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor(C_DIVIDER));
        card.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    /** 顶部标题栏：返回箭头 + 标题。 */
    public LinearLayout titleBar(String title) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.parseColor(C_BG));
        bar.setPadding(dp(8), dp(12), dp(8), dp(12));

        TextView back = text("‹", 28, C_TEXT, false);
        back.setPadding(dp(4), 0, dp(12), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        bar.addView(back);

        TextView t = text(title, 18, C_TEXT, true);
        bar.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    /** 创建根容器（深色背景 + 纵向）。 */
    public LinearLayout root() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setBackgroundColor(Color.parseColor(C_BG));
        r.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return r;
    }

    /** 底部导航（主页/直播/短剧/历史收藏/我的）。current: 0-4，-1 表示无高亮。 */
    public LinearLayout bottomNav(final int current) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.parseColor("#0B1422"));
        nav.setPadding(0, dp(8), 0, dp(8));

        String[] labels = {"主页", "直播", "短剧", "历史/收藏", "我的"};
        final Class<?>[] targets = {
                com.github.tvbox.osc.ui.activity.MobileHomeActivity.class,
                com.github.tvbox.osc.ui.activity.MobileLiveActivity.class,
                com.github.tvbox.osc.ui.activity.MobileListActivity.class, // 短剧 -> 分类浏览
                com.github.tvbox.osc.ui.activity.MobileListActivity.class,
                com.github.tvbox.osc.ui.activity.MobileSettingActivity.class
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView item = text(labels[i], 12, current == i ? C_ACCENT : C_TEXT_2, current == i);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2), dp(6), dp(2), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nav.addView(item, lp);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    onNavClick(idx, targets[idx]);
                }
            });
        }
        return nav;
    }

    /** 底部导航点击默认行为；子类可覆写以处理"历史/收藏"等多模式入口。 */
    protected void onNavClick(int index, Class<?> target) {
        if (index == 3) {
            // 历史/收藏 -> 历史模式
            Intent intent = new Intent(this, target);
            intent.putExtra("mode", "history");
            startActivity(intent);
            return;
        }
        if (index == 2) {
            // 短剧 -> 分类浏览模式（用首页的推荐/搜索承载）
            Intent intent = new Intent(this, com.github.tvbox.osc.ui.activity.MobileHomeActivity.class);
            startActivity(intent);
            return;
        }
        if (target != null && !target.equals(getClass())) {
            startActivity(new Intent(this, target));
        }
    }

    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** 圆角图片占位背景色。 */
    public static GradientDrawable coverPlaceholder() {
        return roundBg("#22344A", 6);
    }
}
