package com.github.tvbox.osc.ui.activity;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.HawkConfig;

import com.orhanobut.hawk.Hawk;

/**
 * 高级设置 —— 照搬 TV ModelSettingFragment 的全部子项，复用真实 HawkConfig 键与类型。
 * 点按切换/循环取值，与 TV 行为一致。
 */
public class MobileAdvanceSettingActivity extends MobileBaseActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = root();
        root.addView(titleBar("高级设置"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(4), pad, dp(24));
        scroll.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        box.addView(sectionTitle("播放"));
        LinearLayout play = card();
        addToggle(play, "播放器内核", HawkConfig.PLAY_TYPE, 2,
                new String[]{"IJK", "EXO"}, new int[]{1, 2},
                "点播播放器内核");
        addDivider(play);
        addToggle(play, "直播内核", HawkConfig.LIVE_PLAY_TYPE, 2,
                new String[]{"IJK", "EXO"}, new int[]{1, 2},
                "直播播放器内核");
        addDivider(play);
        addToggle(play, "渲染方式", HawkConfig.PLAY_RENDER, 0,
                new String[]{"Texture", "Surface"}, new int[]{0, 1}, "渲染方式");
        addDivider(play);
        addToggle(play, "画面缩放", HawkConfig.PLAY_SCALE, 0,
                new String[]{"默认", "16:9", "4:3", "填充", "原始", "裁剪"},
                new int[]{0, 1, 2, 3, 4, 5}, "画面缩放");
        addDivider(play);
        addBool(play, "自动换线", HawkConfig.AUTO_SWITCH_LINE, true, "播放失败自动切换线路");
        addDivider(play);
        addBool(play, "跳过片头片尾", HawkConfig.SHOW_PREVIEW, true, "窗口预览");
        addDivider(play);
        addBool(play, "IJK 缓存播放", HawkConfig.IJK_CACHE_PLAY, false, "IJK 边播边缓存");
        box.addView(play, cardLp());

        box.addView(sectionTitle("画面与弹幕"));
        LinearLayout view = card();
        addBool(view, "弹幕", HawkConfig.DANMU_OPEN, true, "显示弹幕");
        addDivider(view);
        addBool(view, "去广告", HawkConfig.M3U8_PURIFY, false, "M3U8 净化");
        addDivider(view);
        addBool(view, "窗口预览", HawkConfig.SHOW_PREVIEW, true, "详情页预览播放");
        box.addView(view, cardLp());

        box.addView(sectionTitle("首页与搜索"));
        LinearLayout home = card();
        addToggle(home, "首页推荐", HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC,
                new String[]{"豆瓣热播", "站点推荐", "观看历史"}, new int[]{0, 1, 2}, "首页推荐来源");
        addDivider(home);
        addBool(home, "首页多行", HawkConfig.HOME_REC_STYLE, false, "推荐多行显示");
        addDivider(home);
        addToggle(home, "搜索展示", HawkConfig.SEARCH_VIEW, 0,
                new String[]{"文字列表", "缩略图"}, new int[]{0, 1}, "搜索结果样式");
        addDivider(home);
        addBool(home, "聚合搜索", HawkConfig.FAST_SEARCH_MODE, true, "多站点同时搜索");
        addDivider(home);
        addToggle(home, "下次进入", HawkConfig.DEFAULT_LOAD_LIVE, 0,
                new String[]{"点播", "直播"}, new int[]{0, 1}, "下次打开显示的页面");
        box.addView(home, cardLp());

        box.addView(sectionTitle("历史与系统"));
        LinearLayout sys = card();
        addToggle(sys, "历史记录", HawkConfig.HISTORY_NUM, 0,
                new String[]{"30", "50", "100"}, new int[]{0, 1, 2}, "历史记录条数");
        addDivider(sys);
        addBool(sys, "历史合并", HawkConfig.HISTORY_MERGE, false, "历史合并显示");
        addDivider(sys);
        addBool(sys, "嗅探 WebView", HawkConfig.PARSE_WEBVIEW, true, "解析使用系统 WebView");
        box.addView(sys, cardLp());

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        return lp;
    }

    /** 整型枚举项：点按循环取值。 */
    private void addToggle(LinearLayout parent, final String label, final String key,
                           int def, final String[] names, final int[] values, final String hint) {
        int cur = intVal(key, def);
        String value = nameOf(cur, names, values, def);
        parent.addView(settingRow(label, value + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int current = intVal(key, def);
                int pos = 0;
                for (int i = 0; i < values.length; i++) if (values[i] == current) { pos = i; break; }
                int next = (pos + 1) % values.length;
                Hawk.put(key, values[next]);
                toast(label + "：" + names[next]);
                recreate();
            }
        }));
    }

    /** 布尔项：点按开关。 */
    private void addBool(LinearLayout parent, final String label, final String key,
                         boolean def, final String hint) {
        boolean cur = boolVal(key, def);
        parent.addView(settingRow(label, (cur ? "开启" : "关闭") + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean n = !boolVal(key, def);
                Hawk.put(key, n);
                toast(label + "：" + (n ? "开启" : "关闭"));
                recreate();
            }
        }));
    }

    private String nameOf(int cur, String[] names, int[] values, int def) {
        for (int i = 0; i < values.length; i++) if (values[i] == cur) return names[i];
        return names.length > 0 ? names[0] : String.valueOf(def);
    }

    private boolean boolVal(String key, boolean def) {
        try { return Hawk.get(key, def); } catch (Throwable t) { return def; }
    }
    private int intVal(String key, int def) {
        try { return Hawk.get(key, def); } catch (Throwable t) { return def; }
    }
}
