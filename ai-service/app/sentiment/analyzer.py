"""评价情感分析器（农产品电商领域）

方法：领域情感词库 + 否定翻转 + 程度副词加权 + 评分先验。
纯规则实现，确定性输出、零 LLM 成本、毫秒级延迟；与客服 Agent 的
"LLM + 兜底"双通道哲学一致——高频明确场景走规则，保证稳定可控。

标签：1=好评（score >= 0.7）  0=中评（0.3 < score < 0.7）  -1=差评（score <= 0.3）
"""
import re

# 正面词库（农产品/生鲜电商场景）→ 权重
POS_WORDS: dict[str, float] = {
    "新鲜": 2.0, "好吃": 2.0, "美味": 2.0, "香甜": 2.0, "多汁": 2.0,
    "满意": 2.0, "回购": 2.0, "推荐": 1.5, "正宗": 2.0, "赞": 1.5,
    "甜": 1.5, "香": 1.0, "脆": 1.5, "嫩": 1.5,
    "饱满": 1.5, "划算": 1.5, "实惠": 1.5, "不错": 1.5, "很好": 2.0,
    "还可以": 1.5, "还行": 1.0,
    "好": 1.0, "喜欢": 1.5, "五星": 2.0, "包装好": 1.5, "用心": 1.5,
    "贴心": 1.5, "完好": 1.5, "无损": 1.0, "放心": 1.5, "快": 1.0,
    "负责": 1.5, "周到": 1.5, "棒": 1.5, "优秀": 1.5, "惊喜": 1.5,
}

# 负面词库 → 权重
NEG_WORDS: dict[str, float] = {
    "坏果": 2.5, "烂": 2.5, "腐烂": 2.5, "变质": 2.5, "发霉": 2.5,
    "不新鲜": 2.5, "难吃": 2.5, "差评": 2.5, "破损": 2.0, "损坏": 2.0,
    "漏发": 2.0, "少件": 2.0, "发错": 2.0, "后悔": 2.0, "坑": 2.0,
    "骗": 2.5, "差": 1.5, "慢": 1.0, "脏": 1.5, "蔫": 2.0,
    "干瘪": 2.0, "酸": 1.0, "涩": 1.5, "失望": 2.0, "投诉": 1.5,
}

# 否定词：命中后翻转其后 4 字符窗口内的情感词极性
NEGATION_WORDS = ("不太", "不够", "没有", "不", "没", "别", "无")

# 程度副词：放大其后 2 字符窗口内的情感词权重
DEGREE_WORDS = ("非常", "特别", "极其", "十分", "相当", "很", "太", "超")

_LABEL_POS, _LABEL_NEU, _LABEL_NEG = 1, 0, -1


def analyze_sentiment(content: str, rating: int | None = None) -> dict:
    """分析单条评价。rating（1~5 星）作为弱先验参与打分。

    返回 {"label": 1|0|-1, "score": float(0~1), "keywords": [str, ...]}
    被否定的情感词以「不X」形式进入关键词，避免差评关键词云出现"好吃"误导。
    """
    text = (content or "").strip()
    if not text:
        return {"label": _LABEL_NEU, "score": 0.5, "keywords": []}

    pos, neg = 0.0, 0.0
    keywords: list[str] = []

    # 情感词命中（同词去重）：极性 = 词性 ×（否定翻转）×（程度放大）
    for word, weight in {**NEG_WORDS, **POS_WORDS}.items():
        if word not in text:
            continue
        polarity = 1.0 if word in POS_WORDS else -1.0
        w = weight
        negated = False
        for m in re.finditer(re.escape(word), text):
            prefix = text[max(0, m.start() - 4):m.start()]
            if any(neg_w in prefix for neg_w in NEGATION_WORDS):
                polarity = -polarity
                w = min(w, 1.5)
                negated = True
                break
        for m in re.finditer(re.escape(word), text):
            prefix = text[max(0, m.start() - 2):m.start()]
            if any(deg in prefix for deg in DEGREE_WORDS):
                w *= 1.5
                break
        if polarity > 0:
            pos += w
        else:
            neg += w
        keywords.append(("不" + word) if negated else word)

    # 评分先验：4~5 星小幅上调，1~2 星小幅下调（文本过短无情感词时为主要依据）
    rating_shift = 0.0
    if rating is not None:
        if rating >= 4:
            rating_shift = 0.08
        elif rating <= 2:
            rating_shift = -0.08

    total = pos + neg
    if total == 0:
        score = 0.5 + rating_shift
    else:
        score = 0.5 + (pos - neg) / (total + 2.0) + rating_shift
    score = max(0.0, min(1.0, round(score, 4)))

    label = _LABEL_POS if score >= 0.7 else (_LABEL_NEG if score <= 0.3 else _LABEL_NEU)
    return {"label": label, "score": score, "keywords": keywords[:5]}
