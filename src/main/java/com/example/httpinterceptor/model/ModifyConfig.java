package com.example.httpinterceptor.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ModifyConfig {

    private final Integer overrideStatusCode;
    private final Map<String, List<String>> replaceHeaders;
    private final List<TextReplacement> textReplacements;

    public ModifyConfig(Integer overrideStatusCode, Map<String, List<String>> replaceHeaders, List<TextReplacement> textReplacements) {
        this.overrideStatusCode = overrideStatusCode;
        this.replaceHeaders = replaceHeaders != null ? replaceHeaders : Collections.emptyMap();
        this.textReplacements = textReplacements != null ? textReplacements : Collections.emptyList();
    }

    public Integer getOverrideStatusCode() {
        return overrideStatusCode;
    }

    public Map<String, List<String>> getReplaceHeaders() {
        return replaceHeaders;
    }

    public List<TextReplacement> getTextReplacements() {
        return textReplacements;
    }

    public static class TextReplacement {
        private final String from;
        private final String to;
        private final boolean regex;

        public TextReplacement(String from, String to, boolean regex) {
            this.from = from;
            this.to = to;
            this.regex = regex;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }

        public boolean isRegex() {
            return regex;
        }

        public String apply(String input) {
            if (input == null || from == null || to == null) return input;
            if (regex) {
                return input.replaceAll(from, to);
            } else {
                return input.replace(from, to);
            }
        }
    }
}
