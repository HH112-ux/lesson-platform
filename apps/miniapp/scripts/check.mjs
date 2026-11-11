// Static validator for the mini program project. It runs in CI because compiling
// with miniprogram-ci needs a real AppID plus an upload private key, which must
// never be committed; the checks below catch broken project files early instead.
import { readFileSync, existsSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import defaults from '../config.js'

const require = createRequire(import.meta.url)
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const errors = []

/**
 * Reads a JSON file below the project root and records a readable error on failure.
 *
 * @param {string} relativePath path relative to the mini program root
 * @returns {unknown} parsed JSON content, or undefined when invalid
 */
function readJson(relativePath) {
  const absolutePath = resolve(root, relativePath)
  if (!existsSync(absolutePath)) {
    errors.push(`missing file: ${relativePath}`)
    return undefined
  }
  try {
    return JSON.parse(readFileSync(absolutePath, 'utf8'))
  } catch (error) {
    errors.push(`invalid JSON in ${relativePath}: ${error.message}`)
    return undefined
  }
}

const projectConfig = readJson('project.config.json')
const appConfig = readJson('app.json')
readJson('sitemap.json')

if (projectConfig) {
  if (projectConfig.compileType !== 'miniprogram') {
    errors.push('project.config.json must set compileType to "miniprogram"')
  }
  if (!projectConfig.appid) {
    errors.push('project.config.json must declare an appid (touristappid is allowed locally)')
  }
}

const pages = appConfig && Array.isArray(appConfig.pages) ? appConfig.pages : []
if (pages.length === 0) {
  errors.push('app.json must declare at least one page')
}

const pageExtensions = ['js', 'json', 'wxml', 'wxss']
for (const page of pages) {
  for (const extension of pageExtensions) {
    const pageFile = `${page}.${extension}`
    if (!existsSync(resolve(root, pageFile))) {
      errors.push(`missing page file: ${pageFile}`)
    }
  }
}

if (errors.length > 0) {
  console.error('FAIL mini program static check')
  for (const message of errors) {
    console.error(`- ${message}`)
  }
  process.exit(1)
}

/**
 * Resolves the apiBaseUrl the mini program will actually use at runtime.
 *
 * @returns {{value: string, source: string}} effective URL and where it came from
 */
function resolveApiBaseUrl() {
  const localPath = resolve(root, 'config.local.js')
  if (existsSync(localPath)) {
    try {
      const local = require(localPath)
      if (typeof local.apiBaseUrl === 'string' && local.apiBaseUrl.trim()) {
        return { value: local.apiBaseUrl.trim().replace(/\/+$/, ''), source: 'config.local.js' }
      }
    } catch (error) {
      console.warn(`WARN ignoring unreadable config.local.js: ${error.message}`)
    }
  }
  return { value: defaults.apiBaseUrl, source: 'default (config.js)' }
}

/**
 * Checks whether a hostname is loopback or inside a private IPv4 range.
 *
 * @param {string} hostname hostname from a parsed URL
 * @returns {boolean} true when the host cannot be reached from the public internet
 */
function isPrivateHost(hostname) {
  const host = hostname.toLowerCase().replace(/^\[|\]$/g, '')
  if (host === 'localhost' || host === '0.0.0.0' || host === '::1') {
    return true
  }
  if (host.startsWith('127.')) {
    return true
  }
  const parts = host.split('.')
  if (parts.length === 4 && parts.every((part) => /^\d+$/.test(part))) {
    const [first, second] = parts.map(Number)
    if (first === 10) {
      return true
    }
    if (first === 172 && second >= 16 && second <= 31) {
      return true
    }
    if (first === 192 && second === 168) {
      return true
    }
  }
  return false
}

/**
 * Describes why the effective apiBaseUrl is not acceptable for upload or release.
 *
 * @param {string} value effective base URL
 * @returns {string} empty string when the URL is a public HTTPS endpoint
 */
function describePublicApiProblem(value) {
  let parsed
  try {
    parsed = new URL(value)
  } catch {
    return `apiBaseUrl is not an absolute URL: ${value}`
  }
  if (parsed.protocol !== 'https:') {
    return `apiBaseUrl must use https, got ${parsed.protocol.replace(':', '')}`
  }
  if (isPrivateHost(parsed.hostname)) {
    return `apiBaseUrl host ${parsed.hostname} is loopback or private; upload needs a filed public domain`
  }
  return ''
}

const apiBase = resolveApiBaseUrl()
const requirePublicApi = process.argv.includes('--require-public-api')
const publicApiProblem = requirePublicApi ? describePublicApiProblem(apiBase.value) : ''
if (publicApiProblem) {
  console.error('FAIL upload/release requires a public HTTPS apiBaseUrl')
  console.error(`- ${publicApiProblem}`)
  console.error(`- effective value: ${apiBase.value} (source: ${apiBase.source})`)
  console.error('- set LP_MINIAPP_API_BASE_URL to the filed HTTPS domain, run: pnpm --filter lp-miniapp prepare:env')
  process.exit(1)
}

const appidMode = projectConfig && projectConfig.appid === 'touristappid' ? 'tourist (no real AppID)' : 'configured'
console.log(`OK mini program static check (${pages.length} page(s), appid: ${appidMode})`)

console.log(`apiBaseUrl: ${apiBase.value} (${apiBase.source})`)
const injectedApiBase = (process.env.LP_MINIAPP_API_BASE_URL ?? '').trim().replace(/\/+$/, '')
if (injectedApiBase && injectedApiBase !== apiBase.value) {
  console.warn('WARN LP_MINIAPP_API_BASE_URL is set but config.local.js was not refreshed; run: pnpm --filter lp-miniapp prepare:env')
}
try {
  if (isPrivateHost(new URL(apiBase.value).hostname)) {
    console.log('NOTE this host is loopback or private; it only works on preview, not for upload.')
  }
} catch {
  console.warn(`WARN apiBaseUrl is not an absolute URL: ${apiBase.value}`)
}
if (requirePublicApi) {
  console.log('NOTE upload gate passed: apiBaseUrl is HTTPS on a public host.')
}

console.log('NOTE miniprogram-ci compile/upload is skipped: it requires a real AppID and upload private key.')
