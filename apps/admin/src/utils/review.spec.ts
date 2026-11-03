import { describe, expect, it } from 'vitest'
import { buildReviewQueuePage } from './review'

describe('buildReviewQueuePage', () => {
  it('counts items by review state', () => {
    const page = buildReviewQueuePage([
      { id: 'a', status: 'pending' },
      { id: 'b', status: 'approved' },
      { id: 'c', status: 'pending' },
    ])

    expect(page).toEqual({
      pageNum: 1,
      pageSize: 20,
      pending: 2,
      approved: 1,
      rejected: 0,
    })
  })

  it('keeps the requested page number', () => {
    expect(buildReviewQueuePage([], 3).pageNum).toBe(3)
  })
})
