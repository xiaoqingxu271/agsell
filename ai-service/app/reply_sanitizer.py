"""回复排版清洗：LLM 输出里的 Markdown 标记一律剥掉，用户只看纯文本

2026-10 用户实测反馈：回复里出现 **400-000-0000** 这类双星号加粗标记，
小程序气泡按纯文本渲染，星号原样露出，排版观感差。
修了两层，缺一不可：
  1. 提示词约束（prompts.py）：要求模型用纯文本作答——治本，但模型偶尔违规；
  2. 本模块确定性清洗——兜底，保证到达用户前标记必被剥掉。

两个入口对应两条下发路径：
  - sanitize_reply_text()      整段清洗：同步接口 / 流式兜底快照 / 降级模板文案；
  - MarkdownStripper.feed()    增量清洗：SSE 逐 token 下发没有"事后整段"的机会，
                               ** 经常被拆在相邻两个 token 里，只能字符级流式处理。
规则刻意保守（客服中文文本里这些符号不承载语义）：
  - `*` 与反引号一律剥除（加粗/斜体/行内代码标记，含跨 token 拆散的半个 **）；
  - 行首的 # 标题记号与 - • 列表符剥除；行内 # 保留（订单号 #123 这类写法）。
"""
import re


_MD_HEADING = re.compile(r"^[ \t]{0,3}#{1,6}[ \t]+")
_MD_BULLET = re.compile(r"^[ \t]{0,3}[-•][ \t]+")


def sanitize_reply_text(text: str) -> str:
    """整段清洗：剥 Markdown 标记 + 去首尾空白（同步链路的唯一入口）"""
    if not text:
        return text or ""
    lines = []
    for line in text.replace("`", "").replace("*", "").split("\n"):
        line = _MD_HEADING.sub("", line)
        line = _MD_BULLET.sub("", line)
        lines.append(line)
    return "\n".join(lines).strip()


class MarkdownStripper:
    """流式增量清洗：逐字符状态机，feed() 进原始 token、吐干净文本

    - `*` / 反引号直接丢弃，不缓存（** 拆成两个 token 也各丢各的）；
    - 行首的 # / - / • 有歧义（"-5 元" "订单#123"），缓存等下一字符裁决：
      后跟空格才当标记剥掉，否则原样吐出。# 连续时最多缓存 6 个（## 标题），
      与整段清洗的 _MD_HEADING（#{1,6} + 空格）口径一致，两路径剥的是同一批标记；
    - flush() 在流收尾时调用，吐出仍被缓存的字符。
    """

    def __init__(self) -> None:
        self._at_line_start = True   # 文本开头等同行首
        self._pending = ""           # 被缓存等待裁决的行首记号（若干个 # 或单个 - / •）

    def feed(self, chunk: str) -> str:
        if not chunk:
            return ""
        out: list[str] = []
        for ch in chunk:
            if ch in ("*", "`"):
                continue
            if ch == "\n":
                # 缓冲中的记号没等到裁决就换行 = 孤立记号，丢弃
                self._pending = ""
                self._at_line_start = True
                out.append(ch)
                continue
            if self._pending:
                # 继续积累标题记号：## / ### …（与 _MD_HEADING 的 {1,6} 对齐）
                if ch == "#" and len(self._pending) < 6 and set(self._pending) == {"#"}:
                    self._pending += ch
                    continue
                self._at_line_start = False
                if ch in (" ", "\t"):
                    self._pending = ""  # "# 文本" / "## 文本" / "- 文本"：记号连同空格一起剥
                    continue
                out.append(self._pending)  # "订单#123" / "3-5 天" / "##没有空格"：原样保留
                self._pending = ""
                out.append(ch)
                continue
            if self._at_line_start and ch in ("#", "-", "•"):
                self._pending = ch
                continue
            self._at_line_start = False
            out.append(ch)
        return "".join(out)

    def flush(self) -> str:
        pending, self._pending = self._pending, ""
        return pending
