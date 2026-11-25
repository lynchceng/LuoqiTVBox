package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.tvbox.osc.ui.base.MobileBaseActivity;

/** 手机版关于页：与 TV 关于弹窗内容一致，含官方频道与开源地址入口。 */
public class MobileAboutActivity extends MobileBaseActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = root();
        root.addView(titleBar("关于洛奇TV"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(10), pad, dp(24));
        scroll.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = text("⚡ 洛奇TV", 24, C_ACCENT, true);
        brand.setGravity(android.view.Gravity.CENTER);
        box.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        try {
            String ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            TextView version = text("手机竖屏版  v" + ver, 13, C_TEXT_2, false);
            version.setGravity(android.view.Gravity.CENTER);
            version.setPadding(0, dp(6), 0, dp(16));
            box.addView(version, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } catch (Throwable ignored) {}

        LinearLayout links = card();
        links.addView(settingRow("官方频道 / 使用说明", "GitHub ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                open("https://github.com/lynchceng/LuoqiTVBox");
            }
        }));
        addDivider(links);
        links.addView(settingRow("开源项目 TVBoxOSC", "GitHub ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                open("https://github.com/CatVodTVOfficial/TVBoxOSC");
            }
        }));
        box.addView(links, cardLp());

        TextView note = text("本软件仅提供网络资源聚合展示服务，所有内容均来源于互联网公开渠道。"
                + "本软件不参与任何内容的制作、上传、存储及分发行为。"
                + "仅供个人学习研究使用，请在安装后 24 小时内自行删除。\n\n"
                + "手机版与电视版为两个独立应用，配置与数据互不影响。",
                13, C_TEXT_2, false);
        note.setPadding(dp(4), dp(20), dp(4), dp(10));
        box.addView(note);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            toast("无法打开浏览器");
        }
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        return lp;
    }
}
