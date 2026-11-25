package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机竖屏播放页 —— 复用 TV 的 SourceViewModel.getPlay + playResult 解析 + 解析器。
 *
 * 关键修复（对比旧版 bug）：
 *  - 不再用全局 played 拦截；每次选集/换线都重置状态、重新 getPlay，用请求序号过滤过期结果。
 *  - looksPlayable 放宽：magnet/rtsp/ftp/本地路径/需解析的地址均可进入播放/解析流程。
 *  - parse=1 时走 TV 的解析器（ParseBean type 0-4）而非直接拒绝。
 */
public class MobilePlayActivity extends MobileBaseActivity {

    public static final String EXTRA_ID = "id";
    public static final String EXTRA_SOURCE_KEY = "sourceKey";
    public static final String EXTRA_NAME = "name";
    private static final int REQ_WEB_PARSE = 1001;
    private Map<String, String> pendingHeaders;

    private SourceViewModel viewModel;
    private VideoView videoView;
    private TextView tvName, tvTip;
    private LinearLayout flagBar, episodeBar;

    private VodInfo vodInfo;
    private String sourceKey;
    private final AtomicInteger reqSeq = new AtomicInteger(0);
    private int currentReq = 0;
    private String currentProgressKey = "";
    private boolean destroyed = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle b = getIntent().getExtras();
        sourceKey = b == null ? "" : b.getString(EXTRA_SOURCE_KEY, "");
        vodInfo = App.getInstance().getVodInfo();
        viewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        buildUI();
        viewModel.playResult.observe(this, new Observer<JSONObject>() {
            @Override public void onChanged(JSONObject jo) { onPlayResult(jo); }
        });
        if (vodInfo != null && !TextUtils.isEmpty(vodInfo.playFlag)) {
            tvName.setText(TextUtils.isEmpty(vodInfo.name) ? "播放" : vodInfo.name);
            buildFlags();
            buildEpisodes();
            playCurrent();
        } else {
            tvTip.setText("播放信息已失效，请返回详情页重新进入");
        }
    }

    private void buildUI() {
        LinearLayout root = root();

        // 视频区（16:9）
        FrameLayout playerBox = new FrameLayout(this);
        playerBox.setBackgroundColor(Color.parseColor("#000000"));
        videoView = new VideoView(this);
        videoView.setVideoController(new com.github.tvbox.osc.player.controller.MobileGestureController(this));
        playerBox.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        root.addView(playerBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setPadding(pad, dp(10), pad, dp(4));
        TextView back = text("‹", 26, C_TEXT, false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        head.addView(back);
        tvName = text("播放", 16, C_TEXT, true);
        head.addView(tvName, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(head);

        tvTip = text("", 12, C_TEXT_2, false);
        tvTip.setPadding(pad, dp(2), pad, dp(6));
        root.addView(tvTip);

        // 线路
        root.addView(sectionTitle("线路"));
        flagBar = new LinearLayout(this);
        flagBar.setOrientation(LinearLayout.HORIZONTAL);
        flagBar.setPadding(pad, 0, pad, dp(4));
        root.addView(flagBar);

        // 选集
        root.addView(sectionTitle("选集"));
        episodeBar = new LinearLayout(this);
        episodeBar.setOrientation(LinearLayout.VERTICAL);
        episodeBar.setPadding(pad, 0, pad, dp(10));
        root.addView(episodeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void buildFlags() {
        flagBar.removeAllViews();
        if (vodInfo == null || vodInfo.seriesFlags == null) return;
        for (final VodInfo.VodSeriesFlag flag : vodInfo.seriesFlags) {
            boolean sel = flag.name.equals(vodInfo.playFlag);
            TextView t = new TextView(this);
            t.setText(flag.name);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            t.setTextColor(Color.parseColor(sel ? "#FFFFFF" : C_TEXT_2));
            t.setPadding(dp(12), dp(7), dp(12), dp(7));
            t.setBackground(roundBg(sel ? C_ACCENT : C_CARD, dp(6)));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    vodInfo.playFlag = flag.name;
                    vodInfo.playIndex = 0;
                    buildFlags();
                    buildEpisodes();
                    playCurrent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            flagBar.addView(t, lp);
        }
    }

    private void buildEpisodes() {
        episodeBar.removeAllViews();
        if (vodInfo == null || vodInfo.seriesMap == null) return;
        List<VodInfo.VodSeries> eps = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (eps == null) return;
        int cols = 5;
        LinearLayout row = null;
        for (int i = 0; i < eps.size(); i++) {
            final int idx = i;
            if (i % cols == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                episodeBar.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            boolean sel = idx == vodInfo.playIndex;
            TextView ep = new TextView(this);
            ep.setText(String.valueOf(i + 1));
            ep.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            ep.setGravity(Gravity.CENTER);
            ep.setTextColor(Color.parseColor(sel ? "#FFFFFF" : C_TEXT));
            ep.setPadding(0, dp(9), 0, dp(9));
            ep.setBackground(roundBg(sel ? C_ACCENT : C_CARD_2, dp(5)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(6);
            lp.bottomMargin = dp(6);
            row.addView(ep, lp);
            ep.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    vodInfo.playIndex = idx;
                    buildEpisodes();
                    playCurrent();
                }
            });
        }
    }

    /** 每次播放都递增请求序号并重置状态——这是修复"播放一次后无法再播"的核心。 */
    private void playCurrent() {
        if (vodInfo == null || vodInfo.seriesMap == null) return;
        List<VodInfo.VodSeries> eps = vodInfo.seriesMap.get(vodInfo.playFlag);
        if (eps == null || eps.isEmpty()) { tvTip.setText("该线路暂无剧集"); return; }
        int idx = vodInfo.playIndex;
        if (idx < 0 || idx >= eps.size()) idx = 0;
        VodInfo.VodSeries ep = eps.get(idx);
        vodInfo.playIndex = idx;
        vodInfo.playNote = ep.name;

        currentReq = reqSeq.incrementAndGet();
        tvTip.setText("正在解析播放地址…");
        videoView.release();
        // proKey 内嵌请求序号，保证每次选集/换线的结果唯一可比，杜绝“播放一次后无法再播”
        String progressKey = "m" + currentReq + "_" + sourceKey + "_" + vodInfo.id
                + "_" + vodInfo.playFlag + "_" + idx;
        currentProgressKey = progressKey;
        videoView.setProgressKey(progressKey);
        viewModel.getPlay(sourceKey, vodInfo.playFlag, progressKey, ep.url, "");
    }

    private void onPlayResult(JSONObject jo) {
        if (destroyed || jo == null) return;
        String proKey = jo.optString("proKey", "");
        if (!TextUtils.isEmpty(proKey) && !proKey.equals(currentProgressKey)) {
            return; // 过期结果
        }
        try {
            String msg = jo.optString("msg", "");
            if (!TextUtils.isEmpty(msg)) { tvTip.setText("播放失败：" + msg); return; }

            boolean parse = "1".equals(jo.optString("parse", "1"));
            String playUrl = jo.optString("playUrl", "");
            Object raw = jo.opt("url");
            String url;
            if (raw instanceof JSONArray) {
                JSONArray arr = (JSONArray) raw;
                url = arr.length() > 0 ? String.valueOf(arr.opt(0)) : "";
            } else {
                url = raw == null ? "" : String.valueOf(raw);
            }
            Map<String, String> headers = parseHeaders(jo);

            if (parse) {
                // 需要解析：走 TV 的解析器
                tvTip.setText("正在解析视频…");
                doParse(playUrl, url, headers, vodInfo.playFlag);
            } else {
                String finalUrl = TextUtils.isEmpty(playUrl) ? url : playUrl + url;
                if (TextUtils.isEmpty(finalUrl)) { tvTip.setText("未获取到播放地址"); return; }
                startPlay(finalUrl, headers);
            }
        } catch (Throwable t) {
            tvTip.setText("播放出错：" + t.getMessage());
        }
    }

    /** 调用 TV 的解析器（ParseBean type 0-4），与 TV PlayFragment.initParse/doParse 行为一致。 */
    private void doParse(String playUrl, String url, Map<String, String> headers, String flag) {
        List<ParseBean> parses = ApiConfigSafe.parses();
        ParseBean chosen = null;
        if (!TextUtils.isEmpty(playUrl) && playUrl.startsWith("json:")) {
            // TV initParse: json:xxx 直接按名称使用某个 JSON 解析器
            String name = playUrl.substring(5);
            for (ParseBean p : parses) if (name.equals(p.getName())) { chosen = p; break; }
            if (chosen == null) {
                chosen = new ParseBean();
                chosen.setType(1);
                chosen.setUrl(name);
            }
        } else if (!TextUtils.isEmpty(playUrl) && playUrl.startsWith("parse:")) {
            String name = playUrl.substring(6);
            for (ParseBean p : parses) if (name.equals(p.getName())) { chosen = p; break; }
        }
        if (chosen == null) chosen = ApiConfigSafe.defaultParse();
        if (chosen == null) {
            tvTip.setText("未配置解析器，尝试直连");
            startPlay(url, headers);
            return;
        }

        final ParseBean pb = chosen;
        final String target = url;
        final Map<String, String> baseHeaders = headers;
        final String parseFlag = flag;
        switch (pb.getType()) {
            case 0:
                // 网页嗅探：手机端用 WebView 完成，不再提示去电视端
                HashMap<String, String> webHeaders = extHeaders(pb);
                String ua = null;
                if (webHeaders != null) {
                    ua = webHeaders.remove("user-agent");
                    if (ua != null) ua = ua.trim();
                }
                openWebParse(TextUtils.isEmpty(pb.getUrl()) ? target : pb.getUrl() + target,
                        ua, webHeaders, baseHeaders);
                break;
            case 1:
                jsonParseAsync(pb, target, baseHeaders);
                break;
            case 2:
                jsonExtAsync(pb, target, baseHeaders);
                break;
            case 3:
            case 4:
                mixParseAsync(pb, target, baseHeaders, parseFlag);
                break;
            default:
                startPlay(target, baseHeaders);
                break;
        }
    }

    /** 从解析器 ext 中读取自定义请求头（TV getHeaders 的等价实现）。 */
    private HashMap<String, String> extHeaders(ParseBean pb) {
        HashMap<String, String> map = new HashMap<>();
        if (pb == null || TextUtils.isEmpty(pb.getExt())) return map;
        try {
            JSONObject ext = new JSONObject(pb.getExt());
            java.util.Iterator<String> it = ext.keys();
            while (it.hasNext()) {
                String key = it.next();
                Object val = ext.get(key);
                if (val instanceof JSONObject) {
                    java.util.Iterator<String> it2 = ((JSONObject) val).keys();
                    while (it2.hasNext()) {
                        String k2 = it2.next();
                        if (!((JSONObject) val).get(k2).equals("")) {
                            map.put(k2, String.valueOf(((JSONObject) val).get(k2)));
                        }
                    }
                } else if (val instanceof String) {
                    map.put(key, (String) val);
                }
            }
        } catch (Throwable ignored) {}
        return map;
    }

    /** type=1 JSON 解析：请求解析接口并识别 data.url / url / video:// / parse=1。 */
    private void jsonParseAsync(final ParseBean pb, final String target, final Map<String, String> baseHeaders) {
        tvTip.setText("正在解析视频…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String api = pb.getUrl() + java.net.URLEncoder.encode(target, "UTF-8");
                    com.lzy.okgo.model.HttpHeaders reqHeaders = new com.lzy.okgo.model.HttpHeaders();
                    for (Map.Entry<String, String> e : extHeaders(pb).entrySet()) {
                        reqHeaders.put(e.getKey(), e.getValue());
                    }
                    String resp = com.lzy.okgo.OkGo.<String>get(api).headers(reqHeaders)
                            .execute().body().string();
                    JSONObject rs = parseJsonResult(resp, target);
                    handleParseResult(rs, target, baseHeaders);
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { tvTip.setText("解析失败：" + t.getMessage()); }
                    });
                }
            }
        }).start();
    }

    /** 解析 JSON 结果，结构与 TV PlayFragment.jsonParse 一致。 */
    private JSONObject parseJsonResult(String json, String original) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        if (data == null) data = root;
        String real = data.optString("url", root.optString("url", ""));
        if (real.startsWith("//")) real = "http:" + real;
        if (real.startsWith("video://")) {
            real = real.substring(8);
            JSONObject rs = new JSONObject();
            rs.put("url", real);
            rs.put("parse", 1);
            return rs;
        }
        real = com.github.tvbox.osc.util.DefaultConfig.checkReplaceProxy(real);
        if (!real.startsWith("http") && !real.startsWith("data:application")) return null;
        JSONObject rs = new JSONObject();
        rs.put("url", real);
        rs.put("parse", root.optInt("parse", data.optInt("parse", 0)));
        rs.put("ua", root.optString("ua", data.optString("ua", "")));
        JSONObject h = data.optJSONObject("header");
        if (h == null) h = data.optJSONObject("headers");
        if (h == null) h = root.optJSONObject("header");
        if (h == null) h = root.optJSONObject("headers");
        if (h != null) rs.put("header", h);
        return rs;
    }

    /** type=2 JSON 扩展解析：复用 ApiConfig.jsonExt（TV jarLoader 同一实现）。 */
    private void jsonExtAsync(final ParseBean pb, final String target, final Map<String, String> baseHeaders) {
        tvTip.setText("正在解析视频…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    LinkedHashMap<String, String> jxs = new LinkedHashMap<>();
                    for (ParseBean p : ApiConfigSafe.parses()) {
                        if (p.getType() == 1) jxs.put(p.getName(), p.mixUrl());
                    }
                    final JSONObject rs = com.github.tvbox.osc.api.ApiConfig.get()
                            .jsonExt(pb.getUrl(), jxs, target);
                    handleParseResult(rs, target, baseHeaders);
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { tvTip.setText("解析失败：" + t.getMessage()); }
                    });
                }
            }
        }).start();
    }

    /** type=3/4 聚合解析：复用 TV 的 jsonExtMix / SuperParse。 */
    private void mixParseAsync(final ParseBean pb, final String target,
                               final Map<String, String> baseHeaders, final String flag) {
        tvTip.setText("正在聚合解析…");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    LinkedHashMap<String, HashMap<String, String>> jxs = new LinkedHashMap<>();
                    String extendName = "";
                    for (ParseBean p : ApiConfigSafe.parses()) {
                        HashMap<String, String> data = new HashMap<>();
                        data.put("url", p.getUrl());
                        data.put("type", String.valueOf(p.getType()));
                        data.put("ext", p.getExt());
                        jxs.put(p.getName(), data);
                        if (p.getName().equals(pb.getName())) extendName = p.getName();
                    }
                    JSONObject rs;
                    if (pb.getType() == 4) {
                        rs = com.github.tvbox.osc.util.parser.SuperParse.parse(
                                jxs, flag + "123", target);
                    } else {
                        rs = com.github.tvbox.osc.api.ApiConfig.get().jsonExtMix(
                                flag + "111", pb.getUrl(), extendName, jxs, target);
                    }
                    handleParseResult(rs, target, baseHeaders);
                } catch (final Throwable t) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { tvTip.setText("解析失败：" + t.getMessage()); }
                    });
                }
            }
        }).start();
    }

    /** 解析结果统一处理：parse=1 走网页嗅探，否则直接播放。 */
    private void handleParseResult(final JSONObject rs, final String original,
                                   final Map<String, String> baseHeaders) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                if (destroyed) return;
                if (rs == null) {
                    tvTip.setText("解析失败，请切换线路");
                    return;
                }
                String real = rs.optString("url", "");
                if (TextUtils.isEmpty(real)) {
                    tvTip.setText("解析失败，请切换线路");
                    return;
                }
                Map<String, String> h = parseHeaders(rs);
                if (h == null || h.isEmpty()) h = baseHeaders;
                String ua = rs.optString("ua", "");
                if (rs.optInt("parse", 0) == 1) {
                    openWebParse(com.github.tvbox.osc.util.DefaultConfig.checkReplaceProxy(real),
                            ua, null, h);
                } else {
                    startPlay(real, h);
                }
            }
        });
    }

    /** 打开手机端网页嗅探页，结果通过 onActivityResult 回到播放页。 */
    private void openWebParse(String url, String ua, HashMap<String, String> reqHeaders,
                              Map<String, String> playHeaders) {
        pendingHeaders = playHeaders;
        Intent intent = new Intent(this, MobileWebParseActivity.class);
        intent.putExtra(MobileWebParseActivity.EXTRA_URL, url);
        intent.putExtra(MobileWebParseActivity.EXTRA_UA, ua);
        if (reqHeaders != null) intent.putExtra(MobileWebParseActivity.EXTRA_HEADERS, reqHeaders);
        tvTip.setText("正在嗅探播放地址…");
        startActivityForResult(intent, REQ_WEB_PARSE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_WEB_PARSE || destroyed) return;
        String url = data == null ? "" : data.getStringExtra(MobileWebParseActivity.EXTRA_RESULT_URL);
        if (TextUtils.isEmpty(url)) {
            tvTip.setVisibility(View.VISIBLE);
            tvTip.setText("未嗅探到播放地址，请切换线路重试");
            return;
        }
        startPlay(url, pendingHeaders);
    }

    private void startPlay(String url, Map<String, String> headers) {
        if (destroyed || TextUtils.isEmpty(url)) return;
        tvTip.setVisibility(View.GONE);
        applyPlayerConfig();
        if (headers == null || headers.isEmpty()) videoView.setUrl(url);
        else videoView.setUrl(url, headers);
        videoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override public void onPlayStateChanged(int playState) {
                if (playState == VideoView.STATE_ERROR) {
                    tvTip.setVisibility(View.VISIBLE);
                    tvTip.setText("播放出错，请切换线路重试");
                }
            }
        });
        videoView.start();
    }

    private void applyPlayerConfig() {
        try {
            int type = Hawk.get(HawkConfig.PLAY_TYPE, 2);
            if (type == 1) videoView.setPlayerFactory(
                    xyz.doikki.videoplayer.ijk.IjkPlayerFactory.create());
            else videoView.setPlayerFactory(
                    xyz.doikki.videoplayer.exo.ExoMediaPlayerFactory.create());
        } catch (Throwable t) {}
    }

    private Map<String, String> parseHeaders(JSONObject jo) {
        Object h = jo.opt("header");
        if (h == null) h = jo.opt("headers");
        Map<String, String> map = new HashMap<>();
        if (h instanceof JSONObject) {
            JSONObject obj = (JSONObject) h;
            java.util.Iterator<String> it = obj.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, String.valueOf(obj.opt(k)));
            }
        }
        return map.isEmpty() ? null : map;
    }

    @Override protected void onPause() {
        super.onPause();
        if (videoView != null) videoView.pause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (videoView != null) videoView.resume();
    }
    @Override protected void onDestroy() {
        destroyed = true;
        if (videoView != null) videoView.release();
        super.onDestroy();
    }

    /** 包装 ApiConfig 中解析器访问，避免直接引用不存在的方法。 */
    private static class ApiConfigSafe {
        static List<ParseBean> parses() {
            try { return com.github.tvbox.osc.api.ApiConfig.get().getParseBeanList(); }
            catch (Throwable t) { return new ArrayList<ParseBean>(); }
        }
        static ParseBean defaultParse() {
            try { return com.github.tvbox.osc.api.ApiConfig.get().getDefaultParse(); }
            catch (Throwable t) { return null; }
        }
    }
}
