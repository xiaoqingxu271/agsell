import { uploadImage } from '../api/upload'

// API 地址统一从环境文件读取：改 agsell-uniapp/.env.mp-weixin 里的 VITE_API_BASE_URL 即可
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

/**
 * 小程序上传图片到 OSS
 * @param filePath 本地文件路径（chooseImage 返回）
 * @param prefix 存储路径前缀
 * @returns OSS URL
 */
export function uploadMiniImage(filePath, prefix) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}/file/upload`,
      filePath: filePath,
      name: 'file',
      formData: { prefix, fileType: 'IMAGE' },
      header: {
        ...(uni.getStorageSync('token') ? { Authorization: `Bearer ${uni.getStorageSync('token')}` } : {})
      },
      success: (res) => {
        const result = JSON.parse(res.data)
        if (result.code === 0) {
          resolve(result.data)
        } else {
          uni.showToast({ title: result.message || '上传失败', icon: 'none' })
          reject(new Error(result.message))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络异常', icon: 'none' })
        reject(err)
      }
    })
  })
}
