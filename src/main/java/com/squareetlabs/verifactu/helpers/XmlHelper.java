package com.squareetlabs.verifactu.helpers;

import java.util.List;
import java.util.Map;

public class XmlHelper {

    public static String mapToXml(Map<String, Object> map, String rootName) {
        StringBuilder sb = new StringBuilder();
        if (rootName != null && !rootName.isEmpty()) {
            sb.append("<").append(rootName).append(">");
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                sb.append(mapToXml((Map<String, Object>) value, key));
            } else if (value instanceof List) {
                for (Object item : (List) value) {
                    if (item instanceof Map) {
                        sb.append(mapToXml((Map<String, Object>) item, key));
                    } else {
                        sb.append("<").append(key).append(">").append(escapeXml(item.toString())).append("</")
                                .append(key).append(">");
                    }
                }
            } else {
                sb.append("<").append(key).append(">").append(escapeXml(value.toString())).append("</").append(key)
                        .append(">");
            }
        }

        if (rootName != null && !rootName.isEmpty()) {
            sb.append("</").append(rootName).append(">");
        }
        return sb.toString();
    }

    private static String escapeXml(String input) {
        if (input == null)
            return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
