// The logged-in session lives in sessionStorage: it survives a page refresh but not closing the tab.
const KEY = 'healthdekho.session';

export function loadSession() {
  try {
    const session = JSON.parse(sessionStorage.getItem(KEY));
    if (!session?.accessToken || !session?.expiresAt) return null;
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function saveSession(loginResponse) {
  const session = {
    accessToken: loginResponse.accessToken,
    expiresAt: loginResponse.expiresAt,
    customer: loginResponse.customer,
  };
  sessionStorage.setItem(KEY, JSON.stringify(session));
  return session;
}

export function clearSession() {
  sessionStorage.removeItem(KEY);
}

export function currentToken() {
  return loadSession()?.accessToken ?? null;
}
