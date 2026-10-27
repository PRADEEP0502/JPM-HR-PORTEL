const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, '') ?? '';
const TOKEN_KEY = 'jpm-hrms.token';

export class ApiError extends Error {
  constructor(public status: number, message: string, public fieldErrors?: Record<string, string>) {
    super(message);
  }
}

/** Session token lives in sessionStorage (cleared when the tab closes); never in source or URLs. */
export const tokenStore = {
  get(): string | null {
    try {
      return sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string | null) {
    try {
      if (token) sessionStorage.setItem(TOKEN_KEY, token);
      else sessionStorage.removeItem(TOKEN_KEY);
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

async function request<T>(method: string, path: string, body?: unknown, isForm = false): Promise<T> {
  const headers: Record<string, string> = {};
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';
  let res: Response;
  try {
    res = await fetch(`${BASE}/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : isForm ? (body as FormData) : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and that the backend is running.');
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
      res = await fetch(`${BASE}/api${path}${qs(params)}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
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
      res = await fetch(`${BASE}/api${path}${qs(params)}`, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
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
