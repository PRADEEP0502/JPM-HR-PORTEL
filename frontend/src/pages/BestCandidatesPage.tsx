import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Award } from 'lucide-react';
import { api } from '../lib/api';
import { useDepartments } from '../lib/queries';
import { PENDING_STATUSES, fmtExperience } from '../lib/format';
import type { CandidateListItem, Page } from '../lib/types';
import { ApplicationPill, RatingPill } from '../components/ui/Pill';
import { Avatar, Field } from '../components/ui/Misc';
import { EmptyState, ErrorState, LoadingBlock } from '../components/ui/States';
import { StatusModal, type StatusTarget } from '../components/modals/CandidateModals';

const MAX_NEED = 100;
const needKey = (departmentId: string, position: string) => `jpm-hrms.need.${departmentId}|${position}`;

/** The number entered is remembered per department and sub-work, on this browser only. */
function rememberedNeed(departmentId: string, position: string) {
  try {
    return localStorage.getItem(needKey(departmentId, position)) ?? '';
  } catch {
    return '';
  }
}

/**
 * Enter how many people are needed; the highest Profile Match Ratings among candidates with no final decision are
 * suggested. Candidates already selected or joined count towards the requirement.
 */
export default function BestCandidatesPage() {
  const { data: departments } = useDepartments();
  const [params, setParams] = useSearchParams();
  const [statusTarget, setStatusTarget] = useState<StatusTarget | null>(null);
  const departmentId = params.get('departmentId') ?? '';
  const position = params.get('position') ?? '';
  const needText = params.get('need') ?? rememberedNeed(departmentId, position);
  const need = Math.min(Math.max(Number(needText) || 0, 0), MAX_NEED);
  const subWork = departments?.find((d) => String(d.id) === departmentId)?.positions ?? [];

  const setFilter = (next: { departmentId: string; position: string }) => {
    const p = new URLSearchParams();
    if (next.departmentId) p.set('departmentId', next.departmentId);
    if (next.position) p.set('position', next.position);
    setParams(p, { replace: true });
  };
  const setNeed = (value: string) => {
    const digits = value.replace(/\D/g, '').slice(0, 3);
    try {
      localStorage.setItem(needKey(departmentId, position), digits);
    } catch {
      /* storage unavailable - the number stays in the URL for this page */
    }
    setParams((p) => {
      const n = new URLSearchParams(p);
      n.set('need', digits);
      return n;
    }, { replace: true });
  };

  const scope = { departmentId, position };
  // under the 'candidates' key, so a status change here refreshes all three
  const filled = useQuery({
    queryKey: ['candidates', 'best-filled', departmentId, position],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { ...scope, applicationStatus: 'SELECTED,JOINED', size: 1 }),
  });
  const unrated = useQuery({
    queryKey: ['candidates', 'best-unrated', departmentId, position],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { ...scope, applicationStatus: PENDING_STATUSES, rating: 'UNRATED', size: 1 }),
  });
  const ranked = useQuery({
    queryKey: ['candidates', 'best-ranked', departmentId, position],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { ...scope, applicationStatus: PENDING_STATUSES, rating: '1-10', sort: 'rating', dir: 'desc', size: MAX_NEED }),
  });

  const selected = filled.data?.totalElements ?? 0;
  const stillNeed = Math.max(need - selected, 0);
  const rated = ranked.data?.content ?? [];
  const suggested = rated.slice(0, stillNeed);
  const cutOff = suggested.length === stillNeed && stillNeed > 0 ? suggested[stillNeed - 1].profileRating : null;
  // same mark as the last suggested candidate: the mark alone cannot decide between them
  const tied = cutOff === null ? [] : rated.slice(stillNeed).filter((c) => c.profileRating === cutOff);
  const shortBy = stillNeed - suggested.length;
  const hasLowMarks = suggested.some((c) => (c.profileRating ?? 0) < 5);

  const row = (c: CandidateListItem, rank: number | null) => (
    <tr key={c.id} className="border-b border-line/40 last:border-0 hover:bg-white/[0.02]">
      <td className="td pl-4 tabular-nums text-ink-3">{rank ?? '='}</td>
      <td className="td">
        <Link to={`/candidates/${c.id}`} className="flex items-center gap-3 font-medium text-ink hover:underline">
          <Avatar name={c.fullName} size="sm" /> {c.fullName}
        </Link>
      </td>
      <td className="td"><RatingPill rating={c.profileRating} /></td>
      <td className="td">
        {c.departmentName ?? 'Unassigned'}
        {c.position && <span className="block text-xs text-ink-3">{c.position}</span>}
      </td>
      <td className="td">{fmtExperience(c.experienceYears)}</td>
      <td className="td max-w-[180px] truncate" title={c.qualification ?? undefined}>{c.qualification ?? '—'}</td>
      <td className="td"><ApplicationPill status={c.applicationStatus} /></td>
      <td className="td pr-4 text-right">
        <button
          className="btn-secondary px-2.5 py-1.5 text-xs"
          onClick={() => setStatusTarget({ id: c.id, name: c.fullName, code: c.candidateCode, applicationStatus: c.applicationStatus, verificationStatus: c.verificationStatus })}
        >
          Change status
        </button>
      </td>
    </tr>
  );

  return (
    <div className="space-y-4">
      <section className="panel grid grid-cols-1 gap-4 p-4 sm:grid-cols-3">
        <Field label="Department">
          <select className="input" value={departmentId} onChange={(e) => setFilter({ departmentId: e.target.value, position: '' })}>
            <option value="">All departments</option>
            {departments?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </Field>
        <Field label="Sub-work">
          <select className="input" value={position} disabled={subWork.length === 0} onChange={(e) => setFilter({ departmentId, position: e.target.value })}>
            <option value="">All sub-work</option>
            {subWork.map((p) => <option key={p} value={p}>{p}</option>)}
          </select>
        </Field>
        <Field label="How many people do you need?">
          <input className="input" inputMode="numeric" placeholder="e.g. 5" value={needText} onChange={(e) => setNeed(e.target.value)} />
        </Field>
      </section>

      {ranked.isLoading || filled.isLoading ? (
        <div className="panel"><LoadingBlock label="Finding the best candidates…" /></div>
      ) : ranked.error ? (
        <div className="panel"><ErrorState error={ranked.error} onRetry={() => ranked.refetch()} /></div>
      ) : need === 0 ? (
        <div className="panel"><EmptyState icon={<Award size={20} />} title="Enter how many people you need" hint="The best candidates by your marks will appear here." /></div>
      ) : (
        <>
          <section className="grid grid-cols-3 gap-3">
            {[
              { label: 'Required', value: need },
              { label: 'Already selected', value: selected, to: `/candidates?applicationStatus=SELECTED,JOINED${departmentId ? `&departmentId=${departmentId}` : ''}${position ? `&position=${encodeURIComponent(position)}` : ''}` },
              { label: 'Still needed', value: stillNeed },
            ].map((s) => {
              const body = (
                <>
                  <p className="text-xs text-ink-3">{s.label}</p>
                  <p className="mt-1 text-2xl font-semibold tabular-nums">{s.value}</p>
                </>
              );
              return s.to ? <Link key={s.label} to={s.to} className="panel p-4 transition hover:bg-card">{body}</Link> : <div key={s.label} className="panel p-4">{body}</div>;
            })}
          </section>

          {stillNeed === 0 ? (
            <div className="panel"><EmptyState icon={<Award size={20} />} title="This requirement is already filled" hint={`${selected} candidate(s) are selected or joined.`} /></div>
          ) : (
            <section className="panel p-4">
              <h2 className="text-[15px] font-semibold">Suggested: top {suggested.length} by your marks</h2>
              {shortBy > 0 && <p className="mt-1 text-sm text-warn">Only {suggested.length} rated candidate(s) available: {shortBy} short of the requirement.</p>}
              {(unrated.data?.totalElements ?? 0) > 0 && (
                <p className="mt-1 text-sm text-ink-3">
                  <Link className="underline hover:text-ink" to={`/candidates?applicationStatus=${PENDING_STATUSES}&rating=UNRATED${departmentId ? `&departmentId=${departmentId}` : ''}${position ? `&position=${encodeURIComponent(position)}` : ''}`}>
                    {unrated.data!.totalElements} candidate(s) have no mark yet
                  </Link>{' '}
                  and are not considered.
                </p>
              )}
              {hasLowMarks && <p className="mt-1 text-sm text-warn">Some suggested candidates scored below 5. They are the best available, not necessarily suitable.</p>}
              {suggested.length > 0 && (
                <div className="-mx-4 mt-3 overflow-x-auto">
                  <table className="w-full min-w-[820px]">
                    <thead>
                      <tr className="border-b border-line">
                        <th className="th pl-4">#</th>
                        <th className="th">Candidate</th>
                        <th className="th">Mark</th>
                        <th className="th">Department</th>
                        <th className="th">Experience</th>
                        <th className="th">Qualification</th>
                        <th className="th">Status</th>
                        <th className="th pr-4 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {suggested.map((c, i) => row(c, i + 1))}
                      {tied.length > 0 && (
                        <tr className="border-b border-line">
                          <td colSpan={8} className="td pl-4 text-sm text-warn">
                            {tied.length} more with the same mark ({cutOff}/10). The mark alone cannot decide between them, so please choose:
                          </td>
                        </tr>
                      )}
                      {tied.map((c) => row(c, null))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          )}
        </>
      )}
      <StatusModal target={statusTarget} onClose={() => setStatusTarget(null)} />
    </div>
  );
}
