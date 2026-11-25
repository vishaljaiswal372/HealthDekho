import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Minimal browser globals for the node test environment.
const storage = new Map();
globalThis.sessionStorage = {
  getItem: (k) => (storage.has(k) ? storage.get(k) : null),
  setItem: (k, v) => storage.set(k, String(v)),
  removeItem: (k) => storage.delete(k),
};
const events = [];
globalThis.window = { dispatchEvent: (e) => events.push(e.type) };

const { request, toQuery, ApiError, UNAUTHORIZED_EVENT } = await import('./client.js');
const { saveSession } = await import('../auth/session.js');

const jsonResponse = (status, body) => ({
  ok: status >= 200 && status < 300,
  status,
  text: async () => (body === undefined ? '' : JSON.stringify(body)),
});

describe('api client', () => {
  beforeEach(() => {
    storage.clear();
    events.length = 0;
  });
  afterEach(() => vi.restoreAllMocks());

  it('builds query strings without empty values', () => {
    expect(toQuery({ a: 'x y', b: '', c: null, d: 0 })).toBe('?a=x+y&d=0');
    expect(toQuery({})).toBe('');
  });

  it('sends the bearer token and JSON body', async () => {
    saveSession({ accessToken: 'tok', expiresAt: new Date(Date.now() + 60_000).toISOString(), customer: {} });
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(201, { ok: true }));

    const data = await request('/api/policies', { method: 'POST', body: { productId: 1 }, fetchImpl });

    expect(data).toEqual({ ok: true });
    const [url, init] = fetchImpl.mock.calls[0];
    expect(url).toBe('/api/policies');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(init.body).toBe('{"productId":1}');
  });

  it('turns the backend error body into an ApiError with field errors', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse(400, { status: 400, message: 'Please correct the highlighted fields.', fieldErrors: { email: 'Required' } }),
    );
    const error = await request('/api/x', { fetchImpl }).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe('Please correct the highlighted fields.');
    expect(error.fieldErrors).toEqual({ email: 'Required' });
  });

  it('signals 401s so the app can end the session', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(401, { message: 'Please log in to continue.' }));
    await expect(request('/api/auth/me', { fetchImpl })).rejects.toThrow('Please log in to continue.');
    expect(events).toEqual([UNAUTHORIZED_EVENT]);
  });

  it('does not signal a session end for a failed login', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(401, { message: 'Invalid email or password.' }));
    await expect(request('/api/auth/login', { method: 'POST', body: {}, auth: false, fetchImpl })).rejects.toThrow(
      'Invalid email or password.',
    );
    expect(events).toEqual([]);
  });

  it('reports an unreachable server clearly', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));
    const error = await request('/api/x', { fetchImpl }).catch((e) => e);
    expect(error.status).toBe(0);
    expect(error.message).toMatch(/Cannot reach the HealthDekho server/);
  });

  it('ignores expired sessions', async () => {
    saveSession({ accessToken: 'old', expiresAt: new Date(Date.now() - 1000).toISOString(), customer: {} });
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, {}));
    await request('/api/x', { fetchImpl });
    expect(fetchImpl.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });
});
