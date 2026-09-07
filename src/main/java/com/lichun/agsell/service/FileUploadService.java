package com.lichun.agsell.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传服务
 * 所有需要上传图片、视频等文件的业务均通过此接口上传
 */
public interface FileUploadService {

    /**
     * 上传文件到 OSS
     *
     * @param file     待上传文件
     * @param prefix   存储目录前缀，如 user/avatar、product/image、banner
     * @param fileType 文件类型：IMAGE（图片）/ VIDEO（视频）/ OTHER（其他）
     * @return 文件访问 URL
     */
    String upload(MultipartFile file, String prefix, FileType fileType);

    /**
     * 文件类型枚举
     */
    enum FileType {
        IMAGE, VIDEO, OTHER
    }

    /**
     * 删除 OSS 上的文件
     *
     * @param fileUrl 文件的完整 URL
     * @return 是否删除成功
     */
    boolean delete(String fileUrl);
}
