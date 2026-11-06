<script setup lang="ts">
import { currentRoute } from '../router'

const orderNo = currentRoute.value.params.orderNo
</script>

<template>
  <main class="lp-page">
    <div class="lp-container">
      <div class="wrapper">
        <section class="lp-card lp-card-body">
          <h1 class="title">
            支付结果确认中
          </h1>
          <p class="lp-muted hint">
            若已在支付宝完成支付，课程通常会在几秒内开通。
          </p>

          <p class="order-no">
            订单号：{{ orderNo }}
          </p>

          <p class="lp-alert notice">
            支付结果以服务端回调为准，请稍后在「我的订单」中确认状态；课程开通后可到「我的课程」开始学习。
          </p>

          <div class="actions">
            <a
              class="lp-btn lp-btn--primary lp-btn--block"
              href="/learning/my-courses"
            >去我的课程</a>
            <a
              class="lp-btn lp-btn--block"
              href="/orders"
            >查看订单</a>
          </div>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.wrapper {
  max-width: 480px;
  margin: 32px auto 0;
}

.title {
  font-size: 22px;
}

.hint {
  margin: 6px 0 20px;
  font-size: 13px;
}

.order-no {
  margin-bottom: 16px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
}

.notice {
  margin-bottom: 20px;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>
