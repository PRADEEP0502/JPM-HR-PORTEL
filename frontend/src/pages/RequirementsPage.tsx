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
  // Primary layout view toggle: Grid (Cards) vs List (Table)
  const [viewMode, setViewMode] = useState<'GRID' | 'LIST'>('GRID');

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
            <span className="text-xs text-ink-3">configured</span>
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
            <span className="text-xs font-medium">Fulfillment Rate</span>
            <Sparkles size={16} className="text-warn" />
          </div>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold tracking-tight tabular-nums text-warn">
              {totalNeeded > 0
                ? Math.min(100, Math.round(((overallFilled.data?.totalElements ?? 0) / totalNeeded) * 100))
                : 0}
              %
            </span>
            <span className="text-xs text-ink-3">progress</span>
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
          <div className="relative min-w-[200px] flex-1 sm:max-w-xs">
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

        {/* View Mode Toggle: Grid vs List with distinct button labels */}
        <div className="flex items-center gap-1.5 border-t border-line/40 pt-2 sm:border-0 sm:pt-0">
          <span className="text-xs text-ink-3 font-medium mr-1">View:</span>
          <button
            type="button"
            onClick={() => setViewMode('GRID')}
            title="Grid View (Cards)"
            className={clsx(
              'flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition',
              viewMode === 'GRID'
                ? 'bg-info text-white shadow-sm ring-1 ring-info'
                : 'bg-raised text-ink-3 hover:text-ink hover:bg-line'
            )}
          >
            <LayoutGrid size={14} />
            <span>Grid</span>
          </button>
          <button
            type="button"
            onClick={() => setViewMode('LIST')}
            title="List View (Table)"
            className={clsx(
              'flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition',
              viewMode === 'LIST'
                ? 'bg-info text-white shadow-sm ring-1 ring-info'
                : 'bg-raised text-ink-3 hover:text-ink hover:bg-line'
            )}
          >
            <List size={14} />
            <span>List</span>
          </button>
        </div>
      </div>

      {/* Requirements List / Grid Display */}
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
      ) : viewMode === 'GRID' ? (
        /* GRID VIEW: 2-3 column responsive cards layout */
        <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
          {filteredList.map((r) => (
            <RequirementGridCard
              key={r.id}
              requirement={r}
              statusFilter={filterStatus}
              onStatusChange={(target) => setStatusTarget(target)}
              onRatingChange={(target) => setRatingTarget(target)}
            />
          ))}
        </div>
      ) : (
        /* LIST VIEW: Compact Data Table layout */
        <RequirementListView
          requirements={filteredList}
          statusFilter={filterStatus}
          onStatusChange={(target) => setStatusTarget(target)}
          onRatingChange={(target) => setRatingTarget(target)}
        />
      )}

      {/* Modals */}
      <StatusModal target={statusTarget} onClose={() => setStatusTarget(null)} />
      <RatingModal target={ratingTarget} onClose={() => setRatingTarget(null)} />
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// GRID VIEW CARD COMPONENT
// -------------------------------------------------------------------------------------------------

function RequirementGridCard({
  requirement: r,
  statusFilter,
  onStatusChange,
  onRatingChange,
}: {
  requirement: Requirement;
  statusFilter: 'ALL' | 'OPEN' | 'FILLED';
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  const toast = useToast();
  const qc = useQueryClient();
  const [draft, setDraft] = useState(String(r.needed));
  const [isExpanded, setIsExpanded] = useState(false);

  useEffect(() => setDraft(String(r.needed)), [r.needed]);

  const scope = { departmentId: r.departmentId, position: r.position || undefined };
  const scopeLink = `departmentId=${r.departmentId}${r.position ? `&position=${encodeURIComponent(r.position)}` : ''}`;

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
      toast('success', 'Requirement updated');
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

  if (statusFilter === 'OPEN' && stillNeed === 0) return null;
  if (statusFilter === 'FILLED' && stillNeed > 0) return null;

  const ratedList = ranked.data?.content ?? [];
  const suggested = ratedList.slice(0, stillNeed);
  const unratedList = unrated.data?.content ?? [];
  const totalApplicants = ratedList.length + unratedList.length;
  const dirty = draft !== String(r.needed);

  const title = `${r.departmentName ?? 'Unassigned'}${r.position ? ` – ${r.position}` : ''}`;

  return (
    <div className="panel flex flex-col justify-between overflow-hidden border border-line/80 shadow-card transition-all hover:border-line">
      {/* Card Header */}
      <div className="p-4 border-b border-line/60 bg-raised/30">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <span
              className={clsx(
                'flex h-8 w-8 shrink-0 items-center justify-center rounded-xl font-bold',
                stillNeed === 0 ? 'bg-good/20 text-good' : selected > 0 ? 'bg-info/20 text-info' : 'bg-warn/20 text-warn'
              )}
            >
              <Building2 size={16} />
            </span>
            <div>
              <h2 className="font-bold text-sm text-ink line-clamp-1" title={title}>
                {title}
              </h2>
              <span className="text-[11px] text-ink-3">
                {r.position ? r.position : 'Whole Department'}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-1.5">
            {stillNeed === 0 ? (
              <span className="rounded-md bg-good/15 px-2 py-0.5 text-[11px] font-semibold text-good">
                ✓ Filled
              </span>
            ) : selected > 0 ? (
              <span className="rounded-md bg-info/15 px-2 py-0.5 text-[11px] font-semibold text-info">
                In Progress
              </span>
            ) : (
              <span className="rounded-md bg-warn/15 px-2 py-0.5 text-[11px] font-semibold text-warn">
                Shortage
              </span>
            )}

            <button
              className="btn-ghost p-1 text-ink-3 hover:text-bad"
              title="Delete requirement"
              disabled={remove.isPending}
              onClick={() =>
                window.confirm(`Delete the requirement "${title}"? Candidates are not affected.`) && remove.mutate()
              }
            >
              <Trash2 size={14} />
            </button>
          </div>
        </div>

        {/* Metric Badges */}
        <div className="mt-3.5 grid grid-cols-3 gap-2 text-center text-xs">
          <div className="rounded-lg border border-line/50 bg-panel/70 p-2">
            <span className="block text-[10px] text-ink-3">Target</span>
            <div className="flex items-center justify-center gap-1 mt-0.5">
              <input
                className="w-8 rounded bg-raised px-1 text-center font-bold text-ink text-xs outline-none"
                inputMode="numeric"
                value={draft}
                onChange={(e) => setDraft(e.target.value.replace(/\D/g, '').slice(0, 3))}
              />
              {dirty && (
                <button
                  className="rounded bg-info px-1.5 py-0.5 text-[10px] font-bold text-white hover:bg-info/80"
                  disabled={update.isPending}
                  onClick={() => (validNumber(draft) ? update.mutate() : toast('error', `1 to ${MAX_NEEDED}`))}
                >
                  ✓
                </button>
              )}
            </div>
          </div>

          <Link
            to={`/candidates?applicationStatus=SELECTED,JOINED&${scopeLink}`}
            className="rounded-lg border border-line/50 bg-panel/70 p-2 transition hover:border-good/50"
          >
            <span className="block text-[10px] text-ink-3">Hired</span>
            <span className="font-bold text-good tabular-nums text-xs mt-0.5 block">{selected}</span>
          </Link>

          <div className="rounded-lg border border-line/50 bg-panel/70 p-2">
            <span className="block text-[10px] text-ink-3">Remaining</span>
            <span className="font-bold text-warn tabular-nums text-xs mt-0.5 block">{stillNeed}</span>
          </div>
        </div>

        {/* Progress Bar */}
        <div className="mt-3 space-y-1">
          <div className="flex items-center justify-between text-[10px] text-ink-3">
            <span>Progress: {selected} of {r.needed}</span>
            <span className="font-bold text-ink-2">{fulfillmentPct}%</span>
          </div>
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-raised">
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

      {/* Card Body: Profiles Preview or Full List */}
      <div className="p-4 flex-1 space-y-3">
        {ranked.isLoading ? (
          <div className="py-4 text-center text-xs text-ink-3">Matching candidates…</div>
        ) : stillNeed === 0 ? (
          <div className="rounded-xl border border-good/30 bg-good/10 p-3 text-center text-xs text-good">
            <CheckCircle2 size={18} className="mx-auto mb-1 text-good" />
            <span className="font-semibold">Requirement fully filled</span>
          </div>
        ) : suggested.length > 0 ? (
          <div className="space-y-2.5">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-ink-2 flex items-center gap-1">
                <Star size={12} className="text-warn fill-warn/20" /> Top Matched Profiles ({suggested.length})
              </span>
            </div>

            {/* Show candidate profile mini cards */}
            <div className="space-y-2">
              {(isExpanded ? suggested : suggested.slice(0, 2)).map((c) => (
                <CandidateMiniProfileCard
                  key={c.id}
                  candidate={c}
                  onStatusChange={onStatusChange}
                  onRatingChange={onRatingChange}
                />
              ))}
            </div>

            {suggested.length > 2 && (
              <button
                type="button"
                onClick={() => setIsExpanded((v) => !v)}
                className="w-full text-center text-xs text-info hover:underline py-1"
              >
                {isExpanded ? 'Show less' : `+ View ${suggested.length - 2} more matched candidate(s)`}
              </button>
            )}
          </div>
        ) : unratedList.length > 0 ? (
          <div className="space-y-2">
            <div className="flex items-center justify-between text-xs">
              <span className="font-semibold text-ink-2 flex items-center gap-1">
                <Users size={12} className="text-info" /> Applicants in this Role ({unratedList.length})
              </span>
              <span className="text-[10px] text-ink-3">Awaiting Score</span>
            </div>

            <div className="space-y-2">
              {(isExpanded ? unratedList : unratedList.slice(0, 2)).map((c) => (
                <CandidateMiniProfileCard
                  key={c.id}
                  candidate={c}
                  onStatusChange={onStatusChange}
                  onRatingChange={onRatingChange}
                />
              ))}
            </div>

            {unratedList.length > 2 && (
              <button
                type="button"
                onClick={() => setIsExpanded((v) => !v)}
                className="w-full text-center text-xs text-info hover:underline py-1"
              >
                {isExpanded ? 'Show less' : `+ View ${unratedList.length - 2} more applicant(s)`}
              </button>
            )}
          </div>
        ) : (
          <div className="rounded-xl border border-line/50 bg-raised/20 p-4 text-center">
            <Users size={20} className="mx-auto text-ink-3" />
            <p className="mt-1.5 text-xs text-ink-3">No active applicants for this position yet</p>
            <Link
              to={`/candidates?departmentId=${r.departmentId}`}
              className="mt-2 inline-block text-[11px] text-info hover:underline"
            >
              Browse {r.departmentName ?? ''} Candidates →
            </Link>
          </div>
        )}
      </div>

      {/* Card Footer */}
      <div className="border-t border-line/60 bg-raised/20 px-4 py-2.5 flex items-center justify-between text-xs">
        <span className="text-ink-3 text-[11px]">
          Total available: <strong className="text-ink-2">{totalApplicants}</strong>
        </span>
        <Link
          to={`/candidates?${scopeLink}`}
          className="text-info font-medium hover:underline flex items-center gap-1"
        >
          View in Candidates <ArrowUpRight size={12} />
        </Link>
      </div>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// LIST VIEW (TABLE) COMPONENT
// -------------------------------------------------------------------------------------------------

function RequirementListView({
  requirements,
  statusFilter,
  onStatusChange,
  onRatingChange,
}: {
  requirements: Requirement[];
  statusFilter: 'ALL' | 'OPEN' | 'FILLED';
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  const [expandedId, setExpandedId] = useState<number | null>(null);

  return (
    <div className="panel overflow-hidden">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[850px] text-left text-xs">
          <thead>
            <tr className="border-b border-line bg-raised/50 text-ink-3">
              <th className="th pl-4">Department & Position</th>
              <th className="th">Target Needed</th>
              <th className="th">Hired</th>
              <th className="th">Remaining</th>
              <th className="th">Fulfillment Progress</th>
              <th className="th">Status</th>
              <th className="th">Matched Profiles</th>
              <th className="th pr-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {requirements.map((r) => (
              <RequirementTableRow
                key={r.id}
                requirement={r}
                statusFilter={statusFilter}
                isExpanded={expandedId === r.id}
                onToggleExpand={() => setExpandedId(expandedId === r.id ? null : r.id)}
                onStatusChange={onStatusChange}
                onRatingChange={onRatingChange}
              />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function RequirementTableRow({
  requirement: r,
  statusFilter,
  isExpanded,
  onToggleExpand,
  onStatusChange,
  onRatingChange,
}: {
  requirement: Requirement;
  statusFilter: 'ALL' | 'OPEN' | 'FILLED';
  isExpanded: boolean;
  onToggleExpand: () => void;
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  const toast = useToast();
  const qc = useQueryClient();
  const [draft, setDraft] = useState(String(r.needed));

  useEffect(() => setDraft(String(r.needed)), [r.needed]);

  const scope = { departmentId: r.departmentId, position: r.position || undefined };
  const scopeLink = `departmentId=${r.departmentId}${r.position ? `&position=${encodeURIComponent(r.position)}` : ''}`;

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
      toast('success', 'Requirement updated');
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

  if (statusFilter === 'OPEN' && stillNeed === 0) return null;
  if (statusFilter === 'FILLED' && stillNeed > 0) return null;

  const ratedList = ranked.data?.content ?? [];
  const suggested = ratedList.slice(0, stillNeed);
  const unratedList = unrated.data?.content ?? [];
  const allCandidates = [...suggested, ...unratedList];
  const dirty = draft !== String(r.needed);

  return (
    <>
      <tr className="border-b border-line/50 transition hover:bg-white/[0.02]">
        <td className="td pl-4">
          <div className="flex items-center gap-2.5">
            <span
              className={clsx(
                'flex h-8 w-8 items-center justify-center rounded-lg',
                stillNeed === 0 ? 'bg-good/15 text-good' : selected > 0 ? 'bg-info/15 text-info' : 'bg-warn/15 text-warn'
              )}
            >
              <Building2 size={16} />
            </span>
            <div>
              <span className="font-bold text-ink block">{r.departmentName ?? 'Unassigned'}</span>
              <span className="text-[11px] text-ink-3 block">{r.position || 'Whole Department'}</span>
            </div>
          </div>
        </td>

        <td className="td">
          <div className="flex items-center gap-1.5">
            <input
              className="w-10 rounded bg-raised px-1 py-0.5 text-center font-bold text-ink text-xs outline-none border border-line/60"
              inputMode="numeric"
              value={draft}
              onChange={(e) => setDraft(e.target.value.replace(/\D/g, '').slice(0, 3))}
            />
            {dirty && (
              <button
                className="rounded bg-info px-1.5 py-0.5 text-[10px] font-bold text-white hover:bg-info/80"
                disabled={update.isPending}
                onClick={() => (validNumber(draft) ? update.mutate() : toast('error', `1 to ${MAX_NEEDED}`))}
              >
                Save
              </button>
            )}
          </div>
        </td>

        <td className="td tabular-nums font-semibold text-good">
          <Link to={`/candidates?applicationStatus=SELECTED,JOINED&${scopeLink}`} className="hover:underline">
            {selected}
          </Link>
        </td>

        <td className="td tabular-nums font-semibold text-warn">{stillNeed}</td>

        <td className="td">
          <div className="flex items-center gap-2 min-w-[130px]">
            <div className="h-1.5 flex-1 rounded-full bg-raised overflow-hidden">
              <div
                className={clsx(
                  'h-full rounded-full transition-all duration-500',
                  stillNeed === 0 ? 'bg-good' : selected > 0 ? 'bg-info' : 'bg-warn'
                )}
                style={{ width: `${fulfillmentPct}%` }}
              />
            </div>
            <span className="text-xs font-semibold tabular-nums text-ink-3">{fulfillmentPct}%</span>
          </div>
        </td>

        <td className="td">
          {stillNeed === 0 ? (
            <span className="inline-flex items-center gap-1 rounded bg-good/15 px-2 py-0.5 text-[11px] font-semibold text-good">
              <CheckCircle2 size={11} /> Filled
            </span>
          ) : selected > 0 ? (
            <span className="inline-flex items-center gap-1 rounded bg-info/15 px-2 py-0.5 text-[11px] font-semibold text-info">
              In Progress
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 rounded bg-warn/15 px-2 py-0.5 text-[11px] font-semibold text-warn">
              <AlertCircle size={11} /> Vacancy
            </span>
          )}
        </td>

        <td className="td">
          <button
            onClick={onToggleExpand}
            className={clsx(
              'flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium border transition',
              isExpanded ? 'bg-info/20 text-info border-info/40' : 'bg-raised text-ink-2 border-line hover:text-ink'
            )}
          >
            <span>Candidates ({allCandidates.length})</span>
            {isExpanded ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
          </button>
        </td>

        <td className="td pr-4 text-right">
          <button
            className="btn-ghost p-1.5 text-ink-3 hover:text-bad"
            title="Delete requirement"
            disabled={remove.isPending}
            onClick={() =>
              window.confirm(`Delete the requirement for "${r.departmentName}"?`) && remove.mutate()
            }
          >
            <Trash2 size={15} />
          </button>
        </td>
      </tr>

      {/* Accordion Drawer for Candidates when row is expanded */}
      {isExpanded && (
        <tr className="bg-raised/20 border-b border-line">
          <td colSpan={8} className="p-4">
            <div className="space-y-3">
              <div className="flex items-center justify-between text-xs text-ink-3">
                <span className="font-semibold text-ink-2">
                  Matching Applicant Profiles for {r.departmentName} ({allCandidates.length})
                </span>
                <Link to={`/candidates?${scopeLink}`} className="text-info hover:underline flex items-center gap-1">
                  Open in Candidates List <ArrowUpRight size={12} />
                </Link>
              </div>

              {allCandidates.length === 0 ? (
                <p className="text-xs text-ink-3 py-2 italic">No applicants found for this position.</p>
              ) : (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {allCandidates.map((c) => (
                    <CandidateMiniProfileCard
                      key={c.id}
                      candidate={c}
                      onStatusChange={onStatusChange}
                      onRatingChange={onRatingChange}
                    />
                  ))}
                </div>
              )}
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

// -------------------------------------------------------------------------------------------------
// CANDIDATE PROFILE CARD (SHARED ACROSS GRID & LIST EXPANSIONS)
// -------------------------------------------------------------------------------------------------

function CandidateMiniProfileCard({
  candidate: c,
  onStatusChange,
  onRatingChange,
}: {
  candidate: CandidateListItem;
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  return (
    <div className="rounded-xl border border-line/70 bg-card p-3 shadow-sm transition hover:border-line">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2.5">
          <Avatar name={c.fullName} size="sm" />
          <div>
            <Link
              to={`/candidates/${c.id}`}
              className="font-bold text-xs text-ink hover:text-info hover:underline line-clamp-1"
              title={c.fullName}
            >
              {c.fullName}
            </Link>
            <div className="flex items-center gap-1.5 text-[10px] text-ink-3">
              <span className="font-mono">{c.candidateCode}</span>
              {c.mobile && (
                <>
                  <span>•</span>
                  <span className="flex items-center gap-0.5">
                    <Phone size={9} /> {c.mobile}
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

      <div className="mt-2.5 grid grid-cols-2 gap-1.5 text-[11px]">
        <div className="rounded bg-raised/50 px-2 py-1 truncate">
          <span className="text-[10px] text-ink-3 block">Role</span>
          <span className="font-medium text-ink-2 truncate block">{c.position || 'General'}</span>
        </div>
        <div className="rounded bg-raised/50 px-2 py-1">
          <span className="text-[10px] text-ink-3 block">Exp</span>
          <span className="font-medium text-ink-2">{fmtExperience(c.experienceYears)}</span>
        </div>
        {c.qualification && (
          <div className="col-span-2 rounded bg-raised/50 px-2 py-1 truncate">
            <span className="text-[10px] text-ink-3 flex items-center gap-1">
              <GraduationCap size={10} /> Qualification
            </span>
            <span className="font-medium text-ink-2 truncate block" title={c.qualification}>
              {c.qualification}
            </span>
          </div>
        )}
      </div>

      <div className="mt-2.5 flex items-center justify-between border-t border-line/50 pt-2 text-[11px]">
        <ApplicationPill status={c.applicationStatus} />

        <div className="flex items-center gap-1">
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
            className="rounded border border-line bg-raised px-1.5 py-0.5 text-[10px] text-ink-2 hover:text-warn transition"
            title="Rate candidate"
          >
            <Star size={10} className="inline mr-0.5 text-warn" /> Rate
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
            className="rounded border border-line bg-raised px-2 py-0.5 text-[10px] font-medium text-ink hover:bg-line transition"
          >
            Status
          </button>
        </div>
      </div>
    </div>
  );
}
