package com.github.tvbox.osc.ui.activity;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.HistoryHelper;

import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;

/**
 * 手机竖屏版「配置地址」页面 —— 与 TV 端 ApiDialog 功能对齐：
 *  - 填写点播订阅（JSON 订阅地址，支持 http/clan/file/;pk; 加密参数，交给 TV 的 ApiConfig 解析）
 *  - 填写直播订阅（留空则与点播地址一致，与 TV 行为相同）
 *  - 从剪贴板粘贴、历史配置快速选择、保存后立即重新加载数据
 *
 * 手机包与 TV 包是不同 applicationId，Hawk 存储天然隔离，互不影响。
 */
public class MobileApiConfigActivity extends MobileBaseActivity {

    public static final String EXTRA_FOCUS = "focus";
    private EditText etApi;
    private EditText etLive;
    private EditText importTarget;
    private static final int REQ_IMPORT = 2001;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = root();
        root.addView(titleBar("配置地址"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(4), pad, dp(24));
        scroll.addView(box, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        box.addView(sectionTitle("点播订阅（JSON 配置地址）"));
        LinearLayout apiCard = card();
        String api = str(HawkConfig.API_URL);
        etApi = input("输入订阅地址，例如 http://.../config.json");
        etApi.setText(api);
        etApi.setSelection(etApi.getText().length());
        apiCard.addView(field("配置地址", etApi));
        addDivider(apiCard);
        apiCard.addView(actionRow("从剪贴板粘贴", new View.OnClickListener() {
            @Override public void onClick(View v) { pasteTo(etApi); }
        }));
        addDivider(apiCard);
        apiCard.addView(actionRow("历史配置", new View.OnClickListener() {
            @Override public void onClick(View v) {
                showHistory(HawkConfig.API_HISTORY, etApi);
            }
        }));
        addDivider(apiCard);
        apiCard.addView(actionRow("从本地文件导入", new View.OnClickListener() {
            @Override public void onClick(View v) { importFromFile(etApi); }
        }));
        box.addView(apiCard, cardLp());

        box.addView(sectionTitle("直播订阅（可选）"));
        LinearLayout liveCard = card();
        String live = str(HawkConfig.LIVE_API_URL);
        if (TextUtils.isEmpty(live)) live = api;
        etLive = input("直播订阅地址，留空则使用点播地址");
        etLive.setText(live);
        etLive.setSelection(etLive.getText().length());
        liveCard.addView(field("直播地址", etLive));
        addDivider(liveCard);
        liveCard.addView(actionRow("从剪贴板粘贴", new View.OnClickListener() {
            @Override public void onClick(View v) { pasteTo(etLive); }
        }));
        addDivider(liveCard);
        liveCard.addView(actionRow("直播历史", new View.OnClickListener() {
            @Override public void onClick(View v) {
                showHistory(HawkConfig.LIVE_API_HISTORY, etLive);
            }
        }));
        addDivider(liveCard);
        liveCard.addView(actionRow("从本地文件导入", new View.OnClickListener() {
            @Override public void onClick(View v) { importFromFile(etLive); }
        }));
        box.addView(liveCard, cardLp());

        TextView tip = text("保存后会自动重新加载站点、分类和推荐数据。\n手机端与电视端数据相互独立，不会互相影响。",
                12, C_TEXT_2, false);
        tip.setPadding(dp(4), dp(16), dp(4), dp(16));
        box.addView(tip);

        TextView save = text("保存并重新加载", 16, "#FFFFFF", true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(12), dp(14), dp(12), dp(14));
        save.setBackground(roundBg(C_ACCENT, dp(10)));
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        box.addView(save, cardLp());

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        String focus = getIntent().getStringExtra(EXTRA_FOCUS);
        if ("live".equals(focus)) etLive.requestFocus();
        else etApi.requestFocus();
    }

    private EditText input(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(android.graphics.Color.parseColor(C_TEXT_2));
        et.setTextColor(android.graphics.Color.parseColor(C_TEXT));
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        et.setBackground(roundBg(C_CARD_2, dp(8)));
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        return et;
    }

    private LinearLayout field(String label, EditText et) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.addView(text(label, 13, C_TEXT_2, false));
        row.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private LinearLayout actionRow(String label, View.OnClickListener click) {
        LinearLayout row = settingRow(label, "", click);
        return row;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        return lp;
    }

    private void pasteTo(EditText target) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData data = cm == null ? null : cm.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) {
                toast("剪贴板为空");
                return;
            }
            CharSequence text = data.getItemAt(0).getText();
            if (TextUtils.isEmpty(text)) {
                toast("剪贴板中没有文本");
                return;
            }
            target.setText(text.toString().trim());
            target.setSelection(target.getText().length());
            toast("已粘贴");
        } catch (Throwable t) {
            toast("读取剪贴板失败：" + t.getMessage());
        }
    }

    private void showHistory(final String key, final EditText target) {
        final ArrayList<String> history = Hawk.get(key, new ArrayList<String>());
        if (history == null || history.isEmpty()) {
            toast("暂无历史配置");
            return;
        }
        String[] items = history.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("历史配置")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        target.setText(history.get(which));
                        target.setSelection(target.getText().length());
                    }
                })
                .setNeutralButton("清空历史", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        Hawk.put(key, new ArrayList<String>());
                        toast("历史已清空");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 从本地 JSON 文件导入订阅地址，对应 TV 的 openLocalConfig。 */
    private void importFromFile(EditText target) {
        importTarget = target;
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "选择配置文件"), REQ_IMPORT);
        } catch (Throwable t) {
            toast("无法打开文件选择器");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            java.io.InputStream in = getContentResolver().openInputStream(data.getData());
            if (in == null) { toast("读取文件失败"); return; }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            String content = new String(out.toByteArray(), "UTF-8").trim();
            if (importTarget != null) {
                importTarget.setText(content);
                importTarget.setSelection(importTarget.getText().length());
            }
            toast("已导入本地配置");
        } catch (Throwable t) {
            toast("读取文件失败：" + t.getMessage());
        }
    }

    private void save() {
        String newApi = etApi.getText().toString().trim();
        String newLive = etLive.getText().toString().trim();
        if (TextUtils.isEmpty(newApi)) {
            toast("请填写点播订阅地址");
            etApi.requestFocus();
            return;
        }
        String oldApi = str(HawkConfig.API_URL);

        if (!newApi.isEmpty()) {
            HistoryHelper.setApiHistory(newApi);
            if (!HistoryHelper.isApiLineHistory(newApi)) HistoryHelper.clearApiLineList();
        }
        if (newLive.isEmpty()) {
            newLive = newApi; // 与 TV ApiDialog 一致：直播默认跟随点播配置
        }
        HistoryHelper.setLiveApiHistory(newLive);
        Hawk.put(HawkConfig.API_URL, newApi);
        Hawk.put(HawkConfig.LIVE_API_URL, newLive);

        if (!newApi.equals(oldApi)) {
            // 让首页/直播页在返回后强制重新初始化，避免停留在旧数据
            MobileDataInitializer.markConfigDirty();
        }
        MobileDataInitializer.reset();
        toast("配置已保存，正在重新加载");
        setResult(RESULT_OK);
        finish();
    }

    private String str(String key) {
        try { return Hawk.get(key, ""); } catch (Throwable t) { return ""; }
    }
}
