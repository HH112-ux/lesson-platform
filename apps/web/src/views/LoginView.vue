<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { login, register } from '../api/auth'
import { describeError } from '../utils/errors'
import { signIn } from '../utils/session'
import { navigate } from '../router'

const props = defineProps<{ mode: 'login' | 'register' }>()

const form = reactive({
  account: '',
  password: '',
  username: '',
  nickname: '',
})
const submitting = ref(false)
const errorText = ref('')

const isRegister = computed(() => props.mode === 'register')
const title = computed(() => (isRegister.value ? '创建账号' : '登录'))
const submitLabel = computed(() => {
  if (submitting.value) {
    return isRegister.value ? '注册中…' : '登录中…'
  }
  return isRegister.value ? '注册' : '登录'
})
const switchHref = computed(() => (isRegister.value ? '/login' : '/register'))
const switchLabel = computed(() => (
  isRegister.value ? '已有账号？去登录' : '还没有账号？去注册'
))

watch(() => props.mode, () => {
  errorText.value = ''
})

/** Reads the post-login destination, only allowing in-app paths. */
function redirectTarget(): string {
  const next = new URLSearchParams(window.location.search).get('next')
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

/** Submits the login or register form and returns to the pending page. */
async function submit(): Promise<void> {
  if (submitting.value) {
    return
  }
  errorText.value = ''
  submitting.value = true
  try {
    const result = isRegister.value
      ? await register({
        username: form.username.trim(),
        password: form.password,
        nickname: form.nickname.trim(),
      })
      : await login(form.account.trim(), form.password)
    signIn(result.accessToken, result.refreshToken, result.user)
    navigate(redirectTarget(), true)
  } catch (error) {
    errorText.value = describeError(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="lp-page">
    <div class="wrapper">
      <section class="lp-card">
        <form
          class="lp-card-body"
          @submit.prevent="submit"
        >
          <h1 class="title">
            {{ title }}
          </h1>
          <p class="lp-muted subtitle">
            {{ isRegister ? '注册后即可购买与学习课程' : '使用用户名或手机号登录' }}
          </p>

          <div
            v-if="isRegister"
            class="lp-field"
          >
            <label
              class="lp-label"
              for="username"
            >用户名</label>
            <input
              id="username"
              v-model="form.username"
              class="lp-input"
              autocomplete="username"
              maxlength="20"
              minlength="4"
              pattern="[A-Za-z0-9]{4,20}"
              placeholder="4-20 位字母或数字"
              required
            >
          </div>

          <div
            v-if="isRegister"
            class="lp-field"
          >
            <label
              class="lp-label"
              for="nickname"
            >昵称</label>
            <input
              id="nickname"
              v-model="form.nickname"
              class="lp-input"
              maxlength="32"
              placeholder="展示给其他学员的名字"
              required
            >
          </div>

          <div
            v-if="!isRegister"
            class="lp-field"
          >
            <label
              class="lp-label"
              for="account"
            >账号</label>
            <input
              id="account"
              v-model="form.account"
              class="lp-input"
              autocomplete="username"
              placeholder="用户名或手机号"
              required
            >
          </div>

          <div class="lp-field">
            <label
              class="lp-label"
              for="password"
            >密码</label>
            <input
              id="password"
              v-model="form.password"
              class="lp-input"
              :autocomplete="isRegister ? 'new-password' : 'current-password'"
              maxlength="64"
              minlength="6"
              placeholder="至少 6 位"
              required
              type="password"
            >
          </div>

          <p
            v-if="errorText"
            class="lp-alert error"
          >
            {{ errorText }}
          </p>

          <button
            type="submit"
            class="lp-btn lp-btn--primary lp-btn--block"
            :disabled="submitting"
          >
            {{ submitLabel }}
          </button>

          <p class="switch">
            <a :href="switchHref">{{ switchLabel }}</a>
          </p>
        </form>
      </section>
    </div>
  </main>
</template>

<style scoped>
.wrapper {
  max-width: 420px;
  margin: 24px auto 0;
}

.title {
  font-size: 22px;
}

.subtitle {
  margin: 6px 0 24px;
  font-size: 13px;
}

.error {
  margin-bottom: 16px;
}

.switch {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
}
</style>
