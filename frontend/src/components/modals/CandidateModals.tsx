import { Fragment, useEffect, useState } from 'react';
import clsx from 'clsx';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../lib/api';
import { CANDIDATE_KEYS, useDepartments } from '../../lib/queries';
import { APPLICATION_STATUSES, RATING_BANDS, VERIFICATION_STATUSES, ratingBand } from '../../lib/format';
import { useToast } from '../../lib/toast';
import type { ApplicationStatus, CandidateDetail, VerificationStatus } from '../../lib/types';
import { Modal } from '../ui/Modal';
import { Field } from '../ui/Misc';
import { ErrorState, LoadingBlock, Spinner } from '../ui/States';

export function useInvalidateCandidates() {
  const qc = useQueryClient();
  return () => CANDIDATE_KEYS.forEach((k) => qc.invalidateQueries({ queryKey: k }));
}

// ------------------------------------------------------------------ edit

type Form = Record<string, string>;
type UploadType = 'RESUME' | 'AADHAAR';
const UPLOADS: Record<UploadType, string> = { RESUME: 'Resume', AADHAAR: 'Aadhaar card' };
const TEXT_FIELDS: { key: keyof CandidateDetail; label: string; type?: string; max?: number; span?: boolean; heading?: string; list?: string }[] = [
  { key: 'fullName', label: 'Full name *', max: 150 },
  { key: 'dateOfBirth', label: 'Date of birth', type: 'date' },
  { key: 'mobile', label: 'Mobile number', type: 'tel', max: 20 },
  { key: 'email', label: 'Email', type: 'email', max: 200 },
  { key: 'address', label: 'Address', max: 1000, span: true },
  { key: 'qualification', label: 'Qualification', max: 200 },
  { key: 'institution', label: 'Institution', max: 200 },
  { key: 'passingYear', label: 'Passing year', type: 'number' },
  { key: 'previousCompany', label: 'Previous company', max: 200 },
  { key: 'experienceYears', label: 'Total experience (years)', type: 'number' },
  { key: 'previousDesignation', label: 'Previous designation', max: 150 },
  { key: 'expectedSalary', label: 'Expected salary', max: 100 },
  { key: 'skills', label: 'Skills', max: 2000, span: true },
  { key: 'referenceName', label: 'Reference name', max: 150, heading: 'Professional reference' },
  { key: 'referenceRelation', label: 'Reference relationship / designation', max: 150 },
  { key: 'referenceContact', label: 'Reference contact', max: 200 },
  { key: 'familyRefName', label: 'Family member name', max: 150, heading: 'Family reference' },
  { key: 'familyRefRelation', label: 'Relationship', max: 100, list: 'family-relations' },
  { key: 'familyRefContact', label: 'Family member contact number', type: 'tel', max: 20 },
];

/** Edits a candidate, or adds one by hand when candidateId is 'new'. */
export function EditCandidateModal({ candidateId, onClose }: { candidateId: number | 'new' | null; onClose: () => void }) {
  const toast = useToast();
  const invalidate = useInvalidateCandidates();
  const { data: departments } = useDepartments();
  const isNew = candidateId === 'new';
  const detail = useQuery({
    queryKey: ['candidate', candidateId],
    queryFn: () => api.get<CandidateDetail>(`/candidates/${candidateId}`),
    enabled: typeof candidateId === 'number',
  });
  const [form, setForm] = useState<Form>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  // documents attached while adding a candidate; uploaded right after the candidate is created
  const [files, setFiles] = useState<Partial<Record<UploadType, File>>>({});
  const [consent, setConsent] = useState(false);

  useEffect(() => {
    if (!isNew) return;
    setForm({});
    setErrors({});
    setFiles({});
    setConsent(false);
  }, [isNew]);

  useEffect(() => {
    if (!detail.data) return;
    const f: Form = {};
    TEXT_FIELDS.forEach(({ key }) => (f[key] = detail.data[key] == null ? '' : String(detail.data[key])));
    f.departmentId = detail.data.departmentId == null ? '' : String(detail.data.departmentId);
    f.position = detail.data.position ?? '';
    setForm(f);
    setErrors({});
  }, [detail.data]);

  const save = useMutation({
    mutationFn: () => {
      const body = {
        ...Object.fromEntries(TEXT_FIELDS.map(({ key }) => [key, form[key]?.trim() ? form[key].trim() : null])),
        passingYear: form.passingYear ? Number(form.passingYear) : null,
        experienceYears: form.experienceYears ? Number(form.experienceYears) : null,
        departmentId: form.departmentId ? Number(form.departmentId) : null,
        position: form.position?.trim() ? form.position.trim() : null,
        version: isNew ? 0 : detail.data!.version,
      };
      if (!isNew) return api.put<CandidateDetail>(`/candidates/${candidateId}`, body).then((c) => ({ c, failed: [] as string[] }));
      return api.post<CandidateDetail>('/candidates', body).then(async (c) => {
        // The candidate exists now, so a failed upload is reported but does not undo the add.
        const failed: string[] = [];
        for (const [type, file] of Object.entries(files) as [UploadType, File][]) {
          const fd = new FormData();
          fd.append('file', file);
          try {
            await api.upload(`/candidates/${c.id}/documents?type=${type}${type === 'AADHAAR' ? `&consent=${consent}` : ''}`, fd);
          } catch (e) {
            failed.push(`${UPLOADS[type]}: ${(e as Error).message}`);
          }
        }
        return { c, failed };
      });
    },
    onSuccess: ({ c, failed }) => {
      if (failed.length) toast('error', `Candidate ${c.candidateCode} added, but upload failed - ${failed.join('; ')}. Add it from the profile.`);
      else toast('success', isNew ? `Candidate ${c.candidateCode} added` : 'Candidate details updated');
      invalidate();
      onClose();
    },
    onError: (e) => {
      if (e instanceof ApiError && e.fieldErrors) setErrors(e.fieldErrors);
      toast('error', e.message);
    },
  });

  const validate = () => {
    const errs: Record<string, string> = {};
    if (!form.fullName?.trim()) errs.fullName = 'Name is required';
    if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(form.email)) errs.email = 'Enter a valid email';
    if (isNew && !form.mobile?.trim()) errs.mobile = 'Mobile number is required';
    if (files.AADHAAR && !consent) errs.aadhaarConsent = 'Confirm the candidate consented to share their Aadhaar';
    else if (form.mobile && form.mobile.replace(/\D/g, '').length < 10) errs.mobile = 'Enter at least 10 digits';
    if (form.familyRefContact && form.familyRefContact.replace(/\D/g, '').length < 10) errs.familyRefContact = 'Enter at least 10 digits';
    if (form.passingYear &&(Number(form.passingYear) < 1950 || Number(form.passingYear) > new Date().getFullYear() + 1)) errs.passingYear = 'Enter a valid year';
    if (form.experienceYears && (Number(form.experienceYears) < 0 || Number(form.experienceYears) > 60)) errs.experienceYears = '0–60 years';
    if (form.dateOfBirth && new Date(form.dateOfBirth) >= new Date()) errs.dateOfBirth = 'Must be in the past';
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  return (
    <Modal
      open={candidateId !== null}
      onClose={onClose}
      size="lg"
      title={isNew ? 'Add candidate' : 'Edit candidate details'}
      description={isNew ? 'For applicants who did not use the Google Form (walk-in, phone, referral)'
        : detail.data ? `${detail.data.candidateCode} · changes are recorded in the audit log` : undefined}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" disabled={(!isNew && !detail.data) || save.isPending} onClick={() => validate() && save.mutate()}>
            {save.isPending && <Spinner size={14} />} {isNew ? 'Add candidate' : 'Save changes'}
          </button>
        </>
      }
    >
      {detail.isLoading && <LoadingBlock />}
      {detail.error && <ErrorState error={detail.error} onRetry={() => detail.refetch()} />}
      {(isNew || detail.data) && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <datalist id="family-relations">
            {['Father', 'Mother', 'Spouse', 'Brother', 'Sister', 'Son', 'Daughter', 'Guardian'].map((r) => <option key={r} value={r} />)}
          </datalist>
          {TEXT_FIELDS.map(({ key, label, type, max, span, heading, list }) => (
            <Fragment key={key}>
            {heading && <h3 className="mt-2 border-t border-line pt-4 text-sm font-semibold text-ink sm:col-span-2">{heading}</h3>}
            <Field label={label} error={errors[key]} className={span ? 'sm:col-span-2' : undefined}>
              {key === 'address' || key === 'skills' ? (
                <textarea className="input min-h-[72px]" maxLength={max} value={form[key] ?? ''} onChange={(e) => setForm({ ...form, [key]: e.target.value })} />
              ) : (
                <input
                  className="input"
                  type={type ?? 'text'}
                  maxLength={max}
                  step={key === 'experienceYears' ? '0.1' : undefined}
                  list={list}
                  max={type === 'date' ? new Date().toISOString().slice(0, 10) : undefined}
                  value={form[key] ?? ''}
                  onChange={(e) => setForm({ ...form, [key]: e.target.value })}
                />
              )}
            </Field>
            </Fragment>
          ))}
          <Field label="Department" hint={detail.data?.departmentRaw && !detail.data.departmentId ? `Submitted as "${detail.data.departmentRaw}"` : undefined}>
            <select className="input" value={form.departmentId ?? ''} onChange={(e) => setForm({ ...form, departmentId: e.target.value, position: '' })}>
              <option value="">Unassigned</option>
              {departments?.map((d) => (
                <option key={d.id} value={d.id} disabled={!d.active && String(d.id) !== form.departmentId}>
                  {d.name}{d.active ? '' : ' (inactive)'}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Sub-work">
            {(() => {
              const options = departments?.find((d) => String(d.id) === form.departmentId)?.positions ?? [];
              // keep a submitted value that is not (or no longer) in the department's list
              const all = form.position && !options.includes(form.position) ? [form.position, ...options] : options;
              return (
                <select className="input" value={form.position ?? ''} disabled={all.length === 0} onChange={(e) => setForm({ ...form, position: e.target.value })}>
                  <option value="">{all.length === 0 ? 'No sub-work set for this department' : 'Not specified'}</option>
                  {all.map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              );
            })()}
          </Field>
          {isNew && (
            <>
              <h3 className="mt-2 border-t border-line pt-4 text-sm font-semibold text-ink sm:col-span-2">Documents</h3>
              {(Object.keys(UPLOADS) as UploadType[]).map((type) => (
                <Field key={type} label={UPLOADS[type]} hint={files[type] ? `${files[type]!.name} · ${(files[type]!.size / 1024 / 1024).toFixed(1)} MB` : 'PDF, JPG or PNG up to 10 MB'}>
                  <input
                    type="file"
                    accept=".pdf,.jpg,.jpeg,.png"
                    className="input file:mr-3 file:rounded-lg file:border-0 file:bg-raised file:px-3 file:py-1 file:text-sm file:text-ink"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f && f.size > 10 * 1024 * 1024) {
                        toast('error', 'File exceeds the 10 MB limit');
                        e.target.value = '';
                        return;
                      }
                      setFiles((prev) => {
                        const next = { ...prev };
                        if (f) next[type] = f;
                        else delete next[type];
                        return next;
                      });
                    }}
                  />
                </Field>
              ))}
              {files.AADHAAR && (
                <div className="sm:col-span-2">
                  <label className="flex items-start gap-2 text-sm">
                    <input type="checkbox" className="mt-0.5 h-4 w-4 accent-[#22a559]" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
                    <span>The candidate has consented to share a copy of their Aadhaar card for verification.</span>
                  </label>
                  {errors.aadhaarConsent && <span className="mt-1 block text-xs text-bad">{errors.aadhaarConsent}</span>}
                </div>
              )}
            </>
          )}
        </div>
      )}
    </Modal>
  );
}

// ------------------------------------------------------------------ status

export interface StatusTarget {
  id: number;
  name: string;
  code: string;
  applicationStatus: ApplicationStatus;
  verificationStatus: VerificationStatus;
}

export function StatusModal({ target, onClose }: { target: StatusTarget | null; onClose: () => void }) {
  const toast = useToast();
  const invalidate = useInvalidateCandidates();
  const [app, setApp] = useState<ApplicationStatus>('NEW');
  const [ver, setVer] = useState<VerificationStatus>('PENDING');
  const [note, setNote] = useState('');

  useEffect(() => {
    if (target) {
      setApp(target.applicationStatus);
      setVer(target.verificationStatus);
      setNote('');
    }
  }, [target]);

  const unchanged = !!target && app === target.applicationStatus && ver === target.verificationStatus;
  const save = useMutation({
    mutationFn: () =>
      api.patch(`/candidates/${target!.id}/status`, {
        applicationStatus: app !== target!.applicationStatus ? app : null,
        verificationStatus: ver !== target!.verificationStatus ? ver : null,
        note: note.trim() || null,
      }),
    onSuccess: () => {
      toast('success', `Status updated for ${target!.name}`);
      invalidate();
      onClose();
    },
    onError: (e) => toast('error', e.message),
  });

  return (
    <Modal
      open={!!target}
      onClose={onClose}
      title="Update status"
      description={target ? `${target.name} · ${target.code}` : undefined}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" disabled={unchanged || save.isPending} onClick={() => save.mutate()}>
            {save.isPending && <Spinner size={14} />} Update status
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <Field label="Application status">
          <select className="input" value={app} onChange={(e) => setApp(e.target.value as ApplicationStatus)}>
            {APPLICATION_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </Field>
        <Field label="Document verification">
          <select className="input" value={ver} onChange={(e) => setVer(e.target.value as VerificationStatus)}>
            {VERIFICATION_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
        </Field>
        <Field label="HR remark (optional)" hint="Saved to the candidate's HR remarks and the status history.">
          <textarea className="input min-h-[84px]" maxLength={2000} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Reason for the change…" />
        </Field>
      </div>
    </Modal>
  );
}

// ------------------------------------------------------------------ profile match rating

export interface RatingTarget {
  id: number;
  name: string;
  code: string;
  rating: number | null;
  remark: string | null;
}

export function RatingModal({ target, onClose }: { target: RatingTarget | null; onClose: () => void }) {
  const toast = useToast();
  const invalidate = useInvalidateCandidates();
  const [rating, setRating] = useState<number | null>(null);
  const [remark, setRemark] = useState('');

  useEffect(() => {
    if (target) {
      setRating(target.rating);
      setRemark(target.remark ?? '');
    }
  }, [target]);

  const unchanged = !!target && rating === target.rating && remark.trim() === (target.remark ?? '');
  const save = useMutation({
    mutationFn: () => api.patch(`/candidates/${target!.id}/rating`, { rating, remark: remark.trim() || null }),
    onSuccess: () => {
      toast('success', `Profile match rating ${rating}/10 saved for ${target!.name}`);
      invalidate();
      onClose();
    },
    onError: (e) => toast('error', e.message),
  });

  const band = rating == null ? null : ratingBand(rating);
  return (
    <Modal
      open={!!target}
      onClose={onClose}
      title={target?.rating == null ? 'Rate profile match' : 'Update profile match rating'}
      description={target ? `${target.name} · ${target.code}` : undefined}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn-primary" disabled={rating == null || unchanged || save.isPending} onClick={() => save.mutate()}>
            {save.isPending && <Spinner size={14} />} Save rating
          </button>
        </>
      }
    >
      <div className="space-y-4">
        <div>
          <span className="label">Profile Match Rating (1–10)</span>
          <div className="grid grid-cols-5 gap-2 sm:grid-cols-10" role="radiogroup" aria-label="Profile match rating">
            {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
              <button
                key={n}
                type="button"
                role="radio"
                aria-checked={rating === n}
                title={ratingBand(n).label}
                onClick={() => setRating(n)}
                className={clsx(
                  'rounded-lg border py-2 text-sm font-semibold tabular-nums transition-colors',
                  rating === n ? 'border-info bg-info text-white' : 'border-line bg-raised text-ink-2 hover:border-info/60 hover:text-ink',
                )}
              >
                {n}
              </button>
            ))}
          </div>
          <p className="mt-2 text-sm text-ink-2" aria-live="polite">
            {band ? <><strong className="text-ink">{rating}/10</strong> · {band.label}</> : 'Not rated yet. Choose a rating after reviewing the application.'}
          </p>
        </div>
        <ul className="grid grid-cols-1 gap-1 rounded-xl border border-line bg-raised/50 p-3 text-xs text-ink-3 sm:grid-cols-2">
          {RATING_BANDS.map((b) => (
            <li key={b.label}><span className="tabular-nums text-ink-2">{b.min}–{b.max}</span> {b.label}</li>
          ))}
          <li className="sm:col-span-2">These are guidelines; HR makes the final decision.</li>
        </ul>
        <Field label="HR remarks (optional)" hint="Explain the rating. Also saved to the candidate's HR remarks history.">
          <textarea className="input min-h-[84px]" maxLength={2000} value={remark} onChange={(e) => setRemark(e.target.value)} placeholder="Why this rating…" />
        </Field>
      </div>
    </Modal>
  );
}

// ------------------------------------------------------------------ archive / restore

export interface ArchiveTarget {
  id: number;
  name: string;
  code: string;
  archived: boolean;
}

/** Confirms archiving (hide from the active list) or restoring a candidate. Nothing is ever deleted. */
export function ArchiveModal({ target, onClose }: { target: ArchiveTarget | null; onClose: () => void }) {
  const toast = useToast();
  const invalidate = useInvalidateCandidates();
  const [reason, setReason] = useState('');
  useEffect(() => setReason(''), [target?.id, target?.archived]);
  const archiving = !!target && !target.archived;

  const save = useMutation({
    mutationFn: () => api.patch(`/candidates/${target!.id}/archive`, { archived: archiving, reason: reason.trim() || null }),
    onSuccess: () => {
      toast('success', archiving ? `${target!.name} archived` : `${target!.name} restored to the active list`);
      invalidate();
      onClose();
    },
    onError: (e) => toast('error', e.message),
  });

  return (
    <Modal
      open={!!target}
      onClose={onClose}
      title={archiving ? 'Archive candidate?' : 'Restore candidate?'}
      description={target ? `${target.name} · ${target.code}` : undefined}
      footer={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancel</button>
          <button className={archiving ? 'btn-danger' : 'btn-primary'} disabled={save.isPending} onClick={() => save.mutate()}>
            {save.isPending && <Spinner size={14} />} {archiving ? 'Archive' : 'Restore'}
          </button>
        </>
      }
    >
      <div className="space-y-4 text-sm text-ink-2">
        <p>
          {archiving
            ? 'The candidate is hidden from the active candidate list. Their record, documents, interviews and history are kept, and you can restore them at any time.'
            : 'The candidate will appear in the active candidate list again.'}
        </p>
        {archiving && (
          <Field label="Reason (optional)" hint="Recorded in the audit log.">
            <input className="input" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Position filled" />
          </Field>
        )}
      </div>
    </Modal>
  );
}
