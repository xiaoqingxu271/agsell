import service from '@/utils/request'

/**
 * 上传单张图片到 OSS（管理端表单使用）
 * @param {File} file
 * @param {string} prefix 如 user/avatar、product/image
 * @returns {string} 文件 URL
 */
export async function uploadFile(file: File, prefix: string): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('prefix', prefix)
  formData.append('fileType', 'IMAGE')
  const res = await service.post<string>('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res
}
