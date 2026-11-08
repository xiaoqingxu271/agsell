# -*- coding: utf-8 -*-
"""论文插图生成脚本（doc/figures/）

生成《基于 RAG 与大模型智能体的农产品电商智能导购系统的设计与实现》全部插图：
  fig3_1_usecase.png           图 3-1  系统用例图
  fig4_1_system_arch.png       图 4-1  系统总体架构
  fig4_2_ai_service_arch.png   图 4-2  AI 服务架构
  fig4_3_rag_pipeline.png      图 4-3  RAG 检索链路
  fig4_4_er_diagram.png        图 4-4  数据库 ER 图（按域分组）
  fig5_1_evidence_chain.png    图 5-1  智能导购五级证据链
  fig6_1_threshold_curve.png   图 6-1  导购闸门阈值-风险曲线（数据：refusal_eval.json）
  fig6_2_latency_trajectory.png 图 6-2  首 token 延迟优化轨迹（数据：latency_eval.json）

用法（系统 Python，需 matplotlib）：
    python doc/figures/make_figures.py

风格约定：白底、微软雅黑、品牌绿 #15803D 为强调色，图题由论文正文给出（图内不含标题）。
"""
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Circle, Ellipse, FancyBboxPatch

OUT = Path(__file__).resolve().parent

INK = "#1F2937"      # 正文深灰
SOFT = "#475569"     # 次级文字
GREEN = "#15803D"    # 品牌绿（强调/AI）
GREEN_BG = "#E8F5EC"
BLUE = "#1D4ED8"     # Java 后端
BLUE_BG = "#E8EEFB"
ORANGE = "#C2410C"   # 数据存储
ORANGE_BG = "#FDEEE3"
GRAY_BG = "#F1F5F9"
GRAY_EC = "#94A3B8"
LINEC = "#64748B"

plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei", "sans-serif"]
plt.rcParams["axes.unicode_minus"] = False


def new_ax(w_in, h_in, xmax, ymax):
    fig, ax = plt.subplots(figsize=(w_in, h_in))
    ax.set_xlim(0, xmax)
    ax.set_ylim(0, ymax)
    ax.axis("off")
    return fig, ax


def box(ax, x, y, w, h, title, body=None, fc="#FFFFFF", ec="#334155", tc=None,
        fs_t=10, fs_b=8, lw=1.3, rounding=0.10, body_dy=None, title_dy=None):
    """圆角矩形。title 在框内顶部，body 为字符串列表逐行排（末行距底边 ≥1.8）。"""
    ax.add_patch(FancyBboxPatch((x, y), w, h,
                                boxstyle=f"round,pad=0,rounding_size={rounding}",
                                fc=fc, ec=ec, lw=lw, zorder=2))
    cx = x + w / 2
    lines = body or []
    n = len(lines)
    tdy = title_dy if title_dy is not None else h - 1.6
    ax.text(cx, y + tdy, title, ha="center", va="center", fontsize=fs_t,
            color=tc or INK, fontweight="bold", zorder=3)
    if lines:
        bdy = body_dy if body_dy is not None else h - 3.6
        step = (bdy - 1.8) / (n - 1) if n > 1 else 0
        for i, s in enumerate(lines):
            yy = y + bdy - step * i
            ax.text(cx, yy, s, ha="center", va="center", fontsize=fs_b,
                    color=SOFT, zorder=3)
    return cx, y + h / 2


def arrow(ax, p1, p2, color=LINEC, lw=1.3, rad=0.0, ls="-", label=None,
          fs=8, lab_dx=0, lab_dy=0, lab_c=None, style="-|>", zorder=4):
    ax.annotate("", xy=p2, xytext=p1,
                arrowprops=dict(arrowstyle=style, color=color, lw=lw,
                                linestyle=ls, shrinkA=2, shrinkB=2,
                                connectionstyle=f"arc3,rad={rad}"), zorder=zorder)
    if label:
        mx, my = (p1[0] + p2[0]) / 2 + lab_dx, (p1[1] + p2[1]) / 2 + lab_dy
        ax.text(mx, my, label, ha="center", va="center", fontsize=fs,
                color=lab_c or color, zorder=5,
                bbox=dict(fc="white", ec="none", pad=0.6, alpha=0.9))


def badge(ax, x, y, num, color=GREEN):
    """带圈编号徽标（用于狭小空隙处的链路标注）。"""
    ax.add_patch(Circle((x, y), 1.5, fc="white", ec=color, lw=1.5, zorder=6))
    ax.text(x, y, num, ha="center", va="center", fontsize=8.2, color=color,
            fontweight="bold", zorder=7)


def actor(ax, x, y, label, color=INK):
    """小人。y 为脚底。"""
    ax.add_patch(Circle((x, y + 6.2), 0.9, fc="white", ec=color, lw=1.4, zorder=3))
    ax.plot([x, x], [y + 5.3, y + 2.6], color=color, lw=1.4, zorder=3)
    ax.plot([x - 1.6, x + 1.6], [y + 4.4, y + 4.4], color=color, lw=1.4, zorder=3)
    ax.plot([x, x - 1.4], [y + 2.6, y], color=color, lw=1.4, zorder=3)
    ax.plot([x, x + 1.4], [y + 2.6, y], color=color, lw=1.4, zorder=3)
    ax.text(x, y - 1.4, label, ha="center", va="top", fontsize=9.5,
            color=INK, fontweight="bold")


def usecase(ax, x, y, w, h, text, fs=8.5):
    ax.add_patch(Ellipse((x, y), w, h, fc="#FFFFFF", ec="#334155", lw=1.1, zorder=2))
    ax.text(x, y, text, ha="center", va="center", fontsize=fs, color=INK, zorder=3)


# ---------------------------------------------------------------- 图 3-1 用例图
def fig3_1_usecase():
    fig, ax = new_ax(11.5, 7.6, 115, 76)
    ax.add_patch(FancyBboxPatch((16, 3), 84, 70,
                                boxstyle="round,pad=0,rounding_size=0.8",
                                fc="#FDFEFE", ec=GRAY_EC, lw=1.2, ls=(0, (6, 3)), zorder=1))
    ax.text(58, 70.2, "农产品电商智能导购系统", ha="center", fontsize=11.5,
            color=INK, fontweight="bold")

    def group(x, w, title, fc):
        ax.add_patch(FancyBboxPatch((x, 6), w, 58,
                                    boxstyle="round,pad=0,rounding_size=0.8",
                                    fc=fc, ec=GRAY_EC, lw=1.0, zorder=1))
        ax.text(x + w / 2, 61.4, title, ha="center", fontsize=9.5,
                color=SOFT, fontweight="bold")

    group(19, 24, "电商交易", GRAY_BG)
    group(46, 24, "AI 智能导购", GREEN_BG)
    group(73, 24, "后台运营", BLUE_BG)

    col1 = ["浏览/搜索商品", "商品详情与溯源", "购物车/下单/支付", "评价（情感标签）",
            "售后退款", "优惠券领取/核销", "秒杀抢购", "收藏与足迹"]
    col2 = ["AI 客服咨询\n（知识问答）", "AI 智能导购推荐", "个性化推荐\n（首页/搜索）", "转人工工单"]
    col3 = ["商品/订单/评价管理", "营销活动管理\n（券/秒杀/轮播）", "溯源信息维护",
            "AI 知识库维护", "AI 工单处理", "用户与权限管理", "数据仪表盘"]

    ys1 = [58 - i * 6.6 for i in range(len(col1))]
    ys2 = [56 - i * 9.4 for i in range(len(col2))]
    ys3 = [57 - i * 7.5 for i in range(len(col3))]
    for t, y in zip(col1, ys1):
        usecase(ax, 31, y, 21, 5.6, t)
    for t, y in zip(col2, ys2):
        usecase(ax, 58, y, 21, 7.2, t, fs=8.2)
    for t, y in zip(col3, ys3):
        usecase(ax, 85, y, 21, 6.2, t, fs=7.8)

    actor(ax, 5.5, 40, "游客")
    actor(ax, 5.5, 16, "注册用户")
    actor(ax, 109.5, 52, "后台运营")
    actor(ax, 109.5, 20, "管理员")

    def seg(p1, p2, ls="-", color=LINEC, lw=1.0):
        ax.plot([p1[0], p2[0]], [p1[1], p2[1]], color=color, lw=lw, ls=ls, zorder=1)

    # 游客 → 电商交易分组（框边缘，不穿用例）
    seg((8.4, 45.5), (19, 54))
    ax.text(9.0, 51.5, "游客可用\n（含 AI 咨询）", fontsize=7.2, color=SOFT, ha="left")
    # 游客 → AI 分组：走分组框上方的走廊（虚线 = 受限可用）
    seg((8.4, 48.2), (8.4, 67.0), ls=(0, (4, 2)))
    seg((8.4, 67.0), (52, 67.0), ls=(0, (4, 2)))
    arrow(ax, (52, 67.0), (52, 64.4), color=LINEC, lw=1.0, ls=(0, (4, 2)))
    # 注册用户 → 电商交易 / AI 分组
    seg((8.4, 21), (19, 28))
    seg((8.4, 22), (19, 44))
    seg((10.8, 24), (10.8, 65.2))
    seg((10.8, 65.2), (58, 65.2))
    arrow(ax, (58, 65.2), (58, 64.4), color=LINEC, lw=1.0)
    # 后台运营 → 前四项；管理员 → 全部
    for y in ys3[:4]:
        seg((106.6, 56), (96.8, y))
    for y in ys3:
        seg((106.6, 26), (96.9, y))

    # 角色泛化：注册用户 —▷ 游客
    arrow(ax, (5.5, 20.5), (5.5, 35.4), color=SOFT, lw=1.1, style="-|>")
    ax.text(6.8, 28, "泛化", fontsize=7.2, color=SOFT, ha="left")

    fig.savefig(OUT / "fig3_1_usecase.png", dpi=220, bbox_inches="tight",
                facecolor="white")
    plt.close(fig)


# ---------------------------------------------------------- 图 4-1 系统总体架构
def fig4_1_system_arch():
    fig, ax = new_ax(12, 8.8, 120, 88)

    # 客户端层
    box(ax, 6, 71, 50, 15, "用户端（uni-app：微信小程序 / H5，28 个页面）",
        ["首页运营位 · 分类 · 搜索 · 商详（口碑摘要）", "购物车 · 订单 · 支付 · 售后 · 优惠券 · 秒杀",
         "溯源 · 评价 · 收藏 · 消息中心 · AI 客服"],
        fc=GRAY_BG, ec="#475569", fs_t=10, fs_b=8)
    box(ax, 60, 71, 50, 15, "管理后台（Vue 3 + Element Plus，17 个功能页）",
        ["仪表盘 · 商品/订单/用户 · 营销（券/秒杀/轮播/公告）", "售后 · 溯源 · 评价 · 热词",
         "AI 知识库 · AI 工单 · 对话审计 · AI 运营配置 · 系统管理"],
        fc=GRAY_BG, ec="#475569", fs_t=10, fs_b=8)

    # 服务层
    box(ax, 4, 42, 72, 20, "Java 后端（Spring Boot 4.1.1，:8080）",
        ["39 个接口控制器（admin 19 + user 23）", "用户/商品/购物车/订单/支付/秒杀/优惠券",
         "售后/溯源/评价/搜索/消息/系统管理/AI 接入", "JWT 鉴权 · RBAC · 限流 · 操作日志 · SSE 转发"],
        fc=BLUE_BG, ec=BLUE, tc=BLUE, fs_t=10.5, fs_b=8.2)
    box(ax, 84, 42, 32, 20, "AI 服务（FastAPI + LangGraph，:8000）",
        ["智能体编排（有界工具循环）", "RAG 三路检索 + 重排 + 闸门", "智能导购 · 个性化重排",
         "评价情感分析 · 知识库管理"],
        fc=GREEN_BG, ec=GREEN, tc=GREEN, fs_t=10.5, fs_b=8.2)

    # 数据层
    data = [
        (4, 26, "MySQL 8（29 张表）", "业务/AI 数据"),
        (24, 26, "Redis", "令牌/秒杀库存/券/限流"),
        (44, 26, "阿里云 OSS", "商品图片"),
        (78, 26, "Chroma", "FAQ/商品 向量集"),
        (98, 26, "SQLite", "会话检查点/引用日志"),
    ]
    for x, y, t, b in data:
        box(ax, x, y, 18, 9, t, [b], fc=ORANGE_BG, ec=ORANGE, tc=ORANGE,
            fs_t=8.6, fs_b=7.4, rounding=0.08)

    # 客户端 → Java（JWT）
    arrow(ax, (31, 71), (31, 62.2), color=SOFT, lw=1.4, label="HTTP（JWT）", lab_dx=-8.5,
          lab_dy=0.5)
    arrow(ax, (68, 71), (68, 62.2), color=SOFT, lw=1.4, label="HTTP（JWT）", lab_dx=-8.2,
          lab_dy=0.5)
    # ③ 知识库维护代理：管理后台 → AI 服务（经 Java 鉴权转发）
    arrow(ax, (100, 71), (100, 62.2), color=GREEN, lw=1.6)
    badge(ax, 104.2, 67, "③")
    ax.text(102.2, 67, "知识库维护代理", fontsize=7.4, color=GREEN, ha="right", va="center")

    # ① / ②：Java ↔ AI（徽标标注，详见图下说明）
    arrow(ax, (76, 55), (84, 55), color=GREEN, lw=2.0)
    badge(ax, 80, 58.6, "①")
    arrow(ax, (84, 47), (76, 47), color=GREEN, lw=2.0)
    badge(ax, 80, 43.4, "②")

    # 数据层连线
    for x in (13, 33, 53):
        arrow(ax, (x, 39.6), (x, 35.2), color=ORANGE, lw=1.0, style="-")
    for x in (87, 107):
        arrow(ax, (x, 39.6), (x, 35.2), color=ORANGE, lw=1.0, style="-")

    # 底注：三条链路说明
    ax.text(4, 20.5, "① 用户端 → Java /api/ai/chat/stream（SseEmitter 转发）→ AI /v1/chat/stream（流式逐字回复）",
            fontsize=8, color=GREEN, ha="left")
    ax.text(4, 17.2, "② AI → Java /api/ai/internal/**（X-Internal-Key 服务间鉴权）：订单/物流/售后/商品取数、转人工工单落库",
            fontsize=8, color=GREEN, ha="left")
    ax.text(4, 13.9, "③ 管理后台 → Java /api/admin/ai/kb/*（管理员鉴权）→ AI /v1/admin/kb/*（知识库与索引维护代理）",
            fontsize=8, color=GREEN, ha="left")
    ax.text(4, 9.6, "设计原则：Java 管业务与身份，Python 只管 AI 推理——AI 服务不接触用户 JWT、不直连业务库做写操作",
            fontsize=8.2, color=SOFT, ha="left", fontweight="bold")

    fig.savefig(OUT / "fig4_1_system_arch.png", dpi=220, bbox_inches="tight",
                facecolor="white")
    plt.close(fig)


# ------------------------------------------------------- 图 4-2 AI 服务架构
def fig4_2_ai_service_arch():
    fig, ax = new_ax(13, 9.2, 130, 92)

    # 入口层
    ax.text(3, 90.2, "入口层（FastAPI 路由）", fontsize=9.5, color=SOFT,
            fontweight="bold", ha="left")
    eps = ["/v1/chat", "/v1/chat/stream", "/v1/rec/feed", "/v1/rec/search",
           "/v1/sentiment/batch", "/v1/admin/kb/*"]
    for i, e in enumerate(eps):
        box(ax, 3 + i * 20.6, 82.5, 18.6, 6, e, [], fc=GRAY_BG, ec=GRAY_EC,
            fs_t=8.4, rounding=0.07)

    # LangGraph 状态图
    box(ax, 3, 46, 57, 33, "LangGraph 智能体状态图", [], fc="#FFFFFF", ec=GREEN,
        tc=GREEN, fs_t=10, lw=1.6)
    node = dict(fc=GREEN_BG, ec=GREEN, fs_t=8.6, rounding=0.09, lw=1.2)
    ax.text(6, 76.6, "START", fontsize=7.4, color=SOFT, ha="left")
    box(ax, 6, 67, 12, 6, "intent", ["规则前置"], **node)
    box(ax, 24, 67, 13.5, 6, "agent", ["有界编排"], **node)
    box(ax, 43, 67, 13, 6, "generate", ["作答出口"], **node)
    box(ax, 6, 51, 12, 5.5, "smalltalk", [], **node)
    box(ax, 22, 51, 15, 5.5, "human_fallback", [], **node)
    box(ax, 42, 47.8, 12, 4, "END", [], fc="#FFFFFF", ec=SOFT, fs_t=8, rounding=0.12)
    arrow(ax, (18, 70), (24, 70), color=LINEC, lw=1.1)
    arrow(ax, (11, 67), (11, 56.5), color=LINEC, lw=1.1, label="寒暄", lab_dx=-2.6, fs=7.4)
    arrow(ax, (16, 67), (27, 56.5), color=LINEC, lw=1.1, rad=-0.12, label="转人工",
          lab_dx=-2.4, lab_dy=3.4, fs=7.4)
    arrow(ax, (37.5, 70), (43, 70), color=LINEC, lw=1.1)
    arrow(ax, (18.5, 54), (42, 49.6), color=LINEC, lw=0.9, rad=0.08)
    arrow(ax, (35, 51), (43, 49.2), color=LINEC, lw=0.9)
    arrow(ax, (48, 67), (47, 51.8), color=LINEC, lw=0.9)

    # agent 内部循环
    box(ax, 3, 8, 57, 30, "agent 节点内部：有界工具编排循环", [],
        fc="#FFFFFF", ec=GREEN, tc=GREEN, fs_t=9.5, lw=1.4)
    steps = ["bind_tools 规划", "执行器取证", "观察回灌\n（瘦身）", "反思重试 /\n补齐核对轮"]
    for i, s in enumerate(steps):
        box(ax, 5.5 + i * 13, 18, 11.6, 9, s, [], fc=GREEN_BG, ec=GREEN,
            fs_t=7.8, rounding=0.08)
        if i:
            arrow(ax, (5.5 + i * 13 - 1.4, 22.5), (5.5 + i * 13, 22.5), color=LINEC, lw=1.0)
    arrow(ax, (51, 18), (11.3, 18), color=LINEC, lw=1.0, rad=0.22, ls=(0, (4, 2)))
    ax.text(31, 10.6, "步数上限 2 · 单轮调用上限 4 · (工具, 参数) 去重 · 多诉求确定性补齐",
            fontsize=7.8, color=SOFT, ha="center")

    # 工具执行器
    ax.text(66, 78.5, "工具执行器（6 个，把关集中于执行层）", fontsize=9.5, color=SOFT,
            fontweight="bold", ha="left")
    tools = [("kb_search", "FAQ 三路检索"), ("product_search", "五级证据链导购"),
             ("order_query", "订单查询"), ("logistics_query", "物流查询"),
             ("after_sales_query", "售后查询"), ("escalate_human", "→ 回调 Java 落工单")]
    for i, (t, b) in enumerate(tools):
        x = 66 + (i % 2) * 30.5
        y = 61.5 - (i // 2) * 8.5
        box(ax, x, y, 28, 7, t, [b], fc="#FFFFFF", ec=GREEN, tc=INK,
            fs_t=8.4, fs_b=7.4, rounding=0.07)

    # 两个子系统
    box(ax, 66, 20, 28, 20, "RAG 知识检索子系统",
        ["三路召回：向量/全文/关键词", "线性加权融合", "阈值闸门 + 确认带", "bge-reranker 重排"],
        fc=GREEN_BG, ec=GREEN, fs_t=9, fs_b=7.6)
    box(ax, 98, 20, 28, 20, "商品导购子系统",
        ["商品向量索引召回", "五级证据链闸门", "六路信号个性化重排", "卡片截断与口碑成句"],
        fc=GREEN_BG, ec=GREEN, fs_t=9, fs_b=7.6)

    # 连线
    arrow(ax, (31, 82.5), (31, 79), color=LINEC, lw=1.2)
    arrow(ax, (60, 66), (66, 65.8), color=GREEN, lw=1.4, label="取证", lab_dy=2.8, fs=8)
    arrow(ax, (66, 63.5), (67.5, 40.2), color=GREEN, lw=1.2, rad=0.3)    # kb_search ↓ RAG（左走廊）
    arrow(ax, (124, 63.5), (120, 40.2), color=GREEN, lw=1.2, rad=-0.3)   # product_search ↓ 导购（右缘）

    # 支撑组件
    ax.text(3, 3.6, "支撑组件：", fontsize=9, color=SOFT, fontweight="bold", ha="left")
    sup = ["SqliteSaver 会话记忆（TTL 清理）", "ai_chat_log 对话审计", "runtime_config 运营配置",
           "kb_hit_log 引用日志", "LLM 限流退避与降级"]
    ax.text(14.5, 3.6, "　".join(sup), fontsize=8, color=SOFT, ha="left")

    fig.savefig(OUT / "fig4_2_ai_service_arch.png", dpi=220, bbox_inches="tight",
                facecolor="white")
    plt.close(fig)


# ------------------------------------------------------ 图 4-3 RAG 检索链路
def fig4_3_rag_pipeline():
    fig, ax = new_ax(12.6, 6.4, 126, 64)

    box(ax, 2, 27, 15, 12, "查询预处理", ["停用词剥离", "指代拼接"], fc=GRAY_BG,
        ec="#475569", fs_t=9, fs_b=7.8)

    ax.add_patch(FancyBboxPatch((23, 14), 30, 38,
                                boxstyle="round,pad=0,rounding_size=0.12",
                                fc="#FFFFFF", ec=GREEN, lw=1.5, zorder=1))
    ax.text(38, 49.2, "三路并行召回", ha="center", fontsize=9.5, color=GREEN,
            fontweight="bold", zorder=3)
    lanes = [("① 向量通道", "Chroma · bge-small-zh", "阈值闸门 0.5"),
             ("② 全文通道", "MySQL ngram BM25", "词面校验防穿透"),
             ("③ 关键词通道", "ai_faq.keywords 子串", "每命中 +0.5 封顶 3")]
    for i, (t, l1, l2) in enumerate(lanes):
        box(ax, 25, 36.5 - i * 11.5, 26, 9.5, t, [l1, l2], fc=GREEN_BG, ec=GREEN,
            fs_t=8.2, fs_b=7.0, rounding=0.07)
    ax.text(38, 10.8, "任一通道故障自动退场（单通道降级）", fontsize=7.6, color=SOFT,
            ha="center", style="italic")

    box(ax, 59, 36, 22, 12, "确认带裁决", ["向量分 ∈ [0.5, 0.6) 的商品域", "条目须词面佐证，否则剔除"],
        fc="#FFFFFF", ec=ORANGE, tc=ORANGE, fs_t=9, fs_b=7.2)
    box(ax, 59, 18, 22, 12, "线性加权融合", ["向量原分 + 0.5×关键词", "+ 0.3×归一 BM25"],
        fc="#FFFFFF", ec="#475569", fs_t=9, fs_b=7.2)
    box(ax, 87, 27, 17, 12, "交叉重排", ["bge-reranker", "20 候选 → Top-5"],
        fc=GREEN_BG, ec=GREEN, fs_t=9, fs_b=7.2)
    box(ax, 108, 27, 16, 12, "输出", ["知识条目", "+ references 引用"], fc=GRAY_BG,
        ec="#475569", fs_t=9, fs_b=7.8)

    arrow(ax, (17, 33), (23, 33), color=LINEC, lw=1.3)
    arrow(ax, (53, 33), (59, 42), color=LINEC, lw=1.3, rad=-0.1)
    arrow(ax, (53, 33), (59, 24), color=LINEC, lw=1.3, rad=0.1)
    arrow(ax, (81, 42), (87, 35), color=LINEC, lw=1.3, rad=-0.08)
    arrow(ax, (81, 24), (87, 31), color=LINEC, lw=1.3, rad=0.08)
    arrow(ax, (104, 33), (108, 33), color=LINEC, lw=1.3)

    ax.text(95.5, 22.8, "只调已过闸条目的顺序、不放行新条目", fontsize=7.4, color=SOFT,
            ha="center", style="italic")
    ax.text(95.5, 20.6, "失败自动回退融合序", fontsize=7.4, color=SOFT, ha="center",
            style="italic")
    ax.text(38, 4.5, "闸门体系（反幻觉核心）：向量阈值 0.5 + 全文词面校验 + 确认带——域外 12/12 拒答、域内 72 条零误拒（见 6.6 节）",
            fontsize=8, color=INK, ha="left", fontweight="bold")

    fig.savefig(OUT / "fig4_3_rag_pipeline.png", dpi=220, bbox_inches="tight",
                facecolor="white")
    plt.close(fig)


# ------------------------------------------------------------ 图 4-4 ER 图
def fig4_4_er_diagram():
    fig, ax = new_ax(15.2, 9.6, 160, 96)

    def domain(x, y, w, h, name, color):
        ax.add_patch(FancyBboxPatch((x, y), w, h,
                                    boxstyle="round,pad=0,rounding_size=0.8",
                                    fc="#FBFCFD", ec=GRAY_EC, lw=1.0, ls=(0, (5, 3)), zorder=1))
        ax.text(x + 1.6, y + h - 2.2, name, fontsize=9.5, color=color,
                fontweight="bold", ha="left", va="center")

    def ent(x, y, name, cn, fields=None, w=17, ec="#475569"):
        h = 8.6 if fields else 6.4
        box(ax, x, y, w, h, name, None, fc="#FFFFFF", ec=ec, fs_t=8.6, rounding=0.07)
        ax.text(x + w / 2, y + h - 3.1, cn, ha="center", va="center", fontsize=7.0,
                color=SOFT, zorder=3)
        if fields:
            ax.text(x + w / 2, y + 1.8, fields, ha="center", va="center", fontsize=6.4,
                    color="#7C8797", zorder=3)
        return (x, y, w, h)

    def rel(e1, e2, card="1:n", rad=0.0, ls="-", color=LINEC, lab=True, p1=None, p2=None):
        def edge(a, b):
            ax_, ay, aw, ah = a
            bx, by = b[0] + b[2] / 2, b[1] + b[3] / 2
            cx, cy = ax_ + aw / 2, ay + ah / 2
            dx, dy = bx - cx, by - cy
            if abs(dx / (aw or 1)) > abs(dy / (ah or 1)):
                return (ax_ + (aw if dx > 0 else 0), cy)
            return (cx, ay + (ah if dy > 0 else 0))
        q1 = p1 if p1 is not None else edge(e1, e2)
        q2 = p2 if p2 is not None else edge(e2, e1)
        ax.annotate("", xy=q2, xytext=q1,
                    arrowprops=dict(arrowstyle="-", color=color, lw=1.0, linestyle=ls,
                                    shrinkA=1, shrinkB=1,
                                    connectionstyle=f"arc3,rad={rad}"), zorder=1)
        if lab:
            mx, my = (q1[0] + q2[0]) / 2, (q1[1] + q2[1]) / 2
            ax.text(mx, my + 1.1, card, fontsize=6.6, color=SOFT, ha="center", zorder=3,
                    bbox=dict(fc="white", ec="none", pad=0.4, alpha=0.85))

    # ── 用户域（sys_user 置于右下，扇出连线向下不穿本域实体）
    domain(4, 58, 52, 34, "用户域", BLUE)
    ua = ent(7, 76, "user_address", "收货地址", "id · user_id · phone", 20, BLUE)
    uv = ent(29, 76, "user_product_view", "浏览足迹", "user_id · product_id", 24, BLUE)
    us = ent(7, 61.5, "user_search_log", "搜索日志", "user_id · keyword", 22, BLUE)
    u = ent(33, 61.5, "sys_user", "用户", "id · openid · nickname", 20, BLUE)

    # ── 商品域（溯源行置于左侧，给 product→营销域 的连线让出右侧通道）
    domain(60, 58, 96, 34, "商品域", GREEN)
    pc = ent(63, 76, "product_category", "商品分类", "id · parent_id · name", 22, GREEN)
    pr = ent(89, 76, "product", "商品", "id · category_id · price · stock", 24, GREEN)
    ps = ent(117, 76, "product_spec", "商品规格", "id · product_id · spec · stock", 24, GREEN)
    tr = ent(63, 61.5, "traceability_info", "溯源档案", "id · product_id", 22, GREEN)
    rec = ent(95, 61.5, "production_record", "生产记录", "id · traceability_id", 20, GREEN)

    # ── 交易域
    domain(4, 24, 52, 30, "交易域", "#7C3AED")
    ca = ent(7, 42, "cart", "购物车", "id · user_id · product_id", 20, "#7C3AED")
    od = ent(30, 42, "order", "订单", "id · user_id · status · amount", 20, "#7C3AED")
    af = ent(7, 27, "after_sales", "售后单", "id · order_id · status", 20, "#7C3AED")
    oi = ent(30, 27, "order_item", "订单明细", "id · order_id · spec_id", 20, "#7C3AED")

    # ── 营销域（评价/收藏靠左承接 product 连线，券与秒杀居右）
    domain(60, 24, 96, 30, "营销域", ORANGE)
    rv = ent(63, 42, "review", "评价", "id · product_id · user_id · score", 24, ORANGE)
    fv = ent(63, 27, "favorite", "收藏", "id · user_id · product_id", 24, ORANGE)
    cp = ent(91, 42, "coupon", "优惠券模板", "id · type · threshold", 22, ORANGE)
    uc = ent(91, 27, "user_coupon", "用户持券", "id · user_id · order_no", 22, ORANGE)
    cpr = ent(121, 42, "coupon_product", "券适用商品", "id · coupon_id", 22, ORANGE)
    sk = ent(121, 27, "seckill_activity", "秒杀活动", "id · product_id · stock", 22, ORANGE)

    # ── AI 域
    domain(4, 2, 52, 18, "AI 域", GREEN)
    fq = ent(7, 6, "ai_faq", "知识库条目", "id · question · category", 22, GREEN)
    cl = ent(31, 6, "ai_chat_log", "对话审计", "session_id · refused", 22, GREEN)
    tk = ent(56, 6, "ai_ticket", "转人工工单", "id · session_id · status", 22, GREEN)

    # ── 系统域
    domain(84, 0.5, 72, 21, "系统域", SOFT)
    sa = ent(94, 11, "sys_admin", "后台账号", "id · role", 18, SOFT)
    sc = ent(114, 11, "sys_config", "系统配置", "config_key · value", 20, SOFT)
    hw = ent(136, 11, "search_hot_word", "搜索热词", "id · keyword", 18, SOFT)
    bn = ent(94, 2.2, "banner", "轮播图", "id · image_url", 18, SOFT)
    sn = ent(114, 2.2, "sys_notice", "公告", "id · content", 20, SOFT)
    sl = ent(136, 2.2, "sys_log", "操作日志", "id · admin_id", 18, SOFT)

    # ── 关系（布局已保证连线不穿越实体框；cart/user_coupon 的外键关系由字段表达，不画线避免交叉）
    rel(u, ua, lab=False); rel(u, uv, lab=False); rel(u, us, "1:n", lab=False)
    rel(u, ca, "1:n", rad=0.06); rel(u, od, "1:n")
    rel(pc, pr, "1:n"); rel(pr, ps, "1:n"); rel(pr, tr, "1:1"); rel(tr, rec, "1:n")
    rel(pr, rv, "1:n", p1=(99, 76))
    rel(pr, fv, "1:n", lab=False, p1=(95, 76), p2=(87, 31.3))
    rel(pr, sk, "1:n", lab=False, p1=(113, 76), p2=(119, 35.6))
    rel(od, oi, "1:n"); rel(od, af, "1:n")
    rel(od, uc, "核销回填", rad=0.0, ls=(0, (4, 2)), p1=(40, 42), p2=(100, 35.6))
    rel(cp, cpr, "1:n"); rel(cp, uc, "1:n")
    rel(cl, tk, "会话关联", ls=(0, (4, 2)))

    fig.savefig(OUT / "fig4_4_er_diagram.png", dpi=220, bbox_inches="tight",
                facecolor="white")
    plt.close(fig)


# ------------------------------------------------------ 图 5-1 五级证据链
def fig5_1_evidence_chain():
    fig, ax = new_ax(9.6, 8.2, 96, 82)

    levels = [
        ("第 0 级　商详指代", "有 productId 且无品类词 → 只出该商品（结构性判据，向量分不可分 0.518 vs 0.558）"),
        ("第 1 级　品类词 LIKE", "商品名/副标题真命中 = 最硬证据；成类命中（≥2 条）直接采信、重排后仍置顶"),
        ("第 2 级　语义召回", "描述性需求（如\"适合送礼的柑橘\"）；独立闸门 REC_MIN_SCORE = 0.47，补齐 LIKE 单薄结果"),
        ("第 3 级　域外拒答", "两路皆空且模型判定 inDomain=false → 确定性拒答话术，不经大模型、不降级热销榜"),
        ("第 4 级　热销兜底", "未拿到经营域判定（模型故障走兜底）时才走，行为与改造前一致——闸门不成为新故障源"),
    ]
    top = 74
    for i, (t, b) in enumerate(levels):
        y = top - i * 11.6
        fc = GREEN_BG if i < 3 else ("#FDECEC" if i == 3 else GRAY_BG)
        ec = GREEN if i < 3 else ("#B91C1C" if i == 3 else GRAY_EC)
        box(ax, 4, y - 9.8, 60, 9.8, t, [b], fc=fc, ec=ec, tc=INK,
            fs_t=9.4, fs_b=7.2, rounding=0.09, body_dy=3.4)
        if i < len(levels) - 1:
            arrow(ax, (34, y - 9.8), (34, y - 11.4), color=LINEC, lw=1.3,
                  label="不命中/未触发 → 降级", lab_dx=14.5, fs=7.2)
    arrow(ax, (34, top - 4 * 11.6 - 9.8), (34, 15.4), color=GREEN, lw=1.3,
          label="全部级别汇合", lab_dx=11.5, fs=7.4, lab_c=GREEN)

    # 右侧注释
    ax.text(68, 68, "设计原则", fontsize=9.5, color=GREEN, fontweight="bold")
    for i, s in enumerate([
        "顺序即证据强度，\n高等级命中即短路；",
        "保守拒答：LIKE 有命中\n即推荐（宁少拒不误伤）；",
        "0.47 选点来自阈值-风险\n曲线（见 6.6 节）。",
    ]):
        ax.text(68, 62.5 - i * 8.6, s, fontsize=7.6, color=SOFT, ha="left", va="top")

    # 底部统一出口
    box(ax, 4, 3, 88, 9, "统一出口 _ok()：证据序 → 个性化重排 → LIKE 命中置顶 → 卡片截断（≤4 张）→ 口碑成句 → payload 与卡片同源",
        [], fc="#FFFFFF", ec=GREEN, tc=GREEN, fs_t=8.8, lw=1.5)

    fig.savefig(OUT / "fig5_1_evidence_chain.png", dpi=220, bbox_inches="tight",
                facecolor="white")
    plt.close(fig)


# --------------------------------------------------- 图 6-1 阈值-风险曲线
def fig6_1_threshold_curve():
    xs = [0.35, 0.40, 0.45, 0.47, 0.50, 0.55]
    leak = [60, 40, 30, 0, 0, 0]
    recall = [100, 100, 90, 90, 90, 80]

    fig, ax = plt.subplots(figsize=(8.6, 4.9))
    ax.plot(xs, leak, marker="o", lw=2.0, color="#B91C1C", label="域外泄漏率（越低越好）")
    ax.plot(xs, recall, marker="s", lw=2.0, color=GREEN, label="域内召回率（越高越好）")
    for x, y in zip(xs, leak):
        ax.annotate(f"{y:.0f}%", (x, y), textcoords="offset points", xytext=(0, 8),
                    ha="center", fontsize=8, color="#B91C1C")
    for x, y in zip(xs, recall):
        ax.annotate(f"{y:.0f}%", (x, y), textcoords="offset points", xytext=(0, 8),
                    ha="center", fontsize=8, color=GREEN)

    ax.axvline(0.47, color=INK, ls="--", lw=1.2, alpha=0.65)
    ax.annotate("对话导购采用 0.47\n泄漏 0% / 召回 90%", xy=(0.47, 50), xytext=(0.487, 62),
                fontsize=8.8, color=INK,
                arrowprops=dict(arrowstyle="->", color=INK, lw=1.0))
    ax.annotate("0.35 时荒谬泄漏：\n手机壳→带壳毛豆（0.453）\n跑步鞋→金华火腿（0.392）",
                xy=(0.35, 60), xytext=(0.356, 76), fontsize=8, color="#B91C1C",
                arrowprops=dict(arrowstyle="->", color="#B91C1C", lw=0.9))

    ax.set_xlabel("语义命中阈值 REC_MIN_SCORE", fontsize=10)
    ax.set_ylabel("比例（%）", fontsize=10)
    ax.set_ylim(-4, 108)
    ax.set_xticks(xs)
    ax.grid(axis="y", ls=":", alpha=0.45)
    ax.legend(loc="center right", fontsize=9, framealpha=0.95)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    fig.tight_layout()
    fig.savefig(OUT / "fig6_1_threshold_curve.png", dpi=220, bbox_inches="tight",
                facecolor="white")
    plt.close(fig)


# ------------------------------------------------- 图 6-2 首 token 优化轨迹
def fig6_2_latency_trajectory():
    fig, (a1, a2) = plt.subplots(1, 2, figsize=(10.6, 4.6),
                                 gridspec_kw={"width_ratios": [3, 2]})

    labels1 = ["改造前\n3 次串行 LLM", "意图+偏好合并\n2 次", "智能体架构\n2 次"]
    vals1 = [19.24, 11.13, 10.49]
    bars = a1.bar(labels1, vals1, width=0.52,
                  color=["#94A3B8", "#4EA971", GREEN])
    for b, v in zip(bars, vals1):
        a1.text(b.get_x() + b.get_width() / 2, v + 0.35, f"{v:.2f} s",
                ha="center", fontsize=9.5, color=INK, fontweight="bold")
    a1.annotate("−42%", xy=(0.5, 16.2), fontsize=10, color=GREEN, ha="center",
                fontweight="bold")
    a1.annotate("累计 −45.5%", xy=(1.62, 15.2), fontsize=9.5, color=GREEN, ha="center",
                fontweight="bold")
    a1.set_ylabel("导购分支首 token 延迟（s）", fontsize=10)
    a1.set_ylim(0, 22.5)
    a1.grid(axis="y", ls=":", alpha=0.45)
    for s in ("top", "right"):
        a1.spines[s].set_visible(False)
    a1.set_title("导购分支：同一改动链路的三轮复测", fontsize=10, color=SOFT)

    labels2 = ["无规则前置\n（多一次意图分类调用）", "规则直出\n（零 LLM 调用）"]
    vals2 = [7.32, 2.26]
    bars2 = a2.bar(labels2, vals2, width=0.42, color=["#94A3B8", GREEN])
    for b, v in zip(bars2, vals2):
        a2.text(b.get_x() + b.get_width() / 2, v + 0.18, f"{v:.2f} s",
                ha="center", fontsize=9.5, color=INK, fontweight="bold")
    a2.annotate("−69%", xy=(0.5, 5.2), fontsize=10, color=GREEN, ha="center",
                fontweight="bold")
    a2.set_ylabel("寒暄分支首 token 延迟（s）", fontsize=10)
    a2.set_ylim(0, 8.6)
    a2.grid(axis="y", ls=":", alpha=0.45)
    for s in ("top", "right"):
        a2.spines[s].set_visible(False)
    a2.set_title("寒暄分支：规则前置的价值", fontsize=10, color=SOFT)

    fig.tight_layout()
    fig.savefig(OUT / "fig6_2_latency_trajectory.png", dpi=220, bbox_inches="tight",
                facecolor="white")
    plt.close(fig)


if __name__ == "__main__":
    fig3_1_usecase()
    fig4_1_system_arch()
    fig4_2_ai_service_arch()
    fig4_3_rag_pipeline()
    fig4_4_er_diagram()
    fig5_1_evidence_chain()
    fig6_1_threshold_curve()
    fig6_2_latency_trajectory()
    print("已生成 8 张图 →", OUT)
