import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { Award, Download, FilterX, Megaphone, Target, Users } from 'lucide-react';
import { api } from '../lib/api';
import { useToast } from '../lib/toast';
import { CATEGORICAL, PENDING_STATUSES, fmtDate, fmtDateTime, fmtNumber } from '../lib/format';
import { REFRESH_MS, useDepartments } from '../lib/queries';
import { SectionTitle } from '../components/ui/Misc';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';
import { HorizontalBars, MultiLine, StackedBars, type SeriesDef } from '../components/charts/Charts';

/** The application form's Source choices, in form order, plus the separate bucket for no answer. */
const SOURCES = ['Indeed', 'Man Power Agencies', 'Street Boards', 'Paper Advertisements', 'Social Media', 'Nithra Jobs', 'Others', 'Not specified'];
const NEUTRAL = '#8a8a85';
const sourceColor = (s: string) => (s === 'Not specified' ? NEUTRAL : CATEGORICAL[SOURCES.indexOf(s) % CATEGORICAL.length]);
const OUTCOMES: SeriesDef[] = [
  { key: 'selected', label: 'Selected / joined', color: '#22a559' },
  { key: 'pending', label: 'Pending', color: '#e08a2e' },
  { key: 'rejected', label: 'Rejected', color: '#e05555' },
];

type SourceRow = {
  source: string; applications: number; share: number; selected: number; joined: number; rejected: number;
  pending: number; selectionRate: number; requirementHires: number;
};
type Period = { label: string; from: string; to: string; applications: Record<string, number>; selected: Record<string, number> };
type RequirementRow = { requirementId: number; department: string; position: string | null; needed: number; filled: number; filledBySource: Record<string, number> };
type Analytics = {
  from: string | null; to: string | null; granularity: 'DAY' | 'MONTH'; totalApplications: number;
  topSource: string | null; topRequirementSource: string | null;
  sources: SourceRow[]; trend: Period[]; requirements: RequirementRow[];
};

export default function SourceAnalyticsPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const { data: departments } = useDepartments();
  const [params, setParams] = useSearchParams();
  const f = {
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    departmentId: params.get('departmentId') ?? '',
    source: params.get('source') ?? '',
    granularity: params.get('granularity') ?? '',
  };
  const month = f.from && f.to && f.from.endsWith('-01') && f.from.slice(0, 7) === f.to.slice(0, 7) && f.to === monthEnd(f.from.slice(0, 7)) ? f.from.slice(0, 7) : '';
  const set = (patch: Record<string, string>) =>
    setParams((p) => {
      const n = new URLSearchParams(p);
      Object.entries(patch).forEach(([k, v]) => (v ? n.set(k, v) : n.delete(k)));
      return n;
    }, { replace: true });
  const invalid = !!f.from && !!f.to && f.from > f.to;
  const [exporting, setExporting] = useState(false);

  // recomputed from the candidate records on every load and every minute, so newly synced form responses count
  const query = useQuery({
    queryKey: ['source-analytics', f],
    queryFn: () => api.get<Analytics>('/analytics/sources', f),
    enabled: !invalid,
    refetchInterval: REFRESH_MS,
    placeholderData: keepPreviousData,
  });
  const a = query.data;

  /** Candidate list for a source (and optionally an outcome or a period), within the current filters. */
  const candidatesLink = (source: string | null, extra: Record<string, string> = {}) => {
    const q = new URLSearchParams({ archive: 'ALL' });
    if (source) q.set('hiringSource', source);
    if (f.departmentId) q.set('departmentId', f.departmentId);
    if (f.from) q.set('appliedFrom', f.from);
    if (f.to) q.set('appliedTo', f.to);
    if (!source && f.source) q.set('hiringSource', f.source);
    Object.entries(extra).forEach(([k, v]) => q.set(k, v));
    return `/candidates?${q.toString()}`;
  };

  const exportAs = async (format: 'xlsx' | 'csv') => {
    setExporting(true);
    try {
      await api.download('/analytics/sources/export', { ...f, format }, `source-analytics.${format}`);
      toast('success', 'Source analytics exported');
    } catch (e) {
      toast('error', (e as Error).message);
    } finally {
      setExporting(false);
    }
  };

  const withApps = a?.sources.filter((s) => s.applications > 0) ?? [];
  const bestRate = [...withApps].sort((x, y) => y.selectionRate - x.selectionRate || y.applications - x.applications)[0];
  const top = a?.sources.find((s) => s.source === a.topSource);
  const topReq = a?.sources.find((s) => s.source === a.topRequirementSource);
  const trendSeries: SeriesDef[] = (a?.sources ?? [])
    .filter((s) => s.applications > 0)
    .map((s) => ({ key: s.source, label: s.source, color: sourceColor(s.source) }));
  const trendData = (a?.trend ?? []).map((p) => ({ label: p.label, ...p.applications }));
  const periodLink = (label: string) => {
    const p = a?.trend.find((x) => x.label === label);
    return p ? candidatesLink(null, { appliedFrom: p.from, appliedTo: p.to }) : null;
  };

  return (
    <div className="space-y-6">
      <section className="panel flex flex-wrap items-end gap-3 p-4">
        <label className="text-sm">
          <span className="label">Applied from</span>
          <input type="date" className="input" value={f.from} max={f.to || undefined} onChange={(e) => set({ from: e.target.value })} />
        </label>
        <label className="text-sm">
          <span className="label">to</span>
          <input type="date" className="input" value={f.to} min={f.from || undefined} onChange={(e) => set({ to: e.target.value })} />
        </label>
        <label className="text-sm">
          <span className="label">Month</span>
          <input
            type="month"
            className="input"
            value={month}
            onChange={(e) => (e.target.value ? set({ from: `${e.target.value}-01`, to: monthEnd(e.target.value) }) : set({ from: '', to: '' }))}
          />
        </label>
        <label className="text-sm">
          <span className="label">Department</span>
          <select className="input" value={f.departmentId} onChange={(e) => set({ departmentId: e.target.value })}>
            <option value="">All departments</option>
            {departments?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
            <option value="0">Unassigned</option>
          </select>
        </label>
        <label className="text-sm">
          <span className="label">Source</span>
          <select className="input" value={f.source} onChange={(e) => set({ source: e.target.value })}>
            <option value="">All sources</option>
            {SOURCES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
        </label>
        <label className="text-sm">
          <span className="label">Trend by</span>
          <select className="input" value={f.granularity} onChange={(e) => set({ granularity: e.target.value })}>
            <option value="">Automatic</option>
            <option value="DAY">Date</option>
            <option value="MONTH">Month</option>
          </select>
        </label>
        <button className="btn-ghost" disabled={!Object.values(f).some(Boolean)} onClick={() => set({ from: '', to: '', departmentId: '', source: '', granularity: '' })}>
          <FilterX size={15} /> Clear
        </button>
        <div className="flex-1" />
        <button className="btn-secondary" onClick={() => exportAs('csv')} disabled={exporting || !a || invalid}>
          <Download size={15} /> CSV
        </button>
        <button className="btn-primary" onClick={() => exportAs('xlsx')} disabled={exporting || !a || invalid}>
          {exporting ? <Spinner size={15} /> : <Download size={15} />} Export to Excel
        </button>
        {invalid && <p className="w-full text-sm text-bad">“From” must be on or before “to”.</p>}
      </section>

      {query.isLoading ? (
        <div className="panel"><LoadingBlock label="Working out where candidates come from…" /></div>
      ) : query.error ? (
        <div className="panel"><ErrorState error={query.error} onRetry={() => query.refetch()} /></div>
      ) : a ? (
        <>
          <p className="-mb-2 text-xs text-ink-3">
            {a.from || a.to ? `${a.from ? fmtDate(a.from) : 'First application'} – ${a.to ? fmtDate(a.to) : 'today'}` : 'All dates'}
            {' · '}updates automatically as new form responses arrive · last updated {fmtDateTime(new Date(query.dataUpdatedAt).toISOString())}
          </p>
          <section className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Kpi icon={<Users size={17} />} label="Total applications" value={fmtNumber(a.totalApplications)}
                 note={`${withApps.length} source${withApps.length === 1 ? '' : 's'} used`} to={candidatesLink(null)} />
            <Kpi icon={<Megaphone size={17} />} label="Top source" value={top?.source ?? '—'}
                 note={top ? `${fmtNumber(top.applications)} applications · ${top.share}% of all` : 'No applications yet'}
                 to={top ? candidatesLink(top.source) : undefined} color={top ? sourceColor(top.source) : undefined} />
            <Kpi icon={<Award size={17} />} label="Best selection rate" value={bestRate ? `${bestRate.selectionRate}%` : '—'}
                 note={bestRate ? `${bestRate.source}: ${bestRate.selected} of ${bestRate.applications} selected` : 'No applications yet'}
                 to={bestRate ? candidatesLink(bestRate.source, { applicationStatus: 'SELECTED,JOINED' }) : undefined}
                 color={bestRate ? sourceColor(bestRate.source) : undefined} />
            <Kpi icon={<Target size={17} />} label="Most hires for requirements" value={topReq?.source ?? '—'}
                 note={topReq ? `${topReq.requirementHires} position${topReq.requirementHires === 1 ? '' : 's'} filled` : 'No requirement filled yet'}
                 to={topReq ? candidatesLink(topReq.source, { applicationStatus: 'SELECTED,JOINED' }) : '/requirements'}
                 color={topReq ? sourceColor(topReq.source) : undefined} />
          </section>

          {a.totalApplications === 0 ? (
            <div className="panel"><EmptyState title="No applications for these filters" hint="Try a wider date range or another department." /></div>
          ) : (
            <>
              <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
                <section className="panel p-5">
                  <SectionTitle title="Applications by source" subtitle="Click a bar to see those candidates" />
                  <HorizontalBars
                    data={a.sources.map((s) => ({ label: s.source, value: s.applications, color: sourceColor(s.source) }))}
                    onSelect={(x) => navigate(candidatesLink(x.label))}
                  />
                </section>
                <section className="panel p-5">
                  <SectionTitle title="Selected, pending and rejected by source" subtitle="Click a source to see its candidates" />
                  <StackedBars
                    data={a.sources.map((s) => ({ label: s.source, selected: s.selected, pending: s.pending, rejected: s.rejected }))}
                    series={OUTCOMES}
                    onSelect={(label) => navigate(candidatesLink(label))}
                  />
                </section>
              </div>

              <section className="panel p-5">
                <SectionTitle
                  title={a.granularity === 'MONTH' ? 'Monthly source performance' : 'Date-wise source performance'}
                  subtitle={`Applications per source per ${a.granularity === 'MONTH' ? 'month' : 'day'} · click a point to see that ${a.granularity === 'MONTH' ? 'month' : 'day'}'s candidates`}
                />
                <MultiLine data={trendData} series={trendSeries} onSelect={(label) => { const l = periodLink(label); if (l) navigate(l); }} />
              </section>

              <section className="panel p-5">
                <SectionTitle title="Source comparison" subtitle="Selection % = selected or joined ÷ applications from that source. Click any number to open those candidates." />
                <div className="-mx-5 overflow-x-auto">
                  <table className="w-full min-w-[860px]">
                    <thead>
                      <tr className="border-b border-line">
                        {['Source', 'Applications', 'Share', 'Selected / joined', 'Joined', 'Rejected', 'Pending', 'Selection %', 'Hires for requirements'].map((h, i) => (
                          <th key={h} className={clsx('th', i === 0 ? 'pl-5' : 'text-right')}>{h}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {[...a.sources].sort((x, y) => y.applications - x.applications).map((s) => (
                        <tr key={s.source} className={clsx('border-b border-line/40 last:border-0 hover:bg-white/[0.02]', s.source === a.topSource && 'bg-white/[0.03]')}>
                          <td className="td pl-5">
                            <Link to={candidatesLink(s.source)} className="inline-flex items-center gap-2 font-medium hover:underline">
                              <span className="h-2.5 w-2.5 rounded-full" style={{ background: sourceColor(s.source) }} />
                              {s.source}
                              {s.source === a.topSource && <span className="rounded bg-info/15 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-[#8fb4f5]">Top</span>}
                            </Link>
                          </td>
                          <NumCell n={s.applications} to={candidatesLink(s.source)} />
                          <td className="td text-right text-ink-2">{s.share}%</td>
                          <NumCell n={s.selected} to={candidatesLink(s.source, { applicationStatus: 'SELECTED,JOINED' })} />
                          <NumCell n={s.joined} to={candidatesLink(s.source, { applicationStatus: 'JOINED' })} />
                          <NumCell n={s.rejected} to={candidatesLink(s.source, { applicationStatus: 'REJECTED' })} />
                          <NumCell n={s.pending} to={candidatesLink(s.source, { applicationStatus: PENDING_STATUSES })} />
                          <td className="td text-right">
                            <span className="inline-flex items-center gap-2">
                              <span className="hidden h-1.5 w-16 overflow-hidden rounded-full bg-raised sm:inline-block">
                                <span className="block h-full rounded-full bg-good" style={{ width: `${Math.min(100, s.selectionRate)}%` }} />
                              </span>
                              {s.applications ? `${s.selectionRate}%` : '—'}
                            </span>
                          </td>
                          <td className="td text-right">{s.requirementHires || '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>

              <section className="panel p-5">
                <SectionTitle
                  title="Which sources fill the requirements"
                  subtitle="Selected or joined candidates in each requirement's department and sub-work, earliest applicants first, up to the number needed"
                  action={<Link to="/requirements" className="text-sm text-ink-3 hover:text-ink hover:underline">Open Requirements</Link>}
                />
                {a.requirements.length === 0 ? (
                  <EmptyState title="No requirements saved" hint="Add requirements on the Requirements page to see which sources fill them." />
                ) : (
                  <div className="-mx-5 overflow-x-auto">
                    <table className="w-full min-w-[640px]">
                      <thead>
                        <tr className="border-b border-line">
                          <th className="th pl-5">Requirement</th>
                          <th className="th text-right">Filled</th>
                          <th className="th">Filled from</th>
                        </tr>
                      </thead>
                      <tbody>
                        {a.requirements.map((r) => (
                          <tr key={r.requirementId} className="border-b border-line/40 last:border-0">
                            <td className="td pl-5">
                              <span className="font-medium">{r.department}</span>
                              {r.position && <span className="text-ink-3"> · {r.position}</span>}
                            </td>
                            <td className={clsx('td text-right', r.filled >= r.needed ? 'text-good' : 'text-ink-2')}>{r.filled} / {r.needed}</td>
                            <td className="td">
                              {r.filled === 0 ? (
                                <span className="text-ink-3">Not filled yet</span>
                              ) : (
                                <span className="flex flex-wrap gap-1.5">
                                  {Object.entries(r.filledBySource).filter(([, n]) => n > 0).map(([s, n]) => (
                                    <span key={s} className="inline-flex items-center gap-1.5 rounded-full border border-line px-2 py-0.5 text-xs">
                                      <span className="h-2 w-2 rounded-full" style={{ background: sourceColor(s) }} />
                                      {s} · {n}
                                    </span>
                                  ))}
                                </span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </>
          )}
        </>
      ) : null}
    </div>
  );
}

function monthEnd(ym: string): string {
  const [y, m] = ym.split('-').map(Number);
  const last = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return `${ym}-${String(last).padStart(2, '0')}`;
}

function Kpi({ icon, label, value, note, to, color }: { icon: React.ReactNode; label: string; value: string; note: string; to?: string; color?: string }) {
  const body = (
    <>
      <div className="flex items-center gap-2 text-xs text-ink-3">{icon} {label}</div>
      <div className="mt-2 flex items-center gap-2 text-xl font-semibold">
        {color && <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: color }} />}
        <span className="truncate">{value}</span>
      </div>
      <div className="mt-1 truncate text-xs text-ink-3" title={note}>{note}</div>
    </>
  );
  return to ? (
    <Link to={to} className="panel block p-4 transition hover:bg-raised">{body}</Link>
  ) : (
    <div className="panel p-4">{body}</div>
  );
}

function NumCell({ n, to }: { n: number; to: string }) {
  return (
    <td className="td text-right">
      {n > 0 ? <Link to={to} className="hover:underline">{fmtNumber(n)}</Link> : <span className="text-ink-3">0</span>}
    </td>
  );
}
