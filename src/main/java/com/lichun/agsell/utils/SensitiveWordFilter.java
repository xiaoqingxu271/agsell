package com.lichun.agsell.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 敏感词过滤（P2 §5.7）：评价文本提交前把命中词替换为等长 * 号。
 *
 * 词库两级：内置兜底词库（涉政/辱骂的最小集合）+ 系统配置 `sensitive_words`
 * （逗号分隔，管理端「系统配置」页可随时增删，改完即生效——每次过滤实时读配置）。
 * 设计为"替换"而非"拒绝"：不打断用户评价动作，脏词不上屏即可。
 */
@Component
public class SensitiveWordFilter {

    /** 内置兜底词库：即使系统配置缺失/为空也保证有基本过滤能力 */
    private static final List<String> BUILTIN_WORDS = List.of(
            "赌博", "诈骗", "毒品", "枪支", "代开发票", "刷单"
    );

    private final com.lichun.agsell.service.SysConfigService sysConfigService;

    @Value("${review.sensitive-words:}")
    private String extraWords;

    public SensitiveWordFilter(com.lichun.agsell.service.SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 过滤文本：命中词替换为等长 * 号。text 为空时原样返回。
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        for (String word : collectWords()) {
            if (word.length() > 1 && result.contains(word)) {
                result = result.replace(word, "*".repeat(word.length()));
            }
        }
        return result;
    }

    /** 词库 = 内置兜底 + sys_config sensitive_words + 配置文件附加词，去重去空 */
    private List<String> collectWords() {
        String configured = sysConfigService.getConfigOrDefault("sensitive_words", "");
        return Arrays.asList(
                (String.join(",", BUILTIN_WORDS) + "," + configured + "," + (extraWords == null ? "" : extraWords))
                        .split(",")
        ).stream()
                .map(String::trim)
                .filter(w -> w.length() > 1)
                .distinct()
                .toList();
    }
}
