package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;

/**
 * 手机竖屏版数据源初始化器 —— 严格复刻 TV HomeActivity.initData() 的两阶段链路：
 *   loadConfig（读订阅配置 + 站点列表） -> loadJar（加载爬虫 js/py/jar） -> 数据就绪
 *
 * 现代订阅源（type=3）全部依赖爬虫，缺 loadJar 会导致 getSort/getList 拿不到任何数据。
 * 首页 / 搜索 / 站点切换共用本类，回调统一切回 UI 线程。
 */
public final class MobileDataInitializer {

    public interface Callback {
        void onReady();
        void onError(String msg);
        void onNotice(String msg);
    }

    private static boolean configReady = false;
    private static boolean jarReady = false;
    /** 配置地址发生变化后置位；页面返回前台时据此强制重新加载，而不是沿用旧缓存。 */
    private static boolean configDirty = false;

    private MobileDataInitializer() {}

    public static void reset() {
        configReady = false;
        jarReady = false;
    }

    public static void markConfigDirty() {
        configDirty = true;
        reset();
    }

    public static boolean consumeConfigDirty() {
        boolean dirty = configDirty;
        configDirty = false;
        return dirty;
    }

    public static boolean isReady() {
        return configReady && jarReady;
    }

    public static void ensureReady(final boolean forceReload, final Activity activity, final Callback cb) {
        if (forceReload) reset();
        if (configReady && jarReady) {
            ui(cb, 0, null);
            return;
        }

        ApiConfig.get().loadConfig(!forceReload, new ApiConfig.LoadConfigCallback() {
            @Override public void notice(String msg) { ui(cb, 2, msg); }
            @Override public void error(String msg) {
                if (msg != null && msg.equalsIgnoreCase("-1")) {
                    // -1：TV 约定使用本地缓存/已有配置，继续
                    configReady = true;
                    jarReady = true;
                    ui(cb, 0, null);
                } else {
                    ui(cb, 1, msg);
                }
            }
            @Override public void success() {
                configReady = true;
                String spider = ApiConfig.get().getSpider();
                if (TextUtils.isEmpty(spider)) {
                    jarReady = true;
                    ui(cb, 0, null);
                    return;
                }
                ApiConfig.get().loadJar(!forceReload, spider, new ApiConfig.LoadConfigCallback() {
                    @Override public void success() {
                        jarReady = true;
                        ui(cb, 0, null);
                    }
                    @Override public void error(String msg) {
                        // 单个爬虫失败不阻断，与 TV 一致继续解析
                        jarReady = true;
                        ui(cb, 0, null);
                    }
                    @Override public void notice(String msg) { ui(cb, 2, msg); }
                });
            }
        }, activity);
    }

    /** 把回调切回 UI 线程。what: 0 ready / 1 error / 2 notice */
    private static void ui(final Callback cb, final int what, final String msg) {
        if (cb == null) return;
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            dispatch(cb, what, msg);
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() { dispatch(cb, what, msg); }
            });
        }
    }

    private static void dispatch(Callback cb, int what, String msg) {
        if (what == 0) cb.onReady();
        else if (what == 1) cb.onError(msg);
        else cb.onNotice(msg);
    }
}
