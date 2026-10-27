import { useState } from 'react';
import { AlertCircle, FlaskConical, Lock, User } from 'lucide-react';
import { ApiError } from '../lib/api';
import { useAuth } from '../lib/auth';
import { usePublicInfo } from '../lib/queries';
import { Logo } from '../components/layout/AppLayout';
import { Spinner } from '../components/ui/States';

export default function LoginPage() {
  const { login } = useAuth();
  const { data: info, isError, isPending, failureCount } = usePublicInfo();
  // The first check failed and retries are running: the backend is most likely waking up from sleep.
  const waking = isPending && failureCount > 0;
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(username.trim(), password);
    } catch (err) {
      const starting = err instanceof ApiError && [0, 502, 503, 504].includes(err.status);
      setError(starting ? 'The server is still starting up. Please wait a moment and sign in again.'
        : err instanceof Error ? err.message : 'Sign-in failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <div className="w-full max-w-md animate-fade-up">
        <div className="mb-8 flex justify-center">
          <Logo />
        </div>
        <form onSubmit={submit} className="panel space-y-5 p-6 sm:p-8">
          <div>
            <h1 className="text-xl font-semibold">Sign in</h1>
            <p className="mt-1 text-sm text-ink-3">Internal recruitment dashboard for JPM HR staff. Candidates apply through the Google Form.</p>
          </div>
          {error && (
            <div className="flex items-start gap-2 rounded-xl border border-bad/40 bg-bad/10 px-3 py-2.5 text-sm text-[#f29a9a]" role="alert">
              <AlertCircle size={16} className="mt-0.5 shrink-0" /> {error}
            </div>
          )}
          {waking && (
            <div className="flex items-start gap-2 rounded-xl border border-info/40 bg-info/10 px-3 py-2.5 text-sm text-[#8fb4f5]" role="status">
              <Spinner size={16} className="mt-0.5 shrink-0" />
              <span>Starting the server… it sleeps when no one has used it for a while. This can take about a minute; this page keeps trying automatically.</span>
            </div>
          )}
          {isError && (
            <div className="rounded-xl border border-warn/40 bg-warn/10 px-3 py-2.5 text-sm text-[#f3c08a]">The server is not reachable right now. Please refresh the page in a few minutes, or contact your administrator.</div>
          )}
          <label className="block">
            <span className="label">Username</span>
            <span className="relative block">
              <User size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
              <input className="input pl-9" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required maxLength={100} />
            </span>
          </label>
          <label className="block">
            <span className="label">Password</span>
            <span className="relative block">
              <Lock size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
              <input className="input pl-9" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required maxLength={100} />
            </span>
          </label>
          <button className="btn-primary w-full py-2.5" disabled={busy || !username || !password}>
            {busy && <Spinner size={16} />} Sign in
          </button>
          {info?.demoMode && (
            <div className="flex gap-2 rounded-xl border border-warn/30 bg-warn/5 px-3 py-2.5 text-xs text-ink-2">
              <FlaskConical size={15} className="mt-0.5 shrink-0 text-warn" />
              <p>
                <strong className="text-warn">Demo mode is on.</strong> Sample accounts: <code>admin / Admin@12345</code> (administrator) and{' '}
                <code>hr / Hr@12345</code> (HR). Data shown is sample data only.
              </p>
            </div>
          )}
        </form>
      </div>
    </div>
  );
}
