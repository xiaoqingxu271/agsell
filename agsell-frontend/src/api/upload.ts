import service from '@/utils/request'

/**
 * 上传文件到 OSS（用于 Element Plus admin 端）
 * @param {File} file
 * @param {string} prefix 如 user/avatar、product/image
 * @returns {string} 文件 URL
 */
export async function uploadFile(file: File, prefix: string): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('prefix', prefix)
  formData.append('fileType', 'IMAGE')
  const res = await service.post('/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return res
}

/**
 * 上传多张图片，返回 URL 数组
 */
export async function uploadFiles(files: File[], prefix: string): Promise<string[]> {
  const results = await Promise.all(
    files.map(f => uploadFile(f, prefix))
  )
  return results
}
