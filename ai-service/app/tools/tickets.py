"""转人工工单：escalate_human 触发后回调 Java 落一张 ai_ticket

两条链路都要挂：
- /v1/chat 同步路径：图结果里直接有 human_fallback 与 observations；
- /v1/chat/stream 流式路径：updates 模式逐节点收集（human_fallback 节点 + agent 节点的观察）。

落库是尽力而为：丢给工作线程跑，失败只记日志——工单丢了不该拖住已经生成的回复。
"""
import asyncio
import logging

from app.tools import java_api

logger = logging.getLogger("agsell.ai.tickets")


def ticket_payload_from_state(
    question: str,
    user_id: int | None,
    session_id: str,
    observations: list,
    human_flag: bool,
) -> dict | None:
    """从本轮图状态提取工单内容；本轮没有转人工返回 None。

    reason 取 escalate_human 工具的入参（模型给出的原因）；规则前置短路路径没有观察，
    用中性文案兜底——比留空更利于管理端分诊。
    """
    if not human_flag:
        return None
    reason = ""
    for o in observations or []:
        if isinstance(o, dict) and o.get("kind") == "human":
            args = o.get("args") or {}
            reason = str(args.get("reason") or "").strip()
            if reason:
                break
    return {
        "userId": user_id,
        "sessionId": session_id,
        "question": (question or "").strip()[:500],
        "reason": (reason or "用户明确要求人工跟进").strip()[:255],
    }


def fire(payload: dict | None) -> None:
    """尽力落一张工单（同步阻塞，几毫秒级本机调用）：失败只记日志。"""
    if not payload:
        return
    try:
        ok = java_api.create_human_ticket(
            payload.get("userId"), payload.get("sessionId") or "",
            payload.get("question") or "", payload.get("reason") or "",
        )
        if ok:
            logger.info("转人工工单已落库：userId=%s sessionId=%s",
                        payload.get("userId"), payload.get("sessionId"))
    except Exception:
        logger.exception("转人工工单落库异常（忽略）")


def fire_async(payload: dict | None) -> None:
    """事件循环入口：丢给工作线程执行，不阻塞流式生成器收尾；无循环时退化为同步。"""
    if not payload:
        return
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        fire(payload)
        return
    loop.create_task(asyncio.to_thread(fire, payload))
