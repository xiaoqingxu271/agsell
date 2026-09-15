<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  listAdmins,
  createAdmin,
  updateAdmin,
  resetAdminPassword,
  updateAdminStatus,
  deleteAdmin,
} from '@/api/admin'
import { useAdminStore } from '@/stores/admin'
import type { AdminListItemVO, AdminCreateRequest, AdminUpdateRequest } from '@/types'
import PageHeader from '@/components/admin/PageHeader.vue'

const adminStore = useAdminStore()
const loading = ref(false)
const list = ref<AdminListItemVO[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')

async function fetchList() {
  loading.value = true
  try {
    const res = await listAdmins({ pageNum: page.value, pageSize: pageSize.value, keyword: keyword.value || undefined })
    list.value = res.records
    total.value = res.total
  } catch {
    // interceptor handles error
  } finally {
    loading.value = false
  }
}

function handlePageChange(val: number) {
  page.value = val
  fetchList()
}

function handleSearch() {
  page.value = 1
  fetchList()
}

function formatTime(time?: string | null): string {
  if (!time) return '—'
  return time.replace('T', ' ').substring(0, 19)
}

function roleText(role: string): string {
  return role === 'SUPER_ADMIN' ? '超级管理员' : role === 'ADMIN' ? '运营管理员' : '运营专员'
}

function roleTagType(role: string): 'danger' | 'warning' | 'info' {
  return role === 'SUPER_ADMIN' ? 'danger' : role === 'ADMIN' ? 'warning' : 'info'
}

function isSelf(row: AdminListItemVO): boolean {
  return row.id === adminStore.adminInfo?.adminId
}

// ── 新增/编辑弹窗 ──
const dialogVisible = ref(false)
const dialogTitle = ref('新增管理员')
const formLoading = ref(false)
const form = ref<AdminCreateRequest & { id?: number }>({ username: '', password: '', realName: '', role: 'OPERATOR' })

function openAdd() {
  dialogTitle.value = '新增管理员'
  form.value = { username: '', password: '', realName: '', role: 'OPERATOR' }
  dialogVisible.value = true
}

function openEdit(row: AdminListItemVO) {
  dialogTitle.value = '编辑管理员'
  form.value = { id: row.id, username: row.username, password: '', realName: row.realName ?? '', role: row.role === 'ADMIN' ? 'ADMIN' : 'OPERATOR' }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.value.username?.trim()) {
    ElMessage.warning('请输入用户名')
    return
  }
  if (!form.value.id && !form.value.password) {
    ElMessage.warning('请输入密码')
    return
  }
  if (!form.value.id && form.value.password.length < 6) {
    ElMessage.warning('密码长度不能少于 6 位')
    return
  }
  formLoading.value = true
  try {
    if (form.value.id) {
      const payload: AdminUpdateRequest = { realName: form.value.realName, role: form.value.role }
      await updateAdmin(form.value.id, payload)
      ElMessage.success('编辑成功')
    } else {
      const payload: AdminCreateRequest = {
        username: form.value.username,
        password: form.value.password,
        realName: form.value.realName,
        role: form.value.role,
      }
      await createAdmin(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch {
    // interceptor handles error
  } finally {
    formLoading.value = false
  }
}

// ── 重置密码 ──
const pwdDialogVisible = ref(false)
const pwdTarget = ref<AdminListItemVO | null>(null)
const newPassword = ref('')
const pwdLoading = ref(false)

function openResetPwd(row: AdminListItemVO) {
  pwdTarget.value = row
  newPassword.value = ''
  pwdDialogVisible.value = true
}

async function handleResetPwd() {
  if (!pwdTarget.value) return
  if (!newPassword.value || newPassword.value.length < 6) {
    ElMessage.warning('新密码长度不能少于 6 位')
    return
  }
  pwdLoading.value = true
  try {
    await resetAdminPassword(pwdTarget.value.id, newPassword.value)
    ElMessage.success('密码已重置')
    pwdDialogVisible.value = false
  } catch {
    // interceptor handles error
  } finally {
    pwdLoading.value = false
  }
}

// ── 启停 / 删除 ──
async function handleStatusChange(row: AdminListItemVO, status: number) {
  if (isSelf(row)) {
    ElMessage.warning('不能禁用当前登录账号')
    return
  }
  try {
    await updateAdminStatus(row.id, status)
    row.status = status
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
  } catch {
    fetchList()
  }
}

async function handleDelete(row: AdminListItemVO) {
  if (isSelf(row)) {
    ElMessage.warning('不能删除当前登录账号')
    return
  }
  await ElMessageBox.confirm(`确认删除管理员「${row.username}」？删除后该账号无法登录。`, '提示', { type: 'warning' })
  try {
    await deleteAdmin(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // interceptor handles error
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page">
    <PageHeader title="管理员管理" description="RBAC 三级角色：超级管理员管理运营管理员与运营专员；系统仅保留一个超级管理员" />

    <el-card shadow="never">
      <template #header>
        <div class="admin-card-header">
          <span class="admin-card-title">管理员列表</span>
          <div class="admin-card-actions">
            <el-input
              v-model="keyword"
              placeholder="搜索用户名/姓名"
              clearable
              style="width: 220px"
              @keyup.enter="handleSearch"
              @clear="handleSearch"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="openAdd()">新增管理员</el-button>
          </div>
        </div>
      </template>

      <el-table class="admin-table" :data="list" v-loading="loading" stripe :border="false" style="width: 100%">
        <el-table-column prop="id" label="ID" width="190" align="center" show-overflow-tooltip />
        <el-table-column prop="username" label="用户名" min-width="110" align="center" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="100" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ row.realName || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :disabled="isSelf(row as AdminListItemVO)"
              active-text="启用"
              inactive-text="禁用"
              inline-prompt
              @change="(val: unknown) => handleStatusChange(row as AdminListItemVO, val ? 1 : 0)"
            />
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="160" align="center" show-overflow-tooltip>
          <template #default="{ row }">{{ formatTime(row.loginTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" :disabled="isSelf(row as AdminListItemVO)" @click="openEdit(row as AdminListItemVO)">编辑</el-button>
            <el-button type="warning" link size="small" :disabled="isSelf(row as AdminListItemVO)" @click="openResetPwd(row as AdminListItemVO)">重置密码</el-button>
            <el-button type="danger" link size="small" :disabled="isSelf(row as AdminListItemVO)" @click="handleDelete(row as AdminListItemVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="admin-pagination">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="440px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="登录用户名" maxlength="32" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" maxlength="32" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" placeholder="真实姓名（可选）" maxlength="50" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="运营管理员" value="ADMIN" />
            <el-option label="运营专员" value="OPERATOR" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formLoading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="pwdDialogVisible" title="重置密码" width="400px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="账号">
          <span>{{ pwdTarget?.username }}</span>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password placeholder="至少 6 位" maxlength="32" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="handleResetPwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>
