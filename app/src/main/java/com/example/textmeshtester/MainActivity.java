package com.example.textmeshtester;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ScaleXSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.AlignmentSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private EditText nameInput, chatInput;
    private TextView namePreview, chatPreview;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        nameInput = findViewById(R.id.nameInput);
        chatInput = findViewById(R.id.chatInput);
        namePreview = findViewById(R.id.namePreview);
        chatPreview = findViewById(R.id.chatPreview);

        TextWatcher w = new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ refresh(); }
            public void afterTextChanged(Editable e){}
        };
        nameInput.addTextChangedListener(w);
        chatInput.addTextChangedListener(w);
        refresh();
    }

    private void refresh() {
        render(nameInput.getText().toString(), namePreview, 24f);
        render(chatInput.getText().toString(), chatPreview, 18f);
    }

    private static class OpenTag {
        String tag;
        int start;
        String arg;
        OpenTag(String t, int s, String a) { tag=t; start=s; arg=a; }
    }

    private static final Pattern UPPERCASE_TAG_PATTERN =
            Pattern.compile("(?is)<(uppercase|allcaps)>(.*?)</\\1>");
    private static final Pattern LOWERCASE_TAG_PATTERN =
            Pattern.compile("(?is)<lowercase>(.*?)</lowercase>");
    private static final Pattern SMALLCAPS_TAG_PATTERN =
            Pattern.compile("(?is)<smallcaps>(.*?)</smallcaps>");

    private static String replaceCaseTags(String src, Pattern pattern, int group, boolean upper) {
        Matcher matcher = pattern.matcher(src);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(group);
            String replacement = upper
                    ? value.toUpperCase(Locale.ROOT)
                    : value.toLowerCase(Locale.ROOT);
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private void render(String src, TextView view, float baseSp) {
        // Case transforms first, matching the requested testing workflow.
        // String.replaceAll() does not accept a lambda replacement; use Matcher
        // with appendReplacement so the project also remains compatible with
        // older Android runtimes.
        src = replaceCaseTags(src, UPPERCASE_TAG_PATTERN, 2, true);
        src = replaceCaseTags(src, LOWERCASE_TAG_PATTERN, 1, false);
        src = replaceCaseTags(src, SMALLCAPS_TAG_PATTERN, 1, true);

        SpannableStringBuilder out = new SpannableStringBuilder();
        ArrayList<OpenTag> stack = new ArrayList<>();

        Pattern token = Pattern.compile("(?is)<(/?)([a-z]+)(?:\\s*=\\s*([^>]*?))?>");
        Matcher m = token.matcher(src);
        int last = 0;
        while (m.find()) {
            out.append(src, last, m.start());
            String closing = m.group(1);
            String tag = m.group(2).toLowerCase(Locale.ROOT);
            String arg = m.group(3) == null ? "" : m.group(3).trim();

            if (closing.isEmpty()) {
                if (isSupported(tag)) {
                    stack.add(new OpenTag(tag, out.length(), arg));
                } else {
                    out.append(m.group());
                }
            } else {
                int idx = -1;
                for (int i = stack.size() - 1; i >= 0; i--) {
                    if (stack.get(i).tag.equals(tag)) { idx = i; break; }
                }
                if (idx >= 0) {
                    OpenTag ot = stack.remove(idx);
                    applySpan(out, ot, out.length(), baseSp);
                } else {
                    out.append(m.group());
                }
            }
            last = m.end();
        }
        out.append(src, last, src.length());

        // Leave unclosed tags as plain text would be confusing; this app instead applies them to end.
        while (!stack.isEmpty()) {
            OpenTag ot = stack.remove(stack.size() - 1);
            applySpan(out, ot, out.length(), baseSp);
        }

        view.setText(out);
    }

    private boolean isSupported(String t) {
        return t.equals("b") || t.equals("i") || t.equals("u") || t.equals("s") ||
               t.equals("sup") || t.equals("sub") || t.equals("color") || t.equals("#") ||
               t.equals("alpha") || t.equals("size") || t.equals("align") || t.equals("voffset") ||
               t.equals("mark") || t.equals("cspace") || t.equals("mspace") || t.equals("word-spacing") ||
               t.equals("line-height") || t.equals("line-indent") || t.equals("margin") ||
               t.equals("margin-left") || t.equals("margin-right");
    }

    private void applySpan(SpannableStringBuilder out, OpenTag ot, int end, float baseSp) {
        int s = Math.max(0, Math.min(ot.start, end));
        switch (ot.tag) {
            case "b": out.setSpan(new StyleSpan(Typeface.BOLD), s, end, 0); break;
            case "i": out.setSpan(new StyleSpan(Typeface.ITALIC), s, end, 0); break;
            case "u": out.setSpan(new UnderlineSpan(), s, end, 0); break;
            case "s": out.setSpan(new StrikethroughSpan(), s, end, 0); break;
            case "sup": out.setSpan(new SuperscriptSpan(), s, end, 0); break;
            case "sub": out.setSpan(new SubscriptSpan(), s, end, 0); break;
            case "color": {
                Integer c = parseColor(ot.arg);
                if (c != null) out.setSpan(new ForegroundColorSpan(c), s, end, 0);
                break;
            }
            case "alpha": {
                try {
                    int a = Integer.parseInt(ot.arg.replace("#",""), 16);
                    int old = Color.WHITE;
                    int c = Color.argb(a, Color.red(old), Color.green(old), Color.blue(old));
                    out.setSpan(new ForegroundColorSpan(c), s, end, 0);
                } catch (Exception ignored) {}
                break;
            }
            case "mark": {
                Integer c = parseColor(ot.arg);
                if (c != null) out.setSpan(new BackgroundColorSpan(c), s, end, 0);
                break;
            }
            case "size": {
                String a = ot.arg.toLowerCase(Locale.ROOT);
                try {
                    if (a.endsWith("%")) {
                        float pct = Float.parseFloat(a.substring(0, a.length()-1));
                        out.setSpan(new RelativeSizeSpan(pct / 100f), s, end, 0);
                    } else {
                        float px = Float.parseFloat(a.replace("px","").replace("sp",""));
                        out.setSpan(new AbsoluteSizeSpan(Math.max(1, (int)px), true), s, end, 0);
                    }
                } catch (Exception ignored) {}
                break;
            }
            case "voffset":
                // Android Spannable has no simple built-in baseline offset for arbitrary px;
                // render with superscript/subscript for the common signed use case.
                try {
                    float v = Float.parseFloat(ot.arg.replace("px",""));
                    out.setSpan(v >= 0 ? new SuperscriptSpan() : new SubscriptSpan(), s, end, 0);
                } catch (Exception ignored) {}
                break;
            case "cspace":
            case "mspace":
                try {
                    float scale = 1f + Math.max(-0.5f, Math.min(0.5f,
                            Float.parseFloat(ot.arg.replace("em","").replace("px","")) / 20f));
                    out.setSpan(new ScaleXSpan(scale), s, end, 0);
                } catch (Exception ignored) {}
                break;
        }
    }

    private Integer parseColor(String raw) {
        String x = raw.trim();
        try {
            if (x.equalsIgnoreCase("red")) return Color.RED;
            if (x.equalsIgnoreCase("green")) return Color.GREEN;
            if (x.equalsIgnoreCase("blue")) return Color.BLUE;
            if (x.equalsIgnoreCase("yellow")) return Color.YELLOW;
            if (x.equalsIgnoreCase("white")) return Color.WHITE;
            if (x.equalsIgnoreCase("black")) return Color.BLACK;
            if (!x.startsWith("#")) x = "#" + x;
            if (x.length() == 7) return Color.parseColor(x);
            if (x.length() == 9) {
                // User source uses RRGGBBAA; Android expects AARRGGBB.
                long v = Long.parseLong(x.substring(1), 16);
                int rrggbb = (int)((v >> 8) & 0xFFFFFF);
                int aa = (int)(v & 0xFF);
                return Color.argb(aa, (rrggbb >> 16) & 255, (rrggbb >> 8) & 255, rrggbb & 255);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
