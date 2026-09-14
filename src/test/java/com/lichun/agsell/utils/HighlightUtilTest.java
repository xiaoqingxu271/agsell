package com.lichun.agsell.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 关键词高亮工具边界测试
 */
class HighlightUtilTest {

    @Test
    @DisplayName("空输入：text 或 keyword 为 null/空白时原样返回")
    void blankInput() {
        assertNull(HighlightUtil.highlight(null, "脐橙"));
        assertEquals("", HighlightUtil.highlight("", "脐橙"));
        assertEquals("   ", HighlightUtil.highlight("   ", "脐橙"));
        assertEquals("赣南脐橙", HighlightUtil.highlight("赣南脐橙", null));
        assertEquals("赣南脐橙", HighlightUtil.highlight("赣南脐橙", ""));
        assertEquals("赣南脐橙", HighlightUtil.highlight("赣南脐橙", "   "));
    }

    @Test
    @DisplayName("单次命中：关键词被 em 标签包裹")
    void singleHit() {
        assertEquals("赣南<em>脐橙</em> 新鲜当季",
                HighlightUtil.highlight("赣南脐橙 新鲜当季", "脐橙"));
    }

    @Test
    @DisplayName("多次命中：同一关键词多处出现全部标记")
    void multipleHits() {
        assertEquals("<em>脐橙</em>与<em>脐橙</em>",
                HighlightUtil.highlight("脐橙与脐橙", "脐橙"));
    }

    @Test
    @DisplayName("多词关键词：按空白拆分逐词标记")
    void multiWord() {
        assertEquals("<em>新鲜</em> <em>脐橙</em>",
                HighlightUtil.highlight("新鲜 脐橙", "新鲜 脐橙"));
    }

    @Test
    @DisplayName("关键词前后空白被去除")
    void trimKeyword() {
        assertEquals("赣南<em>脐橙</em>", HighlightUtil.highlight("赣南脐橙", "  脐橙  "));
    }

    @Test
    @DisplayName("HTML 注入防护：keyword 中的尖括号被转义，不会注入标签")
    void htmlInjectionSafe() {
        // "<b>" 转义为 "&lt;b&gt;"，原文无该串，不产生任何 em 标记
        assertEquals("赣南脐橙", HighlightUtil.highlight("赣南脐橙", "<b>"));
        assertEquals("赣南脐橙", HighlightUtil.highlight("赣南脐橙", "<script>alert(1)</script>"));
    }

    @Test
    @DisplayName("命中即安全转义：命中含尖括号文本时，em 内内容不破坏结构")
    void hitWithEscapedChar() {
        // keyword "a<b" 转义为 "a&lt;b"，原文含 "a&lt;b" 才命中（一般不会出现，验证不抛异常）
        String result = HighlightUtil.highlight("a&lt;b", "a<b");
        assertEquals("<em>a&lt;b</em>", result);
    }

    @Test
    @DisplayName("未命中：原样返回")
    void noHit() {
        assertEquals("五常大米", HighlightUtil.highlight("五常大米", "脐橙"));
    }

    @Test
    @DisplayName("关键词为单个字符时也能高亮（降级 LIKE 场景）")
    void singleCharKeyword() {
        assertEquals("赣南<em>橙</em>子", HighlightUtil.highlight("赣南橙子", "橙"));
    }

    @Test
    @DisplayName("长文本多次命中不越界、不无限循环")
    void longTextNoLoop() {
        String text = "橙".repeat(100);
        String result = HighlightUtil.highlight(text, "橙");
        assertEquals(100, result.split("<em>").length - 1);
        assertTrue(result.startsWith("<em>"));
        assertTrue(result.endsWith("</em>"));
    }
}
