package com.github.tvbox.osc.ui.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.HawkConfig;

import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

/**
 * 高级设置 —— 与 TV 端 ModelSettingFragment 的设置项逐项对齐，复用同一 HawkConfig 键与类型。
 * 手机端只改 UI（竖屏卡片 + 点按循环/弹窗），底层行为与 TV 完全一致。
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

        // ===== 配置（与 TV 高级设置首组一致）=====
        box.addView(sectionTitle("配置"));
        LinearLayout cfg = card();
        cfg.addView(settingRow("配置地址", shortUrl(str(HawkConfig.API_URL)), new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileAdvanceSettingActivity.this, MobileApiConfigActivity.class));
            }
        }));
        addDivider(cfg);
        cfg.addView(settingRow("历史配置", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileAdvanceSettingActivity.this, MobileApiConfigActivity.class));
            }
        }));
        addDivider(cfg);
        cfg.addView(settingRow("线路切换", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileAdvanceSettingActivity.this, MobileSettingActivity.class));
            }
        }));
        box.addView(cfg, cardLp());

        // ===== 播放 =====
        box.addView(sectionTitle("播放"));
        LinearLayout play = card();
        addToggle(play, "播放器内核", HawkConfig.PLAY_TYPE, 2,
                new String[]{"IJK", "EXO"}, new int[]{1, 2}, "点播播放器内核");
        addDivider(play);
        addToggle(play, "直播内核", HawkConfig.LIVE_PLAY_TYPE, 2,
                new String[]{"IJK", "EXO", "MXPlayer"}, new int[]{1, 2, 10}, "直播播放器内核");
        addDivider(play);
        addIjkCodec(play);
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
        addBool(play, "IJK 缓存播放", HawkConfig.IJK_CACHE_PLAY, false, "IJK 边播边缓存");
        box.addView(play, cardLp());

        // ===== 画面与弹幕 =====
        box.addView(sectionTitle("画面与弹幕"));
        LinearLayout view = card();
        addBool(view, "弹幕", HawkConfig.DANMU_OPEN, true, "显示弹幕");
        addDivider(view);
        addText(view, "弹幕接口", HawkConfig.DANMU_API, "自定义弹幕 API 地址");
        addDivider(view);
        addBool(view, "去广告", HawkConfig.M3U8_PURIFY, false, "M3U8 净化");
        addDivider(view);
        addBool(view, "窗口预览", HawkConfig.SHOW_PREVIEW, true, "详情页预览播放");
        box.addView(view, cardLp());

        // ===== 首页与搜索 =====
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

        // ===== 直播（TV LivePlayActivity 中的设置项，手机端同样可设置）=====
        box.addView(sectionTitle("直播"));
        LinearLayout live = card();
        addText(live, "EPG 地址", HawkConfig.EPG_URL, "自定义节目单地址");
        addDivider(live);
        addBool(live, "换台反转", HawkConfig.LIVE_CHANNEL_REVERSE, false, "上下键换台方向反转");
        addDivider(live);
        addBool(live, "跨组换台", HawkConfig.LIVE_CROSS_GROUP, false, "上下键切换频道组");
        addDivider(live);
        addConnectTimeout(live);
        addDivider(live);
        addBool(live, "显示网速", HawkConfig.LIVE_SHOW_NET_SPEED, false, "播放时显示实时网速");
        addDivider(live);
        addBool(live, "显示分辨率", HawkConfig.LIVE_SHOW_RESOLUTION, false, "播放时显示分辨率");
        addDivider(live);
        addBool(live, "显示时间", HawkConfig.LIVE_SHOW_TIME, false, "播放时显示时间");
        box.addView(live, cardLp());

        // ===== 历史与系统 =====
        box.addView(sectionTitle("历史与系统"));
        LinearLayout sys = card();
        addToggle(sys, "历史记录", HawkConfig.HISTORY_NUM, 0,
                new String[]{"30", "50", "100"}, new int[]{0, 1, 2}, "历史记录条数");
        addDivider(sys);
        addBool(sys, "历史合并", HawkConfig.HISTORY_MERGE, false, "历史合并显示");
        addDivider(sys);
        addBool(sys, "嗅探 WebView", HawkConfig.PARSE_WEBVIEW, true, "解析使用系统 WebView");
        addDivider(sys);
        addBool(sys, "调试模式", HawkConfig.DEBUG_OPEN, false, "打开调试日志");
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
                final int nextPos = (pos + 1) % values.length;
                new AlertDialog.Builder(MobileAdvanceSettingActivity.this)
                        .setTitle(label)
                        .setSingleChoiceItems(names, pos, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                Hawk.put(key, values[which]);
                                toast(label + "：" + names[which]);
                                dialog.dismiss();
                                recreate();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
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

    /** 文本项：弹窗输入，与 TV 的自定义弹幕/EPG 地址一致。 */
    private void addText(LinearLayout parent, final String label, final String key, final String hint) {
        final String cur = str(key);
        parent.addView(settingRow(label, TextUtils.isEmpty(cur) ? "未设置 ›" : shortUrl(cur) + " ›",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        final EditText et = new EditText(MobileAdvanceSettingActivity.this);
                        et.setHint(hint);
                        et.setText(cur);
                        et.setSingleLine(true);
                        et.setTextColor(android.graphics.Color.parseColor(C_TEXT));
                        new AlertDialog.Builder(MobileAdvanceSettingActivity.this)
                                .setTitle(label)
                                .setView(et)
                                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                                    @Override public void onClick(DialogInterface dialog, int which) {
                                        Hawk.put(key, et.getText().toString().trim());
                                        toast(label + "已保存");
                                        recreate();
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                }));
    }

    /** IJK 解码：从 TV 的 ApiConfig.getIjkCodes() 读取，与 TV 完全同一份列表。 */
    private void addIjkCodec(LinearLayout parent) {
        final List<IJKCode> codes;
        try { codes = ApiConfig.get().getIjkCodes(); }
        catch (Throwable t) { return; }
        if (codes == null || codes.isEmpty()) return;
        String cur = str(HawkConfig.IJK_CODEC);
        if (TextUtils.isEmpty(cur)) cur = "硬解码";
        parent.addView(settingRow("IJK 解码", cur + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                String[] names = new String[codes.size()];
                int sel = 0;
                for (int i = 0; i < codes.size(); i++) {
                    names[i] = codes.get(i).getName();
                    if (names[i].equals(str(HawkConfig.IJK_CODEC))) sel = i;
                }
                new AlertDialog.Builder(MobileAdvanceSettingActivity.this)
                        .setTitle("IJK 解码")
                        .setSingleChoiceItems(names, sel, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                Hawk.put(HawkConfig.IJK_CODEC, codes.get(which).getName());
                                toast("IJK 解码：" + codes.get(which).getName());
                                dialog.dismiss();
                                recreate();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        }));
    }

    /** 直播连接超时：与 TV 的 (index + 1) * 5 秒一致。 */
    private void addConnectTimeout(LinearLayout parent) {
        final int cur = intVal(HawkConfig.LIVE_CONNECT_TIMEOUT, 1);
        parent.addView(settingRow("连接超时", ((cur + 1) * 5) + " 秒 ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                int next = cur >= 4 ? 0 : cur + 1;
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, next);
                toast("连接超时：" + ((next + 1) * 5) + " 秒");
                recreate();
            }
        }));
    }

    private String nameOf(int cur, String[] names, int[] values, int def) {
        for (int i = 0; i < values.length; i++) if (values[i] == cur) return names[i];
        return names.length > 0 ? names[0] : String.valueOf(def);
    }

    private String shortUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        return url.length() > 28 ? url.substring(0, 28) + "…" : url;
    }

    private String str(String key) {
        try { return Hawk.get(key, ""); } catch (Throwable t) { return ""; }
    }
    private boolean boolVal(String key, boolean def) {
        try { return Hawk.get(key, def); } catch (Throwable t) { return def; }
    }
    private int intVal(String key, int def) {
        try { return Hawk.get(key, def); } catch (Throwable t) { return def; }
    }
}
