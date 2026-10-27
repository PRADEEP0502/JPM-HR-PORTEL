import { Fragment, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  AlertTriangle,
  CalendarClock,
  CheckCircle2,
  ChevronDown,
  Info,
  Mail,
  MessageCircle,
  Phone,
  PhoneCall,
  RotateCw,
  Send,
  XCircle,
} from 'lucide-react';
import { api } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { TEMPLATE_PURPOSES, fmtDateTime, fmtRelative, labelOf } from '../../lib/format';
import type { CallOutcome, CommChannel, CommStatus, CommunicationRecord, Compose, TemplatePurpose } from '../../lib/types';
import { Modal } from '../ui/Modal';
import { Pill } from '../ui/Pill';
import { Field } from '../ui/Misc';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../ui/States';

export type Tab = 'whatsapp' | 'email';

/** Data that depends on communications; refreshed after every action. */
function useInvalidateComms() {
  const qc = useQueryClient();
  return (candidateId: number) => {
    qc.invalidateQueries({ queryKey: ['communications', candidateId] });
    qc.invalidateQueries({ queryKey: ['candidate', candidateId] });
  };
}

// ------------------------------------------------------------------ previews

/** WhatsApp-style bubble; *text* is shown bold exactly as WhatsApp renders it. */
export function WhatsAppBubble({ text }: { text: string }) {
  const parts = text.split(/(\*[^*\n]+\*)/g);
  return (
    <div className="rounded-xl bg-[#0b141a] p-3">
      <div className="ml-auto max-w-[92%] whitespace-pre-wrap rounded-lg rounded-tr-none bg-[#005c4b] px-3 py-2 text-[13px] leading-relaxed text-[#e9edef] shadow">
        {parts.map((p, i) => (p.startsWith('*') && p.endsWith('*') && p.length > 2 ? <strong key={i}>{p.slice(1, -1)}</strong> : <Fragment key={i}>{p}</Fragment>))}
      </div>
    </div>
  );
}

export function EmailPreview({ to, subject, body }: { to?: string | null; subject: string | null; body: string }) {
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-white text-[#1f2328]">
      <dl className="space-y-1 border-b border-[#d0d7de] bg-[#f6f8fa] px-4 py-2.5 text-xs">
        {to !== undefined && (
          <div className="flex gap-2">
            <dt className="w-14 text-[#59636e]">To</dt>
            <dd className="font-medium">{to ?? '—'}</dd>
          </div>
        )}
        <div className="flex gap-2">
          <dt className="w-14 text-[#59636e]">Subject</dt>
          <dd className="font-semibold">{subject}</dd>
        </div>
      </dl>
      <div className="whitespace-pre-wrap px-4 py-3 text-[13px] leading-relaxed">{body}</div>
    </div>
  );
}

const STATUS_META: Record<CommStatus, { label: string; tone: 'blue' | 'green' | 'red' | 'orange' | 'grey' | 'violet' }> = {
  CALL_INITIATED: { label: 'Call started', tone: 'blue' },
  PREPARED: { label: 'Prepared', tone: 'orange' },
  MARKED_SENT: { label: 'Sent (HR confirmed)', tone: 'violet' },
  ACCEPTED: { label: 'Accepted by provider', tone: 'green' },
  DELIVERED: { label: 'Delivered', tone: 'green' },
  FAILED: { label: 'Failed', tone: 'red' },
};

export const CommStatusPill = ({ status }: { status: CommStatus }) => <Pill tone={STATUS_META[status].tone}>{STATUS_META[status].label}</Pill>;

function Problems({ items }: { items: string[] }) {
  if (!items.length) return null;
  return (
    <ul className="space-y-1 rounded-xl border border-warn/40 bg-warn/10 px-3 py-2.5 text-sm text-[#f3c08a]" role="alert">
      {items.map((p) => (
        <li key={p} className="flex gap-2">
          <AlertTriangle size={15} className="mt-0.5 shrink-0" /> {p}
        </li>
      ))}
    </ul>
  );
}

// ------------------------------------------------------------------ compose panel

interface PanelProps {
  candidateId: number;
  interviewId?: number | null;
  initialTab?: Tab;
  initialPurpose?: TemplatePurpose;
  onScheduleInterview?: () => void;
}

/** Candidate + interview details, both previews, and the WhatsApp / email actions. */
export function CommunicationPanel({ candidateId, interviewId, initialTab = 'whatsapp', initialPurpose = 'INTERVIEW_INVITATION', onScheduleInterview }: PanelProps) {
  const toast = useToast();
  const invalidate = useInvalidateComms();
  const [tab, setTab] = useState<Tab>(initialTab);
  const [purpose, setPurpose] = useState<TemplatePurpose>(initialPurpose);
  const [prepared, setPrepared] = useState<CommunicationRecord | null>(null);
  const [emailResult, setEmailResult] = useState<CommunicationRecord | null>(null);
  const needsInterview = TEMPLATE_PURPOSES.find((p) => p.value === purpose)?.needsInterview ?? true;
  const compose = useQuery({
    queryKey: ['compose', candidateId, interviewId ?? null, purpose],
    queryFn: () => api.get<Compose>(`/candidates/${candidateId}/compose`, { interviewId: interviewId ?? undefined, purpose }),
  });
  const choosePurpose = (p: TemplatePurpose) => {
    setPurpose(p);
    setPrepared(null);
    setEmailResult(null);
  };

  const whatsapp = useMutation({
    mutationFn: async (win: Window | null) => {
      try {
        const res = await api.post<{ communication: CommunicationRecord; url: string }>(`/candidates/${candidateId}/communications/whatsapp`, {
          interviewId: compose.data?.interview?.id ?? null,
          purpose,
        });
        if (win) {
          win.opener = null;
          win.location.href = res.url;
        } else {
          window.location.href = res.url;
        }
        return res.communication;
      } catch (e) {
        win?.close();
        throw e;
      }
    },
    onSuccess: (c) => {
      setPrepared(c);
      invalidate(candidateId);
      toast('info', 'WhatsApp opened with the prepared message. Review it and press send in WhatsApp.');
    },
    onError: (e) => toast('error', e.message),
  });

  const markSent = useMutation({
    mutationFn: (id: number) => api.patch<CommunicationRecord>(`/communications/${id}/mark-sent`, {}),
    onSuccess: (c) => {
      setPrepared(c);
      invalidate(candidateId);
      toast('success', 'Marked as sent');
    },
    onError: (e) => toast('error', e.message),
  });

  const sendEmail = useMutation({
    mutationFn: () => api.post<CommunicationRecord>(`/candidates/${candidateId}/communications/email`, { interviewId: compose.data?.interview?.id ?? null, purpose }),
    onSuccess: (c) => {
      setEmailResult(c);
      invalidate(candidateId);
      if (c.status === 'ACCEPTED') toast('success', 'Email accepted by the email provider');
      else toast('error', `Email failed: ${c.errorMessage ?? 'unknown error'}`);
    },
    onError: (e) => toast('error', e.message),
  });

  const retry = useMutation({
    mutationFn: (id: number) => api.post<CommunicationRecord>(`/communications/${id}/retry`),
    onSuccess: (c) => {
      setEmailResult(c);
      invalidate(candidateId);
      toast(c.status === 'ACCEPTED' ? 'success' : 'error', c.status === 'ACCEPTED' ? 'Email accepted on retry' : `Retry failed: ${c.errorMessage}`);
    },
    onError: (e) => toast('error', e.message),
  });

  const purposePicker = (
    <Field label="Message type">
      <select className="input" value={purpose} onChange={(e) => choosePurpose(e.target.value as TemplatePurpose)}>
        {TEMPLATE_PURPOSES.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
      </select>
    </Field>
  );
  if (compose.isLoading) return <div className="space-y-4">{purposePicker}<LoadingBlock label="Preparing messages…" /></div>;
  if (compose.error || !compose.data) return <div className="space-y-4">{purposePicker}<ErrorState error={compose.error} onRetry={() => compose.refetch()} /></div>;
  const d = compose.data;

  return (
    <div className="space-y-4">
      {purposePicker}
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <section className="rounded-xl border border-line bg-card p-3.5 text-sm">
          <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-ink-3">Candidate</h3>
          <p className="font-semibold">{d.candidateName} <span className="font-mono text-xs font-normal text-ink-3">{d.candidateCode}</span></p>
          <p className="text-ink-2">{d.department ?? <span className="text-warn">No department assigned</span>}</p>
          <ContactLine icon={<Phone size={13} />} value={d.contact.whatsappNumber ?? d.contact.mobile} ok={d.contact.mobileValid} problem={d.contact.mobileProblem} />
          <ContactLine icon={<Mail size={13} />} value={d.contact.email} ok={d.contact.emailValid} problem={d.contact.emailProblem} />
        </section>
        {needsInterview && (
        <section className="rounded-xl border border-line bg-card p-3.5 text-sm">
          <h3 className="mb-2 text-xs font-medium uppercase tracking-wide text-ink-3">Interview details</h3>
          {d.interview ? (
            <dl className="grid grid-cols-[84px_1fr] gap-y-1">
              <dt className="text-ink-3">Date</dt><dd>{d.interview.date}</dd>
              <dt className="text-ink-3">Time</dt><dd>{d.interview.time}</dd>
              <dt className="text-ink-3">Venue</dt><dd>{d.interview.venue}</dd>
              <dt className="text-ink-3">Round</dt><dd>{d.interview.roundName} · {d.interview.interviewer}</dd>
            </dl>
          ) : (
            <div className="space-y-2">
              <p className="text-ink-3">No upcoming interview is scheduled.</p>
              {onScheduleInterview && (
                <button className="btn-secondary py-1.5 text-xs" onClick={onScheduleInterview}><CalendarClock size={14} /> Schedule interview</button>
              )}
            </div>
          )}
        </section>
        )}
      </div>

      <div className="flex rounded-xl border border-line bg-raised p-1" role="tablist">
        {(['whatsapp', 'email'] as Tab[]).map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={clsx('flex flex-1 items-center justify-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium transition', tab === t ? 'bg-ink text-canvas' : 'text-ink-2 hover:text-ink')}
          >
            {t === 'whatsapp' ? <MessageCircle size={15} /> : <Mail size={15} />} {t === 'whatsapp' ? 'WhatsApp' : 'Email'}
            {(t === 'whatsapp' ? d.whatsapp.ready : d.email.ready) ? <CheckCircle2 size={13} className="text-good" /> : <XCircle size={13} className="opacity-60" />}
          </button>
        ))}
      </div>

      {tab === 'whatsapp' ? (
        <div className="space-y-3">
          <Problems items={d.whatsapp.problems} />
          {d.whatsapp.message && (
            <>
              <p className="text-xs text-ink-3">Preview · approved WhatsApp template v{d.whatsapp.templateVersion} · to {d.contact.whatsappNumber}</p>
              <WhatsAppBubble text={d.whatsapp.message} />
            </>
          )}
          {prepared ? (
            <div className="flex flex-wrap items-center gap-3 rounded-xl border border-line bg-card px-3 py-2.5 text-sm">
              <CommStatusPill status={prepared.status} />
              {prepared.status === 'PREPARED' ? (
                <>
                  <span className="flex-1 text-ink-2">WhatsApp was opened with this message. Did you press send?</span>
                  <button className="btn-secondary py-1.5 text-xs" disabled={markSent.isPending} onClick={() => markSent.mutate(prepared.id)}>
                    {markSent.isPending && <Spinner size={13} />} Yes, I sent it
                  </button>
                </>
              ) : (
                <span className="flex-1 text-ink-2">Recorded as sent by you. WhatsApp click-to-chat cannot confirm delivery.</span>
              )}
            </div>
          ) : (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="flex max-w-md gap-1.5 text-xs text-ink-3">
                <Info size={13} className="mt-0.5 shrink-0" /> Opens WhatsApp with this message. You review and send it yourself; the dashboard records it as “Prepared”, not delivered.
              </p>
              <button className="btn-primary bg-[#25d366] text-[#0b141a] hover:bg-[#2fe074]" disabled={!d.whatsapp.ready || whatsapp.isPending}
                // open the tab synchronously inside the click so pop-up blockers allow it
                onClick={() => whatsapp.mutate(window.open("about:blank", "_blank"))}
              >
                {whatsapp.isPending ? <Spinner size={15} /> : <MessageCircle size={15} />} Open WhatsApp
              </button>
            </div>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {!d.email.serviceConfigured && (
            <Problems items={['Email service is not configured on the server. The preview works, but sending is disabled until an administrator sets the MAIL_* settings.']} />
          )}
          <Problems items={d.email.problems} />
          {d.email.body && (
            <>
              <p className="text-xs text-ink-3">Preview · approved email template v{d.email.templateVersion}</p>
              <EmailPreview to={d.email.to} subject={d.email.subject} body={d.email.body} />
            </>
          )}
          {emailResult && (
            <div className={clsx('flex flex-wrap items-center gap-3 rounded-xl border px-3 py-2.5 text-sm', emailResult.status === 'FAILED' ? 'border-bad/40 bg-bad/10' : 'border-good/40 bg-good/10')}>
              <CommStatusPill status={emailResult.status} />
              <span className="flex-1 text-ink-2">
                {emailResult.status === 'FAILED'
                  ? emailResult.errorMessage
                  : `Accepted by ${emailResult.provider}. Inbox delivery is not reported by SMTP.`}
              </span>
              {emailResult.status === 'FAILED' && (
                <button className="btn-secondary py-1.5 text-xs" disabled={retry.isPending} onClick={() => retry.mutate(emailResult.id)}>
                  {retry.isPending ? <Spinner size={13} /> : <RotateCw size={13} />} Retry
                </button>
              )}
            </div>
          )}
          {(!emailResult || emailResult.status !== 'ACCEPTED') && (
            <div className="flex justify-end">
              <button
                className="btn-primary"
                disabled={!d.email.ready || !d.email.serviceConfigured || sendEmail.isPending || emailResult?.status === 'FAILED'}
                onClick={() => sendEmail.mutate()}
              >
                {sendEmail.isPending ? <Spinner size={15} /> : <Send size={15} />} Send email
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ContactLine({ icon, value, ok, problem }: { icon: ReactNode; value: string | null; ok: boolean; problem: string | null }) {
  return (
    <p className={clsx('mt-1 flex items-center gap-1.5', ok ? 'text-ink-2' : 'text-warn')} title={problem ?? undefined}>
      {icon} {value ?? '—'} {ok ? <CheckCircle2 size={13} className="text-good" /> : <span className="text-xs">({problem})</span>}
    </p>
  );
}

export function CommunicationDialog({
  target,
  onClose,
}: {
  target: { candidateId: number; interviewId?: number | null; tab?: Tab; title?: string } | null;
  onClose: () => void;
}) {
  return (
    <Modal open={!!target} onClose={onClose} size="lg" title={target?.title ?? 'Contact candidate'} description="Review the details and message before sending.">
      {target && <CommunicationPanel candidateId={target.candidateId} interviewId={target.interviewId} initialTab={target.tab} />}
    </Modal>
  );
}

// ------------------------------------------------------------------ calls

const OUTCOMES: { value: CallOutcome; label: string }[] = [
  { value: 'CONNECTED', label: 'Connected' },
  { value: 'NO_ANSWER', label: 'No answer' },
  { value: 'BUSY', label: 'Busy' },
  { value: 'SWITCHED_OFF', label: 'Switched off / not reachable' },
  { value: 'WRONG_NUMBER', label: 'Wrong number' },
  { value: 'CALL_BACK_LATER', label: 'Asked to call back later' },
];

/** Starts a call (tel: link) and then asks for the outcome so call history stays useful. */
export function useCallAction() {
  const toast = useToast();
  const invalidate = useInvalidateComms();
  const [pending, setPending] = useState<CommunicationRecord | null>(null);
  const [outcome, setOutcome] = useState<CallOutcome>('CONNECTED');
  const [notes, setNotes] = useState('');

  const start = useMutation({
    mutationFn: (candidateId: number) => api.post<{ communication: CommunicationRecord; telUri: string }>(`/candidates/${candidateId}/communications/call`),
    onSuccess: (r) => {
      window.location.href = r.telUri;
      setOutcome('CONNECTED');
      setNotes('');
      setPending(r.communication);
      invalidate(r.communication.candidateId);
    },
    onError: (e) => toast('error', e.message),
  });

  const save = useMutation({
    mutationFn: () => api.patch<CommunicationRecord>(`/communications/${pending!.id}/call-outcome`, { outcome, notes: notes.trim() || null }),
    onSuccess: (c) => {
      toast('success', 'Call outcome saved');
      invalidate(c.candidateId);
      setPending(null);
    },
    onError: (e) => toast('error', e.message),
  });

  const modal = (
    <Modal
      open={!!pending}
      onClose={() => setPending(null)}
      title="Call outcome"
      description={pending ? `${pending.candidateName} · ${pending.recipient}` : undefined}
      footer={
        <>
          <button className="btn-ghost" onClick={() => setPending(null)}>Skip</button>
          <button className="btn-primary" disabled={save.isPending} onClick={() => save.mutate()}>{save.isPending && <Spinner size={14} />} Save outcome</button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="flex gap-2 text-sm text-ink-2">
          <PhoneCall size={16} className="mt-0.5 shrink-0 text-info" /> Your phone app should open to dial {pending?.recipient}. After the call, record how it went.
        </p>
        <Field label="Outcome">
          <select className="input" value={outcome} onChange={(e) => setOutcome(e.target.value as CallOutcome)}>
            {OUTCOMES.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </Field>
        <Field label="Notes (optional)">
          <textarea className="input min-h-[72px]" maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
  return { start: (candidateId: number) => start.mutate(candidateId), starting: start.isPending ? start.variables : null, modal };
}

// ------------------------------------------------------------------ history

const CHANNEL_ICON: Record<CommChannel, typeof Phone> = { CALL: Phone, WHATSAPP: MessageCircle, EMAIL: Mail };
const CHANNEL_LABEL: Record<CommChannel, string> = { CALL: 'Call', WHATSAPP: 'WhatsApp', EMAIL: 'Email' };

export function CommunicationHistory({ candidateId }: { candidateId: number }) {
  const toast = useToast();
  const invalidate = useInvalidateComms();
  const [open, setOpen] = useState<number | null>(null);
  const history = useQuery({
    queryKey: ['communications', candidateId],
    queryFn: () => api.get<CommunicationRecord[]>(`/candidates/${candidateId}/communications`),
  });
  const retry = useMutation({
    mutationFn: (id: number) => api.post<CommunicationRecord>(`/communications/${id}/retry`),
    onSuccess: (c) => {
      invalidate(candidateId);
      toast(c.status === 'ACCEPTED' ? 'success' : 'error', c.status === 'ACCEPTED' ? 'Email accepted on retry' : `Retry failed: ${c.errorMessage}`);
    },
    onError: (e) => toast('error', e.message),
  });
  const markSent = useMutation({
    mutationFn: (id: number) => api.patch<CommunicationRecord>(`/communications/${id}/mark-sent`, {}),
    onSuccess: () => {
      invalidate(candidateId);
      toast('success', 'Marked as sent');
    },
    onError: (e) => toast('error', e.message),
  });

  if (history.isLoading) return <LoadingBlock />;
  if (history.error) return <ErrorState error={history.error} onRetry={() => history.refetch()} />;
  if (!history.data?.length) return <EmptyState title="No communication yet" hint="Calls, WhatsApp messages and emails will be recorded here." />;
  const retried = new Set(history.data.map((c) => c.retryOfId).filter(Boolean));

  return (
    <ol className="space-y-2">
      {history.data.map((c) => {
        const Icon = CHANNEL_ICON[c.channel];
        const expanded = open === c.id;
        return (
          <li key={c.id} className="rounded-xl border border-line/70 bg-card">
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 px-3 py-2.5">
              <Icon size={16} className="shrink-0 text-ink-3" />
              <span className="text-sm font-medium">
                {CHANNEL_LABEL[c.channel]}
                {c.channel !== 'CALL' && (
                  <span className="font-normal text-ink-3"> · {labelOf(TEMPLATE_PURPOSES, c.purpose ?? 'INTERVIEW_INVITATION')}</span>
                )}
              </span>
              <CommStatusPill status={c.status} />
              {c.callOutcome && <Pill tone="grey">{OUTCOMES.find((o) => o.value === c.callOutcome)?.label}</Pill>}
              <span className="text-xs text-ink-3">to {c.recipient}</span>
              <span className="flex-1" />
              <span className="text-xs text-ink-3" title={fmtDateTime(c.createdAt)}>{c.initiatedBy} · {fmtRelative(c.createdAt)}</span>
              {c.content && (
                <button className="btn-ghost p-1.5" onClick={() => setOpen(expanded ? null : c.id)} aria-expanded={expanded} aria-label="Show message">
                  <ChevronDown size={15} className={clsx('transition', expanded && 'rotate-180')} />
                </button>
              )}
            </div>
            {(c.errorMessage || c.notes || (c.status === 'FAILED' && !retried.has(c.id)) || (c.channel === 'WHATSAPP' && c.status === 'PREPARED')) && (
              <div className="flex flex-wrap items-center gap-2 border-t border-line/60 px-3 py-2 text-xs">
                {c.errorMessage && <span className="text-[#f29a9a]">{c.errorMessage}</span>}
                {c.notes && <span className="text-ink-2">{c.notes}</span>}
                <span className="flex-1" />
                {c.channel === 'EMAIL' && c.status === 'FAILED' && !retried.has(c.id) && (
                  <button className="btn-secondary py-1 text-xs" disabled={retry.isPending} onClick={() => retry.mutate(c.id)}><RotateCw size={12} /> Retry</button>
                )}
                {c.channel === 'WHATSAPP' && c.status === 'PREPARED' && (
                  <button className="btn-secondary py-1 text-xs" disabled={markSent.isPending} onClick={() => markSent.mutate(c.id)}>Mark as sent</button>
                )}
              </div>
            )}
            {expanded && c.content && (
              <div className="space-y-2 border-t border-line/60 p-3">
                <p className="text-[11px] text-ink-3">
                  Template v{c.templateVersion ?? '—'}
                  {c.provider ? ` · ${c.provider}` : ''}
                  {c.providerMessageId ? ` · Message-ID ${c.providerMessageId}` : ''}
                  {c.retryOfId ? ` · retry of #${c.retryOfId}` : ''}
                </p>
                {c.channel === 'EMAIL' ? <EmailPreview to={c.recipient} subject={c.subject} body={c.content} /> : <WhatsAppBubble text={c.content} />}
              </div>
            )}
          </li>
        );
      })}
    </ol>
  );
}
