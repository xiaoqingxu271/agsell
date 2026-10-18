"""口碑话术服务端模板化（改进项 #22）

存在的理由：实测模型会把**真数字安上错的指标名**——66.67% 说成"口碑不错"、
topKeywords 里的"回购"说成"回购率"、sales=1046 说成"1046 人回购"。提示词禁句压得住
具体词，压不住同义改写。所以把口碑句子做成服务端唯一来源的成品文本，
卡片字段与作答上下文都只能引用它。
"""
from app.agent.executors import ToolContext, run_tool
from app.prompts import AGENT_ANSWER_SYSTEM_TEMPLATE, PRODUCT_QA_SYSTEM_TEMPLATE
from app.tools.java_api import annotate_reputation, format_product_facts, reputation_text


def test_zero_review_never_claims_a_positive_rate():
    """零评价商品拿到的好评信号是全站先验（排序用），绝不能变成对用户的实测宣称"""
    assert reputation_text({"name": "阳山水蜜桃", "reviewCount": 0, "positiveRate": None}) == "暂无评价数据"
    assert reputation_text(None) == "暂无评价数据"
    assert reputation_text({}) == "暂无评价数据"


def test_sentence_carries_only_verifiable_numbers():
    # Java 侧 positiveRate 是字符串，必须能吃下并四舍五入到一位
    assert reputation_text({"reviewCount": 3, "positiveRate": "66.67"}) == "3 条买家评价，好评率 66.7%"
    assert reputation_text({"reviewCount": 12, "positiveRate": 98.5}) == "12 条买家评价，好评率 98.5%"
    # 有关键词时用"买家提到"引出——它们是词，不是指标
    assert reputation_text({"reviewCount": 3, "positiveRate": "66.67",
                            "topKeywords": ["新鲜", "多汁", "回购", "好"]}) == \
        "3 条买家评价，好评率 66.7%，买家提到：新鲜、多汁、回购"
    # 只有条数、没有率：不编造好评率
    assert reputation_text({"reviewCount": 5, "positiveRate": None}) == "5 条买家评价"


def test_malformed_fields_degrade_instead_of_raising():
    """脏数据（空串/字符串数字/None）不能让导购整条链路炸掉"""
    assert reputation_text({"reviewCount": None}) == "暂无评价数据"
    assert reputation_text({"reviewCount": "abc", "positiveRate": "66.67"}) == "暂无评价数据"
    assert reputation_text({"reviewCount": 3, "positiveRate": "无"}) == "3 条买家评价"
    assert reputation_text({"reviewCount": 3, "topKeywords": None}) == "3 条买家评价"


def test_sentence_never_contains_conclusion_words():
    """成品句子里不允许出现评价性结论或把词当指标——这是模板化的全部意义"""
    sample = reputation_text({"reviewCount": 3, "positiveRate": "66.67",
                              "topKeywords": ["新鲜", "回购", "好评"]})
    for banned in ("口碑不错", "口碑好", "好评率高", "很受欢迎", "回购率", "好评率很高"):
        assert banned not in sample


def test_annotate_does_not_mutate_source_rows():
    rows = [{"id": 1, "name": "赣南脐橙", "reviewCount": 3, "positiveRate": "66.67"}]
    out = annotate_reputation(rows)
    assert "reputation" not in rows[0]          # 原 VO 不被污染（重排/兜底还会复用）
    assert out[0]["reputation"] == "3 条买家评价，好评率 66.7%"


class _Store:
    def search(self, question, top_k=None):
        return [{"id": 1, "score": 0.62}, {"id": 2, "score": 0.55}]


class _Api:
    def search_products(self, keyword=None, min_price=None, max_price=None, limit=6, ids=None):
        pool = {
            1: {"id": 1, "name": "赣南脐橙", "price": 39.9, "reviewCount": 3,
                "positiveRate": "66.67", "topKeywords": ["新鲜", "多汁"]},
            2: {"id": 2, "name": "水果玉米", "price": 15.9, "reviewCount": 0, "positiveRate": None},
        }
        return [pool[i] for i in (ids or []) if i in pool]

    def fetch_product(self, product_id):
        return None

    def format_payload(self, data):
        import json

        return json.dumps(data, ensure_ascii=False, default=str)


def test_product_search_items_and_payload_carry_the_same_sentence():
    """卡片字段与给 LLM 的资料必须是同一句口碑，否则前端显示的和用户听到的会分叉"""
    import json

    ctx = ToolContext(product_store=_Store(), java_api_module=_Api())
    obs = run_tool(ctx, {"messages": [], "userId": None}, "product_search",
                   {"query": "有什么水果推荐", "inDomain": True})
    assert [p["reputation"] for p in obs.items] == [
        "3 条买家评价，好评率 66.7%，买家提到：新鲜、多汁",
        "暂无评价数据",
    ]
    payload = json.loads(obs.payload)
    assert [p["reputation"] for p in payload] == [p["reputation"] for p in obs.items]


def test_browsing_product_facts_use_the_same_sentence():
    """商详页上下文里的口碑也走同一句——两处措辞不同就会各自漂开"""
    facts = format_product_facts({"name": "赣南脐橙", "price": 39.9, "storage": "常温避光",
                                  "shelfLife": "30天", "reviewCount": 3, "positiveRate": "66.67",
                                  "topKeywords": ["新鲜"]})
    assert "口碑：3 条买家评价，好评率 66.7%，买家提到：新鲜" in facts
    assert "口碑：暂无评价数据" in format_product_facts({"name": "水果玉米", "reviewCount": 0})


def test_prompts_require_verbatim_quote_and_ban_conclusions():
    """提示词侧只允许"原样引用"，不给模型自由组织的空间（双约束的另一半）"""
    assert "一字不改地引用" in AGENT_ANSWER_SYSTEM_TEMPLATE
    assert "reputation" in AGENT_ANSWER_SYSTEM_TEMPLATE
    assert "原样引用" in PRODUCT_QA_SYSTEM_TEMPLATE
    for name, tpl in (("智能体作答", AGENT_ANSWER_SYSTEM_TEMPLATE), ("商品问答", PRODUCT_QA_SYSTEM_TEMPLATE)):
        assert "口碑不错" in tpl, name          # 明确列为禁句，模型才知道边界在哪
        assert "暂无评价数据" in tpl or name == "智能体作答", name
    # 销量与回购人数是两件事（E2E 实测过模型把 sales=1046 说成"1046人回购"）
    assert "已售 N 件" in AGENT_ANSWER_SYSTEM_TEMPLATE
