<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import AppHeader from './components/AppHeader.vue'
import PageState from './components/PageState.vue'
import { currentRoute, navigate, syncRoute } from './router'
import CourseListView from './views/CourseListView.vue'
import CourseDetailView from './views/CourseDetailView.vue'
import CheckoutView from './views/CheckoutView.vue'
import OrderResultView from './views/OrderResultView.vue'
import HealthView from './views/HealthView.vue'
import LoginView from './views/LoginView.vue'
import PlayerView from './views/PlayerView.vue'
import PurchaseView from './views/PurchaseView.vue'

const focused = computed(() => currentRoute.value.name === 'purchase')

/** Re-reads the address bar so back and forward keep working without a router library. */
function onPopState(): void {
  syncRoute()
}

onMounted(() => {
  syncRoute()
  window.addEventListener('popstate', onPopState)
})

onUnmounted(() => {
  window.removeEventListener('popstate', onPopState)
})
</script>

<template>
  <AppHeader v-if="!focused" />

  <PurchaseView v-if="currentRoute.name === 'purchase'" />
  <LoginView
    v-else-if="currentRoute.name === 'login'"
    mode="login"
  />
  <LoginView
    v-else-if="currentRoute.name === 'register'"
    mode="register"
  />
  <HealthView v-else-if="currentRoute.name === 'health'" />
  <CourseListView v-else-if="currentRoute.name === 'home'" />
  <CourseDetailView v-else-if="currentRoute.name === 'course-detail'" />
  <CheckoutView v-else-if="currentRoute.name === 'checkout'" />
  <OrderResultView v-else-if="currentRoute.name === 'order-result'" />
  <PlayerView v-else-if="currentRoute.name === 'player'" />
  <main
    v-else
    class="lp-page"
  >
    <div class="lp-container">
      <section class="lp-card">
        <PageState
          v-if="currentRoute.name === 'not-found'"
          variant="empty"
          title="页面不存在"
          message="链接可能已失效，请返回首页继续浏览。"
          action-label="返回首页"
          @action="navigate('/')"
        />
        <PageState
          v-else
          variant="empty"
          title="页面即将上线"
          message="该页面将在后续批次开放。"
          action-label="返回首页"
          @action="navigate('/')"
        />
      </section>
    </div>
  </main>
</template>
