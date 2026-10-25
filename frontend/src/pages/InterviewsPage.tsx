import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { AlertTriangle, CalendarClock, CalendarPlus, Search, Send } from 'lucide-react';
import { api } from '../lib/api';
import { INTERVIEW_MODES, fmtDate, fmtTime, labelOf } from '../lib/format';
import type { Interview, Page } from '../lib/types';
import { InterviewPill } from '../components/ui/Pill';
import { Avatar } from '../components/ui/Misc';
import { Pagination } from '../components/ui/Pagination';
import { EmptyState, ErrorState, LoadingBlock } from '../components/ui/States';
import { InterviewStatusModal, ScheduleInterviewModal } from '../components/modals/InterviewModals';
import { CommunicationDialog } from '../components/communication/Communication';

type Scope = 'UPCOMING' | 'COMPLETED' | 'CANCELLED' | 'ALL';
const TABS: { value: Scope; label: string }[] = [
  { value: 'UPCOMING', label: 'Upcoming' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'ALL', label: 'All' },
];

export default function InterviewsPage() {
  const [scope, setScope] = useState<Scope>('UPCOMING');
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [scheduling, setScheduling] = useState(false);
  const [editing, setEditing] = useState<Interview | null>(null);
  const [invite, setInvite] = useState<{ candidateId: number; interviewId: number; title: string } | null>(null);

  useEffect(() => {
    const t = setTimeout(() => {
      setDebounced(q.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [q]);

  const rangeInvalid = !!from && !!to && from > to;
  const query = useQuery({
    queryKey: ['interviews', scope, debounced, from, to, page],
    queryFn: () => api.get<Page<Interview>>('/interviews', { scope, q: debounced, from, to, page, size: 15 }),
    placeholderData: keepPreviousData,
    enabled: !rangeInvalid,
  });
  const data = query.data;

  return (
    <div className="space-y-4">
      <section className="panel p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex rounded-xl border border-line bg-raised p-1" role="tablist">
            {TABS.map((t) => (
              <button
                key={t.value}
                role="tab"
                aria-selected={scope === t.value}
                onClick={() => {
                  setScope(t.value);
                  setPage(0);
                }}
                className={clsx('rounded-lg px-3 py-1.5 text-sm font-medium transition', scope === t.value ? 'bg-ink text-canvas' : 'text-ink-2 hover:text-ink')}
              >
                {t.label}
              </button>
            ))}
          </div>
          <label className="relative min-w-[200px] flex-1">
            <span className="sr-only">Search interviews</span>
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
            <input className="input pl-9" placeholder="Candidate, ID or interviewer" value={q} onChange={(e) => setQ(e.target.value)} />
          </label>
          <label className="flex items-center gap-2 text-sm text-ink-3">
            From <input type="date" className="input w-auto" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
          </label>
          <label className="flex items-center gap-2 text-sm text-ink-3">
            To <input type="date" className="input w-auto" value={to} min={from || undefined} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
          </label>
          <button className="btn-primary" onClick={() => setScheduling(true)}>
            <CalendarPlus size={16} /> Schedule interview
          </button>
        </div>
        {rangeInvalid && <p className="mt-2 text-sm text-bad">“From” must be on or before “To”.</p>}
      </section>

      <section className="panel p-4">
        {query.isLoading ? (
          <LoadingBlock label="Loading interviews…" />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : !data || data.totalElements === 0 ? (
          <EmptyState
            icon={<CalendarClock size={20} />}
            title={scope === 'UPCOMING' ? 'No upcoming interviews' : 'No interviews found'}
            hint="Use “Schedule interview” to book one."
          />
        ) : (
          <>
            <div className="-mx-4 overflow-x-auto">
              <table className="w-full min-w-[900px]">
                <thead>
                  <tr className="border-b border-line">
                    <th className="th pl-4">Candidate</th>
                    <th className="th">Date</th>
                    <th className="th">Time</th>
                    <th className="th">Round</th>
                    <th className="th">Interviewer</th>
                    <th className="th">Mode</th>
                    <th className="th">Status</th>
                    <th className="th">Remarks</th>
                    <th className="th pr-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((i) => {
                    const active = i.status === 'SCHEDULED' || i.status === 'RESCHEDULED';
                    const overdue = active && new Date(i.scheduledAt).getTime() < Date.now();
                    return (
                      <tr key={i.id} className="border-b border-line/40 last:border-0 hover:bg-white/[0.02]">
                        <td className="td pl-4">
                          <Link to={`/candidates/${i.candidateId}`} className="flex items-center gap-3 hover:underline">
                            <Avatar name={i.candidateName} size="sm" />
                            <span>
                              <span className="block font-medium text-ink">{i.candidateName}</span>
                              <span className="block text-xs text-ink-3">{i.candidateCode} · {i.departmentName ?? 'Unassigned'}</span>
                            </span>
                          </Link>
                        </td>
                        <td className="td tabular-nums">{fmtDate(i.scheduledAt)}</td>
                        <td className="td tabular-nums">{fmtTime(i.scheduledAt)} <span className="text-ink-3">· {i.durationMinutes}m</span></td>
                        <td className="td">{i.roundName}</td>
                        <td className="td">{i.interviewer}</td>
                        <td className="td">
                          {labelOf(INTERVIEW_MODES, i.mode)}
                          {i.location && <span className="block max-w-[160px] truncate text-xs text-ink-3" title={i.location}>{i.location}</span>}
                        </td>
                        <td className="td">
                          <div className="flex flex-col items-start gap-1">
                            <InterviewPill status={i.status} />
                            {overdue && <span className="flex items-center gap-1 text-[11px] text-warn"><AlertTriangle size={11} /> Needs update</span>}
                          </div>
                        </td>
                        <td className="td max-w-[200px] truncate" title={i.remarks ?? undefined}>{i.remarks ?? '—'}</td>
                        <td className="td pr-4 text-right">
                          {active ? (
                            <div className="flex justify-end gap-1.5">
                              <button className="btn-secondary px-3 py-1.5 text-xs" onClick={() => setInvite({ candidateId: i.candidateId, interviewId: i.id, title: `Interview invitation · ${i.candidateName}` })} disabled={new Date(i.scheduledAt).getTime() < Date.now()} title={new Date(i.scheduledAt).getTime() < Date.now() ? "This interview time has passed" : "Send the interview invitation by WhatsApp or email"}>
                                <Send size={13} /> Send invite
                              </button>
                              <button className="btn-secondary px-3 py-1.5 text-xs" onClick={() => setEditing(i)}>Update status</button>
                            </div>
                          ) : (
                            <span className="text-xs text-ink-3">Closed</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} size={data.size} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
          </>
        )}
      </section>

      <ScheduleInterviewModal open={scheduling} onClose={() => setScheduling(false)} />
      <InterviewStatusModal interview={editing} onClose={() => setEditing(null)} />
      <CommunicationDialog target={invite} onClose={() => setInvite(null)} />
    </div>
  );
}
