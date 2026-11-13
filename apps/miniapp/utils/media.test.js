const test = require('node:test')
const assert = require('node:assert/strict')

const { resolveMediaUrl } = require('./media')

function silentWarn() {
  const messages = []
  const warn = (message) => messages.push(message)
  warn.messages = messages
  return warn
}

test('resolves a site-relative path against the base url', () => {
  assert.equal(
    resolveMediaUrl('/lp-media/covers/1.png', 'https://example.com'),
    'https://example.com/lp-media/covers/1.png',
  )
})

test('removes trailing slashes from the base url', () => {
  assert.equal(
    resolveMediaUrl('/lp-media/covers/1.png', 'https://example.com/'),
    'https://example.com/lp-media/covers/1.png',
  )
})

test('passes absolute http and https urls through unchanged', () => {
  assert.equal(
    resolveMediaUrl('https://cdn.example.com/a.png', 'https://example.com'),
    'https://cdn.example.com/a.png',
  )
  assert.equal(
    resolveMediaUrl('http://cdn.example.com/a.png', 'https://example.com'),
    'http://cdn.example.com/a.png',
  )
})

test('returns an empty string for empty input', () => {
  assert.equal(resolveMediaUrl('', 'https://example.com'), '')
  assert.equal(resolveMediaUrl('   ', 'https://example.com'), '')
  assert.equal(resolveMediaUrl(null, 'https://example.com'), '')
  assert.equal(resolveMediaUrl(undefined, 'https://example.com'), '')
})

test('keeps the original path and warns when the base url is missing', () => {
  const warn = silentWarn()
  assert.equal(resolveMediaUrl('/lp-media/covers/1.png', '', warn), '/lp-media/covers/1.png')
  assert.equal(warn.messages.length, 1)
  assert.match(warn.messages[0], /\/lp-media\/covers\/1\.png/)

  const missingWarn = silentWarn()
  assert.equal(resolveMediaUrl('/lp-media/covers/2.png', undefined, missingWarn), '/lp-media/covers/2.png')
  assert.equal(missingWarn.messages.length, 1)
})

test('keeps values that are neither absolute nor site-relative', () => {
  const warn = silentWarn()
  assert.equal(resolveMediaUrl('covers/1.png', 'https://example.com', warn), 'covers/1.png')
  assert.equal(warn.messages.length, 0)
})
