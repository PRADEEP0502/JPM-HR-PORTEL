import { useEffect, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Archive, ArchiveRestore, ArrowDown, ArrowUp, ArrowUpDown, Download, Eye, FileDown, FileText, FilterX, Mail, MessageCircle, MoreVertical, PenSquare, Phone, RefreshCcw, Search, Star, UserCheck, Users } from 'lucide-react';
import { api } from '../lib/api';
import { useDepartments } from '../lib/queries';
import { useToast } from '../lib/toast';
import {
  APPLICATION_STATUSES,
  EXPERIENCE_BUCKETS,
  INTERVIEW_STATUSES,
  PENDING_STATUSES,
  VERIFICATION_STATUSES,
  fmtDate,
  fmtExperience,
} from '../lib/format';
import type { CandidateListItem, Page } from '../lib/types';
import { ApplicationPill, DemoTag, InterviewPill, RatingPill } from '../components/ui/Pill';
import { Pagination } from '../components/ui/Pagination';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';
import { DocumentViewer, type ViewTarget } from '../components/modals/DocumentViewer';
import { ArchiveModal, EditCandidateModal, RatingModal, StatusModal, type ArchiveTarget, type RatingTarget, type StatusTarget } from '../components/modals/CandidateModals';
import { CommunicationDialog, useCallAction, type Tab } from '../components/communication/Communication';
import { emailProblem, phoneProblem } from '../lib/contact';

const FILTER_KEYS = ['q', 'departmentId', 'experience', 'applicationStatus', 'interviewStatus', 'verificationStatus', 'rating', 'appliedFrom', 'appliedTo', 'archive'] as const;

/** Profile Match Rating filter values understood by the API. */
const RATING_FILTERS = [
  { value: '7+', label: 'Rating 7 or above' },
  { value: '9-10', label: '9–10 · Excellent Match' },
  { value: '7-8', label: '7–8 · Good Match' },
  { value: '5-6', label: '5–6 · Average Match' },
  { value: '3-4', label: '3–4 · Low Match' },
  { value: '1-2', label: '1–2 · Not Suitable' },
  { value: 'UNRATED', label: 'Not Rated' },
];

const COLUMNS: { key: string; label: string; sortable?: boolean }[] = [
  { key: 'candidateCode', label: 'Candidate ID', sortable: true },
  { key: 'fullName', label: 'Candidate Name', sortable: true },
  { key: 'mobile', label: 'Mobile Number', sortable: true },
  { key: 'department', label: 'Department', sortable: true },
  { key: 'qualification', label: 'Qualification', sortable: true },
  { key: 'experience', label: 'Experience', sortable: true },
  { key: 'applicationDate', label: 'Application Date', sortable: true },
  { key: 'rating', label: 'Profile Match', sortable: true },
  { key: 'applicationStatus', label: 'Application Status', sortable: true },
  { key: 'interviewStatus', label: 'Interview Status', sortable: true },
  { key: 'ratingRemark', label: 'HR Remarks' },
];

export default function CandidatesPage() {
  const [params, setParams] = useSearchParams();
  const toast = useToast();
  const { data: departments } = useDepartments();
  const [search, setSearch] = useState(params.get('q') ?? '');
  const [editId, setEditId] = useState<number | null>(null);
  const [statusTarget, setStatusTarget] = useState<StatusTarget | null>(null);
  const [ratingTarget, setRatingTarget] = useState<RatingTarget | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<ArchiveTarget | null>(null);
  const [viewing, setViewing] = useState<ViewTarget | null>(null);
  const [exporting, setExporting] = useState(false);
  const [downloading, setDownloading] = useState<number | null>(null);
  const [contact, setContact] = useState<{ candidateId: number; tab: Tab; title: string } | null>(null);
  const call = useCallAction();

  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? 10);
  const sort = params.get('sort') ?? 'applicationDate';
  const dir = params.get('dir') ?? 'desc';
  const filters = Object.fromEntries(FILTER_KEYS.map((k) => [k, params.get(k) ?? ''])) as Record<(typeof FILTER_KEYS)[number], string>;
  const activeFilters = FILTER_KEYS.filter((k) => filters[k]).length;

  const update = (patch: Record<string, string | number | null>, resetPage = true) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v === null || v === '' ? next.delete(k) : next.set(k, String(v))));
    if (resetPage) next.delete('page');
    setParams(next, { replace: true });
  };

  // keep search box in sync with URL (e.g. header search) and debounce typing into the URL
  useEffect(() => setSearch(params.get('q') ?? ''), [params]);
  useEffect(() => {
    const t = setTimeout(() => {
      if ((params.get('q') ?? '') !== search.trim()) update({ q: search.trim() });
    }, 350);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const query = useQuery({
    queryKey: ['candidates', filters, page, size, sort, dir],
    queryFn: () => api.get<Page<CandidateListItem>>('/candidates', { ...filters, page, size, sort, dir }),
    placeholderData: keepPreviousData,
  });

  const toggleSort = (key: string) => update({ sort: key, dir: sort === key && dir === 'asc' ? 'desc' : 'asc' }, false);

  const exportExcel = async () => {
    setExporting(true);
    try {
      await api.download('/candidates/export', { ...filters, sort, dir }, 'candidates.xlsx');
      toast('success', 'Excel export downloaded');
    } catch (e) {
      toast('error', (e as Error).message);
    } finally {
      setExporting(false);
    }
  };

  const downloadResume = async (c: CandidateListItem) => {
    setDownloading(c.id);
    try {
      await api.download(`/candidates/${c.id}/resume`, {}, `${c.candidateCode}-resume`);
    } catch (e) {
      toast('error', (e as Error).message.includes('not found') ? `No resume on file for ${c.fullName}` : (e as Error).message);
    } finally {
      setDownloading(null);
    }
  };

  const data = query.data;
  return (
    <div className="space-y-4">
      <section className="panel p-4">
        <div className="flex flex-wrap items-center gap-3">
          <label className="relative min-w-[220px] flex-1">
            <span className="sr-only">Search by name or candidate ID</span>
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
            <input className="input pl-9" placeholder="Search by name, candidate ID or mobile" value={search} onChange={(e) => setSearch(e.target.value)} />
          </label>
          <button className="btn-secondary" onClick={() => query.refetch()} disabled={query.isFetching} title="Refresh">
            <RefreshCcw size={15} className={clsx(query.isFetching && 'animate-spin')} /> <span className="hidden sm:inline">Refresh</span>
          </button>
          <button className="btn-primary" onClick={exportExcel} disabled={exporting || !data?.totalElements}>
            {exporting ? <Spinner size={15} /> : <Download size={15} />} Export to Excel
          </button>
        </div>
        <div className="mt-3 grid grid-cols-2 gap-3 md:grid-cols-4">
          <select className="input" aria-label="Department" value={filters.departmentId} onChange={(e) => update({ departmentId: e.target.value })}>
            <option value="">All departments</option>
            {departments?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            <option value="0">Unassigned</option>
          </select>
          <select className="input" aria-label="Experience" value={filters.experience} onChange={(e) => update({ experience: e.target.value })}>
            <option value="">Any experience</option>
            {EXPERIENCE_BUCKETS.map((b) => <option key={b.value} value={b.value}>{b.label}</option>)}
          </select>
          <select className="input" aria-label="Application status" value={filters.applicationStatus} onChange={(e) => update({ applicationStatus: e.target.value })}>
            <option value="">Any application status</option>
            <option value={PENDING_STATUSES}>Pending (no final decision)</option>
            {APPLICATION_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
          <select className="input" aria-label="Interview status" value={filters.interviewStatus} onChange={(e) => update({ interviewStatus: e.target.value })}>
            <option value="">Any interview status</option>
            <option value="SCHEDULED,RESCHEDULED">Upcoming (scheduled or rescheduled)</option>
            {INTERVIEW_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
          <select className="input" aria-label="Verification" value={filters.verificationStatus} onChange={(e) => update({ verificationStatus: e.target.value })}>
            <option value="">Any verification</option>
            {VERIFICATION_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
          <select className="input" aria-label="Profile match rating" value={filters.rating} onChange={(e) => update({ rating: e.target.value })}>
            <option value="">Any rating</option>
            {RATING_FILTERS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
          <label className="block">
            <span className="sr-only">Applied from</span>
            <input className="input" type="date" aria-label="Applied from" title="Applied from" value={filters.appliedFrom} max={filters.appliedTo || undefined} onChange={(e) => update({ appliedFrom: e.target.value })} />
          </label>
          <label className="block">
            <span className="sr-only">Applied to</span>
            <input className="input" type="date" aria-label="Applied to" title="Applied to" value={filters.appliedTo} min={filters.appliedFrom || undefined} onChange={(e) => update({ appliedTo: e.target.value })} />
          </label>
          <select className="input" aria-label="Archived candidates" value={filters.archive} onChange={(e) => update({ archive: e.target.value })}>
            <option value="">Active candidates</option>
            <option value="ARCHIVED">Archived only</option>
            <option value="ALL">Active and archived</option>
          </select>
          <button
            className={clsx('justify-start', filters.rating === '7+' ? 'btn-primary' : 'btn-secondary')}
            aria-pressed={filters.rating === '7+'}
            title="Show candidates rated 7 or above, highest first"
            onClick={() =>
              filters.rating === '7+'
                ? update({ rating: null })
                : update({ rating: '7+', sort: 'rating', dir: 'desc' })
            }
          >
            <Star size={15} /> Top matches (7+)
          </button>
          <button className="btn-ghost justify-start" disabled={activeFilters === 0} onClick={() => update(Object.fromEntries(FILTER_KEYS.map((k) => [k, null])))}>
            <FilterX size={15} /> Clear filters {activeFilters > 0 && `(${activeFilters})`}
          </button>
        </div>
      </section>

      <section className="panel p-4">
        {query.isLoading ? (
          <LoadingBlock label="Loading candidates…" />
        ) : query.error ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : !data || data.totalElements === 0 ? (
          <EmptyState
            icon={<Users size={20} />}
            title={activeFilters ? 'No candidates match these filters' : 'No candidates yet'}
            hint={activeFilters ? 'Try removing a filter or changing the search.' : 'New Google Form submissions will appear here after the next sync.'}
          />
        ) : (
          <>
            {/* Phones: one card per candidate. Tablets and up: the sortable table below. */}
            <ul className={clsx('space-y-3 md:hidden', query.isFetching && 'opacity-70')} aria-label="Candidates">
              {data.content.map((c) => (
                <li key={c.id} className="rounded-xl border border-line/70 bg-card p-3.5">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <Link to={`/candidates/${c.id}`} className="block truncate font-medium text-ink hover:underline">{c.fullName}</Link>
                      <p className="flex flex-wrap items-center gap-1.5 text-xs text-ink-3">
                        <span className="font-mono">{c.candidateCode}</span>
                        {c.source === 'DEMO' && <DemoTag />}
                        {c.archived && <span className="rounded border border-line px-1 text-[10px] font-semibold uppercase">Archived</span>}
                        · {c.departmentName ?? 'Unassigned'}{c.position ? ` – ${c.position}` : ''}
                      </p>
                    </div>
                    <ApplicationPill status={c.applicationStatus} />
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-ink-3">
                    <button onClick={() => setRatingTarget({ id: c.id, name: c.fullName, code: c.candidateCode, rating: c.profileRating, remark: c.ratingRemark })} aria-label={`Rating for ${c.fullName}`}>
                      <RatingPill rating={c.profileRating} />
                    </button>
                    <span>Applied {fmtDate(c.applicationDate)}</span>
                  </div>
                  <div className="mt-2 flex items-center gap-1 border-t border-line/60 pt-2">
                    <button className="btn-ghost p-2" disabled={!!phoneProblem(c.mobile)} onClick={() => call.start(c.id)} aria-label={`Call ${c.fullName}`}><Phone size={16} /></button>
                    <button className="btn-ghost p-2" disabled={!!phoneProblem(c.mobile)} onClick={() => setContact({ candidateId: c.id, tab: 'whatsapp', title: `Message ${c.fullName}` })} aria-label={`WhatsApp ${c.fullName}`}><MessageCircle size={16} /></button>
                    <button className="btn-ghost p-2" disabled={!!emailProblem(c.email)} onClick={() => setContact({ candidateId: c.id, tab: 'email', title: `Email ${c.fullName}` })} aria-label={`Email ${c.fullName}`}><Mail size={16} /></button>
                    <Link to={`/candidates/${c.id}`} className="btn-ghost ml-auto px-2 py-1.5 text-sm"><Eye size={15} /> Profile</Link>
                  </div>
                </li>
              ))}
            </ul>
            <div className={clsx('-mx-4 hidden overflow-x-auto transition-opacity md:block', query.isFetching && 'opacity-70')}>
              <table className="w-full min-w-[1380px]">
                <thead>
                  <tr className="border-b border-line">
                    {COLUMNS.map((c) => (
                      <th key={c.key} className="th first:pl-4" aria-sort={!c.sortable ? undefined : sort === c.key ? (dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
                        {c.sortable ? (
                          <button className="inline-flex items-center gap-1 hover:text-ink" onClick={() => toggleSort(c.key)}>
                            {c.label}
                            {sort === c.key ? (dir === 'asc' ? <ArrowUp size={12} /> : <ArrowDown size={12} />) : <ArrowUpDown size={12} className="opacity-40" />}
                          </button>
                        ) : (
                          c.label
                        )}
                      </th>
                    ))}
                    <th className="th pr-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((c) => (
                    <tr key={c.id} className="border-b border-line/40 last:border-0 hover:bg-white/[0.02]">
                      <td className="td pl-4 font-mono text-xs text-ink">
                        <span className="flex items-center gap-1.5">
                          {c.candidateCode}
                          {c.source === 'DEMO' && <DemoTag />}
                          {c.archived && <span className="rounded border border-line px-1 text-[10px] font-semibold uppercase tracking-wide text-ink-3">Archived</span>}
                        </span>
                      </td>
                      <td className="td">
                        <Link to={`/candidates/${c.id}`} className="font-medium text-ink hover:underline">{c.fullName}</Link>
                        {c.verificationStatus === 'PENDING' && <span className="block text-[11px] text-warn">Verification pending</span>}
                      </td>
                      <td className="td tabular-nums">{c.mobile ?? '—'}</td>
                      <td className="td">
                        {c.departmentName ?? <span className="text-ink-3">Unassigned</span>}
                        {c.position && <span className="block text-xs text-ink-3">{c.position}</span>}
                      </td>
                      <td className="td max-w-[180px] truncate" title={c.qualification ?? undefined}>{c.qualification ?? '—'}</td>
                      <td className="td">{fmtExperience(c.experienceYears)}</td>
                      <td className="td tabular-nums">{fmtDate(c.applicationDate)}</td>
                      <td className="td">
                        <button
                          className="rounded-md hover:opacity-80"
                          title={c.profileRating == null ? 'Rate this profile' : 'Update rating'}
                          aria-label={`${c.profileRating == null ? 'Rate' : 'Update rating for'} ${c.fullName}`}
                          onClick={() => setRatingTarget({ id: c.id, name: c.fullName, code: c.candidateCode, rating: c.profileRating, remark: c.ratingRemark })}
                        >
                          <RatingPill rating={c.profileRating} />
                        </button>
                      </td>
                      <td className="td"><ApplicationPill status={c.applicationStatus} /></td>
                      <td className="td"><InterviewPill status={c.interviewStatus} /></td>
                      <td className="td max-w-[220px] truncate text-ink-2" title={c.ratingRemark ?? undefined}>{c.ratingRemark ?? <span className="text-ink-3">—</span>}</td>
                      <td className="td pr-4">
                        {(() => {
                          const phoneIssue = phoneProblem(c.mobile);
                          const emailIssue = emailProblem(c.email);
                          return (
                            <div className="flex items-center justify-end gap-0.5">
                              <button
                                className="btn-ghost p-2 enabled:hover:text-info"
                                disabled={!!phoneIssue || call.starting === c.id}
                                title={phoneIssue ?? `Call ${c.mobile}`}
                                aria-label={`Call ${c.fullName}`}
                                onClick={() => call.start(c.id)}
                              >
                                {call.starting === c.id ? <Spinner size={16} /> : <Phone size={16} />}
                              </button>
                              <button
                                className="btn-ghost p-2 enabled:hover:text-[#25d366]"
                                disabled={!!phoneIssue}
                                title={phoneIssue ?? 'WhatsApp interview invitation'}
                                aria-label={`WhatsApp ${c.fullName}`}
                                onClick={() => setContact({ candidateId: c.id, tab: 'whatsapp', title: `Message ${c.fullName}` })}
                              >
                                <MessageCircle size={16} />
                              </button>
                              <button
                                className="btn-ghost p-2 enabled:hover:text-info"
                                disabled={!!emailIssue}
                                title={emailIssue ?? `Email ${c.email}`}
                                aria-label={`Email ${c.fullName}`}
                                onClick={() => setContact({ candidateId: c.id, tab: 'email', title: `Email ${c.fullName}` })}
                              >
                                <Mail size={16} />
                              </button>
                              <Link to={`/candidates/${c.id}`} className="btn-ghost p-2" title="View profile" aria-label={`View ${c.fullName}`}><Eye size={16} /></Link>
                              <RowMenu
                                label={`More actions for ${c.fullName}`}
                                items={[
                                  { label: 'Edit details', icon: <PenSquare size={15} />, onClick: () => setEditId(c.id) },
                                  {
                                    label: 'Update status',
                                    icon: <UserCheck size={15} />,
                                    onClick: () => setStatusTarget({ id: c.id, name: c.fullName, code: c.candidateCode, applicationStatus: c.applicationStatus, verificationStatus: c.verificationStatus }),
                                  },
                                  {
                                    label: c.profileRating == null ? 'Rate profile' : 'Update rating',
                                    icon: <Star size={15} />,
                                    onClick: () => setRatingTarget({ id: c.id, name: c.fullName, code: c.candidateCode, rating: c.profileRating, remark: c.ratingRemark }),
                                  },
                                  {
                                    label: 'View resume',
                                    icon: <FileText size={15} />,
                                    onClick: () => setViewing({ title: `${c.fullName} - Resume`, path: `/candidates/${c.id}/resume`, fileName: `${c.candidateCode}-resume` }),
                                  },
                                  { label: downloading === c.id ? 'Downloading…' : 'Download resume', icon: <FileDown size={15} />, onClick: () => downloadResume(c) },
                                  {
                                    label: c.archived ? 'Restore from archive' : 'Archive',
                                    icon: c.archived ? <ArchiveRestore size={15} /> : <Archive size={15} />,
                                    onClick: () => setArchiveTarget({ id: c.id, name: c.fullName, code: c.candidateCode, archived: c.archived }),
                                  },
                                ]}
                              />
                            </div>
                          );
                        })()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination
              page={data.page}
              size={data.size}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              onPage={(p) => update({ page: p }, false)}
              onSize={(s) => update({ size: s })}
            />
          </>
        )}
      </section>

      <EditCandidateModal candidateId={editId} onClose={() => setEditId(null)} />
      <StatusModal target={statusTarget} onClose={() => setStatusTarget(null)} />
      <RatingModal target={ratingTarget} onClose={() => setRatingTarget(null)} />
      <ArchiveModal target={archiveTarget} onClose={() => setArchiveTarget(null)} />
      <DocumentViewer target={viewing} onClose={() => setViewing(null)} />
      <CommunicationDialog target={contact} onClose={() => setContact(null)} />
      {call.modal}
    </div>
  );
}

/** Overflow menu rendered with fixed positioning so the scrollable table never clips it. */
function RowMenu({ label, items }: { label: string; items: { label: string; icon: ReactNode; onClick: () => void }[] }) {
  const [pos, setPos] = useState<{ top: number; right: number } | null>(null);
  useEffect(() => {
    if (!pos) return;
    const close = () => setPos(null);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [pos]);
  return (
    <>
      <button
        className="btn-ghost p-2"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={!!pos}
        onClick={(e) => {
          const r = e.currentTarget.getBoundingClientRect();
          setPos(pos ? null : { top: r.bottom + 4, right: window.innerWidth - r.right });
        }}
      >
        <MoreVertical size={16} />
      </button>
      {pos && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setPos(null)} />
          <ul role="menu" className="fixed z-50 min-w-[180px] rounded-xl border border-line bg-raised p-1 shadow-card animate-fade-up" style={{ top: pos.top, right: pos.right }}>
            {items.map((it) => (
              <li key={it.label}>
                <button
                  role="menuitem"
                  className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-ink-2 hover:bg-line hover:text-ink"
                  onClick={() => {
                    setPos(null);
                    it.onClick();
                  }}
                >
                  {it.icon} {it.label}
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}
