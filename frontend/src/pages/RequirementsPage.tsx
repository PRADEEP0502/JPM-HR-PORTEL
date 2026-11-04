import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardList, Plus, Trash2 } from 'lucide-react';
import { api } from '../lib/api';
import { useDepartments } from '../lib/queries';
import { useToast } from '../lib/toast';
import { PENDING_STATUSES, fmtExperience } from '../lib/format';
import type { CandidateListItem, Page } from '../lib/types';
import { ApplicationPill, RatingPill } from '../components/ui/Pill';
import { Avatar, Field } from '../components/ui/Misc';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';
import { StatusModal, type StatusTarget } from '../components/modals/CandidateModals';

interface Requirement {
  id: number;
  departmentId: number;
  departmentName: string | null;
  position: string | null;
  needed: number;
}

const MAX_NEEDED = 100;

const validNumber = (text: string) => {
  const n = Number(text);
  return Number.isInteger(n) && n >= 1 && n <= MAX_NEEDED;
};

/** One place to enter how many people are needed; each saved requirement suggests its best candidates by mark. */
export default function RequirementsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const { data: departments } = useDepartments();
  const [departmentId, setDepartmentId] = useState('');
  const [position, setPosition] = useState('');
  const [needed, setNeeded] = useState('');
  const subWork = departments?.find((d) => String(d.id) === departmentId)?.positions ?? [];

  const requirements = useQuery({ queryKey: ['requirements'], queryFn: () => api.get<Requirement[]>('/requirements') });

  const add = useMutation({
    mutationFn: () => api.post<Requirement>('/requirements', { departmentId: Number(departmentId), position: position || null, needed: Number(needed) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['requirements'] });
      setPosition('');
      setNeeded('');
      toast('success', 'Requirement added');
    },
    onError: (e) => toast('error', e.message),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!departmentId) return toast('error', 'Choose a department');
    if (!validNumber(needed)) return toast('error', `Enter how many people are needed (1 to ${MAX_NEEDED})`);
    add.mutate();
  };

  return (
    <div className="space-y-4">
      <form className="panel grid grid-cols-1 items-end gap-4 p-4 sm:grid-cols-4" onSubmit={submit}>
        <Field label="Department *">
          <select className="input" value={departmentId} onChange={(e) => { setDepartmentId(e.target.value); setPosition(''); }}>
            <option value="">Choose a department</option>
            {departments?.filter((d) => d.active).map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
        </Field>
        <Field label="Sub-work">
          <select className="input" value={position} disabled={subWork.length === 0} onChange={(e) => setPosition(e.target.value)}>
            <option value="">Whole department</option>
            {subWork.map((p) => <option key={p} value={p}>{p}</option>)}
          </select>
        </Field>
        <Field label="People needed *">
          <input className="input" inputMode="numeric" placeholder="e.g. 5" value={needed} onChange={(e) => setNeeded(e.target.value.replace(/\D/g, '').slice(0, 3))} />
        </Field>
        <button className="btn-primary justify-center" disabled={add.isPending}>
          {add.isPending ? <Spinner size={15} /> : <Plus size={16} />} Add requirement
        </button>
      </form>

      {requirements.isLoading ? (
        <div className="panel"><LoadingBlock label="Loading requirements…" /></div>
      ) : requirements.error ? (
        <div className="panel"><ErrorState error={requirements.error} onRetry={() => requirements.refetch()} /></div>
      ) : requirements.data?.length === 0 ? (
        <div className="panel"><EmptyState icon={<ClipboardList size={20} />} title="No requirements yet" hint="Add one above. The best candidates by your marks will appear here." /></div>
      ) : (
        requirements.data?.map((r) => <RequirementCard key={r.id} requirement={r} />)
      )}
    </div>
  );
}

function RequirementCard({ requirement: r }: { requirement: Requirement }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [draft, setDraft] = useState(String(r.needed));
  const [statusTarget, setStatusTarget] = useState<StatusTarget | null>(null);
  useEffect(() => setDraft(String(r.needed)), [r.needed]);

  const scope = { departmentId: r.departmentId, position: r.position };
  const scopeLink = `departmentId=${r.departmentId}${r.position ? `&position=${encodeURIComponent(r.position)}` : ''}`;
  // under the 'candidates' key, so a status change refreshes the counts and the suggestions
  const filled = useQuery({
    queryKey: ['candidates', 'requirement-filled', r.departmentId, r.position],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { ...scope, applicationStatus: 'SELECTED,JOINED', size: 1 }),
  });
  const unrated = useQuery({
    queryKey: ['candidates', 'requirement-unrated', r.departmentId, r.position],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { ...scope, applicationStatus: PENDING_STATUSES, rating: 'UNRATED', size: 1 }),
  });
  const ranked = useQuery({
    queryKey: ['candidates', 'requirement-ranked', r.departmentId, r.position],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { ...scope, applicationStatus: PENDING_STATUSES, rating: '1-10', sort: 'rating', dir: 'desc', size: MAX_NEEDED }),
  });

  const update = useMutation({
    mutationFn: () => api.put<Requirement>(`/requirements/${r.id}`, { needed: Number(draft) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['requirements'] }),
    onError: (e) => toast('error', e.message),
  });
  const remove = useMutation({
    mutationFn: () => api.del(`/requirements/${r.id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['requirements'] }),
    onError: (e) => toast('error', e.message),
  });

  const title = `${r.departmentName ?? 'Deleted department'}${r.position ? ` – ${r.position}` : ''}`;
  const selected = filled.data?.totalElements ?? 0;
  const stillNeed = Math.max(r.needed - selected, 0);
  const rated = ranked.data?.content ?? [];
  const suggested = rated.slice(0, stillNeed);
  const cutOff = suggested.length === stillNeed && stillNeed > 0 ? suggested[stillNeed - 1].profileRating : null;
  // same mark as the last suggested candidate: the mark alone cannot decide between them
  const tied = cutOff === null ? [] : rated.slice(stillNeed).filter((c) => c.profileRating === cutOff);
  const shortBy = stillNeed - suggested.length;
  const hasLowMarks = suggested.some((c) => (c.profileRating ?? 0) < 5);
  const dirty = draft !== String(r.needed);

  const row = (c: CandidateListItem, rank: number | null) => (
    <tr key={c.id} className="border-b border-line/40 last:border-0 hover:bg-white/[0.02]">
      <td className="td pl-4 tabular-nums text-ink-3">{rank ?? '='}</td>
      <td className="td">
        <Link to={`/candidates/${c.id}`} className="flex items-center gap-3 font-medium text-ink hover:underline">
          <Avatar name={c.fullName} size="sm" /> {c.fullName}
        </Link>
      </td>
      <td className="td"><RatingPill rating={c.profileRating} /></td>
      <td className="td">{c.position ?? '—'}</td>
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
    <section className="panel p-4">
      <div className="flex flex-wrap items-center gap-x-5 gap-y-2">
        <h2 className="min-w-[180px] flex-1 text-[15px] font-semibold">{title}</h2>
        <label className="flex items-center gap-2 text-sm text-ink-3">
          Needed
          <input
            className="input w-16 py-1.5 text-center"
            inputMode="numeric"
            aria-label={`People needed for ${title}`}
            value={draft}
            onChange={(e) => setDraft(e.target.value.replace(/\D/g, '').slice(0, 3))}
          />
        </label>
        {dirty && (
          <button
            className="btn-primary px-2.5 py-1.5 text-xs"
            disabled={update.isPending}
            onClick={() => (validNumber(draft) ? update.mutate() : toast('error', `Enter a number from 1 to ${MAX_NEEDED}`))}
          >
            Save
          </button>
        )}
        <Link to={`/candidates?applicationStatus=SELECTED,JOINED&${scopeLink}`} className="text-sm text-ink-3 hover:text-ink hover:underline">
          Selected <span className="font-semibold text-ink tabular-nums">{selected}</span>
        </Link>
        <span className="text-sm text-ink-3">Still needed <span className="font-semibold text-ink tabular-nums">{stillNeed}</span></span>
        <button
          className="btn-ghost p-2"
          aria-label={`Delete requirement ${title}`}
          disabled={remove.isPending}
          onClick={() => window.confirm(`Delete the requirement "${title}"? Candidates are not affected.`) && remove.mutate()}
        >
          <Trash2 size={15} />
        </button>
      </div>

      {ranked.isLoading || filled.isLoading ? (
        <LoadingBlock label="Finding the best candidates…" />
      ) : ranked.error ? (
        <ErrorState error={ranked.error} onRetry={() => ranked.refetch()} />
      ) : stillNeed === 0 ? (
        <p className="mt-3 text-sm text-good">This requirement is filled.</p>
      ) : (
        <>
          {shortBy > 0 && <p className="mt-3 text-sm text-warn">Only {suggested.length} rated candidate(s) available: {shortBy} short of the requirement.</p>}
          {(unrated.data?.totalElements ?? 0) > 0 && (
            <p className="mt-2 text-sm text-ink-3">
              <Link className="underline hover:text-ink" to={`/candidates?applicationStatus=${PENDING_STATUSES}&rating=UNRATED&${scopeLink}`}>
                {unrated.data!.totalElements} candidate(s) have no mark yet
              </Link>{' '}
              and are not considered.
            </p>
          )}
          {hasLowMarks && <p className="mt-2 text-sm text-warn">Some suggested candidates scored below 5. They are the best available, not necessarily suitable.</p>}
          {suggested.length > 0 && (
            <div className="-mx-4 mt-3 overflow-x-auto">
              <table className="w-full min-w-[820px]">
                <thead>
                  <tr className="border-b border-line">
                    <th className="th pl-4">#</th>
                    <th className="th">Suggested candidate</th>
                    <th className="th">Mark</th>
                    <th className="th">Sub-work</th>
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
        </>
      )}
      <StatusModal target={statusTarget} onClose={() => setStatusTarget(null)} />
    </section>
  );
}
