package com.lichun.agsell.controller.user;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * AI 推荐可解释理由：原始信号（0~1）择优映射中文标签，热销兜底（全 0）不展示；
 * 零评价商品的口碑分来自全站先验，属中性值，不得对外宣称"好评率高"
 */
class AiRecommendControllerReasonTest {

    private JSONObject sig(double sem, double pref, double sales, double rate, double fresh) {
        return sig(sem, pref, sales, rate, fresh, 0.0);
    }

    private JSONObject sig(double sem, double pref, double sales, double rate, double fresh, double browse) {
        return sig(true, sem, pref, sales, rate, fresh, browse);
    }

    /** rateReviewed=false 表示该商品没有一条已分析评价，signals.rate 只是全站先验兜底 */
    private JSONObject sig(boolean rateReviewed, double sem, double pref, double sales,
                           double rate, double fresh, double browse) {
        JSONObject o = new JSONObject();
        o.set("sem", sem);
        o.set("pref", pref);
        o.set("sales", sales);
        o.set("rate", rate);
        o.set("rate_conf", rateReviewed ? 1.0 : 0.0);
        o.set("fresh", fresh);
        o.set("browse", browse);
        return o;
    }

    @Test
    @DisplayName("偏好最强：带分类名的'偏爱'（偏好由订单+好评+浏览合成，不能宣称'常买'）")
    void prefWins() {
        assertEquals("偏爱「粮油米面」", AiRecommendController.buildReason(sig(0, 1.0, 0.9, 0.5, 0.85), "粮油米面"));
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
    @DisplayName("口碑最强且有真实评价：'好评率高'")
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
        assertEquals("最近看过「水果」", AiRecommendController.buildReason(sig(0, 0.5, 0.5, 0.5, 0.5, 0.5), "水果"));
        assertEquals("偏爱「水果」", AiRecommendController.buildReason(sig(0, 0.5, 0.5, 0.5, 0.5, 0.1), "水果"));
        assertEquals("日期新鲜", AiRecommendController.buildReason(sig(0, 0, 0.5, 0.5, 0.5), "水果"));
        assertEquals("好评率高", AiRecommendController.buildReason(sig(0, 0, 0.5, 0.5, 0.0), "水果"));
    }

    @Test
    @DisplayName("最近浏览最强：'最近看过「分类」'（浏览过的类目才可能有此标签）")
    void browseWins() {
        assertEquals("最近看过「乌龙普洱」",
                AiRecommendController.buildReason(sig(0.2, 0.4, 0.9, 0.3, 0.1, 1.0), "乌龙普洱"));
    }

    @Test
    @DisplayName("最近浏览最强但无分类名：退化为'符合你最近的浏览'")
    void browseWinsNoCategory() {
        assertEquals("符合你最近的浏览", AiRecommendController.buildReason(sig(0, 0.3, 0, 0, 0, 1.0), null));
    }

    // ---------- 口碑可信度 ----------

    @Test
    @DisplayName("零评价商品（rate 是全站先验 0.6667）：口碑项不参与择优，让位给真实信号")
    void priorRateYieldsToRealSignals() {
        // rate 数值最大（先验 0.6667 > sales 0.5），但无评价证据 ⇒ 不能说"好评率高"
        assertEquals("热销爆款", AiRecommendController.buildReason(
                sig(false, 0, 0, 0.5, 0.6667, 0.0, 0.0), "水果"));
    }

    @Test
    @DisplayName("零评价商品且口碑是唯一信号：宁可不展示理由，也不编造好评")
    void priorRateAloneShowsNothing() {
        assertNull(AiRecommendController.buildReason(
                sig(false, 0.0, 0.0, 0.0, 0.6667, 0.0, 0.0), "水果"));
    }

    @Test
    @DisplayName("缺 rate_conf 字段（旧版本 AI 服务）：按无评价处理，保守不出口碑标签")
    void missingRateConfTreatedAsUnreviewed() {
        JSONObject o = new JSONObject();
        o.set("sem", 0.0);
        o.set("pref", 0.0);
        o.set("sales", 0.1);
        o.set("rate", 1.0);
        o.set("fresh", 0.0);
        o.set("browse", 0.0);
        assertEquals("热销爆款", AiRecommendController.buildReason(o, "水果"));
    }

    // ---------- 类目名文案 ----------

    @Test
    @DisplayName("类目名带'××类'后缀：剥掉后缀进文案更像人话（浆果类 → 偏爱「浆果」）")
    void categorySuffixTrimmed() {
        assertEquals("偏爱「浆果」", AiRecommendController.buildReason(sig(0, 1.0, 0, 0, 0), "浆果类"));
        assertEquals("最近看过「葱蒜」", AiRecommendController.buildReason(sig(0, 0, 0, 0, 0, 1.0), "葱蒜类"));
    }

    @Test
    @DisplayName("两字类目名不剥后缀（避免'×类'被削成单字）；空白分类名按无分类处理")
    void categoryTrimGuarded() {
        assertEquals("偏爱「果类」", AiRecommendController.buildReason(sig(0, 1.0, 0, 0, 0), "果类"));
        assertEquals("符合你的偏好", AiRecommendController.buildReason(sig(0, 1.0, 0, 0, 0), "   "));
    }

    @Test
    @DisplayName("全 0（热销兜底无 signals）：不展示理由")
    void allZeroNoReason() {
        assertNull(AiRecommendController.buildReason(sig(0, 0, 0, 0, 0), "水果"));
        assertNull(AiRecommendController.buildReason(null, "水果"));
    }
}
