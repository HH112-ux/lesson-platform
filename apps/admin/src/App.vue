<script setup lang="ts">
/**
 * Root shell of the operations console.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { computed, onMounted } from 'vue'
import { accessToken, clearSession } from './api/client'
import { currentRoute, currentUser, navigate, syncRoute } from './router'
import DashboardView from './views/DashboardView.vue'
import LessonVideoView from './views/LessonVideoView.vue'
import LoginView from './views/LoginView.vue'
import OrdersView from './views/OrdersView.vue'
import RefundQueueView from './views/RefundQueueView.vue'
import ReviewQueueView from './views/ReviewQueueView.vue'
import UsersView from './views/UsersView.vue'

const signedIn = computed(() => Boolean(accessToken.value) && Boolean(currentUser.value))

const showLogin = computed(() => !signedIn.value || currentRoute.value.name === 'login')

/** Teacher entry only: the menu hides it for other roles, the backend still checks ownership. */
const isTeacher = computed(() => (currentUser.value?.roles ?? []).includes('TEACHER'))

function signOut(): void {
  clearSession()
  currentUser.value = null
  navigate('/login', true)
}

onMounted(() => {
  window.addEventListener('popstate', syncRoute)
})
</script>

<template>
  <LoginView v-if="showLogin" />
  <div
    v-else
    class="shell"
  >
    <aside class="sidebar">
      <div class="brand">
        课程平台 · 运营后台
      </div>
      <nav>
        <a
          href="/"
          :class="{ active: currentRoute.name === 'overview' }"
        >数据看板</a>
        <a
          href="/courses"
          :class="{ active: currentRoute.name === 'review' }"
        >课程审核</a>
        <a
          href="/users"
          :class="{ active: currentRoute.name === 'users' }"
        >用户管理</a>
        <a
          href="/orders"
          :class="{ active: currentRoute.name === 'orders' }"
        >订单管理</a>
        <a
          href="/refunds"
          :class="{ active: currentRoute.name === 'refunds' }"
        >退款审核</a>
        <a
          v-if="isTeacher"
          href="/lesson-videos"
          :class="{ active: currentRoute.name === 'lesson-video' }"
        >课时视频</a>
      </nav>
      <div class="account">
        <span>{{ currentUser?.nickname }}</span>
        <button
          class="lp-btn logout"
          @click="signOut"
        >
          退出登录
        </button>
      </div>
    </aside>

    <main class="content">
      <DashboardView v-if="currentRoute.name === 'overview'" />
      <ReviewQueueView v-else-if="currentRoute.name === 'review'" />
      <UsersView v-else-if="currentRoute.name === 'users'" />
      <OrdersView v-else-if="currentRoute.name === 'orders'" />
      <RefundQueueView v-else-if="currentRoute.name === 'refunds'" />
      <LessonVideoView v-else-if="currentRoute.name === 'lesson-video'" />
      <p
        v-else
        class="lp-empty"
      >
        页面不存在，<a href="/">返回数据看板</a>
      </p>
    </main>
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  min-height: 100vh;
}

.sidebar {
  display: flex;
  flex-direction: column;
  width: 220px;
  padding: 20px 16px;
  background: #1f2329;
  color: #ffffff;
}

.brand {
  margin-bottom: 24px;
  font-size: 15px;
  font-weight: 600;
}

nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

nav a {
  padding: 10px 12px;
  border-radius: 6px;
  color: #c9cdd4;
  font-size: 14px;
}

nav a:hover,
nav a.active {
  background: #2b3038;
  color: #ffffff;
}

.account {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid #363b43;
  font-size: 13px;
}

.logout {
  height: 30px;
  border-color: #4a4f57;
  background: transparent;
  color: #c9cdd4;
  font-size: 13px;
}

.content {
  flex: 1;
  padding: 28px 32px;
  min-width: 0;
}
</style>
