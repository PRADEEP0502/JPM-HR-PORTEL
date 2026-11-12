import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  AlertCircle,
  ArrowUpRight,
  Briefcase,
  Building2,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  GraduationCap,
  LayoutGrid,
  List,
  Phone,
  Plus,
  Search,
  Sparkles,
  Star,
  Trash2,
  TrendingUp,
  UserCheck,
  Users,
} from 'lucide-react';
import { api } from '../lib/api';
import { useDepartments } from '../lib/queries';
import { useToast } from '../lib/toast';
import { PENDING_STATUSES, fmtExperience } from '../lib/format';
import type { CandidateListItem, Page, Requirement } from '../lib/types';
import { ApplicationPill, RatingPill } from '../components/ui/Pill';
import { Avatar, Field } from '../components/ui/Misc';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';
import { RatingModal, StatusModal, type RatingTarget, type StatusTarget } from '../components/modals/CandidateModals';

const MAX_NEEDED = 100;

const validNumber = (text: string) => {
  const n = Number(text);
  return Number.isInteger(n) && n >= 1 && n <= MAX_NEEDED;
};

export default function RequirementsPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const { data: departments } = useDepartments();
  const [params] = useSearchParams();

  // Add requirement form state
  const [showAddForm, setShowAddForm] = useState(false);
  const [departmentId, setDepartmentId] = useState(params.get('departmentId') ?? '');
  const [position, setPosition] = useState('');
  const [needed, setNeeded] = useState('');
  const subWork = departments?.find((d) => String(d.id) === departmentId)?.positions ?? [];

  // Filters & search
  const [search, setSearch] = useState('');
  const [filterDepartment, setFilterDepartment] = useState('');
  const [filterStatus, setFilterStatus] = useState<'ALL' | 'OPEN' | 'FILLED'>('ALL');
  const [viewMode, setViewMode] = useState<'CARDS' | 'TABLE'>('CARDS');

  // Shared modals
  const [statusTarget, setStatusTarget] = useState<StatusTarget | null>(null);
  const [ratingTarget, setRatingTarget] = useState<RatingTarget | null>(null);

  const requirements = useQuery({
    queryKey: ['requirements'],
    queryFn: () => api.get<Requirement[]>('/requirements'),
  });

  const overallFilled = useQuery({
    queryKey: ['candidates', 'overall-filled-count'],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { applicationStatus: 'SELECTED,JOINED', size: 1 }),
  });

  const add = useMutation({
    mutationFn: () =>
      api.post<Requirement>('/requirements', {
        departmentId: Number(departmentId),
        position: position || null,
        needed: Number(needed),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['requirements'] });
      setPosition('');
      setNeeded('');
      setShowAddForm(false);
      toast('success', 'Requirement added successfully');
    },
    onError: (e) => toast('error', e.message),
  });

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!departmentId) return toast('error', 'Choose a department');
    if (!validNumber(needed)) return toast('error', `Enter how many people are needed (1 to ${MAX_NEEDED})`);
    add.mutate();
  };

  const reqList = requirements.data ?? [];
  const totalRoles = reqList.length;
  const totalNeeded = reqList.reduce((acc, r) => acc + r.needed, 0);

  // Filtered requirements
  const filteredList = useMemo(() => {
    return reqList.filter((r) => {
      const depName = r.departmentName ?? '';
      const posName = r.position ?? '';
      const textMatch =
        search === '' ||
        depName.toLowerCase().includes(search.toLowerCase()) ||
        posName.toLowerCase().includes(search.toLowerCase());
      const depMatch = filterDepartment === '' || String(r.departmentId) === filterDepartment;
      return textMatch && depMatch;
    });
  }, [reqList, search, filterDepartment]);

  return (
    <div className="space-y-6">
      {/* Dashboard Top Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-info/15 text-info">
              <TrendingUp size={18} />
            </span>
            <h1 className="text-xl font-bold tracking-tight text-ink">Manpower & Requirements Dashboard</h1>
          </div>
          <p className="mt-1 text-xs text-ink-3">
            Real-time workforce planning, position fulfillment, and matching applicant profiles.
          </p>
        </div>
        <button
          onClick={() => setShowAddForm((v) => !v)}
          className={clsx(
            'btn flex items-center gap-2 transition-all',
            showAddForm ? 'btn-secondary' : 'btn-primary shadow-sm'
          )}
        >
          {showAddForm ? <ChevronUp size={16} /> : <Plus size={16} />}
          <span>{showAddForm ? 'Hide Form' : 'Add New Requirement'}</span>
        </button>
      </div>

      {/* KPI Summary Cards */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-4">
        <div className="panel p-4 transition hover:border-line">
          <div className="flex items-center justify-between text-ink-3">
            <span className="text-xs font-medium">Target Roles</span>
            <Briefcase size={16} className="text-info" />
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight tabular-nums text-ink">{totalRoles}</span>
            <span className="text-xs text-ink-3">active roles</span>
          </div>
        </div>

        <div className="panel p-4 transition hover:border-line">
          <div className="flex items-center justify-between text-ink-3">
            <span className="text-xs font-medium">Total Openings</span>
            <Users size={16} className="text-violet" />
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight tabular-nums text-ink">{totalNeeded}</span>
            <span className="text-xs text-ink-3">people needed</span>
          </div>
        </div>

        <div className="panel p-4 transition hover:border-line">
          <div className="flex items-center justify-between text-ink-3">
            <span className="text-xs font-medium">Hired Candidates</span>
            <UserCheck size={16} className="text-good" />
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight tabular-nums text-good">
              {overallFilled.data?.totalElements ?? 0}
            </span>
            <span className="text-xs text-ink-3">selected / joined</span>
          </div>
        </div>

        <div className="panel p-4 transition hover:border-line">
          <div className="flex items-center justify-between text-ink-3">
            <span className="text-xs font-medium">Hiring Progress</span>
            <Sparkles size={16} className="text-warn" />
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight tabular-nums text-warn">
              {totalNeeded > 0
                ? Math.min(100, Math.round(((overallFilled.data?.totalElements ?? 0) / totalNeeded) * 100))
                : 0}
              %
            </span>
            <span className="text-xs text-ink-3">overall fulfillment</span>
          </div>
        </div>
      </div>

      {/* Collapsible Add Requirement Form */}
      {showAddForm && (
        <form
          className="panel animate-fade-up border-info/40 bg-panel/90 p-5 shadow-lg backdrop-blur"
          onSubmit={submit}
        >
          <div className="mb-4 flex items-center justify-between border-b border-line/60 pb-3">
            <div className="flex items-center gap-2">
              <Plus size={16} className="text-info" />
              <h2 className="text-sm font-semibold text-ink">Create New Position Requirement</h2>
            </div>
            <span className="text-xs text-ink-3">Specify how many candidates are needed</span>
          </div>

          <div className="grid grid-cols-1 items-end gap-4 sm:grid-cols-4">
            <Field label="Department *">
              <select
                className="input"
                value={departmentId}
                onChange={(e) => {
                  setDepartmentId(e.target.value);
                  setPosition('');
                }}
              >
                <option value="">Choose a department</option>
                {departments
                  ?.filter((d) => d.active)
                  .map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                    </option>
                  ))}
              </select>
            </Field>

            <Field label="Sub-work / Position">
              <select
                className="input"
                value={position}
                disabled={subWork.length === 0}
                onChange={(e) => setPosition(e.target.value)}
              >
                <option value="">Whole department</option>
                {subWork.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="People needed *">
              <input
                className="input"
                inputMode="numeric"
                placeholder="e.g. 5"
                value={needed}
                onChange={(e) => setNeeded(e.target.value.replace(/\D/g, '').slice(0, 3))}
              />
            </Field>

            <div className="flex items-center gap-2">
              <button className="btn-primary flex-1 justify-center" disabled={add.isPending}>
                {add.isPending ? <Spinner size={15} /> : <Plus size={16} />} Save Requirement
              </button>
              <button
                type="button"
                className="btn-ghost"
                onClick={() => setShowAddForm(false)}
              >
                Cancel
              </button>
            </div>
          </div>
        </form>
      )}

      {/* Search & Filter Toolbar */}
      <div className="panel flex flex-col gap-3 p-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 flex-wrap items-center gap-3">
          <div className="relative min-w-[220px] flex-1 sm:max-w-xs">
            <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
            <input
              type="text"
              placeholder="Search department or role…"
              className="input pl-9 text-xs"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <select
            className="input w-auto text-xs"
            value={filterDepartment}
            onChange={(e) => setFilterDepartment(e.target.value)}
          >
            <option value="">All Departments</option>
            {departments?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>

          <div className="flex items-center rounded-xl bg-raised p-1 text-xs border border-line/60">
            <button
              onClick={() => setFilterStatus('ALL')}
              className={clsx(
                'rounded-lg px-2.5 py-1 font-medium transition',
                filterStatus === 'ALL' ? 'bg-panel text-ink shadow-sm' : 'text-ink-3 hover:text-ink'
              )}
            >
              All Roles ({reqList.length})
            </button>
            <button
              onClick={() => setFilterStatus('OPEN')}
              className={clsx(
                'rounded-lg px-2.5 py-1 font-medium transition',
                filterStatus === 'OPEN' ? 'bg-panel text-warn shadow-sm' : 'text-ink-3 hover:text-ink'
              )}
            >
              Open Vacancies
            </button>
            <button
              onClick={() => setFilterStatus('FILLED')}
              className={clsx(
                'rounded-lg px-2.5 py-1 font-medium transition',
                filterStatus === 'FILLED' ? 'bg-panel text-good shadow-sm' : 'text-ink-3 hover:text-ink'
              )}
            >
              Filled
            </button>
          </div>
        </div>

        <div className="flex items-center gap-1 border-t border-line/40 pt-2 sm:border-0 sm:pt-0">
          <span className="text-xs text-ink-3 mr-1">View:</span>
          <button
            onClick={() => setViewMode('CARDS')}
            title="Profile Cards View"
            className={clsx(
              'rounded-lg p-1.5 transition',
              viewMode === 'CARDS' ? 'bg-raised text-ink' : 'text-ink-3 hover:text-ink'
            )}
          >
            <LayoutGrid size={16} />
          </button>
          <button
            onClick={() => setViewMode('TABLE')}
            title="Table View"
            className={clsx(
              'rounded-lg p-1.5 transition',
              viewMode === 'TABLE' ? 'bg-raised text-ink' : 'text-ink-3 hover:text-ink'
            )}
          >
            <List size={16} />
          </button>
        </div>
      </div>

      {/* Requirements List */}
      {requirements.isLoading ? (
        <div className="panel">
          <LoadingBlock label="Loading manpower dashboard…" />
        </div>
      ) : requirements.error ? (
        <div className="panel">
          <ErrorState error={requirements.error} onRetry={() => requirements.refetch()} />
        </div>
      ) : reqList.length === 0 ? (
        <div className="panel">
          <EmptyState
            icon={<Briefcase size={22} />}
            title="No hiring requirements configured"
            hint="Click '+ Add New Requirement' above to define roles and hiring targets for each department."
          />
        </div>
      ) : filteredList.length === 0 ? (
        <div className="panel p-8 text-center text-sm text-ink-3">
          No requirements match your current search or filter.
        </div>
      ) : (
        <div className="space-y-5">
          {filteredList.map((r) => (
            <RequirementCard
              key={r.id}
              requirement={r}
              viewMode={viewMode}
              statusFilter={filterStatus}
              onStatusChange={(target) => setStatusTarget(target)}
              onRatingChange={(target) => setRatingTarget(target)}
            />
          ))}
        </div>
      )}

      {/* Modals */}
      <StatusModal target={statusTarget} onClose={() => setStatusTarget(null)} />
      <RatingModal target={ratingTarget} onClose={() => setRatingTarget(null)} />
    </div>
  );
}

function RequirementCard({
  requirement: r,
  viewMode,
  statusFilter,
  onStatusChange,
  onRatingChange,
}: {
  requirement: Requirement;
  viewMode: 'CARDS' | 'TABLE';
  statusFilter: 'ALL' | 'OPEN' | 'FILLED';
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  const toast = useToast();
  const qc = useQueryClient();
  const [draft, setDraft] = useState(String(r.needed));
  const [isExpanded, setIsExpanded] = useState(true);

  useEffect(() => setDraft(String(r.needed)), [r.needed]);

  const scope = { departmentId: r.departmentId, position: r.position };
  const scopeLink = `departmentId=${r.departmentId}${r.position ? `&position=${encodeURIComponent(r.position)}` : ''}`;

  // Queries for filled candidates and matching applicants
  const filled = useQuery({
    queryKey: ['candidates', 'requirement-filled', r.departmentId, r.position],
    queryFn: () =>
      api.get<Page<CandidateListItem>>('/candidates', {
        ...scope,
        applicationStatus: 'SELECTED,JOINED',
        size: 1,
      }),
  });

  const ranked = useQuery({
    queryKey: ['candidates', 'requirement-ranked', r.departmentId, r.position],
    queryFn: () =>
      api.get<Page<CandidateListItem>>('/candidates', {
        ...scope,
        applicationStatus: PENDING_STATUSES,
        rating: '1-10',
        sort: 'rating',
        dir: 'desc',
        size: MAX_NEEDED,
      }),
  });

  const unrated = useQuery({
    queryKey: ['candidates', 'requirement-unrated', r.departmentId, r.position],
    queryFn: () =>
      api.get<Page<CandidateListItem>>('/candidates', {
        ...scope,
        applicationStatus: PENDING_STATUSES,
        rating: 'UNRATED',
        sort: 'applicationDate',
        dir: 'desc',
        size: MAX_NEEDED,
      }),
  });

  const update = useMutation({
    mutationFn: () => api.put<Requirement>(`/requirements/${r.id}`, { needed: Number(draft) }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['requirements'] });
      toast('success', 'Requirement count updated');
    },
    onError: (e) => toast('error', e.message),
  });

  const remove = useMutation({
    mutationFn: () => api.del(`/requirements/${r.id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['requirements'] });
      toast('success', 'Requirement deleted');
    },
    onError: (e) => toast('error', e.message),
  });

  const selected = filled.data?.totalElements ?? 0;
  const stillNeed = Math.max(r.needed - selected, 0);
  const fulfillmentPct = r.needed > 0 ? Math.min(100, Math.round((selected / r.needed) * 100)) : 0;

  // Filter out if user selected OPEN or FILLED tabs
  if (statusFilter === 'OPEN' && stillNeed === 0) return null;
  if (statusFilter === 'FILLED' && stillNeed > 0) return null;

  const ratedList = ranked.data?.content ?? [];
  const suggested = ratedList.slice(0, stillNeed);
  const cutOff = suggested.length === stillNeed && stillNeed > 0 ? suggested[stillNeed - 1].profileRating : null;
  const tied = cutOff === null ? [] : ratedList.slice(stillNeed).filter((c) => c.profileRating === cutOff);
  const unratedList = unrated.data?.content ?? [];
  const dirty = draft !== String(r.needed);

  const title = `${r.departmentName ?? 'Unassigned Department'}${r.position ? ` – ${r.position}` : ''}`;

  return (
    <section className="panel overflow-hidden border border-line/80 shadow-card transition-all">
      {/* Top Banner / Card Header */}
      <div className="border-b border-line/60 bg-raised/40 p-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-start gap-3">
            <span
              className={clsx(
                'mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-white font-semibold',
                stillNeed === 0 ? 'bg-good/20 text-good' : selected > 0 ? 'bg-info/20 text-info' : 'bg-warn/20 text-warn'
              )}
            >
              {stillNeed === 0 ? <CheckCircle2 size={18} /> : <Building2 size={18} />}
            </span>

            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-base font-bold text-ink">{title}</h2>
                {stillNeed === 0 ? (
                  <span className="inline-flex items-center gap-1 rounded-md bg-good/15 px-2 py-0.5 text-xs font-semibold text-good">
                    <CheckCircle2 size={12} /> Filled
                  </span>
                ) : selected > 0 ? (
                  <span className="inline-flex items-center gap-1 rounded-md bg-info/15 px-2 py-0.5 text-xs font-semibold text-[#8fb4f5]">
                    In Progress ({selected}/{r.needed})
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 rounded-md bg-warn/15 px-2 py-0.5 text-xs font-semibold text-warn">
                    <AlertCircle size={12} /> {stillNeed} Needed
                  </span>
                )}
              </div>
              <p className="mt-0.5 text-xs text-ink-3">
                Department: <span className="text-ink-2 font-medium">{r.departmentName ?? 'General'}</span>
                {r.position && (
                  <>
                    {' '}• Role: <span className="text-ink-2 font-medium">{r.position}</span>
                  </>
                )}
              </p>
            </div>
          </div>

          {/* Right Action Counters & Inline Controls */}
          <div className="flex flex-wrap items-center gap-3 text-xs">
            {/* Inline Editor for Needed */}
            <div className="flex items-center gap-1.5 rounded-xl border border-line/70 bg-panel px-2.5 py-1">
              <span className="text-ink-3">Target:</span>
              <input
                className="w-10 rounded bg-raised px-1 py-0.5 text-center font-bold text-ink outline-none"
                inputMode="numeric"
                value={draft}
                onChange={(e) => setDraft(e.target.value.replace(/\D/g, '').slice(0, 3))}
              />
              {dirty && (
                <button
                  className="rounded bg-info px-2 py-0.5 text-[11px] font-semibold text-white transition hover:bg-info/80"
                  disabled={update.isPending}
                  onClick={() => (validNumber(draft) ? update.mutate() : toast('error', `Enter 1 to ${MAX_NEEDED}`))}
                >
                  Save
                </button>
              )}
            </div>

            <Link
              to={`/candidates?applicationStatus=SELECTED,JOINED&${scopeLink}`}
              className="rounded-xl border border-line/70 bg-panel px-3 py-1.5 text-ink-3 transition hover:border-line hover:text-ink"
            >
              Hired: <span className="font-bold text-good tabular-nums">{selected}</span>
            </Link>

            <span className="rounded-xl border border-line/70 bg-panel px-3 py-1.5 text-ink-3">
              Remaining: <span className="font-bold text-warn tabular-nums">{stillNeed}</span>
            </span>

            <button
              className="btn-ghost p-1.5 text-ink-3 hover:text-bad"
              title={`Delete requirement ${title}`}
              disabled={remove.isPending}
              onClick={() =>
                window.confirm(`Delete the requirement "${title}"? Candidates will not be deleted.`) && remove.mutate()
              }
            >
              <Trash2 size={16} />
            </button>

            <button
              onClick={() => setIsExpanded((v) => !v)}
              className="btn-ghost p-1.5 text-ink-3"
              title={isExpanded ? 'Collapse' : 'Expand'}
            >
              {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
            </button>
          </div>
        </div>

        {/* Progress Bar Strip */}
        <div className="mt-3.5 space-y-1">
          <div className="flex items-center justify-between text-[11px] text-ink-3">
            <span>Fulfillment Status</span>
            <span className="font-semibold text-ink-2">
              {selected} of {r.needed} hired ({fulfillmentPct}%)
            </span>
          </div>
          <div className="h-2 w-full overflow-hidden rounded-full bg-raised">
            <div
              className={clsx(
                'h-full rounded-full transition-all duration-500',
                stillNeed === 0 ? 'bg-good' : selected > 0 ? 'bg-info' : 'bg-warn'
              )}
              style={{ width: `${fulfillmentPct}%` }}
            />
          </div>
        </div>
      </div>

      {/* Expandable Candidates & Profiles Section */}
      {isExpanded && (
        <div className="p-4 space-y-4">
          {ranked.isLoading || filled.isLoading ? (
            <LoadingBlock label="Matching applicant profiles…" />
          ) : ranked.error ? (
            <ErrorState error={ranked.error} onRetry={() => ranked.refetch()} />
          ) : stillNeed === 0 ? (
            <div className="rounded-xl border border-good/30 bg-good/10 p-4 text-center">
              <CheckCircle2 size={24} className="mx-auto text-good" />
              <h3 className="mt-2 text-sm font-semibold text-ink">Requirement Fully Met!</h3>
              <p className="mt-1 text-xs text-ink-2">
                All {r.needed} required candidate(s) have been successfully selected or joined.
              </p>
              <Link
                to={`/candidates?applicationStatus=SELECTED,JOINED&${scopeLink}`}
                className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-good hover:underline"
              >
                View Hired Candidates <ArrowUpRight size={13} />
              </Link>
            </div>
          ) : (
            <>
              {/* Recommended Top Rated Candidates */}
              {suggested.length > 0 && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Star size={15} className="text-warn fill-warn/20" />
                      <h3 className="text-xs font-bold uppercase tracking-wider text-ink-2">
                        Top Rated Candidate Profiles ({suggested.length})
                      </h3>
                    </div>
                    <span className="text-xs text-ink-3">Ranked by interview mark</span>
                  </div>

                  {viewMode === 'CARDS' ? (
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                      {suggested.map((c) => (
                        <CandidateProfileCard
                          key={c.id}
                          candidate={c}
                          onStatusChange={onStatusChange}
                          onRatingChange={onRatingChange}
                        />
                      ))}
                    </div>
                  ) : (
                    <div className="-mx-4 overflow-x-auto">
                      <CandidateTableView
                        candidates={suggested}
                        onStatusChange={onStatusChange}
                        onRatingChange={onRatingChange}
                      />
                    </div>
                  )}
                </div>
              )}

              {/* Tied Candidates Alert */}
              {tied.length > 0 && (
                <div className="rounded-xl border border-warn/40 bg-warn/10 p-3 text-xs text-warn">
                  <p className="font-semibold">
                    {tied.length} more applicant(s) tied with mark {cutOff}/10. Review their profiles:
                  </p>
                  <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                    {tied.map((c) => (
                      <CandidateProfileCard
                        key={c.id}
                        candidate={c}
                        onStatusChange={onStatusChange}
                        onRatingChange={onRatingChange}
                      />
                    ))}
                  </div>
                </div>
              )}

              {/* Applicants Awaiting Rating (Shown so user can see candidates even before rating!) */}
              {unratedList.length > 0 && (
                <div className="mt-4 space-y-3 border-t border-line/60 pt-4">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Users size={15} className="text-info" />
                      <h3 className="text-xs font-bold uppercase tracking-wider text-ink-2">
                        Applicants in this Role ({unratedList.length})
                      </h3>
                      <span className="rounded bg-raised px-1.5 py-0.5 text-[10px] text-ink-3">
                        Awaiting Score
                      </span>
                    </div>
                    <Link
                      to={`/candidates?applicationStatus=${PENDING_STATUSES}&rating=UNRATED&${scopeLink}`}
                      className="text-xs text-info hover:underline flex items-center gap-1"
                    >
                      Rate in candidate list <ArrowUpRight size={13} />
                    </Link>
                  </div>

                  {viewMode === 'CARDS' ? (
                    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                      {unratedList.slice(0, 6).map((c) => (
                        <CandidateProfileCard
                          key={c.id}
                          candidate={c}
                          onStatusChange={onStatusChange}
                          onRatingChange={onRatingChange}
                        />
                      ))}
                    </div>
                  ) : (
                    <div className="-mx-4 overflow-x-auto">
                      <CandidateTableView
                        candidates={unratedList.slice(0, 6)}
                        onStatusChange={onStatusChange}
                        onRatingChange={onRatingChange}
                      />
                    </div>
                  )}

                  {unratedList.length > 6 && (
                    <p className="text-center text-xs text-ink-3">
                      + {unratedList.length - 6} more applicant(s).{' '}
                      <Link
                        to={`/candidates?${scopeLink}`}
                        className="text-info underline hover:text-white"
                      >
                        View all in Candidates page
                      </Link>
                    </p>
                  )}
                </div>
              )}

              {/* Empty State when zero candidates are in database for this role */}
              {suggested.length === 0 && unratedList.length === 0 && (
                <div className="rounded-xl border border-line/60 bg-raised/30 p-6 text-center">
                  <Users size={28} className="mx-auto text-ink-3" />
                  <h4 className="mt-2 text-sm font-semibold text-ink">No applicants found for this position</h4>
                  <p className="mt-1 text-xs text-ink-3">
                    There are currently no active candidates applying for {title}.
                  </p>
                  <div className="mt-4 flex items-center justify-center gap-3">
                    <Link
                      to={`/candidates?departmentId=${r.departmentId}`}
                      className="btn-secondary text-xs px-3 py-1.5"
                    >
                      Browse All {r.departmentName ?? ''} Applicants
                    </Link>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </section>
  );
}

/** Individual Visual Candidate Profile Card */
function CandidateProfileCard({
  candidate: c,
  onStatusChange,
  onRatingChange,
}: {
  candidate: CandidateListItem;
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  return (
    <div className="flex flex-col justify-between rounded-xl border border-line/70 bg-card p-4 transition-all hover:border-line hover:shadow-md">
      <div>
        {/* Header: Avatar, Name, Code, Rating */}
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-3">
            <Avatar name={c.fullName} size="md" />
            <div>
              <Link
                to={`/candidates/${c.id}`}
                className="font-bold text-sm text-ink hover:text-info hover:underline line-clamp-1"
                title={c.fullName}
              >
                {c.fullName}
              </Link>
              <div className="flex items-center gap-2 text-[11px] text-ink-3 mt-0.5">
                <span className="font-mono">{c.candidateCode}</span>
                {c.mobile && (
                  <>
                    <span>•</span>
                    <span className="flex items-center gap-1">
                      <Phone size={10} className="text-ink-3" /> {c.mobile}
                    </span>
                  </>
                )}
              </div>
            </div>
          </div>
          <div className="shrink-0">
            <RatingPill rating={c.profileRating} />
          </div>
        </div>

        {/* Details Badges: Role, Exp, Qualification */}
        <div className="mt-3.5 grid grid-cols-2 gap-2 text-xs">
          <div className="rounded-lg border border-line/40 bg-raised/50 p-2">
            <span className="block text-[10px] text-ink-3">Sub-work / Role</span>
            <span className="block truncate font-medium text-ink-2" title={c.position ?? 'Whole department'}>
              {c.position || 'Department Wide'}
            </span>
          </div>

          <div className="rounded-lg border border-line/40 bg-raised/50 p-2">
            <span className="block text-[10px] text-ink-3">Experience</span>
            <span className="block font-medium text-ink-2">{fmtExperience(c.experienceYears)}</span>
          </div>

          {c.qualification && (
            <div className="col-span-2 rounded-lg border border-line/40 bg-raised/50 p-2">
              <span className="flex items-center gap-1 text-[10px] text-ink-3">
                <GraduationCap size={11} /> Qualification
              </span>
              <span className="block truncate font-medium text-ink-2" title={c.qualification}>
                {c.qualification}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Card Footer: Status & Actions */}
      <div className="mt-4 flex items-center justify-between border-t border-line/50 pt-3">
        <ApplicationPill status={c.applicationStatus} />

        <div className="flex items-center gap-1.5">
          <button
            onClick={() =>
              onRatingChange({
                id: c.id,
                name: c.fullName,
                code: c.candidateCode,
                rating: c.profileRating,
                remark: c.ratingRemark,
              })
            }
            className="rounded-lg border border-line/60 bg-raised px-2 py-1 text-xs text-ink-2 transition hover:text-warn hover:border-warn/40"
            title="Set / update rating mark"
          >
            <Star size={12} className="inline mr-1 text-warn" />
            Rate
          </button>

          <button
            onClick={() =>
              onStatusChange({
                id: c.id,
                name: c.fullName,
                code: c.candidateCode,
                applicationStatus: c.applicationStatus,
                verificationStatus: c.verificationStatus,
              })
            }
            className="rounded-lg bg-raised px-2.5 py-1 text-xs font-medium text-ink border border-line hover:bg-line transition"
          >
            Status
          </button>
        </div>
      </div>
    </div>
  );
}

/** Table View Component */
function CandidateTableView({
  candidates,
  onStatusChange,
  onRatingChange,
}: {
  candidates: CandidateListItem[];
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  return (
    <table className="w-full min-w-[760px] text-left text-xs">
      <thead>
        <tr className="border-b border-line text-ink-3">
          <th className="th pl-4">Candidate</th>
          <th className="th">Match Rating</th>
          <th className="th">Role</th>
          <th className="th">Experience</th>
          <th className="th">Qualification</th>
          <th className="th">Status</th>
          <th className="th pr-4 text-right">Actions</th>
        </tr>
      </thead>
      <tbody>
        {candidates.map((c) => (
          <tr key={c.id} className="border-b border-line/40 last:border-0 hover:bg-white/[0.02]">
            <td className="td pl-4">
              <Link to={`/candidates/${c.id}`} className="flex items-center gap-2.5 font-medium text-ink hover:underline">
                <Avatar name={c.fullName} size="sm" />
                <div>
                  <span className="block font-semibold">{c.fullName}</span>
                  <span className="block text-[11px] text-ink-3">{c.candidateCode}</span>
                </div>
              </Link>
            </td>
            <td className="td">
              <RatingPill rating={c.profileRating} />
            </td>
            <td className="td">{c.position ?? '—'}</td>
            <td className="td">{fmtExperience(c.experienceYears)}</td>
            <td className="td max-w-[160px] truncate" title={c.qualification ?? undefined}>
              {c.qualification ?? '—'}
            </td>
            <td className="td">
              <ApplicationPill status={c.applicationStatus} />
            </td>
            <td className="td pr-4 text-right">
              <div className="flex items-center justify-end gap-1.5">
                <button
                  onClick={() =>
                    onRatingChange({
                      id: c.id,
                      name: c.fullName,
                      code: c.candidateCode,
                      rating: c.profileRating,
                      remark: c.ratingRemark,
                    })
                  }
                  className="rounded border border-line bg-raised px-2 py-1 text-[11px] text-ink-2 hover:text-warn"
                >
                  <Star size={11} className="inline mr-0.5 text-warn" /> Rate
                </button>
                <button
                  onClick={() =>
                    onStatusChange({
                      id: c.id,
                      name: c.fullName,
                      code: c.candidateCode,
                      applicationStatus: c.applicationStatus,
                      verificationStatus: c.verificationStatus,
                    })
                  }
                  className="rounded border border-line bg-raised px-2 py-1 text-[11px] text-ink hover:bg-line"
                >
                  Status
                </button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
