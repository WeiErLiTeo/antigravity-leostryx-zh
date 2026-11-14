package com.agy.ru;

import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AgyEffort {
    public static String TEST_FILE = null;

    private AgyEffort() {}

    static int anthropicBudget(String effort, JSONObject meta) {
        try {
            if (meta != null && meta.optInt("thinkingBudget", 0) > 0) {
                return meta.optInt("thinkingBudget", 0);
            }
        } catch (Throwable ignored) {
        }
        if ("low".equals(effort)) {
            return 1024;
        }
        if ("medium".equals(effort)) {
            return 4096;
        }
        if ("high".equals(effort)) {
            return 8192;
        }
        if ("xhigh".equals(effort)) {
            return 16384;
        }
        if ("max".equals(effort)) {
            return 32768;
        }
        return 0;
    }

    static String effortFor(String model) {
        try {
            JSONObject meta = readMeta(model);
            if (meta == null) {
                return null;
            }
            String effort = meta.optString("effort", "");
            return effort.isEmpty() ? null : effort;
        } catch (Throwable t) {
            return null;
        }
    }

    static String geminiLevel(String effort) {
        if ("low".equals(effort)) {
            return "low";
        }
        return "high";
    }

    static String openaiEffort(String effort) {
        if (effort == null) {
            return null;
        }
        if ("low".equals(effort) || "medium".equals(effort) || "high".equals(effort)) {
            return effort;
        }
        if ("xhigh".equals(effort) || "max".equals(effort)) {
            return "high";
        }
        return null;
    }

    public static JSONObject injectAnthropic(JSONObject body, String model) {
        try {
            if (body == null || body.has("thinking")) {
                return body;
            }
            String effort = effortFor(model);
            if (effort == null) {
                return body;
            }
            JSONObject meta = readMeta(model);
            int budget = anthropicBudget(effort, meta);
            if (budget <= 0) {
                return body;
            }
            body.put("thinking", new JSONObject().put("type", "enabled").put("budget_tokens", budget));
        } catch (Throwable ignored) {
        }
        return body;
    }

    public static JSONObject injectGemini(JSONObject body, String model) {
        if (body == null) {
            return body;
        }
        try {
            JSONObject genCfg = body.optJSONObject("generationConfig");
            if (genCfg != null && genCfg.has("thinkingLevel")) {
                return body;
            }
            String effort = effortFor(model);
            if (effort == null) {
                return body;
            }
            if (genCfg == null) {
                genCfg = new JSONObject();
                body.put("generationConfig", genCfg);
            }
            genCfg.put("thinkingLevel", geminiLevel(effort));
        } catch (Throwable ignored) {
        }
        return body;
    }

    public static JSONObject injectOpenAI(JSONObject body, String model) {
        try {
            if (body == null || body.has("reasoning_effort")) {
                return body;
            }
            String mapped = openaiEffort(effortFor(model));
            if (mapped == null) {
                return body;
            }
            body.put("reasoning_effort", mapped);
        } catch (Throwable ignored) {
        }
        return body;
    }

    public static String injectOpenAIString(String raw, String model) {
        try {
            JSONObject obj = new JSONObject(raw);
            injectOpenAI(obj, model);
            return obj.toString();
        } catch (Throwable t) {
            return raw;
        }
    }

    static JSONObject readMeta(String model) {
        try {
            String raw;
            if (TEST_FILE != null) {
                File tf = new File(TEST_FILE);
                if (tf.exists()) {
                    RandomAccessFile raf = new RandomAccessFile(tf, "r");
                    byte[] b = new byte[(int) raf.length()];
                    raf.readFully(b);
                    raf.close();
                    raw = new String(b, StandardCharsets.UTF_8);
                } else {
                    raw = null;
                }
            } else {
                Class<?> atClass = Class.forName("android.app.ActivityThread");
                Method curApp = atClass.getMethod("currentApplication");
                Object app = curApp.invoke(null);
                if (app == null) {
                    return null;
                }
                Method getFilesDir = app.getClass().getMethod("getFilesDir");
                Object filesDir = getFilesDir.invoke(app);
                File pf = new File(filesDir.toString(), "agy-providers/providers.json");
                if (!pf.exists()) {
                    return null;
                }
                RandomAccessFile raf = new RandomAccessFile(pf, "r");
                byte[] b = new byte[(int) raf.length()];
                raf.readFully(b);
                raf.close();
                raw = new String(b, StandardCharsets.UTF_8);
            }
            if (raw == null || raw.isEmpty()) {
                return null;
            }
            JSONArray conns = new JSONObject(raw).optJSONArray("connections");
            if (conns == null) {
                return null;
            }
            for (int i = 0; i < conns.length(); i++) {
                JSONObject conn = conns.optJSONObject(i);
                if (conn == null) {
                    continue;
                }
                JSONArray sel = conn.optJSONArray("selected");
                boolean matched = false;
                if (sel != null) {
                    for (int j = 0; j < sel.length(); j++) {
                        String s = sel.optString(j);
                        if (model != null && (model.equals(s) || model.endsWith("/" + s))) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) {
                    continue;
                }
                JSONObject modelMeta = conn.optJSONObject("modelMeta");
                if (modelMeta == null) {
                    return null;
                }
                return modelMeta.optJSONObject(model);
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
