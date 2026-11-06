package com.lichun.agsell.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * AI 运行参数条目（GET /admin/ai/config 单行）。
 *
 * value/default 是混合类型（布尔/整数/浮点由 Python 按 type 下发），用 Object 承接，
 * 前端按 type 渲染对应控件（switch / input-number）。overridden=有自定义覆盖记录，
 * 与"值是否恰好等于默认"无关——覆盖成默认值也应显示已自定义，便于运营清理。
 */
@Data
public class AiConfigItemVO {

    private String key;

    private String description;

    /** bool / int / float */
    private String type;

    private Object value;

    /**
     * Python 下发的键名是 "default"（Java 关键字不能做字段名），
     * 不用 @JsonProperty 重命名的话 Jackson 输出 "defaultValue"，
     * 前端读 item.default 得 undefined——默认值列空白、"恢复默认"必坏
     */
    @JsonProperty("default")
    private Object defaultValue;

    private boolean overridden;

    private Object min;

    private Object max;
}
