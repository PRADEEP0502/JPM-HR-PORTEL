import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Building2, Pencil, Plus, Trash2 } from 'lucide-react';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { useDepartments } from '../lib/queries';
import { useToast } from '../lib/toast';
import { CATEGORICAL } from '../lib/format';
import type { Department, Report } from '../lib/types';
import { Modal } from '../components/ui/Modal';
import { Field, SectionTitle } from '../components/ui/Misc';
import { Pill } from '../components/ui/Pill';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';
import { HorizontalBars } from '../components/charts/Charts';

function DepartmentModal({ dept, open, onClose }: { dept: Department | null; open: boolean; onClose: () => void }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [active, setActive] = useState(true);
  const [positions, setPositions] = useState('');
  useEffect(() => {
    if (open) {
      setName(dept?.name ?? '');
      setDescription(dept?.description ?? '');
      setActive(dept?.active ?? true);
      setPositions((dept?.positions ?? []).join('\n'));
    }
  }, [open, dept]);
  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: name.trim(),
        description: description.trim() || null,
        active,
        positions: positions.split('\n').map((p) => p.trim()).filter(Boolean),
      };
      return dept ? api.put(`/departments/${dept.id}`, body) : api.post('/departments', body);
    },
    onSuccess: () => {
      toast('success', dept ? 'Department updated' : 'Department created');
      ['departments', 'reports', 'dashboard'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
      onClose();
    },
    onError: (e) => toast('error', e.message),
  });
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={dept ? 'Edit department' : 'Add department'}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" disabled={!name.trim() || save.isPending} onClick={() => save.mutate()}>
            {save.isPending && <Spinner size={14} />} Save
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Name *" hint="Google Form answers are matched to departments by this name (case-insensitive).">
          <input className="input" maxLength={100} value={name} onChange={(e) => setName(e.target.value)} />
        </Field>
        <Field label="Sub-work" hint="One per line, e.g. Machine Operator. Use the same names as the Google Form.">
          <textarea className="input min-h-[120px]" value={positions} onChange={(e) => setPositions(e.target.value)} placeholder={'Machine Operator\nHelper\nSupervisor'} />
        </Field>
        <Field label="Description">
          <textarea className="input min-h-[72px]" maxLength={500} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="h-4 w-4 accent-[#22a559]" />
          Active (inactive departments are hidden from new assignments and sync matching)
        </label>
      </div>
    </Modal>
  );
}

export default function DepartmentsPage() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const qc = useQueryClient();
  const depts = useDepartments();
  const summary = useQuery({ queryKey: ['reports', 'DEPARTMENT'], queryFn: () => api.get<Report>('/reports/DEPARTMENT') });
  const [modal, setModal] = useState<{ open: boolean; dept: Department | null }>({ open: false, dept: null });
  const remove = useMutation({
    mutationFn: (d: Department) => api.del(`/departments/${d.id}`),
    onSuccess: () => {
      toast('success', 'Department deleted');
      ['departments', 'reports', 'dashboard'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }));
    },
    onError: (e) => toast('error', e.message),
  });

  const idByName = new Map(depts.data?.map((d) => [d.name, d.id]) ?? []);
  const rows = summary.data?.rows ?? [];

  return (
    <div className="space-y-6">
      <section className="panel p-5">
        <SectionTitle title="Department-wise Summary" subtitle="Live counts across all applications. Pending = no hiring decision yet (New, Under Review, On Hold)." />
        {summary.isLoading ? (
          <LoadingBlock />
        ) : summary.error ? (
          <ErrorState error={summary.error} onRetry={() => summary.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState icon={<Building2 size={20} />} title="No departments yet" hint={isAdmin ? 'Add a department below.' : 'An administrator can add departments.'} />
        ) : (
          <div className="grid grid-cols-1 gap-6 2xl:grid-cols-5">
            <div className="-mx-5 overflow-x-auto 2xl:col-span-3">
              <table className="w-full min-w-[640px]">
                <thead>
                  <tr className="border-b border-line">
                    <th className="th pl-5">Department</th>
                    <th className="th text-right">Total</th>
                    <th className="th text-right">New</th>
                    <th className="th text-right">Shortlisted</th>
                    <th className="th text-right">Selected</th>
                    <th className="th text-right pr-5">Pending</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => {
                    const id = r.department === 'Unassigned' ? 0 : idByName.get(String(r.department));
                    const link = (extra = '') => `/candidates?departmentId=${id}${extra}`;
                    return (
                      <tr key={String(r.department)} className="border-b border-line/40 last:border-0 hover:bg-white/[0.02]">
                        <td className="td pl-5 font-medium text-ink">{id !== undefined ? <Link className="hover:underline" to={link()}>{r.department}</Link> : r.department}</td>
                        <td className="td text-right tabular-nums text-ink">{r.total}</td>
                        <td className="td text-right tabular-nums"><Link className="hover:underline" to={link('&applicationStatus=NEW')}>{r.new}</Link></td>
                        <td className="td text-right tabular-nums"><Link className="hover:underline" to={link('&applicationStatus=SHORTLISTED')}>{r.shortlisted}</Link></td>
                        <td className="td text-right tabular-nums"><Link className="hover:underline" to={link('&applicationStatus=SELECTED')}>{r.selected}</Link></td>
                        <td className="td pr-5 text-right tabular-nums">{r.pending}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="2xl:col-span-2">
              <HorizontalBars data={summary.data!.chart.map((c) => ({ label: c.label, value: c.value }))} color={CATEGORICAL[0]} />
            </div>
          </div>
        )}
      </section>

      <section className="panel p-5">
        <SectionTitle
          title="Manage Departments"
          subtitle={isAdmin ? 'Departments offered on the application form and used for matching synced responses.' : 'Only administrators can change departments.'}
          action={isAdmin && <button className="btn-primary" onClick={() => setModal({ open: true, dept: null })}><Plus size={15} /> Add department</button>}
        />
        {depts.isLoading ? (
          <LoadingBlock />
        ) : depts.error ? (
          <ErrorState error={depts.error} onRetry={() => depts.refetch()} />
        ) : (
          <ul className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
            {depts.data?.map((d, i) => (
              <li key={d.id} className="flex items-start gap-3 rounded-xl border border-line/70 bg-card p-4">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-white" style={{ background: CATEGORICAL[i % CATEGORICAL.length] }}>
                  <Building2 size={17} />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="flex items-center gap-2 font-medium">{d.name} {!d.active && <Pill tone="grey">Inactive</Pill>}</p>
                  {d.description && <p className="mt-0.5 text-xs text-ink-3">{d.description}</p>}
                  {d.positions.length > 0 ? (
                    <ul className="mt-2 flex flex-wrap gap-1.5" aria-label={`${d.name} sub-work`}>
                      {d.positions.map((p) => (
                        <li key={p} className="rounded-md border border-line/70 px-2 py-0.5 text-xs text-ink-2">{p}</li>
                      ))}
                    </ul>
                  ) : (
                    <p className="mt-0.5 text-xs text-ink-3">No sub-work added</p>
                  )}
                </div>
                {isAdmin && (
                  <div className="flex gap-1">
                    <button className="btn-ghost p-2" aria-label={`Edit ${d.name}`} onClick={() => setModal({ open: true, dept: d })}><Pencil size={15} /></button>
                    <button
                      className="btn-ghost p-2 hover:text-bad"
                      aria-label={`Delete ${d.name}`}
                      disabled={remove.isPending}
                      onClick={() => window.confirm(`Delete department "${d.name}"? Departments with candidates must be deactivated instead.`) && remove.mutate(d)}
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
      <DepartmentModal open={modal.open} dept={modal.dept} onClose={() => setModal({ open: false, dept: null })} />
    </div>
  );
}
