import { useEffect, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { CalendarClock, ClipboardCheck, Download, Eye, GraduationCap, MapPin } from 'lucide-react';
import { api } from '../lib/api';
import { useToast } from '../lib/toast';
import { INTERVIEW_MODES, RECOMMENDATIONS, fmtDateTime, fmtExperience, labelOf } from '../lib/format';
import type { AssignedInterview, Recommendation } from '../lib/types';
import { InterviewPill } from '../components/ui/Pill';
import { DocumentViewer, type ViewTarget } from '../components/modals/DocumentViewer';
import { Field } from '../components/ui/Misc';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';

/** Interviewer home: only the interviews assigned to this account, with feedback. */
export default function MyInterviewsPage() {
  const query = useQuery({ queryKey: ['my-interviews'], queryFn: () => api.get<AssignedInterview[]>('/my/interviews') });
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const list = query.data ?? [];
  const selected = list.find((a) => a.interview.id === selectedId) ?? list[0];

  if (query.isLoading) return <LoadingBlock label="Loading your interviews…" />;
  if (query.error) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  if (list.length === 0) {
    return (
      <section className="panel p-6">
        <EmptyState
          icon={<CalendarClock size={20} />}
          title="No interviews assigned to you yet"
          hint="When HR schedules an interview with you as the interviewer, it appears here."
        />
      </section>
    );
  }
  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(280px,360px)_1fr]">
      <section className="panel p-2" aria-label="My interviews">
        <ul className="divide-y divide-line/50">
          {list.map(({ interview: i, candidate: c }) => (
            <li key={i.id}>
              <button
                className={clsx('w-full rounded-lg px-3 py-3 text-left transition hover:bg-raised', selected?.interview.id === i.id && 'bg-raised')}
                onClick={() => setSelectedId(i.id)}
                aria-current={selected?.interview.id === i.id}
              >
                <span className="flex items-center justify-between gap-2">
                  <span className="truncate font-medium text-ink">{c?.fullName ?? i.candidateName}</span>
                  <InterviewPill status={i.status} />
                </span>
                <span className="mt-1 block text-xs text-ink-3">
                  {fmtDateTime(i.scheduledAt)}
                  {i.feedback && <span className="ml-1 text-good">· feedback given</span>}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </section>
      {selected && <InterviewDetail item={selected} />}
    </div>
  );
}

function InterviewDetail({ item }: { item: AssignedInterview }) {
  const { interview: i, candidate: c } = item;
  const toast = useToast();
  const [viewing, setViewing] = useState<ViewTarget | null>(null);
  const [downloading, setDownloading] = useState(false);
  const started = new Date(i.scheduledAt).getTime() <= Date.now();

  const downloadResume = async () => {
    setDownloading(true);
    try {
      await api.download(`/my/interviews/${i.id}/resume`, {}, `${c?.candidateCode ?? 'candidate'}-resume`);
    } catch (e) {
      toast('error', (e as Error).message);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="space-y-6">
      <section className="panel p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-xl font-semibold">{c?.fullName ?? i.candidateName}</h2>
            <p className="text-sm text-ink-3">
              <span className="font-mono">{c?.candidateCode}</span> · {c?.departmentName ?? 'No department'}
            </p>
          </div>
          <div className="flex gap-2">
          <button
            className="btn-secondary"
            disabled={!c?.hasResume}
            onClick={() => setViewing({ title: `${c?.fullName ?? i.candidateName} - Resume`, path: `/my/interviews/${i.id}/resume`, fileName: `${c?.candidateCode ?? 'candidate'}-resume` })}
          >
            <Eye size={15} /> View resume
          </button>
          <button className="btn-primary" disabled={!c?.hasResume || downloading} onClick={downloadResume} title={c?.hasResume ? undefined : 'No resume on file'}>
            {downloading ? <Spinner size={15} /> : <Download size={15} />} Resume
          </button>
          </div>
        </div>
        <dl className="mt-5 grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
          <Detail icon={<CalendarClock size={15} />} label="Interview" value={`${fmtDateTime(i.scheduledAt)} · ${i.durationMinutes} min`} />
          <Detail icon={<MapPin size={15} />} label="Mode" value={`${labelOf(INTERVIEW_MODES, i.mode)}${i.location ? ` · ${i.location}` : ''}`} />
          <Detail icon={<GraduationCap size={15} />} label="Qualification" value={[c?.qualification, c?.institution, c?.passingYear].filter(Boolean).join(' · ') || '—'} />
          <Detail label="Experience" value={c ? fmtExperience(c.experienceYears) : '—'} />
          <Detail label="Previous role" value={[c?.previousDesignation, c?.previousCompany].filter(Boolean).join(' at ') || '—'} />
          <Detail label="Skills" value={c?.skills ?? '—'} />
        </dl>
        {i.remarks && <p className="mt-4 rounded-lg bg-raised px-3 py-2 text-sm text-ink-2"><strong className="text-ink">Note from HR:</strong> {i.remarks}</p>}
      </section>
      <FeedbackForm item={item} started={started} />
      <DocumentViewer target={viewing} onClose={() => setViewing(null)} />
    </div>
  );
}

function Detail({ label, value, icon }: { label: string; value: string; icon?: ReactNode }) {
  return (
    <div>
      <dt className="flex items-center gap-1.5 text-xs text-ink-3">{icon}{label}</dt>
      <dd className="mt-0.5 text-ink">{value}</dd>
    </div>
  );
}

function FeedbackForm({ item, started }: { item: AssignedInterview; started: boolean }) {
  const i = item.interview;
  const toast = useToast();
  const qc = useQueryClient();
  const [recommendation, setRecommendation] = useState<Recommendation | ''>(i.recommendation ?? '');
  const [feedback, setFeedback] = useState(i.feedback ?? '');
  useEffect(() => {
    setRecommendation(i.recommendation ?? '');
    setFeedback(i.feedback ?? '');
  }, [i.id, i.recommendation, i.feedback]);

  const save = useMutation({
    mutationFn: () => api.post(`/my/interviews/${i.id}/feedback`, { recommendation, feedback: feedback.trim() }),
    onSuccess: () => {
      toast('success', 'Feedback saved');
      qc.invalidateQueries({ queryKey: ['my-interviews'] });
    },
    onError: (e) => toast('error', e.message),
  });

  const cancelled = i.status === 'CANCELLED';
  const disabled = !started || cancelled;
  return (
    <section className="panel p-5">
      <h3 className="flex items-center gap-2 font-semibold"><ClipboardCheck size={16} /> Interview feedback</h3>
      <p className="mt-1 text-sm text-ink-3">
        {cancelled
          ? 'This interview was cancelled.'
          : !started
            ? 'You can record feedback once the interview has started.'
            : 'Your recommendation helps HR decide; the hiring decision stays with HR.'}
        {i.feedbackAt && ` Last saved ${fmtDateTime(i.feedbackAt)}.`}
      </p>
      <div className="mt-4 space-y-4">
        <Field label="Recommendation *">
          <select className="input" value={recommendation} disabled={disabled} onChange={(e) => setRecommendation(e.target.value as Recommendation)}>
            <option value="">Choose…</option>
            {RECOMMENDATIONS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
        </Field>
        <Field label="Feedback *" hint="Strengths, concerns and anything HR should know. Visible to HR only.">
          <textarea className="input min-h-[120px]" maxLength={4000} value={feedback} disabled={disabled} onChange={(e) => setFeedback(e.target.value)} />
        </Field>
        <button className="btn-primary" disabled={disabled || !recommendation || !feedback.trim() || save.isPending} onClick={() => save.mutate()}>
          {save.isPending && <Spinner size={14} />} {i.feedback ? 'Update feedback' : 'Save feedback'}
        </button>
      </div>
    </section>
  );
}
