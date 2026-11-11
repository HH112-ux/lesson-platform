/**
 * Payment channel helpers of the browser app.
 *
 * <p>Alipay page pay answers with a self submitting form instead of a URL. The form is parsed in
 * an inert document, validated, rebuilt from its hidden inputs and submitted, so no script carried
 * by the channel payload ever runs in this origin.</p>
 *
 * @author Caspian
 * @since 0.4.0
 */
import type { PayView } from '../api/orders'

/** Only an absolute {@code https} action may receive the signed payment form. */
const TRUSTED_PROTOCOL = 'https:'

/** Fields every Alipay page pay form carries; a form without them is not a page pay form. */
const REQUIRED_FIELDS = ['biz_content', 'sign']

/** Reason the payload was refused, mapped to a message by {@link describePaymentFormRejection}. */
export type PaymentFormRejection = 'missing' | 'malformed' | 'untrusted'

/**
 * Tells whether a form action may receive the payment form.
 *
 * @param action absolute form action taken from the channel payload
 * @returns true only for absolute {@code https} URLs, false for relative or script schemes
 * @author Caspian
 * @since 0.4.0
 */
export function isTrustedFormAction(action: string): boolean {
  try {
    return new URL(action).protocol === TRUSTED_PROTOCOL
  } catch {
    return false
  }
}

/**
 * Maps a refusal to a message safe to show next to the created order.
 *
 * @param rejection reason returned by {@link submitPaymentForm}
 * @returns Chinese message telling the buyer what happened
 * @author Caspian
 * @since 0.4.0
 */
export function describePaymentFormRejection(rejection: PaymentFormRejection): string {
  switch (rejection) {
    case 'missing':
      return '支付表单缺失，请稍后重试'
    case 'untrusted':
      return '支付网关地址不受信，请联系客服'
    default:
      return '支付表单结构异常，请稍后重试'
  }
}

/**
 * Submits the self submitting form returned by the payment channel.
 *
 * <p>The payload is parsed as {@code text/html} in an inert document, never injected. It is
 * refused, instead of being submitted half empty, when the document holds anything other than
 * exactly one top level form, when the form carries no named field, when the signed fields
 * {@code biz_content} or {@code sign} are absent, or when the action is not an absolute
 * {@code https} URL. The caller must not fall back to navigating to the form body: treating the
 * payload as a URL leaves the buyer on a blank page with an unpaid order.</p>
 *
 * @param payment prepay payload returned by {@code POST /api/v1/payments/{orderNo}/pay}
 * @returns {@code null} when the form was submitted, otherwise the reason it was refused
 * @author Caspian
 * @since 0.4.0
 */
export function submitPaymentForm(payment: PayView): PaymentFormRejection | null {
  const html = payment.payParams?.paymentHtml
  if (!html) {
    return 'missing'
  }
  const parsed = new DOMParser().parseFromString(html, 'text/html')
  const forms = parsed.querySelectorAll('form')
  if (forms.length !== 1) {
    return 'malformed'
  }
  const source = forms[0]
  if (source.parentElement !== parsed.body) {
    return 'malformed'
  }
  const fields = collectFields(source)
  if (fields.size === 0 || REQUIRED_FIELDS.some((name) => !fields.has(name))) {
    return 'malformed'
  }
  const action = source.getAttribute('action') ?? ''
  if (!isTrustedFormAction(action)) {
    return 'untrusted'
  }
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = action
  form.style.display = 'none'
  fields.forEach((value, name) => {
    const field = document.createElement('input')
    field.type = 'hidden'
    field.name = name
    field.value = value
    form.appendChild(field)
  })
  document.body.appendChild(form)
  form.submit()
  return null
}

/**
 * Copies the named inputs of the channel form, keeping the last value of a repeated name.
 *
 * @param source form parsed from the channel payload
 * @returns field name to value, empty when the form carries no named input
 * @author Caspian
 * @since 0.4.0
 */
function collectFields(source: HTMLFormElement): Map<string, string> {
  const fields = new Map<string, string>()
  source.querySelectorAll('input').forEach((input) => {
    const name = input.getAttribute('name')
    if (name) {
      fields.set(name, input.getAttribute('value') ?? '')
    }
  })
  return fields
}
