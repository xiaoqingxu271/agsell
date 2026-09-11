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
     * 上传字节数据到 OSS（服务端生成的图片等，如溯源二维码 PNG）
     * objectKey 由调用方指定，重复上传同一 objectKey 会覆盖（幂等）
     *
     * @param data        图片字节
     * @param objectKey   存储对象路径，如 trace/qr/B202609110001.png
     * @param contentType 内容类型，如 image/png
     * @return 文件访问 URL
     */
    String uploadBytes(byte[] data, String objectKey, String contentType);

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
