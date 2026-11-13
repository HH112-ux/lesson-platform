<script setup lang="ts">
import { currentUser, signOut } from '../utils/session'
import { navigate } from '../router'

/** Ends the local session and sends the visitor to the login page. */
function logout(): void {
  signOut()
  navigate('/login', true)
}
</script>

<template>
  <header class="header">
    <div class="lp-container header-inner">
      <a
        class="brand"
        href="/"
      >课程平台</a>

      <nav class="nav">
        <a href="/">课程</a>
        <a href="/learning/my-courses">我的课程</a>
        <a href="/cart">购物车</a>
        <a href="/orders">订单</a>
        <a href="/account">账号</a>
      </nav>

      <div class="account">
        <template v-if="currentUser">
          <span class="lp-muted small">{{ currentUser.nickname }}</span>
          <button
            type="button"
            class="lp-btn lp-btn--ghost"
            @click="logout"
          >
            退出
          </button>
        </template>
        <template v-else>
          <a
            class="lp-btn lp-btn--ghost"
            href="/login"
          >登录</a>
          <a
            class="lp-btn lp-btn--primary"
            href="/register"
          >注册</a>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 10;
  border-bottom: 1px solid var(--lp-border);
  background: var(--lp-surface);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 32px;
  height: 60px;
}

.brand {
  color: var(--lp-text);
  font-size: 17px;
  font-weight: 600;
}

.brand:hover {
  text-decoration: none;
}

.nav {
  display: flex;
  gap: 20px;
  flex: 1;
}

.nav a {
  color: var(--lp-text-muted);
}

.nav a:hover {
  color: var(--lp-text);
  text-decoration: none;
}

.account {
  display: flex;
  align-items: center;
  gap: 10px;
}

.small {
  font-size: 13px;
}
</style>
