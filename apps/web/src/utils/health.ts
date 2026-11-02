/**
 * Health summary shown by the web skeleton page.
 *
 * @author Caspian
 * @since 0.0.0
 */
export interface HealthSummary {
  /** Short text rendered next to the status dot. */
  label: string
  /** True when the gateway answered the health probe. */
  reachable: boolean
}

/**
 * Maps the gateway probe result to display text.
 *
 * @param reachable whether the gateway answered the health probe
 * @returns the summary used by the skeleton page
 * @author Caspian
 * @since 0.0.0
 */
export function describeGatewayHealth(reachable: boolean): HealthSummary {
  return reachable
    ? { label: 'Gateway reachable', reachable: true }
    : { label: 'Gateway unreachable', reachable: false }
}
