package com.lichun.agsell.controller;

import com.lichun.agsell.common.BaseResponse;
import com.lichun.agsell.service.FileUploadService;
import com.lichun.agsell.utils.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件上传接口", description = "通用文件上传，支持图片、视频等静态资源")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @Operation(summary = "上传文件", description = "支持上传头像、商品图片、轮播图等各类静态资源")
    @PostMapping("/upload")
    public BaseResponse<String> upload(
            @Parameter(description = "上传的文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "存储目录前缀，如 user/avatar、product/image、banner", required = true)
            @RequestParam("prefix") String prefix,
            @Parameter(description = "文件类型：IMAGE=图片 VIDEO=视频 OTHER=其他", required = true)
            @RequestParam("fileType") FileUploadService.FileType fileType) {
        String fileUrl = fileUploadService.upload(file, prefix, fileType);
        return ResultUtils.success(fileUrl);
    }

    @Operation(summary = "删除文件", description = "根据文件 URL 删除 OSS 上的对应文件")
    @DeleteMapping("/delete")
    public BaseResponse<Void> delete(
            @Parameter(description = "文件的完整 URL", required = true)
            @RequestParam("fileUrl") String fileUrl) {
        fileUploadService.delete(fileUrl);
        return ResultUtils.success(null);
    }
}
