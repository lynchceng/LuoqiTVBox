package com.github.tvbox.osc.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.ui.base.MobileBaseActivity;
import com.github.tvbox.osc.util.HawkConfig;

import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import xyz.doikki.videoplayer.player.VideoView;

/**
 * 手机竖屏直播页 —— 复用 TV 的 loadLiveConfig -> getChannelGroupList -> LiveChannelItem.getUrl。
 * 竖屏布局：16:9 视频 + 频道分组下拉 + 频道列表 + 换台/换源按钮。
 */
public class MobileLiveActivity extends MobileBaseActivity {

    private VideoView videoView;
    private TextView tvStatus, tvCurrent;
    private Spinner groupSpinner;
    private LinearLayout channelList;

    private List<LiveChannelGroup> groups = new ArrayList<>();
    private int groupIndex = 0;
    private int channelIndex = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
        loadLive();
    }

    private void buildUI() {
        LinearLayout root = root();
        root.addView(titleBar("直播"));

        // 视频区
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(Color.parseColor("#000000"));
        videoView = new VideoView(this);
        videoView.setVideoController(new com.github.tvbox.osc.player.controller.MobileGestureController(this));
        box.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));
        root.addView(box);

        tvCurrent = text("未选择频道", 15, C_TEXT, true);
        tvCurrent.setPadding(pad, dp(10), pad, dp(4));
        root.addView(tvCurrent);

        // 换台/换源按钮
        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setPadding(pad, dp(4), pad, dp(6));
        TextView prev = smallBtn("◀ 上一个");
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchChannel(-1); }
        });
        TextView next = smallBtn("下一个 ▶");
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchChannel(1); }
        });
        TextView source = smallBtn("换源");
        source.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { switchSource(); }
        });
        ctrl.addView(prev, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ctrl.addView(next, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ctrl.addView(source, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(ctrl);

        // 分组选择
        groupSpinner = new Spinner(this);
        groupSpinner.setBackgroundColor(Color.parseColor(C_CARD));
        root.addView(groupSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvStatus = text("正在加载直播源…", 12, C_TEXT_2, false);
        tvStatus.setPadding(pad, dp(4), pad, dp(4));
        root.addView(tvStatus);

        // 频道列表
        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        channelList.setPadding(pad, 0, pad, dp(10));
        root.addView(channelList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(bottomNav(1));
        setContentView(root);
    }

    private TextView smallBtn(String label) {
        TextView t = text(label, 13, Color.parseColor("#FFFFFF"), false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(9), dp(6), dp(9));
        t.setBackground(roundBg(C_CARD_2, dp(6)));
        return t;
    }

    private void loadLive() {
        ApiConfig.get().loadLiveConfig(true, new ApiConfig.LoadConfigCallback() {
            @Override public void success() { runOnUiThread(new Runnable() {
                @Override public void run() { applyGroups(); }
            }); }
            @Override public void error(String msg) { runOnUiThread(new Runnable() {
                @Override public void run() { tvStatus.setText("直播加载失败：" + msg); }
            }); }
            @Override public void notice(String msg) { runOnUiThread(new Runnable() {
                @Override public void run() { tvStatus.setText("提示：" + msg); }
            }); }
        });
    }

    private void applyGroups() {
        List<LiveChannelGroup> gs = ApiConfig.get().getChannelGroupList();
        groups = gs == null ? new ArrayList<LiveChannelGroup>() : gs;
        if (groups.isEmpty()) {
            tvStatus.setText("暂无频道，请在设置中配置直播地址");
            return;
        }
        List<String> names = new ArrayList<>();
        for (LiveChannelGroup g : groups) names.add(g.getGroupName());
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groupSpinner.setAdapter(adapter);
        int saved = intVal(HawkConfig.LIVE_GROUP_INDEX, 0);
        if (saved >= groups.size()) saved = 0;
        groupSpinner.setSelection(saved);
        groupIndex = saved;
        groupSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                groupIndex = pos;
                channelIndex = 0;
                Hawk.put(HawkConfig.LIVE_GROUP_INDEX, pos);
                buildChannels();
                playCurrent();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        buildChannels();
        tvStatus.setText(groups.size() + " 个分组 · " + groups.get(0).getLiveChannels().size() + " 个频道");
        playCurrent();
    }

    private void buildChannels() {
        channelList.removeAllViews();
        if (groupIndex < 0 || groupIndex >= groups.size()) return;
        List<LiveChannelItem> items = groups.get(groupIndex).getLiveChannels();
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            LiveChannelItem item = items.get(i);
            TextView t = new TextView(this);
            t.setText(item.getChannelNum() + ". " + item.getChannelName());
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            t.setTextColor(Color.parseColor(i == channelIndex ? C_ACCENT : C_TEXT));
            t.setPadding(dp(10), dp(10), dp(10), dp(10));
            t.setBackground(roundBg(i == channelIndex ? C_CARD_2 : C_CARD, dp(6)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            channelList.addView(t, lp);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    channelIndex = idx;
                    buildChannels();
                    playCurrent();
                }
            });
        }
    }

    private void switchChannel(int dir) {
        if (groupIndex < 0 || groupIndex >= groups.size()) return;
        List<LiveChannelItem> items = groups.get(groupIndex).getLiveChannels();
        if (items.isEmpty()) return;
        channelIndex = (channelIndex + dir + items.size()) % items.size();
        buildChannels();
        playCurrent();
    }

    private void switchSource() {
        if (groupIndex < 0 || groupIndex >= groups.size()) return;
        List<LiveChannelItem> items = groups.get(groupIndex).getLiveChannels();
        if (channelIndex < 0 || channelIndex >= items.size()) return;
        items.get(channelIndex).nextSource();
        playCurrent();
    }

    private void playCurrent() {
        if (groupIndex < 0 || groupIndex >= groups.size()) return;
        List<LiveChannelItem> items = groups.get(groupIndex).getLiveChannels();
        if (channelIndex < 0 || channelIndex >= items.size()) return;
        LiveChannelItem item = items.get(channelIndex);
        String url = item.getUrl();
        if (TextUtils.isEmpty(url)) {
            tvStatus.setText("该频道暂无播放地址");
            return;
        }
        tvCurrent.setText(item.getChannelName());
        Hawk.put(HawkConfig.LIVE_CHANNEL, item.getChannelName());
        videoView.release();
        applyLivePlayer();
        Map<String, String> headers = item.getHeaders();
        if (headers == null || headers.isEmpty()) videoView.setUrl(url);
        else videoView.setUrl(url, headers);
        videoView.start();
    }

    private void applyLivePlayer() {
        try {
            int type = Hawk.get(HawkConfig.LIVE_PLAY_TYPE, 2);
            if (type == 1) videoView.setPlayerFactory(
                    xyz.doikki.videoplayer.ijk.IjkPlayerFactory.create());
            else videoView.setPlayerFactory(
                    xyz.doikki.videoplayer.exo.ExoMediaPlayerFactory.create());
        } catch (Throwable t) {}
    }

    private int intVal(String key, int def) {
        try { return Hawk.get(key, def); } catch (Throwable t) { return def; }
    }

    @Override protected void onPause() {
        super.onPause();
        if (videoView != null) videoView.pause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (videoView != null) videoView.resume();
        // 配置地址变化后重新拉取直播订阅，避免继续用旧配置
        if (MobileDataInitializer.consumeConfigDirty()) {
            groups.clear();
            loadLive();
        }
    }
    @Override protected void onDestroy() {
        if (videoView != null) videoView.release();
        super.onDestroy();
    }
}
