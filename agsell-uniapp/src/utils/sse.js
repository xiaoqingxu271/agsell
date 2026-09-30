// SSE 流式请求封装（AI 客服逐字回复）
//
// 传输层双实现：
// - H5：fetch + ReadableStream 逐块读取
// - 微信小程序：uni.request enableChunked + task.onChunkReceived
//
// 协议约定（与 Java /ai/chat/stream、Python /v1/chat/stream 一致）：
//   event: status  data: {"stage":"order_query","intent":"order"}   业务阶段提示
//   event: token   data: {"content":"你"}                            逐字回复
//   event: done    data: {"suggestions":[...],"sessionId":"..."}     正常收尾
//   event: error   data: {"message":"..."}                           上游异常
//
// Promise 语义：SSE 流正常结束（含收到 error 事件后收尾）resolve；
// 传输失败 / 服务端返回非 SSE 响应（如 401 业务错误 JSON）reject(err)。
import { BASE_URL } from './request'

/**
 * 增量 UTF-8 解码器：处理多字节字符被网络分包截断的情况。
 * 微信小程序基础库无全局 TextDecoder 时用纯 JS 状态机兜底。
 */
class Utf8StreamDecoder {
  constructor() {
    this.pending = [] // 上一包尾部的未完成字节序列
  }

  decode(bytes) {
    const input = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
    const buf = this.pending.concat(Array.from(input))
    let out = ''
    let i = 0
    while (i < buf.length) {
      const b = buf[i]
      let need = 0
      let cp = 0
      if (b < 0x80) {
        need = 1
        cp = b
      } else if ((b & 0xe0) === 0xc0) {
        need = 2
        cp = b & 0x1f
      } else if ((b & 0xf0) === 0xe0) {
        need = 3
        cp = b & 0x0f
      } else if ((b & 0xf8) === 0xf0) {
        need = 4
        cp = b & 0x07
      } else {
        i += 1 // 非法起始字节，跳过
        continue
      }
      if (i + need > buf.length) break // 序列不完整，留给下一包
      let valid = true
      for (let k = 1; k < need; k++) {
        if ((buf[i + k] & 0xc0) !== 0x80) {
          valid = false
          break
        }
        cp = (cp << 6) | (buf[i + k] & 0x3f)
      }
      if (!valid) {
        i += 1
        continue
      }
      i += need
      if (need === 4) {
        // 4 字节序列超出 BMP，转 UTF-16 代理对（emoji 等）
        cp -= 0x10000
        out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff))
      } else {
        out += String.fromCharCode(cp)
      }
    }
    this.pending = buf.slice(i)
    return out
  }
}

function makeDecoder() {
  if (typeof TextDecoder !== 'undefined') {
    const d = new TextDecoder('utf-8')
    return { decode: (bytes) => d.decode(bytes, { stream: true }) }
  }
  return new Utf8StreamDecoder()
}

/** 解析一条 SSE 事件块（不含结尾空行），data 按 JSON 解析失败时降级为 { _raw } */
function parseBlock(block, onEvent) {
  let event = 'message'
  const dataLines = []
  block.split('\n').forEach((line) => {
    if (line.indexOf('event:') === 0) event = line.slice(6).trim()
    else if (line.indexOf('data:') === 0) dataLines.push(line.slice(5).replace(/^ /, ''))
  })
  if (!dataLines.length) return
  let data
  try {
    data = JSON.parse(dataLines.join('\n'))
  } catch (e) {
    data = { _raw: dataLines.join('\n') }
  }
  onEvent(event, data)
}

/** SSE 流解析器：按空行（\n\n）切分事件块，跨包缓存不完整块 */
function createSseParser(onEvent) {
  let buffer = ''
  return {
    feed(text) {
      buffer += text
      let idx
      while ((idx = buffer.indexOf('\n\n')) >= 0) {
        const block = buffer.slice(0, idx)
        buffer = buffer.slice(idx + 2)
        if (block.trim()) parseBlock(block, onEvent)
      }
    },
    end() {
      if (buffer.trim()) parseBlock(buffer, onEvent)
      buffer = ''
    }
  }
}

/**
 * 发起 SSE 流式 POST 请求
 * @param {Object} opts
 * @param {string} opts.url       API 路径（相对 BASE_URL，如 /ai/chat/stream）
 * @param {Object} opts.data      请求体（JSON）
 * @param {Object} [opts.header]  额外请求头（如 Authorization）
 * @param {number} [opts.timeout] 超时毫秒（小程序端生效）
 * @param {Function} opts.onEvent (event, data) => void  每个 SSE 事件回调
 * @returns {Promise<void>} 流结束时 resolve；传输/业务失败 reject
 */
export function sseRequest({ url, data, header = {}, timeout = 120000, onEvent }) {
  return new Promise((resolve, reject) => {
    let gotAny = false
    const parser = createSseParser((event, payload) => {
      gotAny = true
      onEvent && onEvent(event, payload)
    })
    const decoder = makeDecoder()
    const fail = (err) => reject(err || new Error('网络异常'))
    // 正常收尾：若整条流没收到任何事件，按网络异常处理（避免调用方无限等待）
    const finish = () => {
      if (gotAny) resolve()
      else fail(new Error('AI 服务未返回内容'))
    }

    // #ifdef H5
    ;(async () => {
      try {
        const resp = await fetch(BASE_URL + url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...header },
          body: JSON.stringify(data)
        })
        const ct = resp.headers.get('content-type') || ''
        if (ct.indexOf('text/event-stream') === -1) {
          // 非流式响应：多为全局异常处理器返回的业务错误 JSON（如 token 失效 40100）
          const body = await resp.text()
          let parsed = null
          try {
            parsed = JSON.parse(body)
          } catch (e) {
            /* 非 JSON */
          }
          const err = new Error((parsed && parsed.message) || `HTTP ${resp.status}`)
          err.code = parsed && parsed.code
          fail(err)
          return
        }
        const reader = resp.body.getReader()
        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          parser.feed(decoder.decode(value))
        }
        parser.end()
        finish()
      } catch (e) {
        fail(e)
      }
    })()
    // #endif

    // #ifdef MP-WEIXIN
    const task = uni.request({
      url: BASE_URL + url,
      method: 'POST',
      data,
      header: { 'Content-Type': 'application/json', ...header },
      enableChunked: true,
      timeout,
      success: (res) => {
        parser.end()
        // 未收到任何分片且返回了 JSON 体：业务错误（如 token 失效）
        if (!gotAny && res && res.data && typeof res.data === 'object' && res.data.code !== undefined) {
          const err = new Error(res.data.message || '请求失败')
          err.code = res.data.code
          fail(err)
          return
        }
        finish()
      },
      fail: (err) => fail(new Error(err && err.errMsg ? err.errMsg : '网络异常'))
    })
    if (task && task.onChunkReceived) {
      task.onChunkReceived((res) => {
        const bytes = res.chunk || res.data
        if (!bytes) return
        parser.feed(decoder.decode(bytes))
      })
    } else {
      // 基础库过低不支持分片：流式不可用，直接失败走降级提示
      fail(new Error('当前环境不支持流式响应'))
    }
    // #endif
  })
}

export default { sseRequest }
