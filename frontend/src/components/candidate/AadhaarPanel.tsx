import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import clsx from 'clsx';
import { Eye, Fingerprint, PenSquare, ShieldAlert, ShieldCheck, ShieldQuestion, Trash2 } from 'lucide-react';
import { api } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { fmtDateTime, fmtRelative } from '../../lib/format';
import type { AadhaarCheck, AadhaarMethod, AadhaarResult, CandidateDetail, DocumentInfo } from '../../lib/types';
import { Modal } from '../ui/Modal';
import { Spinner } from '../ui/States';

const METHODS: { value: AadhaarMethod; label: string; hint: string }[] = [
  { value: 'ORIGINAL_SEEN', label: 'Original card checked in person', hint: 'The candidate showed the physical Aadhaar card' },
  { value: 'COPY_CHECKED', label: 'Uploaded copy checked', hint: 'The copy in Documents was compared with the application' },
  { value: 'DIGILOCKER', label: 'DigiLocker / e-Aadhaar checked', hint: 'Seen in DigiLocker or as a downloaded e-Aadhaar' },
  { value: 'QR_SCANNED', label: 'QR code scanned', hint: 'The QR code on the card was scanned with the Aadhaar QR scanner app' },
];

/** "XXXX XXXX 1234" when the last 4 digits were recorded. */
export const maskedAadhaar = (last4: string | null) => (last4 ? `XXXX XXXX ${last4}` : null);

/** Small badge for the header and lists: verified, mismatch, or not checked yet. */
export function AadhaarBadge({ result, compact = false }: { result: AadhaarResult | null | undefined; compact?: boolean }) {
  if (compact) {
    if (result === 'VERIFIED') return <ShieldCheck size={14} className="shrink-0 text-good" aria-label="Aadhaar verified"><title>Aadhaar verified</title></ShieldCheck>;
    if (result === 'MISMATCH') return <ShieldAlert size={14} className="shrink-0 text-bad" aria-label="Aadhaar mismatch"><title>Aadhaar mismatch found</title></ShieldAlert>;
    return null;
  }
  const [Icon, label, tone] =
    result === 'VERIFIED'
      ? [ShieldCheck, 'Aadhaar verified', 'bg-good/15 text-[#6fd99a] ring-good/30']
      : result === 'MISMATCH'
        ? [ShieldAlert, 'Aadhaar mismatch', 'bg-bad/15 text-[#f29a9a] ring-bad/30']
        : [ShieldQuestion, 'Aadhaar not checked', 'bg-white/5 text-ink-2 ring-white/10'];
  return (
    <a href="#aadhaar" className={clsx('inline-flex items-center gap-1 whitespace-nowrap rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset hover:opacity-80', tone)}>
      <Icon size={12} /> {label}
    </a>
  );
}

/**
 * HR checks the candidate's Aadhaar themselves (the original card, the uploaded copy, DigiLocker or the QR code)
 * and records it here, so everyone can see it was done, by whom and when.
 */
export function AadhaarPanel({ candidate, onChanged, onViewCopy }: {
  candidate: Pick<CandidateDetail, 'id' | 'fullName' | 'aadhaar' | 'documents'>;
  onChanged: () => void;
  onViewCopy: (doc: DocumentInfo) => void;
}) {
  const toast = useToast();
  const [editing, setEditing] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const check = candidate.aadhaar;
  const copy = candidate.documents.find((d) => d.docType === 'AADHAAR');

  const remove = useMutation({
    mutationFn: () => api.del(`/candidates/${candidate.id}/aadhaar-verification`),
    onSuccess: () => {
      toast('success', 'Aadhaar check removed');
      setConfirmRemove(false);
      onChanged();
    },
    onError: (e) => toast('error', e.message),
  });

  return (
    <section id="aadhaar" className="panel scroll-mt-6 p-5">
      <h2 className="mb-4 flex items-center gap-2 text-[15px] font-semibold">
        <Fingerprint size={16} className="text-ink-3" /> Aadhaar Verification
      </h2>

      {!check ? (
        <div className="rounded-xl border border-dashed border-line px-4 py-5 text-center">
          <ShieldQuestion size={26} className="mx-auto text-ink-3" />
          <p className="mt-2 text-sm font-medium text-ink">Not verified yet</p>
          <p className="mt-1 text-xs text-ink-3">Check the original card, the uploaded copy or DigiLocker, then record it here.</p>
          <div className="mt-4 flex flex-wrap justify-center gap-2">
            {copy && (
              <button className="btn-secondary" onClick={() => onViewCopy(copy)}>
                <Eye size={15} /> View Aadhaar copy
              </button>
            )}
            <button className="btn-primary" onClick={() => setEditing(true)}>
              <ShieldCheck size={15} /> Verify Aadhaar
            </button>
          </div>
        </div>
      ) : (
        <div className={clsx('rounded-xl border px-4 py-4', check.result === 'VERIFIED' ? 'border-good/40 bg-good/10' : 'border-bad/40 bg-bad/10')}>
          <div className="flex items-start gap-3">
            {check.result === 'VERIFIED' ? <ShieldCheck size={26} className="shrink-0 text-good" /> : <ShieldAlert size={26} className="shrink-0 text-bad" />}
            <div className="min-w-0 flex-1">
              <p className={clsx('text-sm font-semibold', check.result === 'VERIFIED' ? 'text-[#6fd99a]' : 'text-[#f29a9a]')}>
                {check.result === 'VERIFIED' ? 'Aadhaar verified' : 'Mismatch found'}
              </p>
              <p className="mt-0.5 text-xs text-ink-2">{check.methodLabel}</p>
            </div>
          </div>
          <dl className="mt-3 space-y-2 text-sm">
            {check.last4 && (
              <div className="flex justify-between gap-3">
                <dt className="text-xs text-ink-3">Aadhaar number</dt>
                <dd className="font-mono text-ink">{maskedAadhaar(check.last4)}</dd>
              </div>
            )}
            <div className="flex justify-between gap-3">
              <dt className="text-xs text-ink-3">Checked by</dt>
              <dd className="text-right text-ink">{check.verifiedBy}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-xs text-ink-3">When</dt>
              <dd className="text-right text-ink" title={fmtDateTime(check.verifiedAt)}>
                {fmtDateTime(check.verifiedAt)} <span className="text-ink-3">· {fmtRelative(check.verifiedAt)}</span>
              </dd>
            </div>
            {check.note && <p className="whitespace-pre-wrap break-words rounded-lg bg-canvas/40 px-3 py-2 text-ink-2">{check.note}</p>}
          </dl>
          <div className="mt-4 flex flex-wrap justify-end gap-2">
            {copy && (
              <button className="btn-ghost px-3 py-1.5" onClick={() => onViewCopy(copy)}>
                <Eye size={14} /> View copy
              </button>
            )}
            {confirmRemove ? (
              <>
                <span className="self-center text-xs text-ink-2">Remove this check?</span>
                <button className="btn-ghost px-3 py-1.5" onClick={() => setConfirmRemove(false)}>Keep</button>
                <button className="btn-danger px-3 py-1.5" disabled={remove.isPending} onClick={() => remove.mutate()}>
                  {remove.isPending ? <Spinner size={14} /> : <Trash2 size={14} />} Remove
                </button>
              </>
            ) : (
              <>
                <button className="btn-ghost px-3 py-1.5 hover:text-bad" onClick={() => setConfirmRemove(true)}>
                  <Trash2 size={14} /> Remove
                </button>
                <button className="btn-secondary px-3 py-1.5" onClick={() => setEditing(true)}>
                  <PenSquare size={14} /> Update
                </button>
              </>
            )}
          </div>
        </div>
      )}

      <AadhaarVerifyModal
        open={editing}
        candidateId={candidate.id}
        name={candidate.fullName}
        current={check}
        hasCopy={!!copy}
        onClose={() => setEditing(false)}
        onSaved={() => {
          setEditing(false);
          onChanged();
        }}
      />
    </section>
  );
}

function AadhaarVerifyModal({ open, candidateId, name, current, hasCopy, onClose, onSaved }: {
  open: boolean;
  candidateId: number;
  name: string;
  current: AadhaarCheck | null;
  hasCopy: boolean;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [result, setResult] = useState<AadhaarResult>('VERIFIED');
  const [method, setMethod] = useState<AadhaarMethod>('ORIGINAL_SEEN');
  const [last4, setLast4] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setResult(current?.result ?? 'VERIFIED');
    setMethod(current?.method ?? (hasCopy ? 'COPY_CHECKED' : 'ORIGINAL_SEEN'));
    setLast4(current?.last4 ?? '');
    setNote(current?.note ?? '');
    setError(null);
  }, [open, current, hasCopy]);

  const save = useMutation({
    mutationFn: () =>
      api.put(`/candidates/${candidateId}/aadhaar-verification`, {
        result,
        method,
        last4: last4.trim() || null,
        note: note.trim() || null,
      }),
    onSuccess: () => {
      toast('success', result === 'VERIFIED' ? 'Aadhaar marked as verified' : 'Aadhaar mismatch recorded');
      onSaved();
    },
    onError: (e) => toast('error', e.message),
  });

  const submit = () => {
    if (last4 && !/^\d{4}$/.test(last4)) return setError('Enter exactly the last 4 digits, or leave it empty');
    if (result === 'MISMATCH' && !note.trim()) return setError('Write what did not match');
    setError(null);
    save.mutate();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Verify Aadhaar"
      description={name}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" onClick={submit} disabled={save.isPending}>
            {save.isPending ? <Spinner size={14} /> : <ShieldCheck size={15} />} Save
          </button>
        </>
      }
    >
      <div className="space-y-5">
        <fieldset>
          <legend className="label">Result</legend>
          <div className="grid grid-cols-2 gap-2">
            {(
              [
                ['VERIFIED', 'Verified', 'Name, photo and details match', ShieldCheck, 'border-good/60 bg-good/10 text-[#6fd99a]'],
                ['MISMATCH', 'Mismatch found', 'Something does not match', ShieldAlert, 'border-bad/60 bg-bad/10 text-[#f29a9a]'],
              ] as const
            ).map(([value, label, hint, Icon, on]) => (
              <label
                key={value}
                className={clsx('flex cursor-pointer items-start gap-2.5 rounded-xl border px-3 py-2.5 transition', result === value ? on : 'border-line text-ink-2 hover:border-ink-3')}
              >
                <input type="radio" name="aadhaar-result" className="sr-only" checked={result === value} onChange={() => setResult(value)} />
                <Icon size={18} className="mt-0.5 shrink-0" />
                <span>
                  <span className="block text-sm font-semibold">{label}</span>
                  <span className="block text-xs text-ink-3">{hint}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend className="label">How was it checked?</legend>
          <div className="space-y-1.5">
            {METHODS.map((m) => (
              <label key={m.value} className={clsx('flex cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-2 transition', method === m.value ? 'border-info/60 bg-info/10' : 'border-line hover:border-ink-3')}>
                <input type="radio" name="aadhaar-method" className="mt-1 accent-[#3987e5]" checked={method === m.value} onChange={() => setMethod(m.value)} />
                <span>
                  <span className="block text-sm text-ink">{m.label}</span>
                  <span className="block text-xs text-ink-3">{m.hint}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <label className="block">
          <span className="label">Last 4 digits of the Aadhaar number (optional)</span>
          <div className="flex items-center gap-2">
            <span className="font-mono text-sm text-ink-3">XXXX XXXX</span>
            <input
              className="input w-24 font-mono tracking-widest"
              inputMode="numeric"
              maxLength={4}
              placeholder="1234"
              value={last4}
              onChange={(e) => setLast4(e.target.value.replace(/\D/g, '').slice(0, 4))}
            />
          </div>
          <span className="mt-1 block text-xs text-ink-3">Only the last 4 digits are kept, never the full number.</span>
        </label>

        <label className="block">
          <span className="label">{result === 'MISMATCH' ? 'What did not match? *' : 'Note (optional)'}</span>
          <textarea
            className="input min-h-[72px]"
            maxLength={500}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder={result === 'MISMATCH' ? 'e.g. Date of birth on the card is 12-03-1997, the application says 1998' : 'e.g. Name and photo match, address updated'}
          />
        </label>

        {error && <p className="text-sm text-bad">{error}</p>}
      </div>
    </Modal>
  );
}
