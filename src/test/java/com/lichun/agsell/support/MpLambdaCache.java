package com.lichun.agsell.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * 纯 Mockito 单测没有 Spring/MyBatis 启动过程，MyBatis-Plus 的实体列名缓存（lambda cache）不会被安装；
 * LambdaUpdateWrapper.set(...) 在链式调用时就会解析列名并抛 "can not find lambda cache"。
 * 这里按需初始化，避免依赖"某个 @SpringBootTest 恰好先跑过"这种脆弱的执行顺序。
 */
public final class MpLambdaCache {

    private MpLambdaCache() {
    }

    public static void init(Class<?>... entityClasses) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entityClass : entityClasses) {
            if (TableInfoHelper.getTableInfo(entityClass) == null) {
                TableInfoHelper.initTableInfo(assistant, entityClass);
            }
        }
    }
}
