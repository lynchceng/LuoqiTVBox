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
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.OkGoHelper;

import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.util.ArrayList;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 手机设置页 —— 与 TV 设置项一一对应，复用 TV 的 HawkConfig 键、ApiConfig 数据层。
 * 分组：配置 / 设置 / 播放器 / 系统。手机与 TV 使用不同包名，设置互不干扰。
 */
public class MobileSettingActivity extends MobileBaseActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = root();
        root.addView(titleBar("设置"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(4), pad, dp(24));
        scroll.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ===== 配置 =====
        box.addView(sectionTitle("配置"));
        LinearLayout cfg = card();
        cfg.addView(settingRow("配置地址", apiName(), new View.OnClickListener() {
            @Override public void onClick(View v) {
                openApiConfig("");
            }
        }));
        addDivider(cfg);
        cfg.addView(settingRow("直播地址", liveApiName(), new View.OnClickListener() {
            @Override public void onClick(View v) { openApiConfig("live"); }
        }));
        addDivider(cfg);
        cfg.addView(settingRow("站点管理", siteCount() + " 个 ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileSettingActivity.this, MobileSourceActivity.class));
            }
        }));
        addDivider(cfg);
        cfg.addView(settingRow("线路切换", lineName(), new View.OnClickListener() {
            @Override public void onClick(View v) { showLineSwitch(); }
        }));
        addDivider(cfg);
        cfg.addView(settingRow("历史配置", "", new View.OnClickListener() {
            @Override public void onClick(View v) { showApiHistory(); }
        }));
        box.addView(cfg, cardLp());

        // ===== 设置 =====
        box.addView(sectionTitle("设置"));
        LinearLayout set = card();
        set.addView(settingRow("高级设置", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileSettingActivity.this, MobileAdvanceSettingActivity.class));
            }
        }));
        addDivider(set);
        set.addView(settingRow("搜索线程", searchThreads(), new View.OnClickListener() {
            @Override public void onClick(View v) { cycleSearchThreads(); }
        }));
        box.addView(set, cardLp());

        // ===== 播放器 =====
        box.addView(sectionTitle("播放器"));
        LinearLayout player = card();
        player.addView(settingRow("播放器设置", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileSettingActivity.this, MobileAdvanceSettingActivity.class));
            }
        }));
        box.addView(player, cardLp());

        // ===== 系统 =====
        box.addView(sectionTitle("系统"));
        LinearLayout sys = card();
        sys.addView(settingRow("换张壁纸", wallpaperName(), new View.OnClickListener() {
            @Override public void onClick(View v) { changeWallpaper(); }
        }));
        addDivider(sys);
        sys.addView(settingRow("恢复默认壁纸", "", new View.OnClickListener() {
            @Override public void onClick(View v) { restoreWallpaper(); }
        }));
        addDivider(sys);
        sys.addView(settingRow("壁纸虚化", boolVal("mobile_wallpaper_blur", false) ? "开" : "关",
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        boolean n = !boolVal("mobile_wallpaper_blur", false);
                        Hawk.put("mobile_wallpaper_blur", n);
                        toast(n ? "壁纸虚化已开启" : "壁纸虚化已关闭");
                        recreate();
                    }
                }));
        addDivider(sys);
        sys.addView(settingRow("安全 DNS", dohName(), new View.OnClickListener() {
            @Override public void onClick(View v) { showDnsDialog(); }
        }));
        addDivider(sys);
        sys.addView(settingRow("清空 App 缓存", "清理缓存与爬虫文件 ›", new View.OnClickListener() {
            @Override public void onClick(View v) { clearCache(); }
        }));
        addDivider(sys);
        sys.addView(settingRow("重置 App", "恢复初始设置 ›", new View.OnClickListener() {
            @Override public void onClick(View v) { confirmReset(); }
        }));
        addDivider(sys);
        sys.addView(settingRow("关于洛奇TV / 官方频道", "", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileSettingActivity.this, MobileAboutActivity.class));
            }
        }));
        box.addView(sys, cardLp());

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bottomNav(3));
        setContentView(root);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        return lp;
    }

    // ===== 显示值 =====
    private String apiName() {
        String url = str(HawkConfig.API_URL);
        if (url.isEmpty()) return "未配置 ›";
        com.github.tvbox.osc.bean.SourceBean home = ApiConfig.get().getHomeSourceBean();
        return (home != null && !TextUtils.isEmpty(home.getName()) ? home.getName() : "已配置") + " ›";
    }

    private String liveApiName() {
        String url = str(HawkConfig.LIVE_API_URL);
        return url.isEmpty() ? "未配置 ›" : "已配置 ›";
    }

    private String siteCount() {
        try {
            return String.valueOf(ApiConfig.get().getSwitchSourceBeanList().size());
        } catch (Throwable t) { return "0"; }
    }

    private String lineName() {
        ArrayList<String> lines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        return lines.isEmpty() ? "暂无线路 ›" : lines.size() + " 条 ›";
    }

    private String searchThreads() {
        return intVal("search_threads", 24) + " ›";
    }

    private void cycleSearchThreads() {
        int cur = intVal("search_threads", 24);
        int next = cur >= 32 ? 8 : cur + 8;
        Hawk.put("search_threads", next);
        toast("搜索线程：" + next);
        recreate();
    }

    private String wallpaperName() {
        String path = str("mobile_wallpaper_path");
        return TextUtils.isEmpty(path) || !new File(path).exists() ? "默认 ›" : "已设置 ›";
    }

    private String dohName() {
        int i = intVal(HawkConfig.DOH_URL, 0);
        try {
            if (OkGoHelper.dnsHttpsList.isEmpty()) OkGoHelper.getDohUrl(0);
        } catch (Throwable ignored) {}
        String name = "关闭";
        try {
            if (i > 0 && i < OkGoHelper.dnsHttpsList.size()) name = OkGoHelper.dnsHttpsList.get(i);
        } catch (Throwable ignored) {}
        return name + " ›";
    }

    // ===== 交互 =====
    private void openApiConfig(String focus) {
        Intent i = new Intent(this, MobileApiConfigActivity.class);
        i.putExtra(MobileApiConfigActivity.EXTRA_FOCUS, focus);
        startActivity(i);
    }

    /** 线路切换：与 TV ModelSettingFragment 的 API_LINE_LIST 完全一致。 */
    private void showLineSwitch() {
        final ArrayList<String> lines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        if (lines == null || lines.isEmpty()) {
            toast("线路列表为空（当前配置未提供多线路）");
            return;
        }
        String current = str(HawkConfig.API_URL);
        int idx = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (current.equals(HistoryHelper.getApiLineUrl(lines.get(i)))) { idx = i; break; }
        }
        String[] names = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) names[i] = HistoryHelper.getApiLineName(lines.get(i));
        new AlertDialog.Builder(this)
                .setTitle("线路选择")
                .setSingleChoiceItems(names, idx, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String newApi = HistoryHelper.getApiLineUrl(lines.get(which));
                        if (TextUtils.isEmpty(newApi)) return;
                        Hawk.put(HawkConfig.API_URL, newApi);
                        Hawk.put(HawkConfig.LIVE_API_URL, newApi);
                        HistoryHelper.setLiveApiHistory(newApi);
                        MobileDataInitializer.markConfigDirty();
                        toast("已切换到线路：" + HistoryHelper.getApiLineName(lines.get(which)));
                        dialog.dismiss();
                        finish();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 历史配置：与 TV 的历史配置列表共用 HawkConfig.API_HISTORY。 */
    private void showApiHistory() {
        final ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        if (history == null || history.isEmpty()) {
            toast("暂无历史配置");
            return;
        }
        String[] items = history.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("历史配置")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String value = history.get(which);
                        Hawk.put(HawkConfig.API_URL, value);
                        Hawk.put(HawkConfig.LIVE_API_URL, value);
                        HistoryHelper.setLiveApiHistory(value);
                        MobileDataInitializer.markConfigDirty();
                        toast("配置已切换，正在重新加载");
                        finish();
                    }
                })
                .setNeutralButton("清空历史", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Hawk.put(HawkConfig.API_HISTORY, new ArrayList<String>());
                        toast("历史已清空");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 安全 DNS：使用 TV 同一份 OkGoHelper.dnsHttpsList，并重载 DNS。 */
    private void showDnsDialog() {
        try { OkGoHelper.getDohUrl(0); } catch (Throwable ignored) {}
        final ArrayList<String> names = OkGoHelper.dnsHttpsList;
        if (names == null || names.isEmpty()) {
            toast("DNS 列表为空");
            return;
        }
        int cur = intVal(HawkConfig.DOH_URL, 0);
        new AlertDialog.Builder(this)
                .setTitle("安全 DNS")
                .setSingleChoiceItems(names.toArray(new String[0]), cur, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Hawk.put(HawkConfig.DOH_URL, which);
                        try {
                            OkGoHelper.reloadDns();
                            IjkMediaPlayer.toggleDotPort(which > 0);
                        } catch (Throwable ignored) {}
                        toast("安全 DNS：" + names.get(which));
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 换壁纸：优先用配置里的 wallpaper，也可手动输入图片地址，全部在手机端完成。 */
    private void changeWallpaper() {
        final String configWp = ApiConfig.get().wallpaper;
        if (!TextUtils.isEmpty(configWp)) {
            downloadWallpaper(configWp);
            return;
        }
        final EditText et = new EditText(this);
        et.setHint("输入壁纸图片地址（http/https）");
        et.setSingleLine(true);
        et.setTextColor(android.graphics.Color.parseColor(C_TEXT));
        new AlertDialog.Builder(this)
                .setTitle("换张壁纸")
                .setView(et)
                .setPositiveButton("下载并应用", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String url = et.getText().toString().trim();
                        if (TextUtils.isEmpty(url)) { toast("请输入图片地址"); return; }
                        downloadWallpaper(url);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadWallpaper(String url) {
        toast("正在下载壁纸…");
        OkGo.<File>get(url).execute(new FileCallback(getFilesDir().getAbsolutePath(), "wp") {
            @Override public void onSuccess(Response<File> response) {
                File f = response.body();
                if (f != null && f.exists()) {
                    Hawk.put("mobile_wallpaper_path", f.getAbsolutePath());
                    toast("壁纸已应用");
                    recreate();
                }
            }
            @Override public void onError(Response<File> response) {
                super.onError(response);
                toast("壁纸下载失败：" + (response.getException() == null
                        ? "" : response.getException().getMessage()));
            }
        });
    }

    private void restoreWallpaper() {
        try {
            File f = new File(getFilesDir(), "wp");
            if (f.exists()) f.delete();
            Hawk.put("mobile_wallpaper_path", "");
            toast("已恢复默认壁纸");
            recreate();
        } catch (Throwable t) {
            toast("恢复失败：" + t.getMessage());
        }
    }

    /** 清空缓存：与 TV onClickClearCache 一致，含爬虫缓存与 IJK/ Thunder 缓存文件。 */
    private void clearCache() {
        toast("正在清理缓存…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ApiConfig.get().clearSpiderCache();
                    File cacheDir = new File(FileUtils.getCachePath());
                    if (cacheDir.exists()) FileUtils.cleanDirectory(cacheDir);
                    FileUtils.clearSpiderCacheFiles();
                } catch (Throwable ignored) {}
                runOnUiThread(new Runnable() {
                    @Override public void run() { toast("缓存已清空"); }
                });
            }
        }).start();
    }

    /** 重置 App：手机端自行完成，无需到电视端操作。 */
    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("重置 App")
                .setMessage("将清空本机所有设置、历史记录与收藏，恢复初始状态。此操作不可撤销。")
                .setPositiveButton("确认重置", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            File wp = new File(getFilesDir(), "wp");
                            if (wp.exists()) wp.delete();
                            RoomDataManger.deleteVodRecordAll();
                            RoomDataManger.deleteVodCollectAll();
                            Hawk.clear();
                        } catch (Throwable ignored) {}
                        toast("已重置，正在重启");
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ===== 安全读取（防类型不匹配闪退）=====
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
