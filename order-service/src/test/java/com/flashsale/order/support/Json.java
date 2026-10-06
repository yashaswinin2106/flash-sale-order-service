package com.flashsale.order.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a top-level string field from a flat JSON response, enough for these tests. */
public final class Json {

    private Json() {
    }

    public static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (!m.find()) {
            throw new AssertionError("No field " + name + " in " + json);
        }
        return m.group(1);
    }
}
