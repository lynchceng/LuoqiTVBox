package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.DefaultConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * 手机端网页嗅探解析页 —— 对应 TV PlayFragment type=0 的 loadWebView 流程。
 * 用 WebView 打开目标网页，拦截 m3u8/mp4 等视频请求，把真实播放地址回传给播放页。
 */
public class MobileWebParseActivity extends MobileBaseActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_UA = "ua";
    public static final String EXTRA_HEADERS = "headers";
    public static final String EXTRA_RESULT_URL = "resultUrl";

    private WebView webView;
    private TextView tip;
    private boolean finished = false;
    private final Handler handler = new Handler();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = root();
        root.addView(titleBar("网页解析"));

        tip = text("正在嗅探播放地址…", 13, C_TEXT_2, false);
        tip.setPadding(pad, dp(10), pad, dp(8));
        root.addView(tip);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        String ua = getIntent().getStringExtra(EXTRA_UA);
        if (!TextUtils.isEmpty(ua)) settings.setUserAgentString(ua);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isMedia(url)) { finishWith(url); return true; }
                return false;
            }
            @Override public void onLoadResource(WebView view, String url) {
                if (isMedia(url)) finishWith(url);
                super.onLoadResource(view, url);
            }
        });
        webView.setBackgroundColor(Color.parseColor(C_BG));
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        final String url = getIntent().getStringExtra(EXTRA_URL);
        if (TextUtils.isEmpty(url)) {
            tip.setText("解析地址为空");
            return;
        }
        HashMap<String, String> headers = (HashMap<String, String>) getIntent()
                .getSerializableExtra(EXTRA_HEADERS);
        webView.loadUrl(url, headers == null ? new HashMap<String, String>() : headers);
        handler.postDelayed(new Runnable() {
            @Override public void run() { finishWith(""); }
        }, 20000);
    }

    private boolean isMedia(String url) {
        if (TextUtils.isEmpty(url)) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("data:application")) return true;
        return DefaultConfig.isVideoFormat(lower.split("\\?")[0]);
    }

    private void finishWith(String url) {
        if (finished) return;
        finished = true;
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_URL, url);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
