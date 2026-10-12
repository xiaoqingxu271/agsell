package com.lichun.agsell.controller.user;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * AI 推荐可解释理由：原始信号（0~1）择优映射中文标签，热销兜底（全 0）不展示
 */
class AiRecommendControllerReasonTest {

    private JSONObject sig(double sem, double pref, double sales, double rate, double fresh) {
        return sig(sem, pref, sales, rate, fresh, 0.0);
    }

    private JSONObject sig(double sem, double pref, double sales, double rate, double fresh, double browse) {
        JSONObject o = new JSONObject();
        o.set("sem", sem);
        o.set("pref", pref);
        o.set("sales", sales);
        o.set("rate", rate);
        o.set("fresh", fresh);
        o.set("browse", browse);
        return o;
    }

    @Test
    @DisplayName("偏好最强：带分类名的'你常买'")
    void prefWins() {
        assertEquals("你常买「粮油米面」", AiRecommendController.buildReason(sig(0, 1.0, 0.9, 0.5, 0.85), "粮油米面"));
    }

    @Test
    @DisplayName("偏好最强但无分类名：退化为'符合你的偏好'")
    void prefWinsNoCategory() {
        assertEquals("符合你的偏好", AiRecommendController.buildReason(sig(0, 1.0, 0, 0, 0), null));
    }

    @Test
    @DisplayName("搜索相关最强：'与你的搜索高度相关'")
    void semWins() {
        assertEquals("与你的搜索高度相关", AiRecommendController.buildReason(sig(0.9, 0.2, 0.5, 0.3, 0.4), "水果"));
    }

    @Test
    @DisplayName("时令最强：'日期新鲜'")
    void freshWins() {
        assertEquals("日期新鲜", AiRecommendController.buildReason(sig(0, 0, 0.9, 0.0, 0.95), "粮油米面"));
    }

    @Test
    @DisplayName("口碑最强：'好评率高'")
    void rateWins() {
        assertEquals("好评率高", AiRecommendController.buildReason(sig(0, 0, 0.5, 1.0, 0.3), "水果"));
    }

    @Test
    @DisplayName("销量最强：'热销爆款'")
    void salesWins() {
        assertEquals("热销爆款", AiRecommendController.buildReason(sig(0, 0, 1.0, 0.2, 0.1), "水果"));
    }

    @Test
    @DisplayName("并列优先级：搜索 > 最近浏览 > 偏好 > 时令 > 口碑 > 销量")
    void tiePriority() {
        assertEquals("与你的搜索高度相关", AiRecommendController.buildReason(sig(0.5, 0.5, 0.5, 0.5, 0.5), "水果"));
        assertEquals("你最近看过「水果」", AiRecommendController.buildReason(sig(0, 0.5, 0.5, 0.5, 0.5, 0.5), "水果"));
        assertEquals("你常买「水果」", AiRecommendController.buildReason(sig(0, 0.5, 0.5, 0.5, 0.5, 0.1), "水果"));
        assertEquals("日期新鲜", AiRecommendController.buildReason(sig(0, 0, 0.5, 0.5, 0.5), "水果"));
        assertEquals("好评率高", AiRecommendController.buildReason(sig(0, 0, 0.5, 0.5, 0.0), "水果"));
    }

    @Test
    @DisplayName("最近浏览最强：'你最近看过「分类」'（浏览过的类目才可能有此标签）")
    void browseWins() {
        assertEquals("你最近看过「乌龙普洱」",
                AiRecommendController.buildReason(sig(0.2, 0.4, 0.9, 0.3, 0.1, 1.0), "乌龙普洱"));
    }

    @Test
    @DisplayName("最近浏览最强但无分类名：退化为'符合你最近的浏览'")
    void browseWinsNoCategory() {
        assertEquals("符合你最近的浏览", AiRecommendController.buildReason(sig(0, 0.3, 0, 0, 0, 1.0), null));
    }

    @Test
    @DisplayName("全 0（热销兜底无 signals）：不展示理由")
    void allZeroNoReason() {
        assertNull(AiRecommendController.buildReason(sig(0, 0, 0, 0, 0), "水果"));
        assertNull(AiRecommendController.buildReason(null, "水果"));
    }
}
