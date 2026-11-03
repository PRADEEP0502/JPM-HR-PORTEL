import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { BarChart3, Download, FileBarChart, X } from 'lucide-react';
import { api } from '../lib/api';
import { useToast } from '../lib/toast';
import { APPLICATION_STATUSES, CATEGORICAL, INTERVIEW_STATUSES, fmtDate, fmtDateTime } from '../lib/format';
import { useDepartmentLink } from '../lib/queries';
import type { Report, ReportType } from '../lib/types';
import { SectionTitle } from '../components/ui/Misc';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';
import { HorizontalBars, TrendArea, VerticalBars } from '../components/charts/Charts';

const REPORTS: { type: ReportType; label: string; desc: string }[] = [
  { type: 'DEPARTMENT', label: 'Department-wise', desc: 'Candidates and outcomes per department' },
  { type: 'MONTHLY', label: 'Monthly applications', desc: 'Applications received per month' },
  { type: 'APPLICATIONS_BY_DATE', label: 'Applications by date', desc: 'Applications received per day (default: last 30 days)' },
  { type: 'STATUS', label: 'Recruitment status', desc: 'Candidates in each recruitment status' },
  { type: 'RATING_DISTRIBUTION', label: 'Profile Match Rating', desc: 'How many candidates have each rating (internal)' },
  { type: 'SELECTED', label: 'Selected', desc: 'Candidates with status Selected' },
  { type: 'SELECTED_JOINED', label: 'Selected & joined', desc: 'Candidates who were selected or have joined' },
  { type: 'REJECTED', label: 'Rejected', desc: 'Candidates with status Rejected' },
  { type: 'PENDING_VERIFICATION', label: 'Pending verification', desc: 'Documents / data not yet verified' },
  { type: 'INTERVIEW_STATUS', label: 'Interview schedule', desc: 'All interviews and their outcome' },
  { type: 'ACTIVITY', label: 'Recruitment activity', desc: 'Who changed what, from the audit log (default: last 30 days)' },
];

/** Candidate list for a bar in the status and rating reports (chart points carry only their label). */
function barLink(type: ReportType, label: string): string | null {
  if (type === 'STATUS') {
    const s = APPLICATION_STATUSES.find((x) => x.label === label);
    return s ? `/candidates?applicationStatus=${s.value}` : null;
  }
  if (type === 'INTERVIEW_STATUS') {
    const s = INTERVIEW_STATUSES.find((x) => x.label === label);
    return s ? `/candidates?interviewStatus=${s.value}` : null;
  }
  if (type === 'RATING_DISTRIBUTION') return `/candidates?rating=${label === 'Not Rated' ? 'UNRATED' : label}`;
  return null;
}

export default function ReportsPage() {
  const toast = useToast();
  // kept in the URL, so Back from a candidate returns to the same report
  const [params, setParams] = useSearchParams();
  const type = REPORTS.find((x) => x.type === params.get('type'))?.type ?? 'DEPARTMENT';
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';
  const setParam = (patch: Record<string, string>) =>
    setParams((p) => {
      const n = new URLSearchParams(p);
      Object.entries(patch).forEach(([k, v]) => (v ? n.set(k, v) : n.delete(k)));
      return n;
    }, { replace: true });
  const [action, setAction] = useState<string | null>(null);
  const navigate = useNavigate();
  const departmentLink = useDepartmentLink();
  const go = (link: string | null) => link && navigate(link);
  const [exporting, setExporting] = useState(false);
  const invalid = !!from && !!to && from > to;

  const query = useQuery({
    queryKey: ['reports', type, from, to],
    queryFn: () => api.get<Report>(`/reports/${type}`, { from, to }),
    enabled: !invalid,
  });
  const r = query.data;
  useEffect(() => setAction(null), [type, from, to]);
  const rows = r ? r.rows.filter((row) => !action || row.action === action) : [];

  const exportReport = async (format: 'xlsx' | 'csv') => {
    setExporting(true);
    try {
      await api.download(`/reports/${type}/export`, { from, to, format }, `${type.toLowerCase()}-report.${format}`);
      toast('success', 'Report exported');
    } catch (e) {
      toast('error', (e as Error).message);
    } finally {
      setExporting(false);
    }
  };

  const dateLabel = type === 'INTERVIEW_STATUS' ? 'Interview date' : type === 'ACTIVITY' ? 'Activity date' : 'Application date';

  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
      <nav className="panel h-fit p-2 xl:col-span-3" aria-label="Reports">
        <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2 xl:grid-cols-1">
          {REPORTS.map((rep) => (
            <li key={rep.type}>
              <button
                onClick={() => setParam({ type: rep.type })}
                className={clsx('flex w-full items-start gap-3 rounded-xl px-3 py-2.5 text-left transition', type === rep.type ? 'bg-ink text-canvas' : 'hover:bg-raised')}
              >
                <FileBarChart size={17} className="mt-0.5 shrink-0" />
                <span>
                  <span className="block text-sm font-medium">{rep.label}</span>
                  <span className={clsx('block text-xs', type === rep.type ? 'text-canvas/60' : 'text-ink-3')}>{rep.desc}</span>
                </span>
              </button>
            </li>
          ))}
        </ul>
      </nav>

      <div className="space-y-6 xl:col-span-9">
        <section className="panel flex flex-wrap items-end gap-3 p-4">
          <label className="text-sm">
            <span className="label">{dateLabel} from</span>
            <input type="date" className="input" value={from} onChange={(e) => setParam({ from: e.target.value })} />
          </label>
          <label className="text-sm">
            <span className="label">to</span>
            <input type="date" className="input" value={to} min={from || undefined} onChange={(e) => setParam({ to: e.target.value })} />
          </label>
          <button className="btn-ghost" disabled={!from && !to} onClick={() => setParam({ from: '', to: '' })}>Clear dates</button>
          <div className="flex-1" />
          <button className="btn-secondary" onClick={() => exportReport('csv')} disabled={exporting || !r || invalid}>
            <Download size={15} /> CSV
          </button>
          <button className="btn-primary" onClick={() => exportReport('xlsx')} disabled={exporting || !r || invalid}>
            {exporting ? <Spinner size={15} /> : <Download size={15} />} Export to Excel
          </button>
          {invalid && <p className="w-full text-sm text-bad">“From” must be on or before “to”.</p>}
        </section>

        {query.isLoading ? (
          <div className="panel"><LoadingBlock label="Building report…" /></div>
        ) : query.error ? (
          <div className="panel"><ErrorState error={query.error} onRetry={() => query.refetch()} /></div>
        ) : r ? (
          <>
            <section className="panel p-5">
              <SectionTitle
                title={r.title}
                subtitle={`${r.rows.length} row${r.rows.length === 1 ? '' : 's'} · ${r.from || r.to ? `${r.from ? fmtDate(r.from) : '…'} – ${r.to ? fmtDate(r.to) : 'today'}` : 'all dates'} · generated ${fmtDateTime(r.generatedAt)}`}
              />
              {r.chart.length === 0 || r.chart.every((c) => c.value === 0) ? (
                <EmptyState icon={<BarChart3 size={20} />} title="Nothing to chart for this selection" />
              ) : type === 'MONTHLY' || type === 'APPLICATIONS_BY_DATE' ? (
                <TrendArea data={r.chart.map((c) => ({ label: c.label, value: c.value }))} />
              ) : type === 'INTERVIEW_STATUS' || type === 'RATING_DISTRIBUTION' || type === 'STATUS' ? (
                <VerticalBars data={r.chart.map((c) => ({ label: c.label, value: c.value }))} color={CATEGORICAL[0]} onSelect={(x) => go(barLink(type, x.label))} />
              ) : (
                <>
                  {type !== 'DEPARTMENT' && <p className="mb-2 text-xs text-ink-3">{type === 'ACTIVITY' ? 'By action' : 'By department'}</p>}
                  <HorizontalBars
                    data={r.chart.map((c) => ({ label: c.label, value: c.value }))}
                    color={CATEGORICAL[0]}
                    onSelect={type === 'ACTIVITY' ? (x) => setAction((a) => (a === x.label ? null : x.label)) : (x) => go(departmentLink(x.label))}
                  />
                </>
              )}
            </section>
            <section className="panel p-5">
              {action && (
                <button className="btn-secondary mb-3 px-2.5 py-1.5 text-xs" onClick={() => setAction(null)}>
                  {rows.length} × {action} <X size={13} />
                </button>
              )}
              {rows.length === 0 ? (
                <EmptyState title="No records for this report" hint="Try a wider date range." />
              ) : (
                <div className="-mx-5 max-h-[560px] overflow-auto">
                  <table className="w-full min-w-[720px]">
                    <thead className="sticky top-0 bg-panel">
                      <tr className="border-b border-line">
                        {r.columns.map((c, i) => (
                          <th key={c.key} className={clsx('th', i === 0 && 'pl-5')}>{c.label}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map((row, idx) => {
                        // a candidate deleted since the activity has no profile to open
                        const profile = row.id && row.fullName ? `/candidates/${row.id}` : null;
                        return (
                        <tr
                          key={idx}
                          className={clsx('border-b border-line/40 last:border-0 hover:bg-white/[0.02]', profile && 'cursor-pointer')}
                          onClick={() => {
                            if (profile && !window.getSelection()?.toString()) navigate(profile);
                          }}
                        >
                          {r.columns.map((c, i) => {
                            const v = row[c.key];
                            const text = v === null || v === undefined || v === '' ? '—' : c.key === 'applicationDate' ? fmtDate(String(v)) : String(v);
                            return (
                              <td key={c.key} className={clsx('td max-w-[260px] truncate', i === 0 && 'pl-5')} title={typeof v === 'string' ? v : undefined}>
                                {(c.key === 'fullName' || c.key === 'candidateCode') && profile ? (
                                  <Link to={profile} className="text-ink hover:underline" onClick={(e) => e.stopPropagation()}>{text}</Link>
                                ) : (
                                  text
                                )}
                              </td>
                            );
                          })}
                        </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </>
        ) : null}
      </div>
    </div>
  );
}
