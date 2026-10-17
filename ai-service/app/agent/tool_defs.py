"""智能体工具目录（function schema）

与旧架构的区别：旧图是"意图 argmax → 人工写死的单分支 → 调一个工具一次 → 生成"，
选择权在 if-else 里；现在把 6 个能力注册成 function schema，交给大模型自己规划调用，
因此天然支持「一句话多个诉求并行取证」（实测 Agnes 一轮内并行发出多个 tool_calls）
与「空结果反思改写」（有界循环）。

注意：本文件只描述"能调什么"，不含任何把关逻辑。已实测生效的闸门一律留在
app/agent/executors.py 里（知识库阈值拒答、导购 REC_MIN_SCORE=0.47、域外拒答出口、
LIKE 命中优先于域判定、商详 product_qa 兜底）。架构改造只动"谁来决定调用什么"，
不动"调用之后怎么把关"，否则量化实验已验证的结论会随重构失效。
"""

# OpenAI/兼容网关的 function schema 格式（实测 bind_tools 接受该结构并回传 tool_calls）
TOOL_SCHEMAS: list[dict] = [
    {
        "type": "function",
        "function": {
            "name": "kb_search",
            "description": (
                "检索平台知识库：运费、退换货政策、发货时限、支付方式、优惠券用法、秒杀玩法、"
                "溯源二维码、平台规则与常识类问题。用户在问某一件具体商品怎么样、适不适合、"
                "能不能放时不要用本工具，应走 product_search。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "question": {
                        "type": "string",
                        "description": "要检索的问题，写成完整具体的中文表述，避免这个/它等指代词",
                    }
                },
                "required": ["question"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "product_search",
            "description": (
                "为用户挑选或推荐在售农产品，或回答用户正在浏览的这件商品相关的问题，"
                "返回候选商品（名称/价格/产地/销量/新鲜度/口碑）。"
                "有没有X、推荐点X、X和Y哪个好、这个适合送礼吗这类问法使用本工具。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "语义召回用的检索句：把用户真实需求补全成一句完整的话，"
                                       "例如用户说便宜点的，结合上文写成 50 元以内的柑橘",
                    },
                    "keyword": {
                        "type": "string",
                        "description": "品类词或商品名，如 水果、大米、脐橙。用户点名要某类商品时务必填写；"
                                       "只在问眼前这件商品（这个/这款/它）或说不清品类时省略",
                    },
                    "maxPrice": {
                        "type": "number",
                        "description": "价格上限（元），用户没提预算就省略",
                    },
                    "inDomain": {
                        "type": "boolean",
                        "description": "用户想买的东西是否属于本平台经营范围：水果、粮油、茶叶、零食、"
                                       "生鲜、滋补特产等为 true；电影、手机、衣服、跑步鞋、书、化妆品、"
                                       "写诗、股票等本平台不卖或不属于购物诉求的一律 false。"
                                       "必须给出判断，不能省略——省略等于放弃拒答的机会",
                    },
                },
                # inDomain 设为必填：实测模型省略它时，"没有判定不拒答"的安全阀会让域外请求
                # 落到热销兜底并端出卡片（探针「帮我写一首关于秋天的诗」泄漏 1 张卡）
                "required": ["query", "inDomain"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "order_query",
            "description": "查询用户自己的订单状态、订单列表或购买记录。需要用户已登录。",
            "parameters": {
                "type": "object",
                "properties": {
                    "orderNo": {"type": "string", "description": "订单号，如 AGS202610050017，没有就省略"},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "logistics_query",
            "description": "查询发货与物流配送进度：快递到哪了、什么时候送到、签收情况。需要用户已登录。",
            "parameters": {
                "type": "object",
                "properties": {
                    "orderNo": {"type": "string", "description": "订单号，省略则取最近一笔订单"},
                },
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "after_sales_query",
            "description": "查询退款、换货、理赔等售后申请的处理进度。注意：问退换货政策与规则应走 kb_search。",
            "parameters": {"type": "object", "properties": {}},
        },
    },
    {
        "type": "function",
        "function": {
            "name": "escalate_human",
            "description": "转人工：用户明确投诉、举报、情绪激烈、要求真人处理，或问题超出系统能力必须人工跟进时调用。",
            "parameters": {
                "type": "object",
                "properties": {
                    "reason": {"type": "string", "description": "转人工原因，用于工单与服务端日志"},
                },
                "required": ["reason"],
            },
        },
    },
]

TOOL_NAMES = tuple(s["function"]["name"] for s in TOOL_SCHEMAS)
