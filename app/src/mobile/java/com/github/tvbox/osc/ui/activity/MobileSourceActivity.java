package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;

import java.util.List;

/**
 * 站点切换页。复用 TV HomeActivity.showSiteSwitch() 的数据接口：
 *   ApiConfig.get().getSwitchSourceBeanList() -> setSourceBean(bean) -> HOME_API 持久化
 */
public class MobileSourceActivity extends MobileBaseActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = root();
        root.addView(titleBar("选择站点"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, pad, pad, pad);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 配置入口常驻：没有站点时也能直接进入 JSON 订阅地址配置
        list.addView(settingRow("配置订阅地址", "点播 / 直播 ›", new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MobileSourceActivity.this, MobileApiConfigActivity.class));
            }
        }));
        addDivider(list);

        List<SourceBean> sites = ApiConfig.get().getSwitchSourceBeanList();
        SourceBean current = ApiConfig.get().getHomeSourceBean();
        String currentKey = current == null ? "" : current.getKey();

        if (sites == null || sites.isEmpty()) {
            TextView empty = text("暂无站点，请先在上方配置订阅地址", 14, C_TEXT_2, false);
            empty.setPadding(0, dp(40), 0, 0);
            empty.setGravity(android.view.Gravity.CENTER);
            list.addView(empty);
        } else {
            for (final SourceBean bean : sites) {
                boolean selected = bean.getKey().equals(currentKey);
                String value = selected ? "✓ 当前" : "";
                LinearLayout row = settingRow(
                        android.text.TextUtils.isEmpty(bean.getName()) ? bean.getKey() : bean.getName(),
                        value, new View.OnClickListener() {
                            @Override public void onClick(View v) {
                                ApiConfig.get().setSourceBean(bean);
                                MobileDataInitializer.markConfigDirty();
                                toast("已切换到：" + bean.getName());
                                setResult(RESULT_OK);
                                finish();
                            }
                        });
                list.addView(row);
                addDivider(list);
            }
        }
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }
}
