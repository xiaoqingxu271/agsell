<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listConfigs, updateConfigs } from '@/api/admin'
import type { ConfigItemVO } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const loading = ref(false)
const saving = ref(false)
const configs = ref<ConfigItemVO[]>([])
const platformName = ref('')
const servicePhone = ref('')
const defaultFreight = ref(0)
const freeShippingThreshold = ref(0)

async function fetchConfigs() {
  loading.value = true
  try {
    const res = await listConfigs()
    configs.value = res
    for (const item of res) {
      if (item.configKey === 'platform_name') platformName.value = item.configValue
      else if (item.configKey === 'service_phone') servicePhone.value = item.configValue
      else if (item.configKey === 'default_freight') defaultFreight.value = Number(item.configValue) || 0
      else if (item.configKey === 'free_shipping_threshold') freeShippingThreshold.value = Number(item.configValue) || 0
    }
  } catch {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateConfigs({
      items: [
        { configKey: 'platform_name', configValue: platformName.value },
        { configKey: 'service_phone', configValue: servicePhone.value },
        { configKey: 'default_freight', configValue: String(defaultFreight.value) },
        { configKey: 'free_shipping_threshold', configValue: String(freeShippingThreshold.value) },
      ],
    })
    ElMessage.success('保存成功')
    fetchConfigs()
  } catch {
    // interceptor handles error
  } finally {
    saving.value = false
  }
}

onMounted(fetchConfigs)
</script>

<template>
  <div class="page">
    <PageHeader title="系统配置" description="平台基础参数：配置项作用于小程序端展示与订单运费计算（满额包邮）" />

    <el-card shadow="never" v-loading="loading" style="max-width: 720px">
      <template #header>
        <span class="admin-card-title">基础参数</span>
      </template>

      <el-form label-width="120px" style="max-width: 560px">
        <el-form-item label="平台名称">
          <el-input v-model="platformName" placeholder="如：绿野农产品优选" maxlength="50" />
          <div class="config-tip">小程序首页与关于页展示的平台名称</div>
        </el-form-item>
        <el-form-item label="客服电话">
          <el-input v-model="servicePhone" placeholder="如：400-888-6666" maxlength="30" />
          <div class="config-tip">小程序"联系客服"展示的电话号码</div>
        </el-form-item>
        <el-form-item label="默认运费（元）">
          <el-input-number v-model="defaultFreight" :min="0" :max="999" :precision="2" style="width: 200px" />
          <div class="config-tip">未达满额包邮门槛时，每单收取的基础运费</div>
        </el-form-item>
        <el-form-item label="满额包邮阈值（元）">
          <el-input-number v-model="freeShippingThreshold" :min="0" :max="999999" :precision="2" style="width: 200px" />
          <div class="config-tip">订单金额达到该阈值时免运费；填 0 表示不启用满额包邮</div>
        </el-form-item>
      </el-form>

      <div class="config-actions">
        <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.config-tip {
  font-size: 12px;
  color: #8a9499;
  line-height: 1.5;
  margin-top: 4px;
  width: 100%;
}
.config-actions {
  padding-left: 120px;
}
</style>
