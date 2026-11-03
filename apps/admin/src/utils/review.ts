import { DEFAULT_PAGE_SIZE, countByStatus, type ReviewItem } from '@lp/shared'

/** Summary rendered by the admin skeleton page. */
export interface ReviewQueuePage {
  /** One-based page number. */
  pageNum: number
  /** Page size requested from the backend. */
  pageSize: number
  /** Items still waiting for review. */
  pending: number
  /** Items already approved. */
  approved: number
  /** Items rejected by reviewers. */
  rejected: number
}

/**
 * Builds the review queue summary for one page.
 *
 * <p>Kept free of Vue and HTTP so the same helper can drive the page and its tests.</p>
 *
 * @param items review items of the current page
 * @param pageNum one-based page number
 * @returns counts per review state and the page size
 * @author Caspian
 * @since 0.0.0
 */
export function buildReviewQueuePage(
  items: readonly ReviewItem[],
  pageNum = 1,
): ReviewQueuePage {
  return {
    pageNum,
    pageSize: DEFAULT_PAGE_SIZE,
    pending: countByStatus(items, 'pending'),
    approved: countByStatus(items, 'approved'),
    rejected: countByStatus(items, 'rejected'),
  }
}
