package com.github.tvbox.osc.player.controller;

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

import xyz.doikki.videoplayer.controller.GestureVideoController;

/**
 * 手机端触摸控制器。
 *
 * 复用 player 模块的 GestureVideoController：
 * - 单击显示/隐藏控制条
 * - 双击播放/暂停
 * - 左右滑动调节进度
 * - 左半屏上下滑动调亮度、右半屏上下滑动调音量
 *
 * 注意：必须在竖屏下启用手势（setEnableInNormal(true)），否则默认只在全屏生效。
 * 布局为手机端专属的纯 Android 控件，不依赖 TV 的 com.owen 控件。
 */
public class MobileGestureController extends GestureVideoController {

    public MobileGestureController(@NonNull Context context) {
        super(context);
        setCanChangePosition(true);
        setEnableInNormal(true);
        setGestureEnabled(true);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.mobile_player_controller;
    }
}
