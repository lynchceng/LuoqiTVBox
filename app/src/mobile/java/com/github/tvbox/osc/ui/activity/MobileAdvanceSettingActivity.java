package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.os.Bundle;

import com.github.tvbox.osc.ui.base.MobileBaseActivity;

/**
 * 兼容旧入口。
 *
 * TV 端设置本来就只有一页（SettingActivity -> ModelSettingFragment / fragment_model.xml），
 * 并不存在「高级设置」子页。为了让手机端设置项与 TV 端完全一致（不多也不少），
 * 全部设置项已统一放在 MobileSettingActivity。这里只做转发，避免出现第二套多余的选项。
 */
public class MobileAdvanceSettingActivity extends MobileBaseActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent i = new Intent(this, MobileSettingActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Throwable ignored) {}
        finish();
    }
}
