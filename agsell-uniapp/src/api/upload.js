import { request } from '../utils/request'

/**
 * 上传图片到 OSS
 * @param file 本地文件
 * @param prefix 存储路径前缀，如 user/avatar、product/image
 * @returns 文件访问 URL
 */
export function uploadImage(file, prefix) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('prefix', prefix)
  formData.append('fileType', 'IMAGE')
  return request({
    url: '/file/upload',
    method: 'POST',
    data: formData,
    header: {
      'Content-Type': 'multipart/form-data',
      ...(uni.getStorageSync('token') ? { Authorization: `Bearer ${uni.getStorageSync('token')}` } : {})
    }
  })
}
