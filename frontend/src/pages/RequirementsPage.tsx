import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import {
  AlertCircle,
  ArrowUpRight,
  Check,
  Briefcase,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  GraduationCap,
  Pencil,
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
import { Modal } from '../components/ui/Modal';
import { RatingModal, StatusModal, type RatingTarget, type StatusTarget } from '../components/modals/CandidateModals';

const MAX_NEEDED = 100;

const validNumber = (text: string) => {
  const n = Number(text);
  return Number.isInteger(n) && n >= 1 && n <= MAX_NEEDED;
};

/** 1 → "1st", 2 → "2nd", 11 → "11th" … */
const ordinal = (n: number) => {
  const s = ['th', 'st', 'nd', 'rd'];
  const v = n % 100;
  return n + (s[(v - 20) % 10] || s[v] || s[0]);
};

/** False while the server is an older version that does not store priorities yet (reordering is then switched off). */
const PriorityReady = createContext(true);
const SERVER_OLD_MSG = 'The server is still running the old version, so priority cannot be saved yet. Redeploy the backend and try again.';

const requirementTitle = (r: Requirement) => `${r.departmentName ?? 'Unassigned'}${r.position ? ` – ${r.position}` : ''}`;

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
  const [priority, setPriority] = useState('');
  const subWork = departments?.find((d) => String(d.id) === departmentId)?.positions ?? [];

  // Filters & search
  const [search, setSearch] = useState('');
  // opened from the Departments page (?departmentId=…) → show that department's requirements
  const [filterDepartment, setFilterDepartment] = useState(params.get('departmentId') ?? '');
  const [filterStatus, setFilterStatus] = useState<'ALL' | 'OPEN' | 'FILLED'>('ALL');

  // Shared modals
  const [statusTarget, setStatusTarget] = useState<StatusTarget | null>(null);
  const [ratingTarget, setRatingTarget] = useState<RatingTarget | null>(null);
  const [editTarget, setEditTarget] = useState<Requirement | null>(null);

  const requirements = useQuery({
    queryKey: ['requirements'],
    queryFn: () => api.get<Requirement[]>('/requirements'),
  });

  const add = useMutation({
    mutationFn: () =>
      api.post<Requirement>('/requirements', {
        departmentId: Number(departmentId),
        position: position || null,
        needed: Number(needed),
        priority: priority ? Number(priority) : null,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['requirements'] });
      setPosition('');
      setNeeded('');
      setPriority('');
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

  // the server sends them in priority order (1st first); sorting again keeps that true after any local change
  const raw = requirements.data ?? [];
  // an older server sends neither priority nor hired: count hires per requirement here instead and number them in saved order
  const priorityReady = raw.every((r) => typeof r.priority === 'number' && r.priority > 0);
  const hiredFallback = useQueries({
    queries: raw.map((r) => ({
      queryKey: ['candidates', 'requirement-filled', r.departmentId, r.position],
      queryFn: () =>
        api.get<Page<CandidateListItem>>('/candidates', {
          departmentId: r.departmentId,
          position: r.position || undefined,
          applicationStatus: 'SELECTED,JOINED',
          size: 1,
        }),
      enabled: typeof r.hired !== 'number',
    })),
  });
  const reqList: Requirement[] = raw
    .map((r, i) => ({ ...r, hired: typeof r.hired === 'number' ? r.hired : (hiredFallback[i]?.data?.totalElements ?? 0) }))
    .sort((a, b) => (a.priority || Infinity) - (b.priority || Infinity) || a.id - b.id)
    .map((r, i) => (r.priority > 0 ? r : { ...r, priority: i + 1 }));

  // hired counts come from the server for each requirement (same department + sub-work match as the candidate list)
  const stillNeedOf = (r: Requirement) => Math.max(0, r.needed - r.hired);

  const totalRoles = reqList.length;
  const totalNeeded = reqList.reduce((acc, r) => acc + r.needed, 0);
  // only hires that count towards a target (extra hires beyond the number needed do not inflate the rate)
  const totalHired = reqList.reduce((acc, r) => acc + Math.min(r.hired, r.needed), 0);
  const openRolesCount = reqList.filter((r) => stillNeedOf(r) > 0).length;
  const filledRolesCount = reqList.length - openRolesCount;
  const fulfillment = totalNeeded > 0 ? Math.min(100, Math.round((totalHired / totalNeeded) * 100)) : 0;

  const filteredList = useMemo(() => {
    const q = search.trim().toLowerCase();
    return reqList.filter((r) => {
      const textMatch =
        q === '' || (r.departmentName ?? '').toLowerCase().includes(q) || (r.position ?? '').toLowerCase().includes(q);
      const depMatch = filterDepartment === '' || String(r.departmentId) === filterDepartment;
      const stillNeed = stillNeedOf(r);
      const statusMatch =
        filterStatus === 'ALL' ||
        (filterStatus === 'OPEN' && stillNeed > 0) ||
        (filterStatus === 'FILLED' && stillNeed === 0);
      return textMatch && depMatch && statusMatch;
    });
  }, [reqList, search, filterDepartment, filterStatus]);

  return (
    <PriorityReady.Provider value={priorityReady}>
    <div className="space-y-6">
      {/* Dashboard Top Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-info/15 text-info">
              <TrendingUp size={18} />
            </span>
            <h1 className="text-xl font-bold tracking-tight text-ink">Manpower Requirements</h1>
          </div>
          <p className="mt-1 text-xs text-ink-3">
            Every role in priority order: how far each one is filled and who can fill it next.
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
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Kpi icon={<Briefcase size={16} />} tone="info" label="Roles" value={totalRoles} hint={`${openRolesCount} open · ${filledRolesCount} filled`} />
        <Kpi icon={<Users size={16} />} tone="violet" label="People needed" value={totalNeeded} hint="across all roles" />
        <Kpi icon={<UserCheck size={16} />} tone="good" label="Hired" value={totalHired} hint="selected / joined, towards targets" />
        <Kpi
          icon={<Sparkles size={16} />}
          tone="warn"
          label="Fulfillment"
          value={`${fulfillment}%`}
          hint={
            <span className="mt-1.5 block h-1.5 w-full overflow-hidden rounded-full bg-raised">
              <span className="block h-full rounded-full bg-gradient-to-r from-warn to-good transition-all duration-700" style={{ width: `${fulfillment}%` }} />
            </span>
          }
        />
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

          <div className="grid grid-cols-1 items-end gap-4 sm:grid-cols-2 lg:grid-cols-5">
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

            <Field label="Priority">
              <select className="input" value={priority} onChange={(e) => setPriority(e.target.value)}>
                <option value="">Last ({ordinal(reqList.length + 1)})</option>
                {reqList.map((_, i) => (
                  <option key={i + 1} value={i + 1}>
                    {ordinal(i + 1)} priority{i === 0 ? ' (most urgent)' : ''}
                  </option>
                ))}
              </select>
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

          {/* Status Tabs with Exact Dynamic Counts and Clear Active Highlighting */}
          <div className="flex items-center rounded-xl bg-raised p-1 text-xs border border-line/60">
            <button
              type="button"
              onClick={() => setFilterStatus('ALL')}
              className={clsx(
                'rounded-lg px-3 py-1 font-semibold transition',
                filterStatus === 'ALL'
                  ? 'bg-panel text-ink shadow-sm ring-1 ring-line/80'
                  : 'text-ink-3 hover:text-ink'
              )}
            >
              All Roles ({reqList.length})
            </button>
            <button
              type="button"
              onClick={() => setFilterStatus('OPEN')}
              className={clsx(
                'rounded-lg px-3 py-1 font-semibold transition',
                filterStatus === 'OPEN'
                  ? 'bg-panel text-warn shadow-sm ring-1 ring-warn/50'
                  : 'text-ink-3 hover:text-ink'
              )}
            >
              Open Vacancies ({openRolesCount})
            </button>
            <button
              type="button"
              onClick={() => setFilterStatus('FILLED')}
              className={clsx(
                'rounded-lg px-3 py-1 font-semibold transition',
                filterStatus === 'FILLED'
                  ? 'bg-panel text-good shadow-sm ring-1 ring-good/50'
                  : 'text-ink-3 hover:text-ink'
              )}
            >
              Filled ({filledRolesCount})
            </button>
          </div>
        </div>
      </div>

      {/* Requirements list (priority order) */}
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
        /* Informative Empty State based on active filter */
        <div className="panel p-8 text-center">
          {filterStatus === 'FILLED' ? (
            <div className="max-w-md mx-auto space-y-2">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-warn/15 text-warn mx-auto mb-2">
                <AlertCircle size={24} />
              </div>
              <h3 className="text-base font-bold text-ink">No Filled Positions Yet</h3>
              <p className="text-xs text-ink-3">
                All {reqList.length} configured roles currently have open vacancies ({openRolesCount} pending hiring). Once candidates are hired (Selected / Joined), they will appear here.
              </p>
              <button
                type="button"
                onClick={() => setFilterStatus('OPEN')}
                className="btn-primary mt-3 text-xs px-4 py-2"
              >
                View Open Vacancies ({openRolesCount})
              </button>
            </div>
          ) : filterStatus === 'OPEN' ? (
            <div className="max-w-md mx-auto space-y-2">
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-good/15 text-good mx-auto mb-2">
                <CheckCircle2 size={24} />
              </div>
              <h3 className="text-base font-bold text-ink">All Positions Are Filled!</h3>
              <p className="text-xs text-ink-3">
                Great job! There are no remaining vacancies among your requirements.
              </p>
              <button
                type="button"
                onClick={() => setFilterStatus('ALL')}
                className="btn-secondary mt-3 text-xs px-4 py-2"
              >
                View All Roles ({reqList.length})
              </button>
            </div>
          ) : (
            <div className="max-w-md mx-auto space-y-2">
              <p className="text-sm text-ink-2 font-medium">No requirements match your current search or filters.</p>
              <button
                type="button"
                onClick={() => {
                  setSearch('');
                  setFilterDepartment('');
                  setFilterStatus('ALL');
                }}
                className="btn-secondary text-xs mt-3 px-3.5 py-1.5"
              >
                Clear Filters
              </button>
            </div>
          )}
        </div>
      ) : (
        <RequirementListView
          requirements={filteredList}
          total={reqList.length}
          onEdit={setEditTarget}
          onStatusChange={(target) => setStatusTarget(target)}
          onRatingChange={(target) => setRatingTarget(target)}
        />
      )}

      {/* Modals */}
      <StatusModal target={statusTarget} onClose={() => setStatusTarget(null)} />
      <RatingModal target={ratingTarget} onClose={() => setRatingTarget(null)} />
      <EditRequirementModal target={editTarget} total={reqList.length} onClose={() => setEditTarget(null)} />
    </div>
    </PriorityReady.Provider>
  );
}

// -------------------------------------------------------------------------------------------------
// KPI TILE
// -------------------------------------------------------------------------------------------------

const TONE = {
  info: 'from-info/25 to-info/5 text-info',
  violet: 'from-violet/25 to-violet/5 text-violet',
  good: 'from-good/25 to-good/5 text-good',
  warn: 'from-warn/25 to-warn/5 text-warn',
} as const;

function Kpi({ icon, tone, label, value, hint }: { icon: React.ReactNode; tone: keyof typeof TONE; label: string; value: React.ReactNode; hint: React.ReactNode }) {
  return (
    <div className="panel relative overflow-hidden p-4">
      <div className={clsx('pointer-events-none absolute -right-6 -top-6 h-20 w-20 rounded-full bg-gradient-to-br opacity-60 blur-xl', TONE[tone])} />
      <div className="relative flex items-center gap-3">
        <span className={clsx('flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br ring-1 ring-line/60', TONE[tone])}>{icon}</span>
        <div className="min-w-0 flex-1">
          <div className="text-[11px] font-medium uppercase tracking-wider text-ink-3">{label}</div>
          <div className="text-2xl font-bold leading-tight tracking-tight tabular-nums text-ink">{value}</div>
        </div>
      </div>
      <div className="relative mt-1 text-[11px] text-ink-3">{hint}</div>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// LIST VIEW
// -------------------------------------------------------------------------------------------------

type FillState = 'OPEN' | 'PROGRESS' | 'FILLED';

const FILL_META: Record<FillState, { label: string; dot: string; pill: string; bar: string; accent: string }> = {
  OPEN: { label: 'Hiring', dot: 'bg-warn', pill: 'bg-warn/10 text-warn ring-warn/30', bar: 'bg-warn', accent: 'bg-warn' },
  PROGRESS: { label: 'In progress', dot: 'bg-info', pill: 'bg-info/10 text-info ring-info/30', bar: 'bg-gradient-to-r from-info to-good', accent: 'bg-info' },
  FILLED: { label: 'Filled', dot: 'bg-good', pill: 'bg-good/10 text-good ring-good/30', bar: 'bg-good', accent: 'bg-good' },
};

const fillState = (r: Requirement): FillState => (r.hired >= r.needed ? 'FILLED' : r.hired > 0 ? 'PROGRESS' : 'OPEN');

function RequirementListView({
  requirements,
  total,
  onEdit,
  onStatusChange,
  onRatingChange,
}: {
  requirements: Requirement[];
  total: number;
  onEdit: (r: Requirement) => void;
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  const [expandedId, setExpandedId] = useState<number | null>(null);

  return (
    <div className="panel overflow-hidden p-0">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line/60 px-5 py-4">
        <div>
          <h2 className="text-sm font-semibold text-ink">Hiring priorities</h2>
          <p className="mt-0.5 text-[11px] text-ink-3">The top row is filled first. Use the arrows or click the number to change the order.</p>
        </div>
        <div className="flex items-center gap-4 text-[11px] text-ink-3">
          {(Object.keys(FILL_META) as FillState[]).map((k) => (
            <span key={k} className="inline-flex items-center gap-1.5">
              <span className={clsx('h-2 w-2 rounded-full', FILL_META[k].dot)} /> {FILL_META[k].label}
            </span>
          ))}
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full min-w-[1000px] text-left text-xs">
          <thead>
            <tr className="bg-raised/40 text-[10px] font-semibold uppercase tracking-wider text-ink-3">
              <th className="w-[110px] py-2.5 pl-5 pr-2 font-semibold">Priority</th>
              <th className="px-3 py-2.5 font-semibold">Role</th>
              <th className="px-3 py-2.5 font-semibold">Target</th>
              <th className="px-3 py-2.5 text-center font-semibold">Hired</th>
              <th className="px-3 py-2.5 text-center font-semibold">Remaining</th>
              <th className="w-[210px] px-3 py-2.5 font-semibold">Fulfillment</th>
              <th className="px-3 py-2.5 font-semibold">Status</th>
              <th className="px-3 py-2.5 font-semibold">Candidates</th>
              <th className="py-2.5 pl-3 pr-5" />
            </tr>
          </thead>
          <tbody className="divide-y divide-line/50">
            {requirements.map((r) => (
              <RequirementTableRow
                key={r.id}
                requirement={r}
                total={total}
                onEdit={() => onEdit(r)}
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
  total,
  onEdit,
  isExpanded,
  onToggleExpand,
  onStatusChange,
  onRatingChange,
}: {
  requirement: Requirement;
  total: number;
  onEdit: () => void;
  isExpanded: boolean;
  onToggleExpand: () => void;
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  const toast = useToast();
  const qc = useQueryClient();
  const scope = { departmentId: r.departmentId, position: r.position || undefined };
  const scopeLink = `departmentId=${r.departmentId}${r.position ? `&position=${encodeURIComponent(r.position)}` : ''}`;

  // waiting candidates in this department / sub-work: rated ones best first, then the unrated ones newest first
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

  const remove = useMutation({
    mutationFn: () => api.del(`/requirements/${r.id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['requirements'] });
      toast('success', 'Requirement deleted');
    },
    onError: (e) => toast('error', e.message),
  });

  const state = fillState(r);
  const meta = FILL_META[state];
  const stillNeed = Math.max(r.needed - r.hired, 0);
  const pct = r.needed > 0 ? Math.min(100, Math.round((r.hired / r.needed) * 100)) : 0;
  const ratedList = ranked.data?.content ?? [];
  const unratedList = unrated.data?.content ?? [];
  const waiting = ratedList.length + unratedList.length;
  // clicks on the row's own controls (priority, hired link, candidates button, edit, delete) must not also toggle the row
  const stop = (e: React.SyntheticEvent) => e.stopPropagation();
  const deptName = r.departmentName ?? 'Unassigned';

  return (
    <>
      <tr
        className={clsx('group cursor-pointer transition-colors hover:bg-raised/40', isExpanded && 'bg-raised/30')}
        onClick={onToggleExpand}
        title={isExpanded ? 'Hide candidates' : 'Show candidates'}
      >
        <td className="relative py-3.5 pl-5 pr-2" onClick={stop}>
          <span className={clsx('absolute inset-y-3 left-0 w-1 rounded-r-full', meta.accent)} />
          <PriorityControl requirement={r} total={total} />
        </td>

        <td className="px-3 py-3.5">
          <div className="flex items-center gap-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-info/25 to-violet/20 text-sm font-bold text-ink ring-1 ring-line/60">
              {deptName.charAt(0).toUpperCase()}
            </span>
            <div className="min-w-0">
              <div className="truncate text-[13px] font-semibold text-ink" title={requirementTitle(r)}>{deptName}</div>
              <span className="mt-0.5 inline-flex max-w-[220px] items-center gap-1 truncate rounded-md bg-raised px-1.5 py-0.5 text-[10px] font-medium text-ink-2">
                <Briefcase size={10} className="shrink-0" /> {r.position || 'Whole department'}
              </span>
            </div>
          </div>
        </td>

        <td className="px-3 py-3.5">
          <span
            className="inline-flex w-12 justify-center rounded-lg border border-line/70 bg-raised px-1.5 py-1 text-[13px] font-bold tabular-nums text-ink"
            title="Change the target with Edit"
          >
            {r.needed}
          </span>
        </td>

        <td className="px-3 py-3.5 text-center">
          <Link
            to={`/candidates?applicationStatus=SELECTED,JOINED&${scopeLink}`}
            className="text-[15px] font-bold tabular-nums text-good hover:underline"
            onClick={stop}
            title="See the hired candidates"
          >
            {r.hired}
          </Link>
        </td>

        <td className="px-3 py-3.5 text-center">
          {stillNeed > 0 ? (
            <span className="text-[15px] font-bold tabular-nums text-warn">{stillNeed}</span>
          ) : (
            <span className="text-ink-3">—</span>
          )}
        </td>

        <td className="px-3 py-3.5">
          <div className="flex items-center justify-between text-[11px]">
            <span className="text-ink-3">
              <span className="font-semibold text-ink-2">{r.hired}</span> of {r.needed}
            </span>
            <span className="font-bold tabular-nums text-ink-2">{pct}%</span>
          </div>
          <div className="mt-1.5 h-2 w-full overflow-hidden rounded-full bg-raised">
            <div className={clsx('h-full rounded-full transition-all duration-700', meta.bar)} style={{ width: `${Math.max(pct, 2)}%` }} />
          </div>
        </td>

        <td className="px-3 py-3.5">
          <span className={clsx('inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold ring-1', meta.pill)}>
            <span className={clsx('h-1.5 w-1.5 rounded-full', meta.dot, state === 'OPEN' && 'animate-pulse')} />
            {meta.label}
          </span>
        </td>

        <td className="px-3 py-3.5">
          <button
            onClick={(e) => {
              stop(e);
              onToggleExpand();
            }}
            className={clsx(
              'inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-[11px] font-semibold transition',
              isExpanded ? 'border-info/40 bg-info/15 text-info' : 'border-line bg-raised text-ink-2 hover:border-info/40 hover:text-ink'
            )}
          >
            <Users size={12} />
            {ranked.isLoading || unrated.isLoading ? <Spinner size={11} /> : waiting}
            {isExpanded ? <ChevronUp size={13} /> : <ChevronDown size={13} />}
          </button>
        </td>

        <td className="py-3.5 pl-3 pr-5 text-right" onClick={stop}>
          <div className="inline-flex items-center gap-1">
            <button
              className="inline-flex items-center gap-1 rounded-lg border border-line bg-raised px-2.5 py-1.5 text-[11px] font-semibold text-ink-2 transition hover:border-info/50 hover:text-info"
              title="Edit priority and target"
              onClick={onEdit}
            >
              <Pencil size={12} /> Edit
            </button>
            <button
              className="rounded-lg p-1.5 text-ink-3 opacity-60 transition hover:bg-bad/10 hover:text-bad group-hover:opacity-100 focus-visible:opacity-100"
              title="Delete requirement"
              disabled={remove.isPending}
              onClick={() => window.confirm(`Delete the requirement "${requirementTitle(r)}"? Candidates are not affected.`) && remove.mutate()}
            >
              <Trash2 size={15} />
            </button>
          </div>
        </td>
      </tr>

      {isExpanded && (
        <tr className="bg-raised/20">
          <td colSpan={9} className="px-5 pb-5 pt-3">
            <div className="animate-fade-up space-y-4">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold text-ink-2">
                  Waiting candidates for {requirementTitle(r)} <span className="text-ink-3">({waiting})</span>
                </span>
                <Link to={`/candidates?${scopeLink}`} className="flex items-center gap-1 font-medium text-info hover:underline">
                  Open in Candidates <ArrowUpRight size={12} />
                </Link>
              </div>

              {waiting === 0 ? (
                <div className="rounded-xl border border-dashed border-line/70 p-6 text-center">
                  <Users size={20} className="mx-auto text-ink-3" />
                  <p className="mt-1.5 text-xs text-ink-3">No candidates are waiting for this role yet.</p>
                </div>
              ) : (
                <>
                  {ratedList.length > 0 && (
                    <CandidateGroup
                      title="Rated — best first"
                      note={stillNeed > 0 ? `Top ${Math.min(stillNeed, ratedList.length)} suggested to fill the remaining ${stillNeed}` : undefined}
                      icon={<Star size={12} className="fill-warn/20 text-warn" />}
                      list={ratedList}
                      suggestedCount={stillNeed}
                      onStatusChange={onStatusChange}
                      onRatingChange={onRatingChange}
                    />
                  )}
                  {unratedList.length > 0 && (
                    <CandidateGroup
                      title="Not rated yet"
                      note="Rate them to rank them"
                      icon={<Users size={12} className="text-info" />}
                      list={unratedList}
                      suggestedCount={0}
                      onStatusChange={onStatusChange}
                      onRatingChange={onRatingChange}
                    />
                  )}
                </>
              )}
            </div>
          </td>
        </tr>
      )}
    </>
  );
}

function CandidateGroup({
  title,
  note,
  icon,
  list,
  suggestedCount,
  onStatusChange,
  onRatingChange,
}: {
  title: string;
  note?: string;
  icon: React.ReactNode;
  list: CandidateListItem[];
  suggestedCount: number;
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  return (
    <div className="space-y-2">
      <div className="flex items-center gap-2 text-[11px]">
        <span className="flex items-center gap-1 font-semibold uppercase tracking-wider text-ink-2">
          {icon} {title} ({list.length})
        </span>
        {note && <span className="text-ink-3">· {note}</span>}
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {list.map((c, i) => (
          <CandidateMiniProfileCard
            key={c.id}
            candidate={c}
            suggested={i < suggestedCount}
            onStatusChange={onStatusChange}
            onRatingChange={onRatingChange}
          />
        ))}
      </div>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// EDIT REQUIREMENT (PRIORITY + TARGET)
// -------------------------------------------------------------------------------------------------

function EditRequirementModal({ target: r, total, onClose }: { target: Requirement | null; total: number; onClose: () => void }) {
  const ready = useContext(PriorityReady);
  const toast = useToast();
  const qc = useQueryClient();
  const [priority, setPriority] = useState(1);
  const [needed, setNeeded] = useState('');

  useEffect(() => {
    if (r) {
      setPriority(r.priority || total);
      setNeeded(String(r.needed));
    }
  }, [r, total]);

  const save = useMutation({
    mutationFn: (body: { priority?: number; needed?: number }) => api.put<Requirement>(`/requirements/${r!.id}`, body),
    onSuccess: (saved, body) => {
      qc.invalidateQueries({ queryKey: ['requirements'] });
      if (body.priority && !saved.priority) return toast('error', SERVER_OLD_MSG);
      toast(
        'success',
        saved.priority === 1 ? `${requirementTitle(saved)} is now 1st priority (top of the list)` : `Saved — ${ordinal(saved.priority)} priority`
      );
      onClose();
    },
    onError: (e) => toast('error', e.message),
  });

  if (!r) return null;
  const changed = priority !== r.priority || Number(needed) !== r.needed;
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validNumber(needed)) return toast('error', `People needed must be 1 to ${MAX_NEEDED}`);
    if (!changed) return onClose();
    save.mutate({
      ...(ready && priority !== r.priority && { priority }),
      ...(Number(needed) !== r.needed && { needed: Number(needed) }),
    });
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Edit requirement"
      description={requirementTitle(r)}
      footer={
        <>
          <button type="button" className="btn-ghost" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="edit-requirement" className="btn-primary" disabled={save.isPending || !changed}>
            {save.isPending ? <Spinner size={15} /> : <Check size={15} />} Save
          </button>
        </>
      }
    >
      <form id="edit-requirement" className="space-y-5" onSubmit={submit}>
        <div>
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold text-ink-2">Priority</span>
            <span className="text-[11px] text-ink-3">1st = top of the list, filled first</span>
          </div>
          {!ready && <p className="mb-2 rounded-lg bg-warn/10 px-3 py-2 text-[11px] text-warn">{SERVER_OLD_MSG}</p>}
          <div className="flex flex-wrap gap-2">
            {Array.from({ length: Math.max(total, 1) }, (_, i) => i + 1).map((p) => (
              <button
                key={p}
                type="button"
                disabled={!ready}
                onClick={() => setPriority(p)}
                className={clsx(
                  'min-w-[56px] disabled:cursor-not-allowed disabled:opacity-50 rounded-xl px-3 py-2 text-xs font-bold ring-1 transition',
                  priority === p
                    ? clsx(PRIORITY_STYLE[p - 1] ?? 'bg-info/15 text-info ring-info/50', 'scale-105 ring-2')
                    : 'bg-raised text-ink-2 ring-line hover:text-ink hover:ring-info/40'
                )}
              >
                {ordinal(p)}
              </button>
            ))}
          </div>
          {priority !== r.priority && (
            <p className="mt-2 text-[11px] text-ink-3">
              Moves from <strong className="text-ink-2">{ordinal(r.priority)}</strong> to{' '}
              <strong className="text-ink-2">{ordinal(priority)}</strong>
              {priority === 1 ? ' — it will show at the top.' : '.'} The others shift to make room.
            </p>
          )}
        </div>

        <Field label="People needed *">
          <input
            className="input"
            inputMode="numeric"
            value={needed}
            onChange={(e) => setNeeded(e.target.value.replace(/\D/g, '').slice(0, 3))}
          />
        </Field>
      </form>
    </Modal>
  );
}

// -------------------------------------------------------------------------------------------------
// PRIORITY (1st, 2nd, 3rd …)
// -------------------------------------------------------------------------------------------------

const PRIORITY_STYLE = [
  'bg-gradient-to-br from-bad/25 to-bad/5 text-bad ring-bad/40',
  'bg-gradient-to-br from-warn/25 to-warn/5 text-warn ring-warn/40',
  'bg-gradient-to-br from-info/25 to-info/5 text-info ring-info/40',
];

/** A rank chip ("1st", "2nd" …); click it to jump to a number, or nudge with the arrows. The others shift to make room. */
function PriorityControl({ requirement: r, total }: { requirement: Requirement; total: number }) {
  const ready = useContext(PriorityReady);
  const toast = useToast();
  const qc = useQueryClient();
  const move = useMutation({
    mutationFn: (priority: number) => api.put<Requirement>(`/requirements/${r.id}`, { priority }),
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ['requirements'] });
      if (!saved.priority) return toast('error', SERVER_OLD_MSG);
      toast('success', `${requirementTitle(r)} is now ${ordinal(saved.priority)} priority`);
    },
    onError: (e) => toast('error', e.message),
  });
  const to = (p: number) => ready && p !== r.priority && p >= 1 && p <= total && move.mutate(p);
  const style = PRIORITY_STYLE[r.priority - 1] ?? 'bg-raised text-ink-2 ring-line';
  const suffix = ordinal(r.priority).slice(String(r.priority).length);

  return (
    <div className="flex items-center gap-1.5">
      <label
        className={clsx('relative flex h-11 w-11 cursor-pointer flex-col items-center justify-center rounded-xl ring-1 transition hover:scale-105', style)}
        title="Change priority"
      >
        {move.isPending ? (
          <Spinner size={14} />
        ) : (
          <>
            <span className="text-base font-extrabold leading-none tabular-nums">{r.priority}</span>
            <span className="mt-0.5 text-[9px] font-bold uppercase leading-none">{suffix}</span>
          </>
        )}
        <select
          className="absolute inset-0 cursor-pointer opacity-0"
          aria-label="Priority"
          value={r.priority}
          disabled={move.isPending || !ready}
          onChange={(e) => to(Number(e.target.value))}
        >
          {Array.from({ length: Math.max(total, r.priority) }, (_, i) => (
            <option key={i + 1} value={i + 1}>
              {ordinal(i + 1)} priority
            </option>
          ))}
        </select>
      </label>
      <div className="flex flex-col">
        <button
          type="button"
          className="rounded p-0.5 text-ink-3 transition hover:bg-raised hover:text-ink disabled:opacity-25"
          title="Move up (more urgent)"
          disabled={move.isPending || !ready || r.priority <= 1}
          onClick={() => to(r.priority - 1)}
        >
          <ChevronUp size={14} />
        </button>
        <button
          type="button"
          className="rounded p-0.5 text-ink-3 transition hover:bg-raised hover:text-ink disabled:opacity-25"
          title="Move down (less urgent)"
          disabled={move.isPending || !ready || r.priority >= total}
          onClick={() => to(r.priority + 1)}
        >
          <ChevronDown size={14} />
        </button>
      </div>
    </div>
  );
}

// -------------------------------------------------------------------------------------------------
// CANDIDATE PROFILE CARD (INSIDE AN EXPANDED ROW)
// -------------------------------------------------------------------------------------------------

function CandidateMiniProfileCard({
  candidate: c,
  suggested = false,
  onStatusChange,
  onRatingChange,
}: {
  candidate: CandidateListItem;
  suggested?: boolean;
  onStatusChange: (target: StatusTarget) => void;
  onRatingChange: (target: RatingTarget) => void;
}) {
  return (
    <div
      className={clsx(
        'relative rounded-xl border bg-card p-3 shadow-sm transition hover:-translate-y-0.5 hover:shadow-card',
        suggested ? 'border-warn/50 ring-1 ring-warn/20' : 'border-line/70 hover:border-line'
      )}
    >
      {suggested && (
        <span className="absolute -top-2 right-3 inline-flex items-center gap-1 rounded-full bg-warn px-2 py-0.5 text-[9px] font-bold uppercase tracking-wide text-white shadow-sm">
          <Star size={9} className="fill-white" /> Suggested
        </span>
      )}
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
