// Writes the real AppID into project.private.config.json (git-ignored) so WeChat DevTools
// can open the project for real-device acceptance without editing tracked files.
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describeAppidProblem, printAppidHelp } from './appid.mjs'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const privatePath = resolve(root, 'project.private.config.json')
const appid = (process.env.LP_MINIPROGRAM_APPID ?? '').trim()

if (!appid) {
  console.error('FAIL cannot prepare AppID: LP_MINIPROGRAM_APPID is empty')
  printAppidHelp()
  process.exit(1)
}

const problem = describeAppidProblem(appid)
if (problem) {
  // Never echo the raw value: a pasted AppSecret must not leak into terminal logs.
  console.error(`FAIL cannot prepare AppID: ${problem} (value length: ${appid.length})`)
  printAppidHelp()
  process.exit(1)
}

let privateConfig = {}
if (existsSync(privatePath)) {
  try {
    privateConfig = JSON.parse(readFileSync(privatePath, 'utf8'))
  } catch (error) {
    console.error(`FAIL cannot parse existing project.private.config.json: ${error.message}`)
    process.exit(1)
  }
  if (typeof privateConfig !== 'object' || privateConfig === null || Array.isArray(privateConfig)) {
    console.error('FAIL existing project.private.config.json must contain a JSON object')
    process.exit(1)
  }
}

privateConfig.appid = appid
privateConfig.projectname = privateConfig.projectname ?? 'lp-miniapp'
writeFileSync(privatePath, `${JSON.stringify(privateConfig, null, 2)}\n`)

// Print only non-recoverable hints; the raw value must not leak into terminal logs.
console.log(`OK wrote AppID (length ${appid.length}, prefix ${appid.slice(0, 3)}) to project.private.config.json (git-ignored)`)
console.log('NEXT open apps/miniapp in WeChat DevTools and verify 详情 -> 基本信息 shows this AppID')
console.log('NOTE LP_MINIPROGRAM_APPID and the private config override the default AppID in project.config.json')
