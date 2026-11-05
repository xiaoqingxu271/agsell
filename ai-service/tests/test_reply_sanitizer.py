"""回复排版清洗测试：整段剥 Markdown + 流式增量剥（** 拆 token、行首歧义裁决、正常文本零损伤）"""
from app.reply_sanitizer import MarkdownStripper, sanitize_reply_text


# ---------- sanitize_reply_text（整段）----------

def test_sanitize_strips_bold_markers():
    assert sanitize_reply_text("**400-000-0000**") == "400-000-0000"
    assert sanitize_reply_text("您可以拨打客服电话 **400-000-0000** 进行咨询。") == \
        "您可以拨打客服电话 400-000-0000 进行咨询。"


def test_sanitize_strips_heading_and_ticks():
    assert sanitize_reply_text("## 运费说明\n运费为 2 元/笔") == "运费说明\n运费为 2 元/笔"
    assert sanitize_reply_text("采用`模拟支付`流程") == "采用模拟支付流程"


def test_sanitize_strips_line_start_bullets():
    assert sanitize_reply_text("- 支持七天无理由\n- 生效后退款") == "支持七天无理由\n生效后退款"


def test_sanitize_keeps_inline_hash_and_dash():
    # 行内的 # 与 - 承载语义（订单号/区间写法），不得误伤
    assert sanitize_reply_text("订单#123 已发货") == "订单#123 已发货"
    assert sanitize_reply_text("3-5 天送达") == "3-5 天送达"


def test_sanitize_trims_and_keeps_paragraphs():
    assert sanitize_reply_text("\n\n 您好。\n\n请问有什么可以帮您？\n") == \
        "您好。\n\n请问有什么可以帮您？"


def test_sanitize_empty_and_none():
    assert sanitize_reply_text("") == ""
    assert sanitize_reply_text(None) == ""


# ---------- MarkdownStripper（流式增量）----------

def _feed_all(stripper, chunks):
    out = []
    for c in chunks:
        out.append(stripper.feed(c))
    out.append(stripper.flush())
    return "".join(out)


def test_stripper_bold_split_across_tokens():
    # ** 常被拆在相邻两个 token 里：逐字符丢星号，不依赖两星相邻
    chunks = ["您可以拨打", " **", "400", "-000", "-0000", "**", " 咨询"]
    assert _feed_all(MarkdownStripper(), chunks) == "您可以拨打 400-000-0000 咨询"


def test_stripper_heading_bullet_at_line_start():
    assert _feed_all(MarkdownStripper(), ["# 运费", "\n", "- 2 元", "\n"]) == "运费\n2 元\n"


def test_stripper_keeps_inline_ambiguous_chars():
    # "-5" 前无空格起步（非列表）、"订单#123" 行内 #：均原样保留
    assert _feed_all(MarkdownStripper(), ["优惠", "-5", " 元"]) == "优惠-5 元"
    assert _feed_all(MarkdownStripper(), ["订单", "#123", " 已发货"]) == "订单#123 已发货"


def test_stripper_pending_char_flushed_at_end():
    # 行首的标记字符被缓存等待裁决，流在下一 token 到来前结束 → flush 吐回原字符
    s = MarkdownStripper()
    assert s.feed("运费如下\n-") == "运费如下\n"
    assert s.flush() == "-"


def test_stripper_plain_text_untouched():
    text = "您好！\n\n请问有什么可以帮您？运费 2 元/笔。"
    assert _feed_all(MarkdownStripper(), [text[i:i + 3] for i in range(0, len(text), 3)]) == text


def test_stripper_matches_batch_sanitizer():
    # 两条路径口径一致：同一句话整段清洗与流式清洗结果相同
    raw = "支付方式有**模拟支付**，另有`微信支付`。\n- 到账时间 1-3 天\n#123 为内部单号"
    assert _feed_all(MarkdownStripper(), [raw[i:i + 5] for i in range(0, len(raw), 5)]) == \
        sanitize_reply_text(raw)
