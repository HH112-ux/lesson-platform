<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import AppHeader from './components/AppHeader.vue'
import PageState from './components/PageState.vue'
import { currentRoute, navigate, syncRoute } from './router'
import HealthView from './views/HealthView.vue'
import LoginView from './views/LoginView.vue'
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
  <main
    v-else
    class="lp-page"
  >
    <div class="lp-container">
      <section class="lp-card">
        <PageState
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
