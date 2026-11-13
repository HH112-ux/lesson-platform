<script setup lang="ts">
/**
 * Root shell of the operations console.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { computed, onMounted } from 'vue'
import { accessToken, clearSession } from './api/client'
import {
  canAccess,
  currentRoute,
  currentUser,
  defaultRouteFor,
  navigate,
  syncRoute,
  visibleMenu,
} from './router'
import DashboardView from './views/DashboardView.vue'
import LessonVideoView from './views/LessonVideoView.vue'
import LoginView from './views/LoginView.vue'
import OrdersView from './views/OrdersView.vue'
import RefundQueueView from './views/RefundQueueView.vue'
import ReviewQueueView from './views/ReviewQueueView.vue'
import TeacherAnalyticsView from './views/TeacherAnalyticsView.vue'
import TeacherCoursesView from './views/TeacherCoursesView.vue'
import UsersView from './views/UsersView.vue'

const signedIn = computed(() => Boolean(accessToken.value) && Boolean(currentUser.value))

const showLogin = computed(() => !signedIn.value)

const roles = computed(() => currentUser.value?.roles ?? [])

const menu = computed(() => visibleMenu(roles.value))

const renderable = computed(() => canAccess(currentRoute.value, roles.value))

/**
 * Redirects signed-in users away from screens their roles do not open.
 *
 * <p>Disallowed routes are never rendered, so no request reaches the backend before the redirect.
 * This is convenience only: the backend keeps its own role and ownership checks.</p>
 */
function guard(): void {
  syncRoute()
  if (!signedIn.value) {
    return
  }
  const route = currentRoute.value
  if (route.name === 'login' || !canAccess(route, roles.value)) {
    navigate(defaultRouteFor(roles.value), true)
  }
}

function signOut(): void {
  clearSession()
  currentUser.value = null
  navigate('/login', true)
}

onMounted(() => {
  guard()
  window.addEventListener('popstate', guard)
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
          v-for="item in menu"
          :key="item.path"
          :href="item.path"
          :class="{ active: currentRoute.name === item.name }"
        >{{ item.label }}</a>
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
      <template v-if="renderable">
        <DashboardView v-if="currentRoute.name === 'overview'" />
        <ReviewQueueView v-else-if="currentRoute.name === 'review'" />
        <UsersView v-else-if="currentRoute.name === 'users'" />
        <OrdersView v-else-if="currentRoute.name === 'orders'" />
        <RefundQueueView v-else-if="currentRoute.name === 'refunds'" />
        <TeacherCoursesView v-else-if="currentRoute.name === 'teacher-courses'" />
        <LessonVideoView v-else-if="currentRoute.name === 'lesson-video'" />
        <TeacherAnalyticsView v-else-if="currentRoute.name === 'teacher-analytics'" />
        <p
          v-else
          class="lp-empty"
        >
          页面不存在，<a href="/">返回数据看板</a>
        </p>
      </template>
      <p
        v-else
        class="lp-empty"
      >
        正在跳转…
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
