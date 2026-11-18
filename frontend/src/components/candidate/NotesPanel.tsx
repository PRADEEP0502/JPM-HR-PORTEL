import { useMemo, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import clsx from 'clsx';
import { Check, ClipboardList, Copy, Hash, MessageSquarePlus, Pencil, PhoneCall, StickyNote, Trash2, X } from 'lucide-react';
import { api } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { fmtDateTime, fmtRelative } from '../../lib/format';
import type { NoteType, Remark } from '../../lib/types';
import { Spinner } from '../ui/States';

const MAX_TEXT = 2000;
const MAX_REF = 100;

const NOTE_TYPES: Record<NoteType, { label: string; icon: typeof StickyNote; tone: string; placeholder: string }> = {
  CALL: {
    label: 'Phone call',
    icon: PhoneCall,
    tone: 'bg-info/15 text-info ring-info/30',
    placeholder: 'What was discussed on the call? e.g. Confirmed interest, available to join from 1 December, expects ₹18,000…',
  },
  INTERVIEW: {
    label: 'Interview',
    icon: ClipboardList,
    tone: 'bg-violet/15 text-violet ring-violet/30',
    placeholder: 'Interview notes: round, who took it, strengths, concerns, result…',
  },
  REFERENCE: {
    label: 'Reference no.',
    icon: Hash,
    tone: 'bg-warn/15 text-warn ring-warn/30',
    placeholder: 'What is this number for? e.g. Offer letter, employee ID, police verification, bank account…',
  },
  GENERAL: {
    label: 'General',
    icon: StickyNote,
    tone: 'bg-raised text-ink-2 ring-line',
    placeholder: 'Anything else the HR team should know…',
  },
};
const TYPE_ORDER: NoteType[] = ['CALL', 'INTERVIEW', 'REFERENCE', 'GENERAL'];

type Draft = { type: NoteType; referenceNo: string; remark: string };

/** HR notes on a candidate: calls, interviews, reference numbers and general notes, with who wrote them and when. */
export function NotesPanel({ candidateId, notes, onChanged }: { candidateId: number; notes: Remark[]; onChanged: () => void }) {
  const toast = useToast();
  const { user, isAdmin } = useAuth();
  const [filter, setFilter] = useState<NoteType | 'ALL'>('ALL');
  const [editingId, setEditingId] = useState<number | null>(null);

  const counts = useMemo(() => {
    const m = { CALL: 0, INTERVIEW: 0, REFERENCE: 0, GENERAL: 0 } as Record<NoteType, number>;
    notes.forEach((n) => (m[n.type ?? 'GENERAL'] += 1));
    return m;
  }, [notes]);
  const shown = filter === 'ALL' ? notes : notes.filter((n) => (n.type ?? 'GENERAL') === filter);

  const add = useMutation({
    mutationFn: (d: Draft) => api.post<Remark>(`/candidates/${candidateId}/remarks`, body(d)),
    onSuccess: (_, d) => {
      toast('success', `${NOTE_TYPES[d.type].label} note saved`);
      onChanged();
    },
    onError: (e) => toast('error', e.message),
  });

  const remove = useMutation({
    mutationFn: (id: number) => api.del(`/candidates/${candidateId}/remarks/${id}`),
    onSuccess: () => {
      toast('success', 'Note deleted');
      onChanged();
    },
    onError: (e) => toast('error', e.message),
  });

  const canChange = (n: Remark) => isAdmin || n.author === user?.username;

  return (
    <section id="remarks" className="panel scroll-mt-6 p-5">
      <div className="mb-4 flex items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 text-[15px] font-semibold">
          <MessageSquarePlus size={16} className="text-ink-3" /> Notes
        </h2>
        <span className="text-[11px] text-ink-3">Calls, interviews, reference numbers · visible to the HR team</span>
      </div>

      <NoteForm
        key="new"
        submitLabel="Save note"
        pending={add.isPending}
        onSubmit={(d, reset) => add.mutate(d, { onSuccess: reset })}
      />

      {notes.length > 0 && (
        <div className="mt-5 flex flex-wrap items-center gap-1.5" role="tablist" aria-label="Filter notes">
          <FilterChip active={filter === 'ALL'} onClick={() => setFilter('ALL')} label={`All (${notes.length})`} />
          {TYPE_ORDER.filter((t) => counts[t] > 0).map((t) => (
            <FilterChip key={t} active={filter === t} onClick={() => setFilter(t)} label={`${NOTE_TYPES[t].label} (${counts[t]})`} />
          ))}
        </div>
      )}

      {notes.length === 0 ? (
        <div className="mt-4 rounded-xl border border-dashed border-line/70 p-6 text-center">
          <StickyNote size={20} className="mx-auto text-ink-3" />
          <p className="mt-1.5 text-xs text-ink-3">No notes yet. Log a call, an interview or a reference number above.</p>
        </div>
      ) : (
        <ul className="mt-3 space-y-2.5">
          {shown.map((n) => {
            const meta = NOTE_TYPES[n.type ?? 'GENERAL'];
            const Icon = meta.icon;
            if (editingId === n.id) {
              return (
                <li key={n.id} className="rounded-xl border border-info/40 bg-card p-3">
                  <EditNote candidateId={candidateId} note={n} onDone={() => setEditingId(null)} onChanged={onChanged} />
                </li>
              );
            }
            return (
              <li key={n.id} className="group flex gap-3 rounded-xl border border-line/60 bg-card px-3 py-2.5">
                <span className={clsx('mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ring-1', meta.tone)} title={meta.label}>
                  <Icon size={15} />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className="text-[11px] font-semibold uppercase tracking-wide text-ink-2">{meta.label}</span>
                    {n.referenceNo && <ReferenceChip value={n.referenceNo} />}
                  </div>
                  <p className="mt-1 whitespace-pre-wrap break-words text-sm text-ink">{n.remark}</p>
                  <p className="mt-1.5 text-[11px] text-ink-3">
                    <span className="font-medium text-ink-2">{n.author}</span> ·{' '}
                    <span title={fmtDateTime(n.createdAt)}>{fmtDateTime(n.createdAt)}</span>
                    {n.editedAt && (
                      <span title={fmtDateTime(n.editedAt)}>
                        {' '}· edited{n.editedBy && n.editedBy !== n.author ? ` by ${n.editedBy}` : ''} {fmtRelative(n.editedAt)}
                      </span>
                    )}
                  </p>
                </div>
                {canChange(n) && (
                  <div className="flex shrink-0 items-start gap-0.5 opacity-60 transition group-hover:opacity-100 focus-within:opacity-100">
                    <button className="btn-ghost p-1.5" title="Edit note" aria-label="Edit note" onClick={() => setEditingId(n.id)}>
                      <Pencil size={14} />
                    </button>
                    <button
                      className="btn-ghost p-1.5 hover:text-bad"
                      title="Delete note"
                      aria-label="Delete note"
                      disabled={remove.isPending}
                      onClick={() => window.confirm('Delete this note? This cannot be undone.') && remove.mutate(n.id)}
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

const body = (d: Draft) => ({ type: d.type, referenceNo: d.referenceNo.trim() || null, remark: d.remark.trim() });

function EditNote({ candidateId, note, onDone, onChanged }: { candidateId: number; note: Remark; onDone: () => void; onChanged: () => void }) {
  const toast = useToast();
  const save = useMutation({
    mutationFn: (d: Draft) => api.put<Remark>(`/candidates/${candidateId}/remarks/${note.id}`, body(d)),
    onSuccess: () => {
      toast('success', 'Note updated');
      onChanged();
      onDone();
    },
    onError: (e) => toast('error', e.message),
  });
  return (
    <NoteForm
      initial={{ type: note.type ?? 'GENERAL', referenceNo: note.referenceNo ?? '', remark: note.remark }}
      submitLabel="Save changes"
      pending={save.isPending}
      onSubmit={(d) => save.mutate(d)}
      onCancel={onDone}
    />
  );
}

function NoteForm({
  initial,
  submitLabel,
  pending,
  onSubmit,
  onCancel,
}: {
  initial?: Draft;
  submitLabel: string;
  pending: boolean;
  onSubmit: (d: Draft, reset: () => void) => void;
  onCancel?: () => void;
}) {
  const toast = useToast();
  const empty: Draft = { type: 'CALL', referenceNo: '', remark: '' };
  const [d, setD] = useState<Draft>(initial ?? empty);
  const needsRef = d.type === 'REFERENCE';
  const ready = d.remark.trim().length > 0 && (!needsRef || d.referenceNo.trim().length > 0);

  const submit = () => {
    if (!d.remark.trim()) return toast('error', 'Write the note first');
    if (needsRef && !d.referenceNo.trim()) return toast('error', 'Enter the reference number');
    onSubmit(d, () => setD({ ...empty, type: d.type }));
  };

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
      className="space-y-2.5"
    >
      <div className="flex flex-wrap gap-1.5" role="radiogroup" aria-label="Note type">
        {TYPE_ORDER.map((t) => {
          const meta = NOTE_TYPES[t];
          const Icon = meta.icon;
          const active = d.type === t;
          return (
            <button
              key={t}
              type="button"
              role="radio"
              aria-checked={active}
              onClick={() => setD({ ...d, type: t })}
              className={clsx(
                'inline-flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-semibold ring-1 transition',
                active ? meta.tone : 'bg-panel text-ink-3 ring-line hover:text-ink'
              )}
            >
              <Icon size={13} /> {meta.label}
            </button>
          );
        })}
      </div>

      <div className="relative">
        <Hash size={14} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-3" />
        <input
          className="input pl-8"
          maxLength={MAX_REF}
          placeholder={needsRef ? 'Reference number (required)' : 'Reference number (optional)'}
          aria-label="Reference number"
          required={needsRef}
          value={d.referenceNo}
          onChange={(e) => setD({ ...d, referenceNo: e.target.value })}
        />
      </div>

      <textarea
        className="input min-h-[88px]"
        maxLength={MAX_TEXT}
        placeholder={NOTE_TYPES[d.type].placeholder}
        aria-label="Note"
        value={d.remark}
        onChange={(e) => setD({ ...d, remark: e.target.value })}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            submit();
          }
        }}
      />

      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] text-ink-3">
          {d.remark.length}/{MAX_TEXT} · Ctrl + Enter to save
        </span>
        <div className="flex items-center gap-2">
          {onCancel && (
            <button type="button" className="btn-ghost" onClick={onCancel}>
              <X size={14} /> Cancel
            </button>
          )}
          <button className="btn-primary" disabled={!ready || pending}>
            {pending ? <Spinner size={14} /> : <Check size={14} />} {submitLabel}
          </button>
        </div>
      </div>
    </form>
  );
}

function FilterChip({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={clsx(
        'rounded-full px-2.5 py-1 text-[11px] font-semibold ring-1 transition',
        active ? 'bg-info/15 text-info ring-info/40' : 'text-ink-3 ring-line hover:text-ink'
      )}
    >
      {label}
    </button>
  );
}

function ReferenceChip({ value }: { value: string }) {
  const toast = useToast();
  return (
    <button
      type="button"
      title="Copy reference number"
      onClick={() =>
        navigator.clipboard
          .writeText(value)
          .then(() => toast('success', `Copied ${value}`))
          .catch(() => toast('error', 'Could not copy'))
      }
      className="inline-flex items-center gap-1 rounded-md bg-warn/10 px-1.5 py-0.5 font-mono text-[11px] font-semibold text-warn ring-1 ring-warn/30 transition hover:bg-warn/20"
    >
      <Hash size={10} /> {value} <Copy size={10} className="opacity-70" />
    </button>
  );
}
