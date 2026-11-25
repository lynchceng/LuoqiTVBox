package com.github.tvbox.osc.ui.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.ui.dialog.AboutDialog;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.DanmuApiDialog;
import com.github.tvbox.osc.util.DanmuHelper;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;

import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 手机设置页 —— 与 TV 端 ModelSettingFragment / fragment_model.xml 的 29 个设置项严格一一对应。
 * 不增加、不遗漏；全部复用 TV 同一份 HawkConfig 键、同一份工具类与同一份对话框。
 * 手机端仅把横屏 TV 交互换成竖屏卡片 + 点按弹窗。
 *
 * 对应 TV 顺序：
 * 调试模式 / 配置地址 / 配置历史 / 线路选择 / 首页站源 / 下次进入 / 首页推荐 / 首页多行 /
 * 搜索展示 / 聚合搜索 / 播放器 / IJK解码方式 / 去广告 / 弹幕 / 弹幕地址 / 渲染方式 /
 * 自动换线 / 安全DNS / 嗅探Webview / 历史合并 / 历史记录 / 换张壁纸 / 重置壁纸 /
 * 画面缩放 / 窗口预览 / IJK缓存 / 清空缓存 / 数据备份 / 关于
 */
public class MobileSettingActivity extends MobileBaseActivity {

    private LinearLayout listBox;
    private LinearLayout currentCard;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = root();
        root.addView(titleBar("设置"));

        ScrollView scroll = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(pad, dp(4), pad, dp(24));
        scroll.addView(listBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildSettings();

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bottomNav(3));
        setContentView(root);
    }

    private void buildSettings() {
        // ===== 配置 =====
        group("配置");
        addBool("调试模式", HawkConfig.DEBUG_OPEN, false, "已打开", "已关闭", null);
        addRow("配置地址", shortUrl(apiInputForDisplay()) + " ›",
                new View.OnClickListener() {
                    @Override public void onClick(View v) { openApiConfig(); }
                });
        addRow("配置历史", "", new View.OnClickListener() {
            @Override public void onClick(View v) { showApiHistory(); }
        });
        addRow("线路选择", currentLineName() + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) { showApiLines(); }
        });

        // ===== 首页与搜索 =====
        group("首页与搜索");
        addRow("首页站源", homeSourceName() + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) { showHomeSources(); }
        });
        addBool("下次进入", HawkConfig.DEFAULT_LOAD_LIVE, false, "直播", "点播", null);
        addChoice("首页推荐", HawkConfig.HOME_REC, HawkConfig.DEFAULT_HOME_REC,
                new String[]{"豆瓣热播", "站点推荐", "观看历史"}, new int[]{0, 1, 2}, null);
        addBool("首页多行", HawkConfig.HOME_REC_STYLE, false, "是", "否", null);
        addChoice("搜索展示", HawkConfig.SEARCH_VIEW, 0,
                new String[]{"文字列表", "缩略图"}, new int[]{0, 1}, null);
        addBool("聚合搜索", HawkConfig.FAST_SEARCH_MODE, true, "开启", "关闭", null);

        // ===== 播放与弹幕 =====
        group("播放与弹幕");
        addPlayerChoice();
        addIjkCodec();
        addBool("去广告", HawkConfig.M3U8_PURIFY, false, "开启", "关闭", null);
        addDanmuRow();
        addRow("弹幕地址", danmuApiName() + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) { showDanmuApi(); }
        });
        addChoice("渲染方式", HawkConfig.PLAY_RENDER, 0,
                new String[]{PlayerHelper.getRenderName(0), PlayerHelper.getRenderName(1)},
                new int[]{0, 1}, new Runnable() {
                    @Override public void run() { PlayerHelper.init(); }
                });
        addBool("自动换线", HawkConfig.AUTO_SWITCH_LINE, true, "开启", "关闭", null);
        addRow("安全DNS", dnsName() + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) { showDns(); }
        });
        addBool("嗅探Webview", HawkConfig.PARSE_WEBVIEW, true, "系统自带", "XWalkView", null);

        // ===== 历史与画面 =====
        group("历史与画面");
        addBool("历史合并", HawkConfig.HISTORY_MERGE, false, "开启", "关闭", null);
        addChoice("历史记录", HawkConfig.HISTORY_NUM, 0,
                new String[]{HistoryHelper.getHistoryNumName(0),
                        HistoryHelper.getHistoryNumName(1),
                        HistoryHelper.getHistoryNumName(2)},
                new int[]{0, 1, 2}, null);
        addRow("换张壁纸", "", new View.OnClickListener() {
            @Override public void onClick(View v) { changeWallpaper(); }
        });
        addRow("重置壁纸", "", new View.OnClickListener() {
            @Override public void onClick(View v) { restoreWallpaper(); }
        });
        String[] scaleNames = new String[6];
        int[] scaleValues = new int[6];
        for (int i = 0; i < 6; i++) {
            scaleNames[i] = PlayerHelper.getScaleName(i);
            scaleValues[i] = i;
        }
        addChoice("画面缩放", HawkConfig.PLAY_SCALE, 0, scaleNames, scaleValues, null);
        addBool("窗口预览", HawkConfig.SHOW_PREVIEW, true, "开启", "关闭", null);

        // ===== 系统 =====
        group("系统");
        addBool("IJK缓存", HawkConfig.IJK_CACHE_PLAY, false, "开启", "关闭", null);
        addRow("清空缓存", "", new View.OnClickListener() {
            @Override public void onClick(View v) { clearCache(); }
        });
        addRow("数据备份", "", new View.OnClickListener() {
            @Override public void onClick(View v) { showBackup(); }
        });
        addRow("关于", "", new View.OnClickListener() {
            @Override public void onClick(View v) { showAbout(); }
        });
    }

    // ===== 列表构建工具 =====
    private void group(String title) {
        listBox.addView(sectionTitle(title));
        currentCard = card();
        listBox.addView(currentCard, cardLp());
    }

    private void addRow(String name, String value, View.OnClickListener click) {
        if (currentCard == null) return;
        if (currentCard.getChildCount() > 0) addDivider(currentCard);
        currentCard.addView(settingRow(name, value, click));
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        return lp;
    }

    /** 整型枚举项：与 TV 的 SelectDialog 一致，弹窗单选后写入同一个 HawkConfig 键。 */
    private void addChoice(final String label, final String key, final int def,
                           final String[] names, final int[] values, final Runnable after) {
        int cur = intVal(key, def);
        int pos = posOf(cur, values);
        final String shown = (names.length == 0) ? "暂无可选" :
                names[pos >= 0 ? pos : 0];
        addRow(label, shown + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (names.length == 0) { toast("暂无可选项"); return; }
                int p = pos < 0 ? 0 : pos;
                new AlertDialog.Builder(MobileSettingActivity.this)
                        .setTitle(label)
                        .setSingleChoiceItems(names, p, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                if (which < 0 || which >= values.length) return;
                                Hawk.put(key, values[which]);
                                if (after != null) {
                                    try { after.run(); } catch (Throwable ignored) {}
                                }
                                toast(label + "：" + names[which]);
                                dialog.dismiss();
                                recreate();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
    }

    /** 布尔开关项：与 TV 的点按取反完全一致。 */
    private void addBool(final String label, final String key, final boolean def,
                         final String onText, final String offText, final Runnable after) {
        boolean cur = boolVal(key, def);
        addRow(label, (cur ? onText : offText) + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean n = !boolVal(key, def);
                Hawk.put(key, n);
                if (after != null) {
                    try { after.run(); } catch (Throwable ignored) {}
                }
                toast(label + "：" + (n ? onText : offText));
                recreate();
            }
        });
    }

    // ===== 具体设置项实现（与 TV ModelSettingFragment 一一对应） =====
    private void openApiConfig() {
        startActivity(new Intent(this, MobileApiConfigActivity.class));
    }

    /** 配置历史：与 TV ApiHistoryDialog 一致，支持选用与单条删除。 */
    private void showApiHistory() {
        final ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        if (history == null || history.isEmpty()) { toast("暂无历史配置"); return; }
        String[] items = history.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("历史配置")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        applyApi(history.get(which));
                        dialog.dismiss();
                    }
                })
                .setNeutralButton("删除单条", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        showDeleteHistory(history);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteHistory(final ArrayList<String> history) {
        String[] items = history.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("选择要删除的历史配置")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        ArrayList<String> data = new ArrayList<String>(history);
                        data.remove(which);
                        Hawk.put(HawkConfig.API_HISTORY, data);
                        toast("已删除");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showApiLines() {
        final ArrayList<String> lines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
        if (lines == null || lines.isEmpty()) { toast("线路列表为空"); return; }
        final String current = str(HawkConfig.API_URL);
        String[] names = new String[lines.size()];
        int idx = 0;
        for (int i = 0; i < lines.size(); i++) {
            names[i] = HistoryHelper.getApiLineName(lines.get(i));
            if (current.equals(HistoryHelper.getApiLineUrl(lines.get(i)))) idx = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("线路选择")
                .setSingleChoiceItems(names, idx, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String newApi = HistoryHelper.getApiLineUrl(lines.get(which));
                        dialog.dismiss();
                        if (TextUtils.isEmpty(newApi)) return;
                        // 线路切换只改当前生效地址，保留用户原始订阅地址回显
                        Hawk.put(HawkConfig.API_URL, newApi);
                        Hawk.put(HawkConfig.LIVE_API_URL, newApi);
                        HistoryHelper.setLiveApiHistory(newApi);
                        MobileDataInitializer.markConfigDirty();
                        toast("线路已切换，正在重新加载");
                        backToHome();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyApi(String value) {
        Hawk.put(HawkConfig.API_URL, value);
        Hawk.put(MobileApiConfigActivity.KEY_API_INPUT, value);
        if (!HistoryHelper.isApiLineHistory(value)) HistoryHelper.clearApiLineList();
        MobileDataInitializer.markConfigDirty();
        toast("配置已切换，正在重新加载");
        backToHome();
    }

    private void backToHome() {
        try {
            Intent i = new Intent(this, MobileHomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        } catch (Throwable ignored) {}
    }

    /** 首页站源：与 TV llHomeApi 一致，ApiConfig.setSourceBean + 事件通知。 */
    private void showHomeSources() {
        final List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        if (sites == null || sites.isEmpty()) {
            toast("暂无可用站点，请先配置订阅地址");
            return;
        }
        String[] names = new String[sites.size()];
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        int idx = 0;
        for (int i = 0; i < sites.size(); i++) {
            names[i] = sites.get(i).getName();
            if (home != null && sites.get(i).getKey().equals(home.getKey())) idx = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("请选择首页数据源")
                .setSingleChoiceItems(names, idx, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        try {
                            ApiConfig.get().setSourceBean(sites.get(which));
                            EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_HOME_SOURCE_CHANGE));
                        } catch (Throwable ignored) {}
                        MobileDataInitializer.markConfigDirty();
                        toast("首页站源：" + sites.get(which).getName());
                        dialog.dismiss();
                        backToHome();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 播放器：与 TV llPlay 一致，PlayerHelper.getExistPlayerTypes。 */
    private void addPlayerChoice() {
        ArrayList<Integer> types;
        try { types = PlayerHelper.getExistPlayerTypes(); }
        catch (Throwable t) { types = new ArrayList<Integer>(); }
        if (types == null || types.isEmpty()) {
            addRow("播放器", "暂无可选 ›", new View.OnClickListener() {
                @Override public void onClick(View v) { toast("暂无可用播放器"); }
            });
            return;
        }
        String[] names = new String[types.size()];
        int[] values = new int[types.size()];
        for (int i = 0; i < types.size(); i++) {
            values[i] = types.get(i);
            names[i] = PlayerHelper.getPlayerName(types.get(i));
        }
        addChoice("播放器", HawkConfig.PLAY_TYPE, 2, names, values, new Runnable() {
            @Override public void run() { PlayerHelper.init(); }
        });
    }

    /** IJK解码方式：与 TV llMediaCodec 一致，来源 ApiConfig.getIjkCodes()。 */
    private void addIjkCodec() {
        List<IJKCode> codesTmp;
        try { codesTmp = ApiConfig.get().getIjkCodes(); }
        catch (Throwable t) { codesTmp = null; }
        final List<IJKCode> codes = codesTmp;
        String cur = str(HawkConfig.IJK_CODEC);
        if (TextUtils.isEmpty(cur)) cur = "硬解码";
        addRow("IJK解码方式", cur + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (codes == null || codes.isEmpty()) { toast("暂无可选解码方式"); return; }
                String[] names = new String[codes.size()];
                String selected = str(HawkConfig.IJK_CODEC);
                if (TextUtils.isEmpty(selected)) selected = "硬解码";
                int sel = 0;
                for (int i = 0; i < codes.size(); i++) {
                    names[i] = codes.get(i).getName();
                    if (names[i].equals(selected)) sel = i;
                }
                new AlertDialog.Builder(MobileSettingActivity.this)
                        .setTitle("请选择IJK解码")
                        .setSingleChoiceItems(names, sel, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                try { codes.get(which).selected(true); }
                                catch (Throwable ignored) {}
                                toast("IJK解码：" + names[which]);
                                dialog.dismiss();
                                recreate();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
    }

    /** 弹幕：与 TV danmuOpen 一致，DanmuHelper + 通知播放器刷新。 */
    private void addDanmuRow() {
        boolean cur = DanmuHelper.isOpen();
        addRow("弹幕", (cur ? "开启" : "关闭") + " ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean open = !DanmuHelper.isOpen();
                DanmuHelper.setOpen(open);
                try {
                    EventBus.getDefault().post(new RefreshEvent(
                            RefreshEvent.TYPE_SET_DANMU_SETTINGS, open));
                } catch (Throwable ignored) {}
                toast("弹幕：" + (open ? "开启" : "关闭"));
                recreate();
            }
        });
    }

    private void showDanmuApi() {
        try {
            DanmuApiDialog dialog = new DanmuApiDialog(this);
            dialog.setOnListener(new DanmuApiDialog.OnListener() {
                @Override public void onChange(String api) { recreate(); }
            });
            dialog.show();
        } catch (Throwable t) {
            toast("弹幕地址设置不可用");
        }
    }

    /** 安全DNS：与 TV llDns 一致，OkGoHelper.dnsHttpsList + reloadDns + toggleDotPort。 */
    private void showDns() {
        try { OkGoHelper.setDnsList(); } catch (Throwable ignored) {}
        final ArrayList<String> names = OkGoHelper.dnsHttpsList;
        if (names == null || names.isEmpty()) { toast("DNS 列表为空"); return; }
        int cur = intVal(HawkConfig.DOH_URL, 0);
        if (cur < 0 || cur >= names.size()) cur = 0;
        new AlertDialog.Builder(this)
                .setTitle("请选择安全DNS")
                .setSingleChoiceItems(names.toArray(new String[0]), cur,
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                Hawk.put(HawkConfig.DOH_URL, which);
                                try {
                                    OkGoHelper.reloadDns();
                                    IjkMediaPlayer.toggleDotPort(which > 0);
                                } catch (Throwable ignored) {}
                                toast("安全DNS：" + names.get(which));
                                dialog.dismiss();
                                recreate();
                            }
                        })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 换张壁纸：与 TV llWp 一致，只使用配置里提供的壁纸地址。 */
    private void changeWallpaper() {
        String wp = "";
        try { wp = ApiConfig.get().wallpaper; } catch (Throwable ignored) {}
        if (TextUtils.isEmpty(wp)) { toast("当前配置未提供壁纸地址"); return; }
        toast("正在下载壁纸…");
        OkGo.<File>get(wp).execute(new FileCallback(getFilesDir().getAbsolutePath(), "wp") {
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
                toast("壁纸下载失败");
            }
        });
    }

    /** 重置壁纸：与 TV llWpRecovery 一致。 */
    private void restoreWallpaper() {
        try {
            File f = new File(getFilesDir(), "wp");
            if (f.exists()) f.delete();
            Hawk.put("mobile_wallpaper_path", "");
            toast("已恢复默认壁纸");
            recreate();
        } catch (Throwable t) {
            toast("恢复失败");
        }
    }

    /** 清空缓存：与 TV onClickClearCache 完全一致。 */
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
                    @Override public void run() {
                        toast("缓存已清空，即将重启");
                        new Handler().postDelayed(new Runnable() {
                            @Override public void run() { restartApp(); }
                        }, 1500);
                    }
                });
            }
        }).start();
    }

    private void restartApp() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                System.exit(0);
            }
        } catch (Throwable ignored) {}
    }

    /** 数据备份：直接复用 TV 的 BackupDialog。 */
    private void showBackup() {
        try { new BackupDialog(this).show(); }
        catch (Throwable t) { toast("数据备份不可用"); }
    }

    /** 关于：直接复用 TV 的 AboutDialog。 */
    private void showAbout() {
        try { new AboutDialog(this).show(); }
        catch (Throwable t) { toast("关于信息不可用"); }
    }

    // ===== 显示值 =====
    /** 优先回显用户原始输入的订阅地址，避免被 TV 的多线路逻辑改写后显示成配置内某个站点。 */
    private String apiInputForDisplay() {
        String v = str(MobileApiConfigActivity.KEY_API_INPUT);
        return TextUtils.isEmpty(v) ? str(HawkConfig.API_URL) : v;
    }

    /** 显示订阅地址本身（不是当前站点名），与 TV tvApi.setText(API_URL) 一致。 */
    private String shortUrl(String url) {
        if (TextUtils.isEmpty(url)) return "未配置";
        return url.length() > 26 ? url.substring(0, 26) + "…" : url;
    }

    private String homeSourceName() {
        try {
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            return (home == null || TextUtils.isEmpty(home.getName())) ? "未设置" : home.getName();
        } catch (Throwable t) { return "未设置"; }
    }

    private String currentLineName() {
        try {
            ArrayList<String> lines = Hawk.get(HawkConfig.API_LINE_LIST, new ArrayList<String>());
            String current = str(HawkConfig.API_URL);
            if (lines == null || lines.isEmpty() || !HistoryHelper.isApiLineUrl(current)) return "默认";
            for (String line : lines) {
                if (current.equals(HistoryHelper.getApiLineUrl(line))) {
                    return HistoryHelper.getApiLineName(line);
                }
            }
        } catch (Throwable ignored) {}
        return "默认";
    }

    private String dnsName() {
        try {
            if (OkGoHelper.dnsHttpsList.isEmpty()) OkGoHelper.setDnsList();
            int i = intVal(HawkConfig.DOH_URL, 0);
            if (i < 0 || i >= OkGoHelper.dnsHttpsList.size()) i = 0;
            return OkGoHelper.dnsHttpsList.isEmpty() ? "关闭" : OkGoHelper.dnsHttpsList.get(i);
        } catch (Throwable t) { return "关闭"; }
    }

    private String danmuApiName() {
        try {
            if (DanmakuApi.isUseDefault()) return "默认";
            String custom = Hawk.get(HawkConfig.DANMU_API, "");
            if (!TextUtils.isEmpty(custom)) return "自定义";
            String config = ApiConfig.get().getDanmaku();
            return TextUtils.isEmpty(config) ? "默认" : "接口";
        } catch (Throwable t) { return "默认"; }
    }

    private int posOf(int value, int[] values) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return -1;
    }

    // ===== 安全读取（防 Hawk 类型不匹配闪退）=====
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
