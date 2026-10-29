package com.omarchytv.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

/**
 * v2.13 Grok Bot Companion (TV) session: a chat panel opened from the floating companion (OK on it,
 * long-press Menu, or the home tile). Type with the on-screen or a Bluetooth/USB keyboard, or speak through
 * the system recognizer (the remote's mic on Android TV). With @tcballard's Grok Bot Desktop Companion on
 * a linked PC (omarchy-link) the question goes to it; otherwise to OmarchyTV's own Ask.
 * Experimental, tracks @tcballard's in-development branch (MIT); the code here is mine.
 */
public class CompanionChatActivity extends Activity implements OmarchyLink.Listener {
    private static final int REQ_VOICE = 41;
    private LinearLayout log;
    private ScrollView scroll;
    private EditText input;
    private TextView mic;
    private GrokbotMark mark;
    private String pendingId;
    private String botName = "Grok Bot";
    private Theme t;
    private Typeface mono;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherConfig.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        t = Theme.cur();
        try {
            mono = CastUi.font(this);
        } catch (Exception e) {
            mono = Typeface.MONOSPACE;
        }
        LauncherConfig cfg = LauncherConfig.get(this);
        if (cfg.grokbotBotName != null && !cfg.grokbotBotName.isEmpty()) botName = cfg.grokbotBotName;

        FrameLayout shade = new FrameLayout(this);
        shade.setBackgroundColor(0xB0000000);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.withAlpha(t.bg, 0.97f));
        bg.setStroke(dp(2), t.activeBorder);
        bg.setCornerRadius(dp(6));
        panel.setBackground(bg);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        mark = new GrokbotMark(this);
        boolean mirrored = cfg.grokbotBotName != null && !cfg.grokbotBotName.isEmpty();
        mark.set(mirrored ? cfg.grokbotBotShape : "orb", mirrored && cfg.grokbotBotFill != 0 ? cfg.grokbotBotFill : GrokbotMark.GROKBOT_ORANGE);
        head.addView(mark, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = text(botName + " · Grok Bot Companion (TV)", 16, t.fgBright, true);
        title.setPadding(dp(12), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(head);
        TextView via = text(routeNote(), 11, t.fgDim, false);
        via.setPadding(0, dp(6), 0, dp(10));
        panel.addView(via);

        scroll = new ScrollView(this);
        scroll.setFocusable(false);
        log = new LinearLayout(this);
        log.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(log);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Ask " + botName + "…  (OK / Enter to send)");
        input.setTextColor(t.fgBright);
        input.setHintTextColor(t.fgDim);
        input.setTypeface(mono);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, id, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (id == EditorInfo.IME_ACTION_SEND || id == EditorInfo.IME_ACTION_DONE || enter) {
                send(input.getText().toString());
                return true;
            }
            return false;
        });
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mic = button("🎤 Speak");
        mic.setOnClickListener(v -> voice());
        row.addView(mic);
        TextView sendBtn = button("Send ›");
        sendBtn.setOnClickListener(v -> send(input.getText().toString()));
        row.addView(sendBtn);
        panel.addView(row);
        TextView foot = text("Experimental, tracks @tcballard's in-development branch (MIT) · built for v0.1.0", 10, t.fgDim, false);
        foot.setPadding(0, dp(8), 0, 0);
        panel.addView(foot);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(720), dp(460), Gravity.CENTER);
        shade.addView(panel, lp);
        setContentView(shade);

        if (cfg.grokbotMessage != null && !cfg.grokbotMessage.isEmpty()) line(botName, cfg.grokbotMessage, false);
        String q = getIntent().getStringExtra("q");
        if (q != null && !q.trim().isEmpty()) send(q);
        if (getIntent().getBooleanExtra("voice", false)) voice();
        else mic.requestFocus(); // soft keyboard opens only when the user selects the text field
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        // Physical/Bluetooth keyboard: typing while focus is elsewhere goes straight into the field.
        if (input != null && !input.hasFocus() && e.getAction() == android.view.KeyEvent.ACTION_DOWN
                && e.isPrintingKey() && e.getUnicodeChar() != 0) {
            input.requestFocus();
            input.append(String.valueOf((char) e.getUnicodeChar()));
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private String route() {
        LauncherConfig cfg = LauncherConfig.get(this);
        return CompanionPlacement.route(cfg.grokbotBotName, OmarchyLink.hasPc(), true);
    }

    private String routeNote() {
        return "companion".equals(route())
                ? "Answers come from @tcballard's Grok Bot Desktop Companion on your PC (omarchy-link)."
                : "No PC companion linked: questions go to OmarchyTV's Ask.";
    }

    private void send(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isEmpty()) return;
        if (q.length() > 240) q = q.substring(0, 240);
        input.setText("");
        line("You", q, true);
        if ("companion".equals(route())) {
            pendingId = UUID.randomUUID().toString();
            line(botName, "…", false);
            OmarchyLink.grokbotAsk(pendingId, q, null);
        } else {
            line("OmarchyTV", "Opening Ask with your question…", false);
            startActivity(new Intent(this, AskActivity.class).putExtra("cmd", q));
        }
    }

    private void voice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, MainActivity.speechLanguage(this))
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask " + botName);
        try {
            startActivityForResult(i, REQ_VOICE);
        } catch (ActivityNotFoundException e) {
            line("OmarchyTV", "No speech recognizer on this TV: type instead (the keyboard's own mic may work).", false);
            input.requestFocus();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_VOICE || res != RESULT_OK || data == null) return;
        List<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (r != null && !r.isEmpty()) send(r.get(0));
    }

    @Override
    public void onLinkEvent(String type, JSONObject d) {
        if (!"grokbot_answer".equals(type) && !"grokbot_error".equals(type)) return;
        runOnUiThread(() -> {
            if (pendingId == null || !pendingId.equals(d.optString("id"))) return;
            pendingId = null;
            if (log.getChildCount() > 0) log.removeViewAt(log.getChildCount() - 1);   // the "…" line
            if ("grokbot_answer".equals(type)) {
                String text = d.optString("text", "");
                line(d.optString("bot", botName), "ipc".equals(d.optString("mode")) || text.isEmpty()
                        ? "Sent to the companion on the PC; the reply is in its desktop bubble (IPC-only mode)." : text, false);
                mark.blink(false);
                mark.postDelayed(() -> mark.blink(true), 160);
            } else {
                String code = d.optString("error", "failed");
                line("OmarchyTV", "not_connected".equals(code) ? "No PC connected over omarchy-link."
                        : "busy".equals(code) ? "One question every 3 seconds." : d.optString("message", code), false);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        OmarchyLink.addListener(this);
    }

    @Override
    protected void onPause() {
        OmarchyLink.removeListener(this);
        super.onPause();
    }

    private void line(String who, String msg, boolean me) {
        TextView v = text(who + ": " + msg, 13, me ? t.cyan : t.fgBright, false);
        v.setPadding(0, dp(3), 0, dp(3));
        log.addView(v);
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private TextView button(String s) {
        TextView v = text(s, 13, t.fg, true);
        v.setFocusable(true);
        v.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        v.setLayoutParams(lp);
        style(v, false);
        v.setOnFocusChangeListener((x, f) -> style((TextView) x, f));
        return v;
    }

    private void style(TextView v, boolean f) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(f ? t.selection : Theme.withAlpha(t.bgDarker, 0.8f));
        g.setStroke(dp(f ? 2 : 1), f ? t.activeBorder : t.inactiveBorder);
        v.setBackground(g);
        v.setTextColor(f ? t.fgBright : t.fg);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(mono, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
