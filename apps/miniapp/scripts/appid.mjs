// Shared AppID resolution. The tracked project.config.json carries the public production AppID
// as the default; LP_MINIPROGRAM_APPID (environment variable or CI secret) and the local,
// git-ignored project.private.config.json override it. An explicit placeholder remains available
// so an unconfigured checkout can never pass real-device acceptance or upload by accident.
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Placeholder accepted by the tracked project.config.json when the AppID is deliberately
 * unconfigured. It cannot be a valid AppID and is intentionally ugly so "not configured" is
 * visible in code review and CI logs.
 */
export const PLACEHOLDER_APPID = 'REPLACE_WITH_MINIPROGRAM_APPID'

/** A real WeChat mini program AppID: `wx` followed by 16 lowercase hex characters. */
const APPID_PATTERN = /^wx[0-9a-f]{16}$/

/** The guest AppID that must no longer be used for acceptance or upload. */
const LEGACY_TOURIST_APPID = 'touristappid'

/**
 * Returns whether the value is a syntactically valid real AppID.
 *
 * @param {unknown} value candidate AppID
 * @returns {boolean} true when the value matches `wx` + 16 hex characters
 */
export function isValidAppid(value) {
  return typeof value === 'string' && APPID_PATTERN.test(value)
}

/**
 * Reads the AppID from project.private.config.json when that local, git-ignored file exists.
 *
 * @param {string} root mini program project root
 * @returns {string|undefined} AppID declared in the private config, if any
 */
export function readPrivateAppid(root) {
  const privatePath = resolve(root, 'project.private.config.json')
  if (!existsSync(privatePath)) {
    return undefined
  }
  try {
    const parsed = JSON.parse(readFileSync(privatePath, 'utf8'))
    return typeof parsed.appid === 'string' ? parsed.appid.trim() : undefined
  } catch (error) {
    console.warn(`WARN ignoring unparseable project.private.config.json: ${error.message}`)
    return undefined
  }
}

/**
 * Reads the AppID declared in the tracked project.config.json.
 *
 * @param {string} root mini program project root
 * @returns {string|undefined} AppID declared in the tracked config, if any
 */
export function readProjectAppid(root) {
  try {
    const parsed = JSON.parse(readFileSync(resolve(root, 'project.config.json'), 'utf8'))
    return typeof parsed.appid === 'string' ? parsed.appid.trim() : undefined
  } catch {
    return undefined
  }
}

/**
 * Resolves the AppID from the environment first, then the local private config, then the
 * tracked project.config.json.
 *
 * @param {string} root mini program project root
 * @param {NodeJS.ProcessEnv} env environment variables
 * @returns {{appid: string, source: string}} resolved AppID and where it came from
 */
export function resolveAppid(root, env = process.env) {
  const injected = (env.LP_MINIPROGRAM_APPID ?? '').trim()
  if (injected) {
    return { appid: injected, source: 'LP_MINIPROGRAM_APPID' }
  }
  const privateAppid = readPrivateAppid(root)
  if (privateAppid) {
    return { appid: privateAppid, source: 'project.private.config.json' }
  }
  const projectAppid = readProjectAppid(root)
  if (projectAppid) {
    return { appid: projectAppid, source: 'project.config.json' }
  }
  return { appid: PLACEHOLDER_APPID, source: 'placeholder' }
}

/**
 * Validates a resolved AppID for acceptance and upload flows.
 *
 * @param {string} appid resolved AppID
 * @returns {string|undefined} problem description, or undefined when usable
 */
export function describeAppidProblem(appid) {
  if (!appid || appid === PLACEHOLDER_APPID) {
    return 'no real AppID configured (still the placeholder)'
  }
  if (appid === LEGACY_TOURIST_APPID) {
    return 'touristappid is a guest AppID and is not accepted for real-device acceptance or upload'
  }
  if (!isValidAppid(appid)) {
    return 'AppID format is invalid (expected wx followed by 16 lowercase hex characters)'
  }
  return undefined
}

/**
 * Prints the shared remediation text without echoing the configured value.
 *
 * @returns {void}
 */
export function printAppidHelp() {
  console.error('- set LP_MINIPROGRAM_APPID=wx<16 hex chars> for this shell or CI job, then re-run')
  console.error('- or run: pnpm --filter lp-miniapp prepare:appid (writes project.private.config.json)')
  console.error('- the AppID is shown in WeChat MP console: 开发 -> 开发管理 -> 开发设置 -> AppID')
  console.error('- the AppID is public and may be committed; never commit the AppSecret or the upload private key')
}
