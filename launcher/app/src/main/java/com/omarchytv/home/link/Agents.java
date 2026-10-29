package com.omarchytv.home.link;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Live AI agent activity from the PC (PROTOCOL.md §6 Agents): the same list arrives as the agent_activity event
 * and as PUT /v1/agents. Everything is re-validated here and reduced to plain, bounded text before the
 * launcher stores or shows it; ids are opaque strings from the PC (no agent is hard-coded).
 */
public final class Agents {
    public static final int MAX_AGENTS = 8, MAX_STEPS = 8, ASK_MAX = 2000;
    static final Set<String> STATUS = new HashSet<>(Arrays.asList("thinking", "running", "waiting", "idle", "done", "error"));
    static final Set<String> STEP_STATUS = new HashSet<>(Arrays.asList("running", "ok", "error"));

    public static final class Step {
        public String at, text, status;
    }

    public static final class Agent {
        public String id, agent, name, project, title, status, step, question, since;
        public double elapsed;
        public boolean needsInput, canAsk;
        public final List<Step> steps = new ArrayList<>();

        /** "Ask" is offered only for an idle or finished session that the PC says can take input. */
        public boolean askable() {
            return canAsk && ("done".equals(status) || "idle".equals(status));
        }
    }

    public boolean enabled;
    public String at;
    /** TV time (ms) the list arrived; 0 = unknown. */
    public long rx;
    public final List<Agent> list = new ArrayList<>();

    private static String s(JSONObject o, String k, int max) {
        Object v = o.opt(k);
        return v instanceof String ? LinkServer.printable((String) v, max) : "";
    }

    /** Parses and bounds a PC list. Unknown statuses become "idle"; more than 8 agents / 8 steps are cut. */
    public static Agents parse(JSONObject in) {
        Agents a = new Agents();
        if (in == null) return a;
        a.enabled = in.optBoolean("enabled", true);
        a.at = s(in, "at", 40);
        a.rx = Math.max(0, in.optLong("rx", 0));
        JSONArray arr = in.optJSONArray("agents");
        for (int i = 0; arr != null && i < arr.length() && a.list.size() < MAX_AGENTS; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Agent g = new Agent();
            g.id = s(o, "id", 128);
            if (g.id.isEmpty()) continue;
            g.agent = s(o, "agent", 32);
            g.name = s(o, "agentName", 40);
            if (g.name.isEmpty()) g.name = g.agent.isEmpty() ? "agent" : g.agent;
            g.project = s(o, "project", 40);
            g.title = s(o, "title", 80);
            g.status = s(o, "status", 16);
            if (!STATUS.contains(g.status)) g.status = "idle";
            g.step = s(o, "step", 72);
            g.question = s(o, "question", 160);
            g.since = s(o, "since", 40);
            double e = o.optDouble("elapsed", 0);
            g.elapsed = Double.isNaN(e) || e < 0 || e > 1e8 ? 0 : e;
            g.needsInput = o.optBoolean("needsInput", false) || "waiting".equals(g.status);
            g.canAsk = o.optBoolean("canAsk", false);
            JSONArray st = o.optJSONArray("steps");
            for (int k = 0; st != null && k < st.length(); k++) {
                JSONObject so = st.optJSONObject(k);
                if (so == null) continue;
                Step x = new Step();
                x.at = s(so, "at", 40);
                x.text = s(so, "text", 72);
                x.status = s(so, "status", 16);
                if (!STEP_STATUS.contains(x.status)) x.status = "ok";
                if (!x.text.isEmpty()) g.steps.add(x);
            }
            while (g.steps.size() > MAX_STEPS) g.steps.remove(0);   // keep the last 8
            a.list.add(g);
        }
        return a;
    }

    /** The bounded list as JSON (what the TV stores, serves on GET /v1/agents and hands to its UI). */
    public JSONObject toJson() {
        try {
            JSONArray arr = new JSONArray();
            for (Agent g : list) {
                JSONArray st = new JSONArray();
                for (Step x : g.steps) st.put(new JSONObject().put("at", x.at).put("text", x.text).put("status", x.status));
                arr.put(new JSONObject().put("id", g.id).put("agent", g.agent).put("agentName", g.name).put("project", g.project)
                        .put("title", g.title.isEmpty() ? JSONObject.NULL : g.title).put("status", g.status).put("step", g.step)
                        .put("question", g.question.isEmpty() ? JSONObject.NULL : g.question).put("since", g.since)
                        .put("elapsed", g.elapsed).put("needsInput", g.needsInput).put("canAsk", g.canAsk).put("steps", st));
            }
            return new JSONObject().put("at", at).put("enabled", enabled).put("agents", arr);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /** Seconds this agent has been in its current status, now: the PC's figure plus the time since it arrived. */
    public long elapsedNow(Agent g, long nowMs) {
        long e = (long) g.elapsed;
        boolean live = !"done".equals(g.status) && !"idle".equals(g.status) && !"error".equals(g.status);
        if (live && rx > 0 && nowMs > rx) e += (nowMs - rx) / 1000;
        return e;
    }

    /** "42s", "12m", "3h", "2d". */
    public static String span(long sec) {
        if (sec < 60) return sec + "s";
        if (sec < 3600) return (sec / 60) + "m";
        if (sec < 48 * 3600) return (sec / 3600) + "h";
        return (sec / 86400) + "d";
    }

    /** Some agent's timer is running (the UI ticks only then). */
    public boolean anyLive() {
        for (Agent g : list) if (!"done".equals(g.status) && !"idle".equals(g.status) && !"error".equals(g.status)) return true;
        return false;
    }

    /** Milliseconds since epoch of an ISO-8601 UTC time ("2026-10-19T08:11:58Z", fractions allowed), or -1. */
    public static long isoMs(String iso) {
        if (iso == null || iso.length() < 19) return -1;
        try {
            String t = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) : iso;
            int dot = t.indexOf('.');
            String frac = dot > 0 ? t.substring(dot + 1) : "";
            if (dot > 0) t = t.substring(0, dot);
            long ms;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                ms = java.time.LocalDateTime.parse(t).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            } else {   // java.time is missing on Android 7.x (minSdk 24, issue #56)
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
                f.setLenient(false);
                f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date d;
                try { d = f.parse(t); } catch (java.text.ParseException pe) { return -1; }
                if (d == null) return -1;
                ms = d.getTime();
            }
            if (!frac.isEmpty()) ms += Long.parseLong((frac + "000").substring(0, 3));
            return ms;
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
