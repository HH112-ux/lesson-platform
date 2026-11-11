/**
 * Payment channel helpers of the browser app.
 *
 * <p>Alipay page pay answers with a self submitting form instead of a URL. The form is parsed in
 * an inert document, rebuilt from its hidden inputs and submitted, so no script carried by the
 * channel payload ever runs in this origin.</p>
 *
 * @author Caspian
 * @since 0.4.0
 */
import type { PayView } from '../api/orders'

/** Only an absolute {@code https} action may receive the signed payment form. */
const TRUSTED_PROTOCOL = 'https:'

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
 * Submits the self submitting form returned by the payment channel.
 *
 * <p>The caller must not fall back to navigating to the form body: the payload is a form, and
 * treating it as a URL leaves the buyer on a blank page with an unpaid order.
 *
 * @param payment prepay payload returned by {@code POST /api/v1/payments/{orderNo}/pay}
 * @returns false when the payload carries no usable form, so the caller can show a notice
 * @author Caspian
 * @since 0.4.0
 */
export function submitPaymentForm(payment: PayView): boolean {
  const html = payment.payParams?.paymentHtml
  if (!html) {
    return false
  }
  const source = new DOMParser().parseFromString(html, 'text/html').querySelector('form')
  if (!source) {
    return false
  }
  const action = source.getAttribute('action') ?? ''
  if (!isTrustedFormAction(action)) {
    return false
  }
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = action
  form.style.display = 'none'
  source.querySelectorAll('input').forEach((input) => {
    const name = input.getAttribute('name')
    if (!name) {
      return
    }
    const field = document.createElement('input')
    field.type = 'hidden'
    field.name = name
    field.value = input.getAttribute('value') ?? ''
    form.appendChild(field)
  })
  document.body.appendChild(form)
  form.submit()
  return true
}
