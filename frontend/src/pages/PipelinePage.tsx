import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { ArrowRightLeft, RefreshCcw } from 'lucide-react';
import { api } from '../lib/api';
import { useDepartments } from '../lib/queries';
import { applicationTone, fmtDate, toneHex } from '../lib/format';
import type { ApplicationStatus, CandidateListItem } from '../lib/types';
import { RatingPill } from '../components/ui/Pill';
import { ErrorState, LoadingBlock } from '../components/ui/States';
import { StatusModal, type StatusTarget } from '../components/modals/CandidateModals';

interface PipelineColumn {
  status: ApplicationStatus;
  label: string;
  count: number;
  candidates: CandidateListItem[];
}

const LIMIT = 25;

/** Recruitment pipeline: active candidates per status. Moving a card uses the normal, audited status update. */
export default function PipelinePage() {
  const { data: departments } = useDepartments();
  const [departmentId, setDepartmentId] = useState('');
  const [statusTarget, setStatusTarget] = useState<StatusTarget | null>(null);
  const query = useQuery({
    queryKey: ['pipeline', departmentId],
    queryFn: () => api.get<PipelineColumn[]>('/pipeline', { departmentId: departmentId || undefined, limit: LIMIT }),
  });

  return (
    <div className="space-y-4">
      <section className="panel flex flex-wrap items-center gap-3 p-4">
        <select className="input w-auto min-w-[220px]" aria-label="Department" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
          <option value="">All departments</option>
          {departments?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          <option value="0">Unassigned</option>
        </select>
        <p className="flex-1 text-sm text-ink-3">Active candidates only (archived are hidden). Each column shows the {LIMIT} most recently updated.</p>
        <button className="btn-secondary" onClick={() => query.refetch()} disabled={query.isFetching}>
          <RefreshCcw size={15} className={clsx(query.isFetching && 'animate-spin')} /> Refresh
        </button>
      </section>

      {query.isLoading ? (
        <LoadingBlock label="Loading pipeline…" />
      ) : query.error || !query.data ? (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      ) : (
        <div className="-mx-4 overflow-x-auto px-4 pb-2 sm:-mx-6 sm:px-6 lg:-mx-8 lg:px-8">
          <div className="flex gap-4" style={{ minWidth: `${query.data.length * 272}px` }}>
            {query.data.map((col) => (
              <section key={col.status} className="panel flex w-64 shrink-0 flex-col" aria-label={`${col.label}: ${col.count}`}>
                <header className="flex items-center justify-between gap-2 border-b border-line px-3 py-2.5">
                  <span className="flex items-center gap-2 text-sm font-semibold">
                    <span className="h-2.5 w-2.5 rounded-full" style={{ background: toneHex[applicationTone[col.status]] }} aria-hidden />
                    {col.label}
                  </span>
                  <Link to={`/candidates?applicationStatus=${col.status}`} className="rounded-md bg-raised px-2 py-0.5 text-xs tabular-nums text-ink-2 hover:text-ink" title="Open the full list">
                    {col.count}
                  </Link>
                </header>
                <ul className="max-h-[65vh] flex-1 space-y-2 overflow-y-auto p-2">
                  {col.candidates.length === 0 && <li className="px-2 py-6 text-center text-xs text-ink-3">No candidates</li>}
                  {col.candidates.map((c) => (
                    <li key={c.id} className="rounded-xl border border-line/70 bg-card p-2.5">
                      <Link to={`/candidates/${c.id}`} className="block font-medium text-ink hover:underline">{c.fullName}</Link>
                      <p className="text-[11px] text-ink-3">
                        <span className="font-mono">{c.candidateCode}</span> · {c.departmentName ?? 'Unassigned'}
                      </p>
                      <div className="mt-2 flex items-center justify-between gap-2">
                        <RatingPill rating={c.profileRating} />
                        <button
                          className="btn-ghost px-1.5 py-1 text-xs"
                          title="Change recruitment status"
                          aria-label={`Change status of ${c.fullName}`}
                          onClick={() => setStatusTarget({ id: c.id, name: c.fullName, code: c.candidateCode, applicationStatus: c.applicationStatus, verificationStatus: c.verificationStatus })}
                        >
                          <ArrowRightLeft size={14} /> Move
                        </button>
                      </div>
                      <p className="mt-1 text-[11px] text-ink-3">Applied {fmtDate(c.applicationDate)}</p>
                    </li>
                  ))}
                  {col.count > col.candidates.length && (
                    <li>
                      <Link to={`/candidates?applicationStatus=${col.status}`} className="block rounded-lg px-2 py-1.5 text-center text-xs text-info hover:underline">
                        View all {col.count}
                      </Link>
                    </li>
                  )}
                </ul>
              </section>
            ))}
          </div>
        </div>
      )}
      <StatusModal target={statusTarget} onClose={() => setStatusTarget(null)} />
    </div>
  );
}
