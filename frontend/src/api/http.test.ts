import { ApiError, configureHttp, request } from './http'

const reply = (status: number, body?: unknown) =>
  Promise.resolve({ ok: status >= 200 && status < 300, status, json: () => (body === undefined ? Promise.reject(new Error('no body')) : Promise.resolve(body)) })

describe('http wrapper', () => {
  const fetchMock = jest.fn()
  const onUnauthorized = jest.fn()
  beforeEach(() => {
    fetchMock.mockReset()
    onUnauthorized.mockReset()
    globalThis.fetch = fetchMock as unknown as typeof fetch
  })

  it('sends the bearer token and a JSON body to a relative /api path', async () => {
    configureHttp({ getToken: () => 'abc', onUnauthorized })
    fetchMock.mockReturnValue(reply(201, { id: 1 }))
    await expect(request('POST', '/projects', { name: 'P' })).resolves.toEqual({ id: 1 })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/projects')
    expect(init.headers.Authorization).toBe('Bearer abc')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe('{"name":"P"}')
  })

  it('sends no Authorization header without a token', async () => {
    configureHttp({ getToken: () => null, onUnauthorized })
    fetchMock.mockReturnValue(reply(200, []))
    await request('GET', '/projects')
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('normalises backend errors including field errors', async () => {
    configureHttp({ getToken: () => 'abc', onUnauthorized })
    fetchMock.mockReturnValue(reply(400, { status: 400, code: 'VALIDATION_ERROR', message: 'Invalid request', fieldErrors: { name: 'Name is required' } }))
    const error = await request('POST', '/projects', {}).catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_ERROR', fieldErrors: { name: 'Name is required' } })
  })

  it('a 401 on a request that carried a token ends the session once', async () => {
    configureHttp({ getToken: () => 'abc', onUnauthorized })
    fetchMock.mockReturnValue(reply(401, { status: 401, code: 'UNAUTHORIZED', message: 'Authentication is required or the token is invalid' }))
    await expect(request('GET', '/projects')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('a 401 without a token (wrong password at login) is just an error', async () => {
    configureHttp({ getToken: () => null, onUnauthorized })
    fetchMock.mockReturnValue(reply(401, { status: 401, code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' }))
    await expect(request('POST', '/auth/login', {})).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' })
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('handles 204, non-JSON error bodies and network failures', async () => {
    configureHttp({ getToken: () => 'abc', onUnauthorized })
    fetchMock.mockReturnValueOnce(reply(204))
    await expect(request('DELETE', '/tasks/1')).resolves.toBeUndefined()
    fetchMock.mockReturnValueOnce(reply(502))
    await expect(request('GET', '/projects')).rejects.toMatchObject({ status: 502, code: 'ERROR' })
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await expect(request('GET', '/projects')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })
})
