import { describe, expect, it } from 'vitest'
import { describeGatewayHealth } from './health'

describe('describeGatewayHealth', () => {
  it('reports a reachable gateway', () => {
    expect(describeGatewayHealth(true)).toEqual({
      label: 'Gateway reachable',
      reachable: true,
    })
  })

  it('reports an unreachable gateway', () => {
    expect(describeGatewayHealth(false)).toEqual({
      label: 'Gateway unreachable',
      reachable: false,
    })
  })
})
