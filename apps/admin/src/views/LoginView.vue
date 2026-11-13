<script setup lang="ts">
/**
 * Operator sign-in screen.
 *
 * @author Caspian
 * @since 0.2.0
 */
import { ref } from 'vue'
import { loadProfile, login } from '../api/auth'
import { clearSession, storeTokens, storeUser } from '../api/client'
import { currentUser, defaultRouteFor, navigate } from '../router'
import { describeSignInError } from '../utils/errors'

const account = ref('')
const password = ref('')
const submitting = ref(false)
const notice = ref('')

function hasConsoleAccess(roles: string[]): boolean {
  return roles.includes('ADMIN') || roles.includes('OPS') || roles.includes('TEACHER')
}

async function submit(): Promise<void> {
  if (!account.value.trim() || !password.value) {
    notice.value = '请输入账号和密码'
    return
  }
  submitting.value = true
  notice.value = ''
  let credentialsAccepted = false
  try {
    const result = await login(account.value.trim(), password.value)
    credentialsAccepted = true
    storeTokens(result.accessToken, result.refreshToken)
    const profile = await loadProfile()
    if (!hasConsoleAccess(profile.roles)) {
      clearSession()
      notice.value = '当前账号没有后台权限，请使用运营、管理员或教师账号'
      return
    }
    storeUser({ id: profile.id, nickname: profile.nickname, avatarUrl: profile.avatarUrl, roles: profile.roles })
    currentUser.value = { id: profile.id, nickname: profile.nickname, roles: profile.roles }
    navigate(defaultRouteFor(profile.roles), true)
  } catch (error) {
    clearSession()
    notice.value = describeSignInError(error, credentialsAccepted)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <form
      class="lp-card login-card"
      @submit.prevent="submit"
    >
      <h1>课程平台运营后台</h1>
      <p class="lp-muted">
        请使用运营、管理员或教师账号登录
      </p>

      <label class="lp-field">
        <span class="lp-label">账号</span>
        <input
          v-model="account"
          class="lp-input"
          autocomplete="username"
          placeholder="用户名或手机号"
        >
      </label>

      <label class="lp-field">
        <span class="lp-label">密码</span>
        <input
          v-model="password"
          class="lp-input"
          type="password"
          autocomplete="current-password"
          placeholder="请输入密码"
        >
      </label>

      <p
        v-if="notice"
        class="lp-alert"
      >
        {{ notice }}
      </p>

      <button
        class="lp-btn lp-btn--primary login-submit"
        type="submit"
        :disabled="submitting"
      >
        {{ submitting ? '登录中…' : '登录' }}
      </button>
    </form>
  </main>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
}

.login-card {
  width: 100%;
  max-width: 380px;
  padding: 32px;
}

h1 {
  margin: 0 0 8px;
  font-size: 22px;
}

.login-card > p {
  margin: 0 0 24px;
  font-size: 13px;
}

.login-submit {
  width: 100%;
  margin-top: 8px;
}
</style>
