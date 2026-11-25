import { currentToken } from '../auth/session.js';

const BASE_URL = import.meta.env?.VITE_API_BASE_URL ?? '';

/** Fired on any 401 from an authenticated call, so the app can end the session and show the login page. */
export const UNAUTHORIZED_EVENT = 'healthdekho:unauthorized';

/** Error carrying the backend's ApiError body: { status, message, fieldErrors }. */
export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || defaultMessage(status));
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = body?.fieldErrors ?? {};
  }
}

function defaultMessage(status) {
  if (status === 0) return 'Cannot reach the HealthDekho server. Please check that the backend is running.';
  if (status >= 500) return 'Something went wrong on the server. Please try again.';
  return 'The request could not be completed.';
}

/** Builds a query string, skipping empty values. */
export function toQuery(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      query.append(key, String(value).trim());
    }
  });
  const text = query.toString();
  return text ? `?${text}` : '';
}

export async function request(path, { method = 'GET', body, params, auth = true, fetchImpl = fetch } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = currentToken();
    if (token) headers.Authorization = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetchImpl(`${BASE_URL}${path}${toQuery(params)}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, null);
  }

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    if (response.status === 401 && auth && typeof window !== 'undefined') {
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    }
    throw new ApiError(response.status, data);
  }
  return data;
}
