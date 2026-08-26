import { AxiosError, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const storeMock = vi.hoisted(() => ({
  token: '',
  refreshSession: vi.fn(),
  clearSession: vi.fn()
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => storeMock
}))

import request from '@/api/request'

function success(config: InternalAxiosRequestConfig, data: unknown = { code: 0, data: 'ok' }) {
  return Promise.resolve({ data, status: 200, statusText: 'OK', headers: {}, config })
}

function unauthorized(config: InternalAxiosRequestConfig) {
  const response = { data: { code: 401, msg: 'unauthorized' }, status: 401, statusText: 'Unauthorized', headers: {}, config }
  return Promise.reject(new AxiosError('unauthorized', AxiosError.ERR_BAD_REQUEST, config, undefined, response))
}

describe('authenticated request client', () => {
  beforeEach(() => {
    storeMock.token = ''
    storeMock.refreshSession.mockReset()
    storeMock.clearSession.mockReset()
  })

  it('injects the current in-memory access token', async () => {
    storeMock.token = 'memory-token'
    let captured: InternalAxiosRequestConfig | undefined
    const adapter = async (config: InternalAxiosRequestConfig) => {
      captured = config
      return success(config)
    }

    await request.get('/probe', { adapter })

    expect(captured?.headers.Authorization).toBe('Bearer memory-token')
  })

  it('refreshes once and retries a 401 request with the new token', async () => {
    storeMock.token = 'expired-token'
    storeMock.refreshSession.mockImplementation(async () => {
      storeMock.token = 'refreshed-token'
      return 'refreshed-token'
    })
    let calls = 0
    const authorizations: string[] = []
    const adapter = (config: InternalAxiosRequestConfig) => {
      calls += 1
      authorizations.push(String(config.headers.Authorization ?? ''))
      return config.retried ? success(config) : unauthorized(config)
    }

    await expect(request.get('/protected', { adapter })).resolves.toEqual({ code: 0, data: 'ok' })

    expect(calls).toBe(2)
    expect(storeMock.refreshSession).toHaveBeenCalledTimes(1)
    expect(authorizations).toEqual(['Bearer expired-token', 'Bearer refreshed-token'])
  })

  it('shares one refresh across concurrent 401 responses', async () => {
    storeMock.token = 'expired-token'
    let resolveRefresh!: (token: string) => void
    storeMock.refreshSession.mockReturnValue(new Promise((resolve) => {
      resolveRefresh = (token) => {
        storeMock.token = token
        resolve(token)
      }
    }))
    const authorizations: Record<string, string[]> = {}
    const adapter = (config: InternalAxiosRequestConfig) => {
      const url = config.url || ''
      ;(authorizations[url] ||= []).push(String(config.headers.Authorization ?? ''))
      return config.retried ? success(config) : unauthorized(config)
    }

    const first = request.get('/first', { adapter })
    const second = request.get('/second', { adapter })
    await vi.waitFor(() => expect(storeMock.refreshSession).toHaveBeenCalledTimes(1))
    resolveRefresh('shared-token')

    await expect(Promise.all([first, second])).resolves.toHaveLength(2)
    expect(storeMock.refreshSession).toHaveBeenCalledTimes(1)
    expect(authorizations).toEqual({
      '/first': ['Bearer expired-token', 'Bearer shared-token'],
      '/second': ['Bearer expired-token', 'Bearer shared-token']
    })
  })

  it('does not refresh requests that explicitly opt out', async () => {
    const adapter = (config: InternalAxiosRequestConfig) => unauthorized(config)
    const config: AxiosRequestConfig = { adapter, skipAuthRefresh: true }

    await expect(request.get('/auth/refresh', config)).rejects.toThrow('unauthorized')

    expect(storeMock.refreshSession).not.toHaveBeenCalled()
  })
})
