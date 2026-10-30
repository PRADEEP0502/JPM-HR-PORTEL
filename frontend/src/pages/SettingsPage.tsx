import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  CheckCircle2,
  ChevronDown,
  FlaskConical,
  KeyRound,
  MessagesSquare,
  Link2,
  Pencil,
  PlayCircle,
  Plug,
  ScrollText,
  Search,
  ShieldCheck,
  Trash2,
  UserPlus,
  Users,
  XCircle,
} from 'lucide-react';
import { api, ApiError } from '../lib/api';
import { useAuth } from '../lib/auth';
import { CANDIDATE_KEYS, useDashboard } from '../lib/queries';
import { useToast } from '../lib/toast';
import { ROLES, fmtDateTime, fmtRelative, labelOf } from '../lib/format';
import type { AuditEntry, IntegrationSettings, Page, Role, SyncRun, SyncTestResult, User } from '../lib/types';
import { Field, SectionTitle } from '../components/ui/Misc';
import { Pill } from '../components/ui/Pill';
import { Modal } from '../components/ui/Modal';
import { Pagination } from '../components/ui/Pagination';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';
import { CommunicationSettingsTab } from '../components/communication/CommunicationSettings';

const TABS = [
  { key: 'profile', label: 'My profile', icon: KeyRound, admin: false },
  { key: 'communication', label: 'Communication templates', icon: MessagesSquare, admin: true },
  { key: 'integration', label: 'Google Sheets integration', icon: Plug, admin: true },
  { key: 'sync', label: 'Sync history', icon: ScrollText, admin: true },
  { key: 'users', label: 'Users', icon: Users, admin: true },
  { key: 'audit', label: 'Audit log', icon: ShieldCheck, admin: true },
  { key: 'demo', label: 'Demo data', icon: FlaskConical, admin: true },
];

export default function SettingsPage() {
  const { isAdmin } = useAuth();
  const [params, setParams] = useSearchParams();
  const tabs = TABS.filter((t) => !t.admin || isAdmin);
  const tab = tabs.find((t) => t.key === params.get('tab'))?.key ?? 'profile';
  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
      <nav className="panel h-fit p-2 xl:col-span-3" aria-label="Settings sections">
        <ul className="grid grid-cols-2 gap-1 sm:grid-cols-3 xl:grid-cols-1">
          {tabs.map((t) => (
            <li key={t.key}>
              <button
                onClick={() => setParams({ tab: t.key }, { replace: true })}
                className={clsx('flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-left text-sm font-medium transition', tab === t.key ? 'bg-ink text-canvas' : 'text-ink-2 hover:bg-raised hover:text-ink')}
              >
                <t.icon size={16} /> {t.label}
              </button>
            </li>
          ))}
        </ul>
        {!isAdmin && <p className="px-3 py-2 text-xs text-ink-3">Integration, users and audit settings are available to administrators.</p>}
      </nav>
      <div className="xl:col-span-9">
        {tab === 'profile' && <ProfileTab />}
        {tab === 'communication' && <CommunicationSettingsTab />}
        {tab === 'integration' && <IntegrationTab />}
        {tab === 'sync' && <SyncHistoryTab />}
        {tab === 'users' && <UsersTab />}
        {tab === 'audit' && <AuditTab />}
        {tab === 'demo' && <DemoTab />}
      </div>
    </div>
  );
}

// ------------------------------------------------------------------ profile

function ProfileTab() {
  const { user } = useAuth();
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const change = useMutation({
    mutationFn: () => api.post('/auth/change-password', { currentPassword: current, newPassword: next }),
    onSuccess: () => {
      toast('success', 'Password changed');
      setCurrent('');
      setNext('');
      setConfirm('');
    },
    onError: (e) => setError(e.message),
  });
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (next !== confirm) return setError('New passwords do not match');
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,100}$/.test(next)) return setError('Use at least 8 characters with a letter and a digit');
    change.mutate();
  };
  return (
    <div className="space-y-6">
      <section className="panel p-5">
        <SectionTitle title="Account" />
        <dl className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-3">
          <div><dt className="text-xs text-ink-3">Name</dt><dd>{user?.fullName}</dd></div>
          <div><dt className="text-xs text-ink-3">Username</dt><dd>{user?.username}</dd></div>
          <div><dt className="text-xs text-ink-3">Role</dt><dd>{labelOf(ROLES, user?.role)}</dd></div>
        </dl>
      </section>
      <form className="panel max-w-xl space-y-4 p-5" onSubmit={submit}>
        <SectionTitle title="Change password" />
        {error && <p className="rounded-xl border border-bad/40 bg-bad/10 px-3 py-2 text-sm text-[#f29a9a]">{error}</p>}
        <Field label="Current password"><input type="password" autoComplete="current-password" className="input" value={current} onChange={(e) => setCurrent(e.target.value)} required /></Field>
        <Field label="New password" hint="At least 8 characters, including a letter and a digit."><input type="password" autoComplete="new-password" className="input" value={next} onChange={(e) => setNext(e.target.value)} required /></Field>
        <Field label="Confirm new password"><input type="password" autoComplete="new-password" className="input" value={confirm} onChange={(e) => setConfirm(e.target.value)} required /></Field>
        <button className="btn-primary" disabled={change.isPending}>{change.isPending && <Spinner size={14} />} Update password</button>
      </form>
    </div>
  );
}

// ------------------------------------------------------------------ integration

function IntegrationTab() {
  const toast = useToast();
  const qc = useQueryClient();
  const settings = useQuery({ queryKey: ['integration-settings'], queryFn: () => api.get<IntegrationSettings>('/sync/settings') });
  const [form, setForm] = useState({ spreadsheetId: '', sheetRange: '', formUrl: '', autoSyncEnabled: false, syncIntervalMinutes: 15 });
  const [mapping, setMapping] = useState<Record<string, string>>({});
  const [showMapping, setShowMapping] = useState(false);
  const [test, setTest] = useState<SyncTestResult | null>(null);
  const [lastRun, setLastRun] = useState<SyncRun | null>(null);

  useEffect(() => {
    const s = settings.data;
    if (!s) return;
    setForm({
      spreadsheetId: s.spreadsheetId ?? '',
      sheetRange: s.sheetRange ?? 'Form Responses 1',
      formUrl: s.formUrl ?? '',
      autoSyncEnabled: s.autoSyncEnabled,
      syncIntervalMinutes: s.syncIntervalMinutes,
    });
    setMapping(s.columnMapping ?? {});
  }, [settings.data]);

  const save = useMutation({
    mutationFn: () => api.put<IntegrationSettings>('/sync/settings', { ...form, columnMapping: mapping }),
    onSuccess: (s) => {
      qc.setQueryData(['integration-settings'], s);
      qc.invalidateQueries({ queryKey: ['sync-status'] });
      toast('success', 'Integration settings saved');
    },
    onError: (e) => toast('error', e.message),
  });
  const runTest = useMutation({
    mutationFn: () => api.post<SyncTestResult>('/sync/test'),
    onSuccess: setTest,
    onError: (e) => toast('error', e.message),
  });
  const runSync = useMutation({
    mutationFn: () => api.post<SyncRun>('/sync/run'),
    onSuccess: (r) => {
      setLastRun(r);
      CANDIDATE_KEYS.forEach((k) => qc.invalidateQueries({ queryKey: k }));
      ['sync-status', 'sync-runs'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
      toast(r.status === 'FAILED' ? 'error' : 'success', r.status === 'FAILED' ? `Sync failed: ${r.errorMessage}` : `Sync finished: ${r.createdCount} new candidate(s)`);
    },
    onError: (e) => toast('error', e.message),
  });

  const newKey = useMutation({
    mutationFn: () => api.post<IntegrationSettings>('/sync/push-key'),
    onSuccess: (s) => {
      qc.setQueryData(['integration-settings'], s);
      toast('success', 'Form key created. Paste it into the Google Form script.');
    },
    onError: (e) => toast('error', e.message),
  });
  const copy = (text: string) =>
    navigator.clipboard.writeText(text).then(() => toast('success', 'Copied'), () => toast('error', 'Copy failed. Select the text and copy it.'));

  if (settings.isLoading) return <div className="panel"><LoadingBlock /></div>;
  if (settings.error || !settings.data) return <div className="panel"><ErrorState error={settings.error} onRetry={() => settings.refetch()} /></div>;
  const s = settings.data;
  const dirty =
    form.spreadsheetId !== (s.spreadsheetId ?? '') ||
    form.sheetRange !== (s.sheetRange ?? '') ||
    form.formUrl !== (s.formUrl ?? '') ||
    form.autoSyncEnabled !== s.autoSyncEnabled ||
    form.syncIntervalMinutes !== s.syncIntervalMinutes ||
    JSON.stringify(mapping) !== JSON.stringify(s.columnMapping ?? {});

  return (
    <div className="space-y-6">
      <section className="panel space-y-4 p-5">
        <SectionTitle
          title="Google Form → dashboard (instant)"
          subtitle="The form's script sends each application here as soon as the candidate submits, with their resume and Aadhaar. No service account needed."
        />
        {s.formPushKey ? (
          <Field label="FORM_KEY (paste into the Google Form script)" hint="Keep it private. Anyone with this key can add candidates.">
            <div className="flex max-w-xl gap-2">
              <input className="input font-mono" readOnly value={s.formPushKey} onFocus={(e) => e.target.select()} />
              <button className="btn-secondary shrink-0" onClick={() => copy(s.formPushKey!)}>Copy</button>
            </div>
          </Field>
        ) : (
          <p className="text-sm text-ink-2">Create a form key, then paste it into the Google Form script and run <code>installAutoSend</code>.</p>
        )}
        <button
          className="btn-secondary"
          disabled={newKey.isPending}
          onClick={() => {
            if (!s.formPushKey || window.confirm('Make a new key? The old key stops working until you paste the new one into the script.')) newKey.mutate();
          }}
        >
          {newKey.isPending ? <Spinner size={14} /> : <KeyRound size={15} />} {s.formPushKey ? 'Make a new key' : 'Create form key'}
        </button>
      </section>

      <section className="panel p-5">
        <SectionTitle title="Server credentials" subtitle="The service-account key is read from the server only. It is never stored in the database or sent to browsers." />
        {s.credentialsConfigured ? (
          <div className="flex items-start gap-3 rounded-xl border border-good/40 bg-good/10 px-4 py-3 text-sm">
            <CheckCircle2 size={18} className="mt-0.5 shrink-0 text-good" />
            <div>
              Service-account credentials are configured.
              {s.serviceAccountEmail && (
                <p className="mt-1 text-ink-2">
                  Share the response sheet (Viewer access) with <code className="rounded bg-raised px-1.5 py-0.5 text-ink">{s.serviceAccountEmail}</code>. Do not make the sheet public.
                </p>
              )}
            </div>
          </div>
        ) : (
          <div className="flex items-start gap-3 rounded-xl border border-warn/40 bg-warn/10 px-4 py-3 text-sm text-[#f3c08a]">
            <XCircle size={18} className="mt-0.5 shrink-0" />
            <p>
              No credentials on the server. Set <code>GOOGLE_SERVICE_ACCOUNT_FILE</code> to the path of a service-account JSON key and restart the backend. See the README for step-by-step setup.
            </p>
          </div>
        )}
      </section>

      <section className="panel space-y-4 p-5">
        <SectionTitle title="Response sheet" subtitle={s.updatedAt ? `Last changed by ${s.updatedBy ?? '—'} ${fmtRelative(s.updatedAt)}` : undefined} />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <Field label="Spreadsheet ID" hint="The part of the sheet URL between /d/ and /edit.">
            <input className="input font-mono" maxLength={200} value={form.spreadsheetId} onChange={(e) => setForm({ ...form, spreadsheetId: e.target.value.trim() })} placeholder="Not set" />
          </Field>
          <Field label="Sheet / range" hint='Usually the tab name, e.g. "Form Responses 1".'>
            <input className="input" maxLength={200} value={form.sheetRange} onChange={(e) => setForm({ ...form, sheetRange: e.target.value })} />
          </Field>
          <Field label="Public Google Form link (optional)" hint="Shown to HR so they can share the application form." className="md:col-span-2">
            <input className="input" maxLength={500} value={form.formUrl} onChange={(e) => setForm({ ...form, formUrl: e.target.value.trim() })} placeholder="https://docs.google.com/forms/…" />
          </Field>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" className="h-4 w-4 accent-[#22a559]" checked={form.autoSyncEnabled} onChange={(e) => setForm({ ...form, autoSyncEnabled: e.target.checked })} />
            Automatic synchronization
          </label>
          <Field label="Sync interval">
            <select className="input" disabled={!form.autoSyncEnabled} value={form.syncIntervalMinutes} onChange={(e) => setForm({ ...form, syncIntervalMinutes: Number(e.target.value) })}>
              {[5, 10, 15, 30, 60, 180, 720, 1440].map((m) => <option key={m} value={m}>Every {m < 60 ? `${m} minutes` : `${m / 60} hour${m === 60 ? '' : 's'}`}</option>)}
            </select>
          </Field>
        </div>

        <div className="rounded-xl border border-line">
          <button className="flex w-full items-center justify-between px-4 py-3 text-left text-sm font-medium" onClick={() => setShowMapping((v) => !v)} aria-expanded={showMapping}>
            Column mapping overrides ({Object.keys(mapping).length})
            <ChevronDown size={16} className={clsx('transition', showMapping && 'rotate-180')} />
          </button>
          {showMapping && (
            <div className="border-t border-line p-4">
              <p className="mb-3 text-xs text-ink-3">
                Headers are detected automatically from common question titles. Only fill a field when your form uses a different question title. Run “Test connection” to load the actual headers.
              </p>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {s.fields.map((f) => (
                  <Field key={f.key} label={`${f.label}${f.required ? ' *' : ''}`} hint={test?.detectedMapping[f.key] ? `Detected: "${test.detectedMapping[f.key]}"` : undefined}>
                    {test?.headers.length ? (
                      <select className="input" value={mapping[f.key] ?? ''} onChange={(e) => setMapping((m) => { const n = { ...m }; if (e.target.value) n[f.key] = e.target.value; else delete n[f.key]; return n; })}>
                        <option value="">Automatic</option>
                        {test.headers.map((h) => <option key={h} value={h}>{h}</option>)}
                      </select>
                    ) : (
                      <input className="input" placeholder="Automatic" value={mapping[f.key] ?? ''} onChange={(e) => setMapping((m) => { const n = { ...m }; if (e.target.value) n[f.key] = e.target.value; else delete n[f.key]; return n; })} />
                    )}
                  </Field>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="flex flex-wrap gap-2">
          <button className="btn-primary" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>{save.isPending && <Spinner size={14} />} Save settings</button>
          <button className="btn-secondary" disabled={dirty || runTest.isPending} onClick={() => runTest.mutate()} title={dirty ? 'Save settings first' : undefined}>
            {runTest.isPending ? <Spinner size={14} /> : <Link2 size={15} />} Test connection
          </button>
          <button className="btn-secondary" disabled={dirty || runSync.isPending} onClick={() => runSync.mutate()} title={dirty ? 'Save settings first' : undefined}>
            {runSync.isPending ? <Spinner size={14} /> : <PlayCircle size={15} />} Sync now
          </button>
        </div>
        {dirty && <p className="text-xs text-warn">You have unsaved changes.</p>}
      </section>

      {test && (
        <section className={clsx('panel p-5', test.ok ? 'border-good/40' : 'border-bad/40')}>
          <SectionTitle title={test.ok ? 'Connection OK' : 'Connection problems'} subtitle={test.spreadsheetTitle ? `Spreadsheet: ${test.spreadsheetTitle} · ${test.dataRows} response row(s)` : undefined} />
          {test.problems.length > 0 && (
            <ul className="mb-3 list-inside list-disc space-y-1 text-sm text-[#f29a9a]">{test.problems.map((p) => <li key={p}>{p}</li>)}</ul>
          )}
          {Object.keys(test.detectedMapping).length > 0 && (
            <div className="grid grid-cols-1 gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
              {s.fields.map((f) => (
                <div key={f.key} className="flex justify-between gap-3 border-b border-line/40 py-1">
                  <span className="text-ink-3">{f.label}</span>
                  <span className={test.detectedMapping[f.key] ? 'text-ink' : 'text-ink-3'}>{test.detectedMapping[f.key] ?? 'not found'}</span>
                </div>
              ))}
            </div>
          )}
          {test.unmappedHeaders.length > 0 && <p className="mt-3 text-xs text-ink-3">Ignored columns: {test.unmappedHeaders.join(', ')}</p>}
        </section>
      )}
      {lastRun && <RunCard run={lastRun} defaultOpen />}
    </div>
  );
}

// ------------------------------------------------------------------ sync history

const runTone = { SUCCESS: 'green', PARTIAL: 'orange', FAILED: 'red', RUNNING: 'blue' } as const;

function RunCard({ run, defaultOpen = false }: { run: SyncRun; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="rounded-xl border border-line/70 bg-card">
      <button className="flex w-full flex-wrap items-center gap-3 px-4 py-3 text-left" onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        <Pill tone={runTone[run.status]}>{run.status}</Pill>
        <span className="text-sm">{fmtDateTime(run.startedAt)}</span>
        <span className="text-xs text-ink-3">by {run.triggeredBy}</span>
        <span className="flex-1" />
        <span className="text-xs text-ink-2">{run.createdCount} new · {run.duplicateCount} duplicate · {run.skippedCount} skipped · {run.warningCount} with warnings</span>
        <ChevronDown size={15} className={clsx('text-ink-3 transition', open && 'rotate-180')} />
      </button>
      {open && (
        <div className="border-t border-line px-4 py-3 text-sm">
          {run.errorMessage && <p className="mb-2 text-[#f29a9a]">{run.errorMessage}</p>}
          <p className="text-xs text-ink-3">{run.rowsRead} response row(s) read{run.finishedAt ? ` · finished ${fmtDateTime(run.finishedAt)}` : ''}</p>
          {run.messages.length > 0 ? (
            <ul className="mt-2 max-h-64 space-y-1 overflow-y-auto font-mono text-xs text-ink-2">{run.messages.map((m, i) => <li key={i}>{m}</li>)}</ul>
          ) : (
            !run.errorMessage && <p className="mt-2 text-xs text-ink-3">No row-level issues.</p>
          )}
        </div>
      )}
    </div>
  );
}

function SyncHistoryTab() {
  const runs = useQuery({ queryKey: ['sync-runs'], queryFn: () => api.get<SyncRun[]>('/sync/runs', { limit: 30 }), refetchInterval: 60_000 });
  return (
    <section className="panel p-5">
      <SectionTitle title="Synchronization history" subtitle="Each run imports only new responses. Duplicates and invalid rows are reported once." />
      {runs.isLoading ? <LoadingBlock /> : runs.error ? <ErrorState error={runs.error} onRetry={() => runs.refetch()} /> : !runs.data?.length ? (
        <EmptyState icon={<ScrollText size={20} />} title="No synchronization has run yet" hint="Configure the integration, then use “Sync now”." />
      ) : (
        <div className="space-y-2">{runs.data.map((r) => <RunCard key={r.id} run={r} />)}</div>
      )}
    </section>
  );
}

// ------------------------------------------------------------------ users

function UserModal({ open, user, onClose }: { open: boolean; user: User | null; onClose: () => void }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [f, setF] = useState({ username: '', fullName: '', email: '', role: 'HR' as Role, enabled: true, password: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (open) {
      setF({ username: user?.username ?? '', fullName: user?.fullName ?? '', email: user?.email ?? '', role: user?.role ?? 'HR', enabled: user?.enabled ?? true, password: '' });
      setErrors({});
    }
  }, [open, user]);
  const save = useMutation({
    mutationFn: () =>
      user
        ? api.put(`/users/${user.id}`, { fullName: f.fullName, email: f.email || null, role: f.role, enabled: f.enabled, newPassword: f.password || null })
        : api.post('/users', { username: f.username, fullName: f.fullName, email: f.email || null, role: f.role, password: f.password }),
    onSuccess: () => {
      toast('success', user ? 'User updated' : 'User created');
      qc.invalidateQueries({ queryKey: ['users'] });
      onClose();
    },
    onError: (e) => {
      if (e instanceof ApiError && e.fieldErrors) setErrors(e.fieldErrors);
      toast('error', e.message);
    },
  });
  const pwOk = (!user && /^(?=.*[A-Za-z])(?=.*\d).{8,100}$/.test(f.password)) || (user && (!f.password || /^(?=.*[A-Za-z])(?=.*\d).{8,100}$/.test(f.password)));
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={user ? `Edit ${user.username}` : 'Add user'}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" disabled={save.isPending || !f.fullName.trim() || (!user && !f.username.trim()) || !pwOk} onClick={() => save.mutate()}>
            {save.isPending && <Spinner size={14} />} Save
          </button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Username *" error={errors.username}>
          <input className="input" disabled={!!user} maxLength={100} value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} />
        </Field>
        <Field label="Full name *" error={errors.fullName}><input className="input" maxLength={150} value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} /></Field>
        <Field label="Email" error={errors.email}><input className="input" type="email" maxLength={200} value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
        <Field label="Role" hint={ROLES.find((r) => r.value === f.role)?.hint}>
          <select className="input" value={f.role} onChange={(e) => setF({ ...f, role: e.target.value as Role })}>
            {ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
        </Field>
        <Field label={user ? 'Reset password (optional)' : 'Initial password *'} error={errors.password ?? errors.newPassword} hint="8+ characters with a letter and a digit" className="sm:col-span-2">
          <input className="input" type="password" autoComplete="new-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
        </Field>
        {user && (
          <label className="flex items-center gap-2 text-sm sm:col-span-2">
            <input type="checkbox" className="h-4 w-4 accent-[#22a559]" checked={f.enabled} onChange={(e) => setF({ ...f, enabled: e.target.checked })} /> Account enabled
          </label>
        )}
      </div>
    </Modal>
  );
}

function UsersTab() {
  const users = useQuery({ queryKey: ['users'], queryFn: () => api.get<User[]>('/users') });
  const [modal, setModal] = useState<{ open: boolean; user: User | null }>({ open: false, user: null });
  return (
    <section className="panel p-5">
      <SectionTitle title="Users" subtitle="Administrators manage settings and users; HR manages candidates and interviews." action={<button className="btn-primary" onClick={() => setModal({ open: true, user: null })}><UserPlus size={15} /> Add user</button>} />
      {users.isLoading ? <LoadingBlock /> : users.error ? <ErrorState error={users.error} onRetry={() => users.refetch()} /> : (
        <div className="-mx-5 overflow-x-auto">
          <table className="w-full min-w-[640px]">
            <thead><tr className="border-b border-line"><th className="th pl-5">User</th><th className="th">Role</th><th className="th">Status</th><th className="th">Last sign-in</th><th className="th pr-5 text-right">Actions</th></tr></thead>
            <tbody>
              {users.data?.map((u) => (
                <tr key={u.id} className="border-b border-line/40 last:border-0">
                  <td className="td pl-5"><span className="block font-medium text-ink">{u.fullName}</span><span className="text-xs text-ink-3">{u.username}{u.email ? ` · ${u.email}` : ''}</span></td>
                  <td className="td">{labelOf(ROLES, u.role)}</td>
                  <td className="td">{u.enabled ? <Pill tone="green">Active</Pill> : <Pill tone="grey">Disabled</Pill>}</td>
                  <td className="td">{u.lastLoginAt ? fmtRelative(u.lastLoginAt) : 'Never'}</td>
                  <td className="td pr-5 text-right"><button className="btn-ghost p-2" aria-label={`Edit ${u.username}`} onClick={() => setModal({ open: true, user: u })}><Pencil size={15} /></button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <UserModal open={modal.open} user={modal.user} onClose={() => setModal({ open: false, user: null })} />
    </section>
  );
}

// ------------------------------------------------------------------ audit

const AUDIT_ACTIONS = ['STATUS_CHANGED', 'VERIFICATION_CHANGED', 'RATING_UPDATED', 'CANDIDATE_UPDATED', 'REMARK_ADDED', 'INTERVIEW_SCHEDULED', 'INTERVIEW_RESCHEDULED', 'INTERVIEW_COMPLETED', 'INTERVIEW_CANCELLED', 'DOCUMENT_UPLOADED', 'DOCUMENT_DOWNLOADED', 'EXPORT', 'CANDIDATE_IMPORTED', 'SYNC_RUN', 'INTEGRATION_SETTINGS_UPDATED', 'WHATSAPP_PREPARED', 'WHATSAPP_MARKED_SENT', 'EMAIL_ACCEPTED', 'EMAIL_FAILED', 'CALL_INITIATED', 'CALL_OUTCOME', 'TEMPLATE_UPDATED', 'COMM_SETTINGS_UPDATED', 'EMAIL_TEST', 'DOCUMENT_DELETED', 'USER_CREATED', 'USER_UPDATED', 'DEPARTMENT_CREATED', 'DEPARTMENT_UPDATED', 'DEPARTMENT_DELETED', 'LOGIN', 'LOGIN_FAILED', 'PASSWORD_CHANGED', 'DEMO_DATA_PURGED'];

function AuditTab() {
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => {
    const t = setTimeout(() => { setDebounced(q.trim()); setPage(0); }, 300);
    return () => clearTimeout(t);
  }, [q]);
  const logs = useQuery({
    queryKey: ['audit', debounced, action, page],
    queryFn: () => api.get<Page<AuditEntry>>('/audit-logs', { q: debounced, action, page, size: 25 }),
    placeholderData: keepPreviousData,
  });
  return (
    <section className="panel p-5">
      <SectionTitle title="Audit log" subtitle="Append-only record of sign-ins, status changes, exports, document access and configuration changes." />
      <div className="mb-4 flex flex-wrap gap-3">
        <label className="relative min-w-[200px] flex-1">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
          <input className="input pl-9" placeholder="Search details or user" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Search audit log" />
        </label>
        <select className="input w-auto" value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }} aria-label="Action">
          <option value="">All actions</option>
          {AUDIT_ACTIONS.map((a) => <option key={a} value={a}>{a.replace(/_/g, ' ').toLowerCase()}</option>)}
        </select>
      </div>
      {logs.isLoading ? <LoadingBlock /> : logs.error ? <ErrorState error={logs.error} onRetry={() => logs.refetch()} /> : !logs.data?.totalElements ? <EmptyState title="No audit entries" /> : (
        <>
          <div className="-mx-5 overflow-x-auto">
            <table className="w-full min-w-[720px]">
              <thead><tr className="border-b border-line"><th className="th pl-5">When</th><th className="th">User</th><th className="th">Action</th><th className="th pr-5">Details</th></tr></thead>
              <tbody>
                {logs.data.content.map((a) => (
                  <tr key={a.id} className="border-b border-line/40 last:border-0 align-top">
                    <td className="td pl-5 tabular-nums">{fmtDateTime(a.occurredAt)}</td>
                    <td className="td">{a.actor}</td>
                    <td className="td"><span className={clsx('font-mono text-xs', a.action === 'LOGIN_FAILED' ? 'text-bad' : 'text-ink')}>{a.action}</span></td>
                    <td className="td whitespace-normal pr-5 text-ink-2">
                      {a.entityType === 'CANDIDATE' && a.entityId ? <Link className="hover:underline" to={`/candidates/${a.entityId}`}>{a.details}</Link> : a.details}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={logs.data.page} size={logs.data.size} totalPages={logs.data.totalPages} totalElements={logs.data.totalElements} onPage={setPage} />
        </>
      )}
    </section>
  );
}

// ------------------------------------------------------------------ demo

function DemoTab() {
  const { data } = useDashboard();
  const toast = useToast();
  const qc = useQueryClient();
  const purge = useMutation({
    mutationFn: () => api.del<{ removed: number }>('/admin/demo-data'),
    onSuccess: (r) => {
      toast('success', `Removed ${r.removed} demo candidate(s)`);
      CANDIDATE_KEYS.forEach((k) => qc.invalidateQueries({ queryKey: k }));
    },
    onError: (e) => toast('error', e.message),
  });
  const add = useMutation({
    mutationFn: () => api.post<{ added: number }>('/admin/demo-data'),
    onSuccess: (r) => {
      toast('success', `Added ${r.added} sample candidates`);
      CANDIDATE_KEYS.forEach((k) => qc.invalidateQueries({ queryKey: k }));
    },
    onError: (e) => toast('error', e.message),
  });
  const reset = useMutation({
    mutationFn: () => api.post<{ removed: number }>('/admin/candidates/reset', { confirm: 'DELETE ALL' }),
    onSuccess: (r) => {
      toast('success', `Deleted ${r.removed} candidate(s)`);
      CANDIDATE_KEYS.forEach((k) => qc.invalidateQueries({ queryKey: k }));
    },
    onError: (e) => toast('error', e.message),
  });
  const confirmReset = () => {
    const typed = window.prompt('This permanently deletes EVERY candidate and cannot be undone.\n\nType DELETE ALL to confirm.');
    if (typed === null) return;
    if (typed.trim() !== 'DELETE ALL') toast('error', 'Not deleted - you must type DELETE ALL exactly');
    else reset.mutate();
  };
  const count = data?.demoCandidates ?? 0;
  return (
    <section className="panel space-y-4 p-5">
      <SectionTitle title="Demo data" subtitle="Demo mode seeds clearly labelled sample candidates into an empty database so the dashboard can be evaluated without live data." />
      <dl className="grid grid-cols-1 gap-4 text-sm sm:grid-cols-2">
        <div><dt className="text-xs text-ink-3">Demo mode</dt><dd>{data?.demoMode ? 'On (DEMO_MODE=true)' : 'Off'}</dd></div>
        <div><dt className="text-xs text-ink-3">Demo candidates in database</dt><dd>{count}</dd></div>
      </dl>
      <div className="rounded-xl border border-line p-4 text-sm">
        <p className="font-medium">Add sample candidates</p>
        <p className="mt-1 text-ink-3">Adds 5 new applicants tagged DEMO, spread over your active departments and their sub-work, to try the dashboard. Remove them below when you are done.</p>
        <button className="btn-secondary mt-3" disabled={add.isPending} onClick={() => add.mutate()}>
          {add.isPending ? <Spinner size={14} /> : <UserPlus size={15} />} Add 5 sample candidates
        </button>
      </div>
      <div className="rounded-xl border border-bad/30 bg-bad/5 p-4 text-sm">
        <p className="font-medium">Remove all demo candidates</p>
        <p className="mt-1 text-ink-3">Permanently deletes every record tagged DEMO, with its interviews, remarks and documents. Live (Google Form) candidates are not affected. To stop demo data from returning, also set DEMO_MODE=false.</p>
        <button
          className="btn-danger mt-3"
          disabled={count === 0 || purge.isPending}
          onClick={() => window.confirm(`Permanently delete ${count} demo candidates? This cannot be undone.`) && purge.mutate()}
        >
          {purge.isPending ? <Spinner size={14} /> : <Trash2 size={15} />} Remove demo data
        </button>
      </div>
      <div className="rounded-xl border border-bad/30 bg-bad/5 p-4 text-sm">
        <p className="font-medium">Delete all candidates</p>
        <p className="mt-1 text-ink-3">Permanently deletes every candidate (Google Form, manual and demo) with their interviews, remarks, messages and documents. Departments, users and settings are kept. Google Form responses already imported are not imported again; only new responses arrive on the next sync.</p>
        <button className="btn-danger mt-3" disabled={reset.isPending} onClick={confirmReset}>
          {reset.isPending ? <Spinner size={14} /> : <Trash2 size={15} />} Delete all candidates
        </button>
      </div>
    </section>
  );
}
