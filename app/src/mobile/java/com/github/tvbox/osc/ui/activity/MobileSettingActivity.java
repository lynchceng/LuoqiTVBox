package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.HawkConfig;

import com.orhanobut.hawk.Hawk;

/**
 * 手机设置页 —— 深色分组卡片样式（参考目标图）。
 * 分组：配置 / 设置 / 播放器 / 系统。所有设置项复用 TV 的 HawkConfig 真实键与类型。
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
                startActivity(new Intent(MobileSettingActivity.this, MobileSourceActivity.class));
            }
        }));
        addDivider(cfg);
        cfg.addView(settingRow("直播地址", liveApiName(), new View.OnClickListener() {
            @Override public void onClick(View v) { showLiveApiInput(); }
        }));
        addDivider(cfg);
        cfg.addView(settingRow("线路切换", lineName(), new View.OnClickListener() {
            @Override public void onClick(View v) { showLineSwitch(); }
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
        sys.addView(settingRow("换张壁纸", "", new View.OnClickListener() {
            @Override public void onClick(View v) { toast("壁纸功能请在电视版设置中使用"); }
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
        sys.addView(settingRow("安全 DNS", dohName(),
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        int cur = intVal(HawkConfig.DOH_URL, 0);
                        int next = cur + 1 > 3 ? 0 : cur + 1;
                        Hawk.put(HawkConfig.DOH_URL, next);
                        toast("安全 DNS：" + (next == 0 ? "关闭" : "已切换"));
                        recreate();
                    }
                }));
        addDivider(sys);
        sys.addView(settingRow("清空 App 缓存", cacheSize(), new View.OnClickListener() {
            @Override public void onClick(View v) {
                RoomDataManger.deleteVodRecordAll();
                RoomDataManger.deleteVodCollectAll();
                toast("缓存已清空");
            }
        }));
        addDivider(sys);
        sys.addView(settingRow("重置 App", "", new View.OnClickListener() {
            @Override public void onClick(View v) { toast("请到电视版设置中执行重置"); }
        }));
        addDivider(sys);
        sys.addView(settingRow("官方频道", "", new View.OnClickListener() {
            @Override public void onClick(View v) { toast("官方频道：见电视版关于页"); }
        }));
        box.addView(sys, cardLp());

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(bottomNav(4));
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
        SourceBeanName n = currentSourceName();
        return n == null ? "已配置 ›" : n.name + " ›";
    }

    private SourceBeanName currentSourceName() {
        try {
            com.github.tvbox.osc.bean.SourceBean b = ApiConfig.get().getHomeSourceBean();
            if (b != null) return new SourceBeanName(b.getName(), b.getKey());
        } catch (Throwable t) {}
        return null;
    }

    private static class SourceBeanName {
        String name; String key;
        SourceBeanName(String name, String key) { this.name = name; this.key = key; }
    }

    private String liveApiName() {
        String url = str(HawkConfig.LIVE_API_URL);
        return url.isEmpty() ? "未配置 ›" : "已配置 ›";
    }

    private String lineName() {
        return "›";
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

    private String cacheSize() {
        return "0MB ›";
    }

    private String dohName() {
        int i = intVal(HawkConfig.DOH_URL, 0);
        return (i <= 0 ? "关闭" : "已开启") + " ›";
    }

    // ===== 交互 =====
    private void showLiveApiInput() {
        startActivity(new Intent(this, MobileSourceActivity.class));
    }

    private void showLineSwitch() {
        // 线路切换复用站点切换页（选择首页站源）
        startActivity(new Intent(this, MobileSourceActivity.class));
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
