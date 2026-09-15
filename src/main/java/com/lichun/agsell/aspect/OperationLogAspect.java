package com.lichun.agsell.aspect;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lichun.agsell.annotation.OperationLog;
import com.lichun.agsell.common.AdminContext;
import com.lichun.agsell.mapper.SysAdminMapper;
import com.lichun.agsell.mapper.SysLogMapper;
import com.lichun.agsell.model.entity.SysAdmin;
import com.lichun.agsell.model.entity.SysLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 操作日志切面：标注 @OperationLog 的接口执行成功后，异步写入 sys_log
 * 失败仅 warn，不影响主业务
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SysLogMapper sysLogMapper;
    private final SysAdminMapper sysAdminMapper;

    /** 简易线程池：日志写入与主流程解耦 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        Object result = pjp.proceed(); // 业务执行成功后才记录
        try {
            Long adminId = AdminContext.getCurrentAdminId();
            if (adminId != null) {
                executor.submit(() -> writeLog(pjp, operationLog, adminId));
            }
        } catch (Exception e) {
            log.warn("[OperationLog] 操作日志写入失败: {}", e.getMessage());
        }
        return result;
    }

    private void writeLog(ProceedingJoinPoint pjp, OperationLog op, Long adminId) {
        try {
            SysLog log = new SysLog();
            log.setAdminId(adminId);
            log.setAdminName(resolveAdminName(adminId));
            log.setModule(op.module());
            log.setAction(op.action());
            log.setContent(buildContent(pjp));
            log.setIp(resolveIp());
            sysLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("[OperationLog] 操作日志落库失败: {}", e.getMessage());
        }
    }

    /** 操作人姓名：按 adminId 查询（演示规模一次查询开销可忽略） */
    private String resolveAdminName(Long adminId) {
        try {
            SysAdmin admin = sysAdminMapper.selectById(adminId);
            if (admin != null) {
                return admin.getRealName() != null ? admin.getRealName() : admin.getUsername();
            }
        } catch (Exception ignored) {
        }
        return String.valueOf(adminId);
    }

    /** 操作内容：截取方法参数（脱敏：跳过 password 类字段），最多 200 字符 */
    private String buildContent(ProceedingJoinPoint pjp) {
        String content = Arrays.stream(pjp.getArgs())
                .filter(arg -> arg != null && !isSensitive(arg))
                .map(String::valueOf)
                .collect(Collectors.joining(" | "));
        return content.length() > 200 ? content.substring(0, 200) : content;
    }

    private boolean isSensitive(Object arg) {
        String s = String.valueOf(arg).toLowerCase();
        return s.contains("password") || s.contains("token");
    }

    private String resolveIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Real-IP");
                if (ip == null || ip.isBlank()) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
