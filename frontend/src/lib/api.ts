const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';
const TOKEN_KEY = 'jpm-hrms.token';

export class ApiError extends Error {
  constructor(public status: number, message: string, public fieldErrors?: Record<string, string>) {
    super(message);
  }
}

/**
 * Session token lives in localStorage, so a user signs in once per device and stays signed in across tabs and
 * browser restarts until they sign out or the token expires (30 days). Never in source or URLs.
 */
export const tokenStore = {
  get(): string | null {
    try {
      // tokens saved by the earlier sessionStorage version are moved over once
      const legacy = sessionStorage.getItem(TOKEN_KEY);
      if (legacy) {
        localStorage.setItem(TOKEN_KEY, legacy);
        sessionStorage.removeItem(TOKEN_KEY);
      }
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string | null) {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* storage unavailable - token stays in memory for this page only */
    }
  },
};

let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn;
}

type Query = Record<string, string | number | boolean | null | undefined>;

export function qs(params: Query): string {
  const sp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v));
  });
  const s = sp.toString();
  return s ? `?${s}` : '';
}

// ---------------------------------------------------------------- server waking up
// The free backend host sleeps when unused; the first requests then fail with 502/503 (or time out) for up to
// a minute or two while it starts. Those requests are retried instead of shown as errors.

const WAKE_DEADLINE_MS = 120_000;
const wakeListeners = new Set<(waking: boolean) => void>();
let wakingCount = 0;

/** Subscribe to "server is starting" (true) / "server answered" (false). Returns an unsubscribe function. */
export function onServerWaking(fn: (waking: boolean) => void): () => void {
  wakeListeners.add(fn);
  return () => wakeListeners.delete(fn);
}

function setWaking(delta: number) {
  const before = wakingCount > 0;
  wakingCount = Math.max(0, wakingCount + delta);
  if (before !== wakingCount > 0) wakeListeners.forEach((fn) => fn(wakingCount > 0));
}

/** Responses from the hosting layer (not from the app, which always answers errors in JSON). */
function isHostUnavailable(res: Response, retryTimeouts: boolean): boolean {
  const statuses = retryTimeouts ? [502, 503, 504] : [502, 503];
  return statuses.includes(res.status) && !(res.headers.get('Content-Type') ?? '').includes('application/json');
}

/**
 * fetch() that waits for a sleeping backend. Reads (and sign-in) are retried on network errors and 502/503/504;
 * changes only on 502/503, which mean the request never reached the app, so nothing can be saved twice.
 */
async function fetchWaiting(url: string, init: RequestInit, safeToRepeat: boolean): Promise<Response> {
  const start = Date.now();
  let waiting = false;
  try {
    for (let attempt = 0; ; attempt++) {
      let res: Response | null = null;
      try {
        res = await fetch(url, init);
      } catch (e) {
        if (!safeToRepeat || Date.now() - start > WAKE_DEADLINE_MS) throw e;
      }
      if (res && !isHostUnavailable(res, safeToRepeat)) return res;
      if (res && Date.now() - start > WAKE_DEADLINE_MS) return res;
      if (!waiting) {
        waiting = true;
        setWaking(1);
      }
      await new Promise((r) => setTimeout(r, Math.min(3000 + attempt * 1000, 6000)));
    }
  } finally {
    if (waiting) setWaking(-1);
  }
}

async function request<T>(method: string, path: string, body?: unknown, isForm = false): Promise<T> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  let res: Response;
  try {
    res = await fetchWaiting(`${BASE}/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
    }, method === 'GET' || path === '/auth/login');
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
  }
  if (res.status === 401 && path !== '/auth/login') {
    onUnauthorized();
    throw new ApiError(401, 'Your session has expired. Please sign in again.');
  }
  if (!res.ok) throw await toError(res);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function toError(res: Response): Promise<ApiError> {
  try {
    const data = await res.json();
    return new ApiError(res.status, data.message || res.statusText, data.fieldErrors ?? undefined);
  } catch {
    return new ApiError(res.status, res.status === 403 ? 'You do not have permission to do this.' : `Request failed (${res.status})`);
  }
}

export const api = {
  get: <T>(path: string, params: Query = {}) => request<T>('GET', path + qs(params)),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
  upload: <T>(path: string, form: FormData) => request<T>('POST', path, form, true),

  /** Authenticated file fetch for the in-browser viewer; the file never gets a public URL. */
  async blob(path: string, params: Query = {}): Promise<Blob> {
    const token = tokenStore.get();
    let res: Response;
    try {
      res = await fetchWaiting(`${BASE}/api${path}${qs(params)}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} }, true);
    } catch {
      throw new ApiError(0, 'Cannot reach the server.');
    }
    if (res.status === 401) {
      onUnauthorized();
      throw new ApiError(401, 'Your session has expired.');
    }
    if (!res.ok) throw await toError(res);
    return res.blob();
  },

  /** Authenticated file download (Excel exports, resumes) - streamed to a Blob, then saved. */
  async download(path: string, params: Query = {}, fallbackName = 'download'): Promise<void> {
    const token = tokenStore.get();
    let res: Response;
    try {
      res = await fetchWaiting(`${BASE}/api${path}${qs(params)}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} }, true);
    } catch {
      throw new ApiError(0, 'Cannot reach the server.');
    }
    if (res.status === 401) {
      onUnauthorized();
      throw new ApiError(401, 'Your session has expired.');
    }
    if (!res.ok) throw await toError(res);
    const blob = await res.blob();
    const cd = res.headers.get('Content-Disposition') ?? '';
    const star = /filename\*=UTF-8''([^;]+)/i.exec(cd);
    const plain = /filename="?([^";]+)"?/i.exec(cd);
    const name = star ? decodeURIComponent(star[1]) : plain ? plain[1] : fallbackName;
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
