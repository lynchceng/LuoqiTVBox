package com.github.tvbox.osc.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.tvbox.osc.receiver.PushReceiver;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;

/**
 * 手机推送页 —— 复用 TV PushReceiver.send()：粘贴视频地址直接推送播放。
 */
public class MobilePushActivity extends MobileBaseActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = root();
        root.addView(titleBar("推送播放"));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(20), pad, dp(20));

        TextView tip = text("粘贴视频地址，直接推送到播放器播放（与电视版推送功能一致）",
                14, C_TEXT_2, false);
        box.addView(tip);

        final EditText et = new EditText(this);
        et.setHint("http://... 视频地址");
        et.setTextColor(Color.parseColor(C_TEXT));
        et.setHintTextColor(Color.parseColor(C_TEXT_2));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.parseColor(C_CARD));
        bg.setCornerRadius(dp(6));
        et.setBackground(bg);
        et.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(12);
        box.addView(et, ep);

        TextView btn = text("播放", 16, Color.parseColor("#FFFFFF"), true);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dp(12), 0, dp(12));
        btn.setBackground(roundBg(C_ACCENT, dp(8)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(12);
        box.addView(btn, bp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String url = et.getText().toString().trim();
                if (TextUtils.isEmpty(url)) { toast("请输入地址"); return; }
                try {
                    PushReceiver.send(MobilePushActivity.this, url);
                    toast("已推送");
                } catch (Throwable t) {
                    toast("推送失败：" + t.getMessage());
                }
            }
        });

        root.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }
}
