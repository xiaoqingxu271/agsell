package com.lichun.agsell.utils;

import cn.hutool.core.util.StrUtil;

/**
 * 关键词高亮工具（应用层实现，MySQL FULLTEXT 无原生高亮）
 * <p>
 * 安全约定：
 * 1. 仅使用 indexOf 定位（非正则），规避用户输入正则注入；
 * 2. keyword 中的 &lt; &gt; 先转义，防止恶意关键词向结果中注入 HTML 标签；
 * 3. 多词关键词按空白拆分，逐词标记。
 */
public final class HighlightUtil {

    private static final String OPEN_TAG = "<em>";
    private static final String CLOSE_TAG = "</em>";

    private HighlightUtil() {
    }

    /**
     * 将 text 中出现的 keyword 包裹为 &lt;em&gt; 标签；输入为空时原样返回。
     */
    public static String highlight(String text, String keyword) {
        if (StrUtil.isBlank(text) || StrUtil.isBlank(keyword)) {
            return text;
        }
        String escaped = keyword.trim().replace("<", "&lt;").replace(">", "&gt;");
        String[] words = escaped.split("\\s+");
        StringBuilder sb = new StringBuilder(text);
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            int from = 0;
            int idx;
            int step = word.length() + OPEN_TAG.length() + CLOSE_TAG.length();
            while ((idx = sb.indexOf(word, from)) >= 0) {
                sb.replace(idx, idx + word.length(), OPEN_TAG + word + CLOSE_TAG);
                from = idx + step;
            }
        }
        return sb.toString();
    }
}
