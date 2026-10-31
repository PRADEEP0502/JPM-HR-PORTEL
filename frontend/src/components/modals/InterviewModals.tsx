import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { AlertTriangle, CheckCircle2, MessageCircle, Search, X, XCircle } from 'lucide-react';
import { api, ApiError } from '../../lib/api';
import { INTERVIEW_MODES, fmtDateTime, toLocalInput } from '../../lib/format';
import { useToast } from '../../lib/toast';
import { useDepartments } from '../../lib/queries';
import type { CandidateDetail, CandidateListItem, CommSettings, Interview, InterviewMode, InterviewStatus, Page } from '../../lib/types';
import { emailProblem, phoneProblem } from '../../lib/contact';
import { CommunicationPanel } from '../communication/Communication';
import { Modal } from '../ui/Modal';
import { Avatar, Field } from '../ui/Misc';
import { Spinner } from '../ui/States';
import { useInvalidateCandidates } from './CandidateModals';


function minDateTime() {
  const d = new Date(Date.now() + 5 * 60_000);
  d.setSeconds(0, 0);
  return toLocalInput(d);
}
function maxDateTime() {
  return toLocalInput(new Date(Date.now() + 364 * 86_400_000));
}

function CandidatePicker({ value, onChange }: { value: { id: number; name: string; code: string } | null; onChange: (c: { id: number; name: string; code: string } | null) => void }) {
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  useEffect(() => {
    const t = setTimeout(() => setDebounced(q.trim()), 250);
    return () => clearTimeout(t);
  }, [q]);
  const results = useQuery({
    queryKey: ['candidate-picker', debounced],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { q: debounced, size: 8, sort: 'fullName', dir: 'asc' }),
    enabled: !value,
  });
  if (value) {
    return (
      <div className="flex items-center gap-3 rounded-xl border border-line bg-raised px-3 py-2">
        <Avatar name={value.name} size="sm" />
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium">{value.name}</div>
          <div className="text-xs text-ink-3">{value.code}</div>
        </div>
        <button type="button" className="btn-ghost p-1.5" onClick={() => onChange(null)} aria-label="Change candidate"><X size={14} /></button>
      </div>
    );
  }
  return (
    <div className="rounded-xl border border-line bg-raised">
      <label className="relative block">
        <span className="sr-only">Search candidate</span>
        <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
        <input className="input border-0 bg-transparent pl-9 focus:ring-0" placeholder="Search candidate by name or ID" value={q} onChange={(e) => setQ(e.target.value)} />
      </label>
      <ul className="max-h-52 overflow-y-auto border-t border-line py-1">
        {results.isLoading && <li className="px-3 py-2 text-sm text-ink-3">Searching…</li>}
        {results.data?.content.length === 0 && <li className="px-3 py-2 text-sm text-ink-3">No matching candidates</li>}
        {results.data?.content.map((c) => (
          <li key={c.id}>
            <button
              type="button"
              disabled={c.applicationStatus === 'REJECTED'}
              onClick={() => onChange({ id: c.id, name: c.fullName, code: c.candidateCode })}
              className="flex w-full items-center gap-3 px-3 py-2 text-left hover:bg-line/60 disabled:cursor-not-allowed disabled:opacity-40"
            >
              <Avatar name={c.fullName} size="sm" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm">{c.fullName}</span>
                <span className="block text-xs text-ink-3">{c.candidateCode} · {c.departmentName ?? 'Unassigned'}{c.applicationStatus === 'REJECTED' ? ' · rejected' : ''}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function todayInput() {
  return toLocalInput(new Date()).slice(0, 10);
}

/**
 * Two steps: (1) schedule - candidate details are filled in automatically, HR picks date & time,
 * the venue is the configured default; (2) review the interview details and the WhatsApp / email
 * previews, then send.
 */
export function ScheduleInterviewModal({ open, onClose, candidate }: { open: boolean; onClose: () => void; candidate?: { id: number; name: string; code: string } }) {
  const invalidate = useInvalidateCandidates();
  const [picked, setPicked] = useState<{ id: number; name: string; code: string } | null>(null);
  const [date, setDate] = useState('');
  const [time, setTime] = useState('10:00');
  const [duration, setDuration] = useState(45);
  const [interviewer, setInterviewer] = useState('');
  const [mode, setMode] = useState<InterviewMode>('IN_PERSON');
  const [location, setLocation] = useState('');
  const [remarks, setRemarks] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [scheduled, setScheduled] = useState<Interview | null>(null);

  useEffect(() => {
    if (open) {
      setPicked(candidate ?? null);
      setDate('');
      setTime('10:00');
      setInterviewer('');
      setLocation('');
      setRemarks('');
      setError(null);
      setScheduled(null);
    }
    // reset only when the modal opens or the preselected candidate changes (not on every parent render)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, candidate?.id]);

  const settings = useQuery({ queryKey: ['comm-settings'], queryFn: () => api.get<CommSettings>('/communication/settings'), enabled: open });
  const interviewerAccounts = useQuery({
    queryKey: ['interviewer-accounts'],
    queryFn: () => api.get<{ username: string; fullName: string }[]>('/interviewers'),
    enabled: open,
  });
  const linkedAccount = interviewerAccounts.data?.find(
    (a) => a.fullName.toLowerCase() === interviewer.trim().toLowerCase() || a.username.toLowerCase() === interviewer.trim().toLowerCase(),
  );
  const detail = useQuery({
    queryKey: ['candidate', picked?.id],
    queryFn: () => api.get<CandidateDetail>(`/candidates/${picked!.id}`),
    enabled: open && !!picked,
  });

  const save = useMutation({
    mutationFn: () =>
      api.post<Interview>('/interviews', {
        candidateId: picked!.id,
        scheduledAt: new Date(`${date}T${time}`).toISOString(),
        durationMinutes: duration,
        interviewer: interviewer.trim(),
        mode,
        location: location.trim() || null,
        remarks: remarks.trim() || null,
      }),
    onSuccess: (i) => {
      setScheduled(i);
      invalidate();
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not schedule interview'),
  });

  const submit = () => {
    setError(null);
    if (!picked) return setError('Select a candidate');
    if (!date || !time) return setError('Choose the interview date and time');
    const t = new Date(`${date}T${time}`).getTime();
    if (Number.isNaN(t) || t <= Date.now()) return setError('Interview date and time must be in the future');
    if (t > Date.now() + 365 * 86_400_000) return setError('Interview cannot be more than a year ahead');
    if (!interviewer.trim()) return setError('Assign an interviewer');
    save.mutate();
  };

  const c = detail.data;
  const department = c ? c.departmentName ?? c.departmentRaw : null;
  const phoneIssue = c ? phoneProblem(c.mobile) : null;
  const emailIssue = c ? emailProblem(c.email) : null;

  if (scheduled) {
    return (
      <Modal
        open={open}
        onClose={onClose}
        size="lg"
        title="Send interview invitation"
        description="The interview is saved. Review the details and the messages below, then send."
        footer={<button className="btn-primary" onClick={onClose}>Done</button>}
      >
        <div className="space-y-4">
          <p className="flex items-center gap-2 rounded-xl border border-good/40 bg-good/10 px-3 py-2 text-sm text-[#8fe0b0]">
            <CheckCircle2 size={16} /> Interview scheduled for {scheduled.candidateName} on {fmtDateTime(scheduled.scheduledAt)}.
          </p>
          <CommunicationPanel candidateId={scheduled.candidateId} interviewId={scheduled.id} />
        </div>
      </Modal>
    );
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title="Schedule interview"
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" onClick={submit} disabled={save.isPending}>
            {save.isPending && <Spinner size={14} />} Schedule &amp; review invitation
          </button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {error && <p className="rounded-xl border border-bad/40 bg-bad/10 px-3 py-2 text-sm text-[#f29a9a] sm:col-span-2" role="alert">{error}</p>}
        <div className="sm:col-span-2">
          <span className="label">Candidate *</span>
          <CandidatePicker value={picked} onChange={setPicked} />
        </div>

        {picked && (
          <div className="grid grid-cols-1 gap-3 rounded-xl border border-line bg-card p-3.5 text-sm sm:col-span-2 sm:grid-cols-2">
            {detail.isLoading ? (
              <span className="text-ink-3 sm:col-span-2"><Spinner size={14} className="inline" /> Loading candidate details…</span>
            ) : c ? (
              <>
                <ReadOnly label="Candidate name" value={c.fullName} />
                <ReadOnly label="Department" value={department} problem={department ? null : 'No department - assign one on the profile before sending invitations'} />
                <ReadOnly label="Candidate email" value={c.email} problem={emailIssue} />
                <ReadOnly label="Candidate mobile" value={c.mobile} problem={phoneIssue} />
              </>
            ) : (
              <span className="text-bad sm:col-span-2">Could not load candidate details.</span>
            )}
          </div>
        )}

        <Field label="Interview date *">
          <input type="date" className="input" min={todayInput()} max={maxDateTime().slice(0, 10)} value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
        <Field label="Interview time *" hint="Your local time">
          <input type="time" className="input" step={300} value={time} onChange={(e) => setTime(e.target.value)} />
        </Field>
        <Field label="Interview venue" className="sm:col-span-2" hint="Fixed venue from Settings → Communication; used in WhatsApp and email.">
          <input className="input cursor-not-allowed opacity-80" readOnly value={settings.data?.defaultVenue ?? 'Loading…'} aria-readonly="true" />
        </Field>
        <Field
          label="Interviewer *"
          hint={
            linkedAccount
              ? `Linked to interviewer account "${linkedAccount.username}" - they will see this interview and can record feedback.`
              : 'Pick an interviewer account to let them see the interview and record feedback, or type any name.'
          }
        >
          <input className="input" maxLength={150} list="interviewer-accounts" value={interviewer} onChange={(e) => setInterviewer(e.target.value)} placeholder="e.g. Ramesh Kumar" />
          <datalist id="interviewer-accounts">
            {interviewerAccounts.data?.map((a) => <option key={a.username} value={a.fullName} />)}
          </datalist>
        </Field>
        <Field label="Mode">
          <select className="input" value={mode} onChange={(e) => setMode(e.target.value as InterviewMode)}>
            {INTERVIEW_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </Field>
        <Field label="Duration">
          <select className="input" value={duration} onChange={(e) => setDuration(Number(e.target.value))}>
            {[15, 30, 45, 60, 90, 120].map((m) => <option key={m} value={m}>{m} minutes</option>)}
          </select>
        </Field>
        <Field label={mode === 'IN_PERSON' ? 'Room (internal, optional)' : 'Meeting link / phone (internal, optional)'} className="sm:col-span-2">
          <input className="input" maxLength={300} value={location} onChange={(e) => setLocation(e.target.value)} />
        </Field>
        <Field label="HR remarks (optional)" className="sm:col-span-2">
          <textarea className="input min-h-[72px]" maxLength={2000} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}

interface GroupResult {
  candidateId: number;
  candidateName: string;
  interview: Interview | null;
  error: string | null;
  emailSent: boolean;
  emailError: string | null;
}

/**
 * Group interview: pick any number of candidates and give them all the same date, time, interviewer and venue.
 * Each candidate gets their own interview; emails can go out right away, WhatsApp is one click per candidate.
 */
export function GroupInterviewModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const invalidate = useInvalidateCandidates();
  const toast = useToast();
  const departments = useDepartments();
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [selected, setSelected] = useState<Map<number, string>>(new Map());
  const [date, setDate] = useState('');
  const [time, setTime] = useState('10:00');
  const [duration, setDuration] = useState(60);
  const [interviewer, setInterviewer] = useState('');
  const [mode, setMode] = useState<InterviewMode>('IN_PERSON');
  const [remarks, setRemarks] = useState('');
  const [sendEmail, setSendEmail] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<GroupResult[] | null>(null);
  const [whatsappDone, setWhatsappDone] = useState<Set<number>>(new Set());

  useEffect(() => {
    if (open) {
      setQ('');
      setDepartmentId('');
      setSelected(new Map());
      setDate('');
      setTime('10:00');
      setInterviewer('');
      setRemarks('');
      setSendEmail(true);
      setError(null);
      setResults(null);
      setWhatsappDone(new Set());
    }
  }, [open]);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(q.trim()), 250);
    return () => clearTimeout(t);
  }, [q]);

  const settings = useQuery({ queryKey: ['comm-settings'], queryFn: () => api.get<CommSettings>('/communication/settings'), enabled: open });
  const interviewerAccounts = useQuery({
    queryKey: ['interviewer-accounts'],
    queryFn: () => api.get<{ username: string; fullName: string }[]>('/interviewers'),
    enabled: open,
  });
  const list = useQuery({
    queryKey: ['group-picker', debounced, departmentId],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { q: debounced, departmentId, size: 50, sort: 'fullName', dir: 'asc' }),
    enabled: open && !results,
  });
  const shown = (list.data?.content ?? []).filter((c) => c.applicationStatus !== 'REJECTED');
  const allShownSelected = shown.length > 0 && shown.every((c) => selected.has(c.id));

  const toggle = (c: CandidateListItem) =>
    setSelected((m) => {
      const n = new Map(m);
      if (n.has(c.id)) n.delete(c.id);
      else n.set(c.id, c.fullName);
      return n;
    });
  const toggleShown = () =>
    setSelected((m) => {
      const n = new Map(m);
      shown.forEach((c) => (allShownSelected ? n.delete(c.id) : n.set(c.id, c.fullName)));
      return n;
    });

  const save = useMutation({
    mutationFn: () =>
      api.post<GroupResult[]>('/interviews/group', {
        candidateIds: [...selected.keys()],
        scheduledAt: new Date(`${date}T${time}`).toISOString(),
        durationMinutes: duration,
        interviewer: interviewer.trim(),
        mode,
        remarks: remarks.trim() || null,
        sendEmail,
      }),
    onSuccess: (r) => {
      setResults(r);
      invalidate();
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not schedule the interviews'),
  });

  const whatsapp = useMutation({
    mutationFn: async ({ r, win }: { r: GroupResult; win: Window | null }) => {
      try {
        const res = await api.post<{ url: string }>(`/candidates/${r.candidateId}/communications/whatsapp`, {
          interviewId: r.interview!.id,
          purpose: 'INTERVIEW_INVITATION',
        });
        if (win) {
          win.opener = null;
          win.location.href = res.url;
        } else {
          window.location.href = res.url;
        }
        return r.candidateId;
      } catch (e) {
        win?.close();
        throw e;
      }
    },
    onSuccess: (id) => setWhatsappDone((s) => new Set(s).add(id)),
    onError: (e) => toast('error', e.message),
  });

  const submit = () => {
    setError(null);
    if (selected.size === 0) return setError('Select at least one candidate');
    if (selected.size > 100) return setError('Select up to 100 candidates at a time');
    if (!date || !time) return setError('Choose the interview date and time');
    const t = new Date(`${date}T${time}`).getTime();
    if (Number.isNaN(t) || t <= Date.now()) return setError('Interview date and time must be in the future');
    if (t > Date.now() + 365 * 86_400_000) return setError('Interview cannot be more than a year ahead');
    if (!interviewer.trim()) return setError('Assign an interviewer');
    save.mutate();
  };

  if (results) {
    const ok = results.filter((r) => r.interview);
    return (
      <Modal
        open={open}
        onClose={onClose}
        size="lg"
        title="Group interview scheduled"
        description={`${ok.length} of ${results.length} candidate(s) on ${fmtDateTime(new Date(`${date}T${time}`).toISOString())}. Send the WhatsApp invitations below, one click each.`}
        footer={<button className="btn-primary" onClick={onClose}>Done</button>}
      >
        <ul className="divide-y divide-line rounded-xl border border-line">
          {results.map((r) => (
            <li key={r.candidateId} className="flex flex-wrap items-center gap-3 px-3 py-2.5">
              <Avatar name={r.candidateName} size="sm" />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium">{r.candidateName}</div>
                {r.error ? (
                  <div className="flex items-center gap-1 text-xs text-bad"><XCircle size={12} /> Not scheduled: {r.error}</div>
                ) : (
                  <div className="text-xs text-ink-3">
                    Scheduled
                    {sendEmail && (r.emailSent ? <span className="text-good"> · email sent</span> : <span className="text-warn"> · email not sent: {r.emailError}</span>)}
                  </div>
                )}
              </div>
              {r.interview && (
                <button
                  className="btn-secondary px-2.5 py-1.5 text-xs"
                  disabled={whatsapp.isPending}
                  onClick={() => whatsapp.mutate({ r, win: window.open('about:blank', '_blank') })}
                >
                  {whatsappDone.has(r.candidateId) ? <CheckCircle2 size={14} className="text-good" /> : <MessageCircle size={14} />}
                  {whatsappDone.has(r.candidateId) ? 'WhatsApp opened' : 'WhatsApp'}
                </button>
              )}
            </li>
          ))}
        </ul>
      </Modal>
    );
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="lg"
      title="Group interview"
      description="Same date, time and interviewer for every selected candidate."
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" onClick={submit} disabled={save.isPending}>
            {save.isPending && <Spinner size={14} />} Schedule {selected.size || ''} candidate{selected.size === 1 ? '' : 's'}
          </button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {error && <p className="rounded-xl border border-bad/40 bg-bad/10 px-3 py-2 text-sm text-[#f29a9a] sm:col-span-2" role="alert">{error}</p>}
        <div className="sm:col-span-2">
          <span className="label">Candidates * ({selected.size} selected)</span>
          <div className="rounded-xl border border-line bg-raised">
            <div className="flex flex-wrap gap-2 border-b border-line p-2">
              <label className="relative min-w-[160px] flex-1">
                <span className="sr-only">Search candidates</span>
                <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
                <input className="input pl-9" placeholder="Search by name or ID" value={q} onChange={(e) => setQ(e.target.value)} />
              </label>
              <select className="input w-auto" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} aria-label="Department">
                <option value="">All departments</option>
                {departments.data?.filter((d) => d.active).map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
              </select>
            </div>
            <label className="flex items-center gap-2 border-b border-line px-3 py-2 text-xs text-ink-3">
              <input type="checkbox" className="h-4 w-4 accent-[#22a559]" checked={allShownSelected} onChange={toggleShown} disabled={shown.length === 0} />
              Select all shown ({shown.length})
            </label>
            <ul className="max-h-60 overflow-y-auto py-1">
              {list.isLoading && <li className="px-3 py-2 text-sm text-ink-3">Loading…</li>}
              {list.data && shown.length === 0 && <li className="px-3 py-2 text-sm text-ink-3">No matching candidates</li>}
              {shown.map((c) => (
                <li key={c.id}>
                  <label className="flex cursor-pointer items-center gap-3 px-3 py-1.5 hover:bg-line/60">
                    <input type="checkbox" className="h-4 w-4 accent-[#22a559]" checked={selected.has(c.id)} onChange={() => toggle(c)} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm">{c.fullName}</span>
                      <span className="block text-xs text-ink-3">{c.candidateCode} · {c.departmentName ?? 'Unassigned'}{c.position ? ` · ${c.position}` : ''}</span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <Field label="Interview date *">
          <input type="date" className="input" min={todayInput()} max={maxDateTime().slice(0, 10)} value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
        <Field label="Interview time *" hint="Your local time">
          <input type="time" className="input" step={300} value={time} onChange={(e) => setTime(e.target.value)} />
        </Field>
        <Field label="Interview venue" className="sm:col-span-2" hint="Fixed venue from Settings → Communication.">
          <input className="input cursor-not-allowed opacity-80" readOnly value={settings.data?.defaultVenue ?? 'Loading…'} aria-readonly="true" />
        </Field>
        <Field label="Interviewer *">
          <input className="input" maxLength={150} list="group-interviewer-accounts" value={interviewer} onChange={(e) => setInterviewer(e.target.value)} placeholder="e.g. Ramesh Kumar" />
          <datalist id="group-interviewer-accounts">
            {interviewerAccounts.data?.map((a) => <option key={a.username} value={a.fullName} />)}
          </datalist>
        </Field>
        <Field label="Mode">
          <select className="input" value={mode} onChange={(e) => setMode(e.target.value as InterviewMode)}>
            {INTERVIEW_MODES.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
        </Field>
        <Field label="Duration">
          <select className="input" value={duration} onChange={(e) => setDuration(Number(e.target.value))}>
            {[15, 30, 45, 60, 90, 120, 180, 240].map((m) => <option key={m} value={m}>{m} minutes</option>)}
          </select>
        </Field>
        <Field label="HR remarks (optional)" className="sm:col-span-2">
          <textarea className="input min-h-[60px]" maxLength={2000} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
        </Field>
        <label className="flex items-center gap-2 text-sm sm:col-span-2">
          <input type="checkbox" className="h-4 w-4 accent-[#22a559]" checked={sendEmail} onChange={(e) => setSendEmail(e.target.checked)} />
          Email the invitation to everyone now (candidates with a valid email)
        </label>
      </div>
    </Modal>
  );
}

function ReadOnly({ label, value, problem }: { label: string; value: string | null; problem?: string | null }) {
  return (
    <div className="min-w-0">
      <p className="text-xs text-ink-3">{label}</p>
      <p className="truncate">{value ?? '—'}</p>
      {problem && <p className="flex items-center gap-1 text-xs text-warn"><AlertTriangle size={12} /> {problem}</p>}
    </div>
  );
}

type Action = 'COMPLETED' | 'CANCELLED' | 'RESCHEDULED';

export function InterviewStatusModal({ interview, onClose }: { interview: Interview | null; onClose: () => void }) {
  const toast = useToast();
  const invalidate = useInvalidateCandidates();
  const [action, setAction] = useState<Action>('COMPLETED');
  const [when, setWhen] = useState('');
  const [remarks, setRemarks] = useState('');
  const [error, setError] = useState<string | null>(null);
  const isFuture = interview ? new Date(interview.scheduledAt).getTime() > Date.now() : false;

  useEffect(() => {
    if (interview) {
      setAction(new Date(interview.scheduledAt).getTime() > Date.now() ? 'RESCHEDULED' : 'COMPLETED');
      setWhen('');
      setRemarks(interview.remarks ?? '');
      setError(null);
    }
  }, [interview]);

  const save = useMutation({
    mutationFn: () =>
      api.patch<Interview>(`/interviews/${interview!.id}/status`, {
        status: action as InterviewStatus,
        remarks: remarks.trim() || null,
        newScheduledAt: action === 'RESCHEDULED' ? new Date(when).toISOString() : null,
      }),
    onSuccess: () => {
      toast('success', 'Interview updated');
      invalidate();
      onClose();
    },
    onError: (e) => setError(e.message),
  });

  const submit = () => {
    setError(null);
    if (action === 'COMPLETED' && isFuture) return setError('An interview cannot be completed before its scheduled time');
    if (action === 'RESCHEDULED') {
      if (!when) return setError('Choose the new date and time');
      if (new Date(when).getTime() <= Date.now()) return setError('The new date and time must be in the future');
    }
    save.mutate();
  };

  return (
    <Modal
      open={!!interview}
      onClose={onClose}
      title="Update interview"
      description={interview ? `${interview.candidateName} · ${fmtDateTime(interview.scheduledAt)}` : undefined}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Close</button>
          <button className="btn-primary" onClick={submit} disabled={save.isPending}>
            {save.isPending && <Spinner size={14} />} Save
          </button>
        </>
      }
    >
      <div className="space-y-4">
        {error && <p className="rounded-xl border border-bad/40 bg-bad/10 px-3 py-2 text-sm text-[#f29a9a]" role="alert">{error}</p>}
        <fieldset>
          <legend className="label">New status</legend>
          <div className="grid grid-cols-3 gap-2">
            {(['COMPLETED', 'RESCHEDULED', 'CANCELLED'] as Action[]).map((a) => (
              <label
                key={a}
                className={`cursor-pointer rounded-xl border px-3 py-2 text-center text-sm transition ${action === a ? 'border-info bg-info/15 text-ink' : 'border-line text-ink-2 hover:bg-raised'} ${a === 'COMPLETED' && isFuture ? 'pointer-events-none opacity-40' : ''}`}
              >
                <input type="radio" className="sr-only" name="interview-action" checked={action === a} onChange={() => setAction(a)} disabled={a === 'COMPLETED' && isFuture} />
                {a === 'COMPLETED' ? 'Completed' : a === 'RESCHEDULED' ? 'Reschedule' : 'Cancel'}
              </label>
            ))}
          </div>
          {isFuture && <p className="mt-1.5 text-xs text-ink-3">“Completed” becomes available once the scheduled time has passed.</p>}
        </fieldset>
        {action === 'RESCHEDULED' && (
          <Field label="New date & time *">
            <input type="datetime-local" className="input" min={minDateTime()} max={maxDateTime()} value={when} onChange={(e) => setWhen(e.target.value)} />
          </Field>
        )}
        <Field label="Interview remarks / feedback">
          <textarea className="input min-h-[84px]" maxLength={2000} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}
