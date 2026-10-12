package com.lichun.agsell.controller.admin;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.service.FileUploadService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端文件接口。
 * <p>必须挂在 /admin/ 前缀下：JwtAuthInterceptor 以 uri.startsWith("/api/admin/") 判定
 * 管理端接口并要求 admin token，放在 /file 下会被当作用户端接口放行。
 */
@Tag(name = "管理端-文件管理", description = "删除 OSS 上的素材文件")
@RestController
@RequestMapping("/admin/file")
@RequiredArgsConstructor
public class AdminFileController {

    private final FileUploadService fileUploadService;

    @Operation(summary = "删除文件", description = "根据文件 URL 删除 OSS 上的对应文件，仅管理员可调用")
    @DeleteMapping("/delete")
    public BaseResponse<Void> delete(
            @Parameter(description = "文件的完整 URL", required = true)
            @RequestParam("fileUrl") String fileUrl) {
        fileUploadService.delete(fileUrl);
        return ResultUtils.success(null);
    }
}
