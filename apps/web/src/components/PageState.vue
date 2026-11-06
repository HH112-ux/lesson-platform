<script setup lang="ts">
withDefaults(
  defineProps<{
    variant?: 'loading' | 'error' | 'empty'
    title?: string
    message?: string
    actionLabel?: string
  }>(),
  {
    variant: 'loading',
    title: '',
    message: '',
    actionLabel: '',
  },
)

defineEmits<{ action: [] }>()
</script>

<template>
  <div
    class="state"
    :class="`state--${variant}`"
  >
    <p
      v-if="title"
      class="title"
    >
      {{ title }}
    </p>
    <p
      v-if="message || $slots.default"
      class="message"
    >
      <slot>{{ message }}</slot>
    </p>
    <button
      v-if="actionLabel"
      type="button"
      class="lp-btn"
      @click="$emit('action')"
    >
      {{ actionLabel }}
    </button>
  </div>
</template>

<style scoped>
.state {
  padding: 48px 24px;
  text-align: center;
}

.title {
  margin-bottom: 8px;
  font-size: 16px;
  font-weight: 600;
}

.state--error .title {
  color: var(--lp-danger);
}

.message {
  margin-bottom: 16px;
  color: var(--lp-text-muted);
  font-size: 14px;
}
</style>
