import { describe, expect, it } from 'vitest'
import { DEFAULT_PAGE_SIZE, countByStatus, type ReviewItem } from './index'

const items: ReviewItem[] = [
  { id: 'a', status: 'pending' },
  { id: 'b', status: 'approved' },
  { id: 'c', status: 'pending' },
]

describe('countByStatus', () => {
  it('counts items in the requested state', () => {
    expect(countByStatus(items, 'pending')).toBe(2)
    expect(countByStatus(items, 'approved')).toBe(1)
    expect(countByStatus(items, 'rejected')).toBe(0)
  })

  it('does not mutate the input list', () => {
    countByStatus(items, 'pending')
    expect(items).toHaveLength(3)
  })

  it('returns zero for an empty queue', () => {
    expect(countByStatus([], 'pending')).toBe(0)
  })
})

describe('DEFAULT_PAGE_SIZE', () => {
  it('matches the documented default', () => {
    expect(DEFAULT_PAGE_SIZE).toBe(20)
  })
})
