import { uploadImage } from '../api/upload'

/**
 * 小程序上传图片到 OSS
 * @param filePath 本地文件路径（chooseImage 返回）
 * @param prefix 存储路径前缀
 * @returns OSS URL
 */
export function uploadMiniImage(filePath, prefix) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: 'http://localhost:8080/api/file/upload',
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
