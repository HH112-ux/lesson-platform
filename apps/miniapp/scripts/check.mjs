// Static validator for the mini program project. It runs in CI because compiling
// with miniprogram-ci needs a real AppID plus an upload private key, which must
// never be committed; the checks below catch broken project files early instead.
import { readFileSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

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

const appidMode = projectConfig && projectConfig.appid === 'touristappid' ? 'tourist (no real AppID)' : 'configured'
console.log(`OK mini program static check (${pages.length} page(s), appid: ${appidMode})`)
console.log('NOTE miniprogram-ci compile/upload is skipped: it requires a real AppID and upload private key.')
