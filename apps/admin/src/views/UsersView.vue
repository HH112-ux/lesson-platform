<script setup lang="ts">
/**
 * User lookup screen of the operations console.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { onMounted, ref } from 'vue'
import { listUsers, type AdminUserView } from '../api/admin'
import { describeError } from '../utils/errors'
import { formatTime } from '../utils/format'

const PAGE_SIZE = 20

const keyword = ref('')
const records = ref<AdminUserView[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const errorText = ref('')

async function load(): Promise<void> {
  loading.value = true
  errorText.value = ''
  try {
    const page = await listUsers(keyword.value, pageNum.value, PAGE_SIZE)
    records.value = page.records
    total.value = page.total
  } catch (error) {
    records.value = []
    total.value = 0
    errorText.value = describeError(error, '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function changePage(next: number): void {
  if (next < 1) {
    return
  }
  pageNum.value = next
  void load()
}

onMounted(load)
</script>

<template>
  <section>
    <header class="page-head">
      <h1>用户管理</h1>
      <form
        class="search"
        @submit.prevent="search"
      >
        <input
          v-model="keyword"
          class="lp-input"
          placeholder="用户名 / 昵称 / 手机号"
        >
        <button
          class="lp-btn lp-btn--primary"
          type="submit"
        >
          查询
        </button>
      </form>
    </header>

    <div class="lp-card table-card">
      <p
        v-if="loading"
        class="lp-empty"
      >
        加载中…
      </p>
      <p
        v-else-if="errorText"
        class="lp-empty"
      >
        {{ errorText }}
      </p>
      <p
        v-else-if="records.length === 0"
        class="lp-empty"
      >
        没有匹配的用户
      </p>
      <table
        v-else
        class="lp-table"
      >
        <thead>
          <tr>
            <th>用户</th>
            <th>手机号</th>
            <th>状态</th>
            <th>注册时间</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="user in records"
            :key="user.userId"
          >
            <td>
              <strong>{{ user.nickname }}</strong>
              <div class="lp-muted small">
                {{ user.username }} · #{{ user.userId }}
              </div>
            </td>
            <td>{{ user.phone || '--' }}</td>
            <td>{{ user.status === 1 ? '正常' : '已禁用' }}</td>
            <td>{{ formatTime(user.createdAt) }}</td>
          </tr>
        </tbody>
      </table>

      <footer
        v-if="!loading && !errorText && total > PAGE_SIZE"
        class="pager"
      >
        <button
          class="lp-btn"
          :disabled="pageNum <= 1"
          @click="changePage(pageNum - 1)"
        >
          上一页
        </button>
        <span class="lp-muted">第 {{ pageNum }} 页 / 共 {{ Math.ceil(total / PAGE_SIZE) }} 页</span>
        <button
          class="lp-btn"
          :disabled="pageNum * PAGE_SIZE >= total"
          @click="changePage(pageNum + 1)"
        >
          下一页
        </button>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

h1 {
  margin: 0;
  font-size: 20px;
}

.search {
  display: flex;
  gap: 8px;
}

.search .lp-input {
  width: 240px;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.small {
  font-size: 12px;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
}
</style>
