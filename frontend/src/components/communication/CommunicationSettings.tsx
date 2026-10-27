import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { CheckCircle2, ChevronDown, History, Mail, MessageCircle, RotateCcw, Send, ShieldCheck, XCircle } from 'lucide-react';
import { api } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { fmtDateTime, fmtRelative } from '../../lib/format';
import type { CommSettings, MessageTemplateInfo, TemplatePurpose, TemplatesOverview } from '../../lib/types';
import { Field, SectionTitle } from '../ui/Misc';
import { ErrorState, LoadingBlock, Spinner } from '../ui/States';
import { EmailPreview, WhatsAppBubble } from './Communication';

type Channel = 'WHATSAPP' | 'EMAIL';

/** Admin page: company identity, approved templates (versioned), and email service status. */
export function CommunicationSettingsTab() {
  const overview = useQuery({ queryKey: ['comm-templates'], queryFn: () => api.get<TemplatesOverview>('/communication/templates') });
  const [purpose, setPurpose] = useState<TemplatePurpose>('INTERVIEW_INVITATION');
  if (overview.isLoading) return <div className="panel"><LoadingBlock /></div>;
  if (overview.error || !overview.data) return <div className="panel"><ErrorState error={overview.error} onRetry={() => overview.refetch()} /></div>;
  const o = overview.data;
  const current = o.purposes.find((p) => p.purpose === purpose) ?? o.purposes[0];
  // Interview placeholders are only offered where an interview exists.
  const placeholders = Object.fromEntries(
    Object.entries(o.placeholders).filter(([key]) => current.needsInterview || !o.interviewPlaceholders.includes(key)),
  );
  return (
    <div className="space-y-6">
      <IdentityForm settings={o.settings} />
      <EmailServiceCard status={o.emailService} />
      <section className="panel p-5">
        <SectionTitle title="Message templates" subtitle="Each message type has its own approved WhatsApp and email wording. Messages are only sent when an HR user chooses to send them." />
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Message type">
          {o.purposes.map((p) => (
            <button
              key={p.purpose}
              role="tab"
              aria-selected={p.purpose === current.purpose}
              onClick={() => setPurpose(p.purpose)}
              className={clsx('rounded-lg border px-3 py-1.5 text-sm transition', p.purpose === current.purpose ? 'border-ink bg-ink text-canvas' : 'border-line text-ink-2 hover:text-ink')}
            >
              {p.label}
            </button>
          ))}
        </div>
      </section>
      <TemplateEditor key={`${current.purpose}-WHATSAPP`} purpose={current.purpose} purposeLabel={current.label} channel="WHATSAPP" template={current.whatsapp} placeholders={placeholders} required={current.required} />
      <TemplateEditor key={`${current.purpose}-EMAIL`} purpose={current.purpose} purposeLabel={current.label} channel="EMAIL" template={current.email} placeholders={placeholders} required={current.required} />
    </div>
  );
}

function IdentityForm({ settings }: { settings: CommSettings }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [f, setF] = useState(settings);
  useEffect(() => setF(settings), [settings]);
  const dirty = f.companyName !== settings.companyName || f.defaultVenue !== settings.defaultVenue || f.hrSignature !== settings.hrSignature || f.defaultCountryCode !== settings.defaultCountryCode;
  const save = useMutation({
    mutationFn: () => api.put<CommSettings>('/communication/settings', {
      companyName: f.companyName, defaultVenue: f.defaultVenue, hrSignature: f.hrSignature, defaultCountryCode: f.defaultCountryCode,
    }),
    onSuccess: () => {
      toast('success', 'Company details saved');
      ['comm-templates', 'comm-settings', 'compose'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
    },
    onError: (e) => toast('error', e.message),
  });
  return (
    <section className="panel space-y-4 p-5">
      <SectionTitle
        title="Company details used in messages"
        subtitle={`Filled into {{CompanyName}}, {{Venue}} and {{HRSignature}} in both WhatsApp and email, so they always match.${settings.updatedAt ? ` Last changed by ${settings.updatedBy ?? '—'} ${fmtRelative(settings.updatedAt)}.` : ''}`}
      />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Field label="Company name *"><input className="input" maxLength={150} value={f.companyName} onChange={(e) => setF({ ...f, companyName: e.target.value })} /></Field>
        <Field label="Default interview venue *"><input className="input" maxLength={300} value={f.defaultVenue} onChange={(e) => setF({ ...f, defaultVenue: e.target.value })} /></Field>
        <Field label="HR signature *" hint="Shown after “Best regards,”. Use separate lines for department and company.">
          <textarea className="input min-h-[72px]" maxLength={500} value={f.hrSignature} onChange={(e) => setF({ ...f, hrSignature: e.target.value })} />
        </Field>
        <Field label="Default country code *" hint="Added to 10-digit mobile numbers for WhatsApp and calls (91 = India).">
          <div className="flex items-center gap-2">
            <span className="text-ink-3">+</span>
            <input className="input w-24" maxLength={3} inputMode="numeric" value={f.defaultCountryCode} onChange={(e) => setF({ ...f, defaultCountryCode: e.target.value.replace(/\D/g, '') })} />
          </div>
        </Field>
      </div>
      <div className="flex gap-2">
        <button className="btn-primary" disabled={!dirty || save.isPending || !f.companyName.trim() || !f.defaultVenue.trim() || !f.hrSignature.trim() || !f.defaultCountryCode}
          onClick={() => save.mutate()}>
          {save.isPending && <Spinner size={14} />} Save company details
        </button>
        {dirty && <button className="btn-ghost" onClick={() => setF(settings)}>Discard</button>}
      </div>
    </section>
  );
}

function EmailServiceCard({ status }: { status: TemplatesOverview['emailService'] }) {
  const toast = useToast();
  const [to, setTo] = useState('');
  const [result, setResult] = useState<{ accepted: boolean; messageId: string | null; error: string | null } | null>(null);
  const test = useMutation({
    mutationFn: () => api.post<{ accepted: boolean; messageId: string | null; error: string | null }>('/communication/test-email', { to: to.trim() }),
    onSuccess: (r) => {
      setResult(r);
      toast(r.accepted ? 'success' : 'error', r.accepted ? 'Test email accepted by the provider' : `Test email failed: ${r.error}`);
    },
    onError: (e) => toast('error', e.message),
  });
  return (
    <section className="panel space-y-4 p-5">
      <SectionTitle title="Email service" subtitle="SMTP credentials are read from the server environment only and never sent to the browser." />
      {status.configured ? (
        <div className="flex items-start gap-3 rounded-xl border border-good/40 bg-good/10 px-4 py-3 text-sm">
          <CheckCircle2 size={18} className="mt-0.5 shrink-0 text-good" />
          <p>Configured: {status.provider}, sending as <strong>{status.fromName}</strong> &lt;{status.fromAddress}&gt;.</p>
        </div>
      ) : (
        <div className="flex items-start gap-3 rounded-xl border border-warn/40 bg-warn/10 px-4 py-3 text-sm text-[#f3c08a]">
          <XCircle size={18} className="mt-0.5 shrink-0" />
          <p>Not configured. Set <code>MAIL_HOST</code>, <code>MAIL_PORT</code>, <code>MAIL_USERNAME</code>, <code>MAIL_PASSWORD</code> and <code>MAIL_FROM</code> on the server and restart. Previews still work; sending is disabled until then.</p>
        </div>
      )}
      <div className="flex flex-wrap items-end gap-2">
        <Field label="Send a test email to" className="min-w-[240px] flex-1">
          <input className="input" type="email" value={to} onChange={(e) => setTo(e.target.value)} placeholder="you@company.com" />
        </Field>
        <button className="btn-secondary" disabled={!status.configured || !/^\S+@\S+\.\S+$/.test(to) || test.isPending} onClick={() => test.mutate()}>
          {test.isPending ? <Spinner size={14} /> : <Send size={14} />} Send test
        </button>
      </div>
      {result && (
        <p className={clsx('text-sm', result.accepted ? 'text-good' : 'text-bad')}>
          {result.accepted ? `Accepted by the provider (Message-ID ${result.messageId}). Check the inbox to confirm delivery.` : result.error}
        </p>
      )}
    </section>
  );
}

interface EditorProps {
  purpose: TemplatePurpose;
  purposeLabel: string;
  channel: Channel;
  template: MessageTemplateInfo;
  placeholders: Record<string, string>;
  required: string[];
}

function TemplateEditor({ purpose, purposeLabel, channel, template, placeholders, required }: EditorProps) {
  const toast = useToast();
  const qc = useQueryClient();
  const [subject, setSubject] = useState(template.subject ?? '');
  const [body, setBody] = useState(template.body);
  const [debounced, setDebounced] = useState({ subject: template.subject ?? '', body: template.body });
  const [showHistory, setShowHistory] = useState(false);
  const bodyRef = useRef<HTMLTextAreaElement>(null);
  const isEmail = channel === 'EMAIL';

  useEffect(() => {
    setSubject(template.subject ?? '');
    setBody(template.body);
  }, [template]);
  useEffect(() => {
    const t = setTimeout(() => setDebounced({ subject, body }), 350);
    return () => clearTimeout(t);
  }, [subject, body]);

  const preview = useQuery({
    queryKey: ['template-preview', purpose, channel, debounced],
    queryFn: () => api.post<{ subject: string | null; body: string | null; problems: string[] }>('/communication/templates/preview', { purpose, channel, subject: debounced.subject, body: debounced.body || ' ' }),
  });
  const history = useQuery({
    queryKey: ['template-history', purpose, channel],
    queryFn: () => api.get<MessageTemplateInfo[]>('/communication/templates/history', { channel, purpose }),
    enabled: showHistory,
  });
  const dirty = body.trim() !== template.body.trim() || (isEmail && subject.trim() !== (template.subject ?? '').trim());
  const save = useMutation({
    mutationFn: () => api.post<MessageTemplateInfo>('/communication/templates', { purpose, channel, subject: isEmail ? subject : null, body }),
    onSuccess: (t) => {
      toast('success', `${isEmail ? 'Email' : 'WhatsApp'} template version ${t.version} approved`);
      ['comm-templates', 'template-history', 'compose'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
    },
    onError: (e) => toast('error', e.message),
  });

  const insert = (token: string) => {
    const el = bodyRef.current;
    const text = `{{${token}}}`;
    if (!el) return setBody((b) => b + text);
    const start = el.selectionStart ?? body.length;
    const end = el.selectionEnd ?? body.length;
    const next = body.slice(0, start) + text + body.slice(end);
    setBody(next);
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(start + text.length, start + text.length);
    });
  };

  const problems = preview.data?.problems ?? [];
  const Icon = isEmail ? Mail : MessageCircle;
  return (
    <section className="panel space-y-4 p-5">
      <SectionTitle
        title={`${purposeLabel} - ${isEmail ? 'email' : 'WhatsApp'}`}
        subtitle={`Approved version ${template.version} · by ${template.createdBy} on ${fmtDateTime(template.createdAt)}. Saving creates a new version; messages already sent keep the version they used.`}
        action={<Icon size={18} className="text-ink-3" />}
      />
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <div className="space-y-3">
          {isEmail && (
            <Field label="Subject *"><input className="input" maxLength={300} value={subject} onChange={(e) => setSubject(e.target.value)} /></Field>
          )}
          <Field label="Message *" hint={isEmail ? 'Plain text; line breaks are kept.' : 'Use *text* for bold in WhatsApp.'}>
            <textarea ref={bodyRef} className="input min-h-[340px] font-mono text-[13px] leading-relaxed" maxLength={4000} value={body} onChange={(e) => setBody(e.target.value)} />
          </Field>
          <div>
            <p className="mb-1.5 text-xs text-ink-3">Insert placeholder (required: {required.map((r) => `{{${r}}}`).join(', ')})</p>
            <div className="flex flex-wrap gap-1.5">
              {Object.entries(placeholders).map(([key, desc]) => (
                <button key={key} type="button" title={desc} onClick={() => insert(key)}
                  className={clsx('rounded-md border px-2 py-0.5 font-mono text-[11px] transition hover:bg-line', required.includes(key) ? 'border-info/50 text-[#8fb4f5]' : 'border-line text-ink-2')}>
                  {`{{${key}}}`}
                </button>
              ))}
            </div>
          </div>
        </div>
        <div className="space-y-2">
          <p className="flex items-center gap-1.5 text-xs text-ink-3"><ShieldCheck size={13} /> Live preview with sample data (not a real candidate)</p>
          {preview.isFetching && !preview.data ? (
            <LoadingBlock />
          ) : problems.length ? (
            <ul className="space-y-1 rounded-xl border border-bad/40 bg-bad/10 px-3 py-2.5 text-sm text-[#f29a9a]">{problems.map((p) => <li key={p}>{p}</li>)}</ul>
          ) : preview.data?.body ? (
            isEmail ? <EmailPreview subject={preview.data.subject} body={preview.data.body} /> : <WhatsAppBubble text={preview.data.body} />
          ) : null}
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <button className="btn-primary" disabled={!dirty || problems.length > 0 || save.isPending || preview.isFetching}
          onClick={() => window.confirm(`Approve this ${isEmail ? 'email' : 'WhatsApp'} template as version ${template.version + 1}? It will be used for all new messages.`) && save.mutate()}>
          {save.isPending && <Spinner size={14} />} Approve &amp; save as version {template.version + 1}
        </button>
        {dirty && <button className="btn-ghost" onClick={() => { setBody(template.body); setSubject(template.subject ?? ''); }}><RotateCcw size={14} /> Revert to approved</button>}
        <span className="flex-1" />
        <button className="btn-ghost text-xs" onClick={() => setShowHistory((s) => !s)} aria-expanded={showHistory}>
          <History size={14} /> Version history <ChevronDown size={14} className={clsx('transition', showHistory && 'rotate-180')} />
        </button>
      </div>
      {showHistory && (
        <div className="space-y-2 border-t border-line pt-3">
          {history.isLoading ? <LoadingBlock /> : history.data?.map((v) => (
            <details key={v.id} className="rounded-xl border border-line/70 bg-card">
              <summary className="flex cursor-pointer items-center gap-3 px-3 py-2 text-sm">
                <span className="font-medium">Version {v.version}</span>
                {v.active && <span className="rounded-md bg-good/20 px-1.5 py-0.5 text-[11px] text-[#6fd99a]">Active</span>}
                <span className="text-xs text-ink-3">{v.createdBy} · {fmtDateTime(v.createdAt)}</span>
              </summary>
              <div className="border-t border-line/60 p-3">
                {v.subject && <p className="mb-2 text-xs text-ink-2">Subject: {v.subject}</p>}
                <pre className="whitespace-pre-wrap font-mono text-xs text-ink-2">{v.body}</pre>
              </div>
            </details>
          ))}
        </div>
      )}
    </section>
  );
}
