import { useRef, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  Archive,
  ArchiveRestore,
  ArrowLeft,
  Briefcase,
  CalendarPlus,
  ClipboardList,
  Download,
  Eye,
  FileText,
  GraduationCap,
  History,
  Mail,
  MessageCircle,
  MessagesSquare,
  Phone,
  IdCard,
  Lock,
  MessageSquarePlus,
  PenSquare,
  Star,
  Trash2,
  Upload,
  User,
  UserCheck,
  Users,
} from 'lucide-react';
import { api } from '../lib/api';
import { useToast } from '../lib/toast';
import { INTERVIEW_MODES, RECOMMENDATIONS, fmtBytes, fmtDate, fmtDateTime, fmtExperience, fmtRelative, labelOf } from '../lib/format';
import type { CandidateDetail, DocType, DocumentInfo, Interview } from '../lib/types';
import { Avatar } from '../components/ui/Misc';
import { ApplicationPill, DemoTag, InterviewPill, RatingPill, VerificationPill } from '../components/ui/Pill';
import { EmptyState, ErrorState, LoadingBlock, Spinner } from '../components/ui/States';
import { DocumentViewer, type ViewTarget } from '../components/modals/DocumentViewer';
import { ArchiveModal, EditCandidateModal, RatingModal, StatusModal, useInvalidateCandidates, type RatingTarget, type StatusTarget } from '../components/modals/CandidateModals';
import { InterviewStatusModal, ScheduleInterviewModal } from '../components/modals/InterviewModals';
import { CommunicationDialog, CommunicationHistory, useCallAction, type Tab } from '../components/communication/Communication';
import { emailProblem, phoneProblem } from '../lib/contact';

type Row = [string, ReactNode | null | undefined];

/** Renders a section only with the fields that actually have values; hides it entirely when none do. */
function Section({ title, icon, rows, children }: { title: string; icon: ReactNode; rows?: Row[]; children?: ReactNode }) {
  const present = (rows ?? []).filter(([, v]) => v !== null && v !== undefined && v !== '');
  if (rows && present.length === 0 && !children) return null;
  return (
    <section className="panel p-5">
      <h2 className="mb-4 flex items-center gap-2 text-[15px] font-semibold">
        <span className="text-ink-3">{icon}</span> {title}
      </h2>
      {present.length > 0 && (
        <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
          {present.map(([label, value]) => (
            <div key={label} className="min-w-0">
              <dt className="text-xs text-ink-3">{label}</dt>
              <dd className="mt-0.5 break-words text-sm text-ink">{value}</dd>
            </div>
          ))}
        </dl>
      )}
      {children}
    </section>
  );
}

/** One reference block; shows "Not provided" instead of inventing values when the candidate left it blank. */
function ReferenceCard({ title, icon, rows }: { title: string; icon: ReactNode; rows: Row[] }) {
  const present = rows.filter(([, v]) => v !== null && v !== undefined && v !== '');
  return (
    <div className="rounded-xl border border-line/70 bg-card p-4">
      <h3 className="mb-3 flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-ink-3">{icon} {title}</h3>
      {present.length === 0 ? (
        <p className="text-sm text-ink-3">Not provided</p>
      ) : (
        <dl className="space-y-2.5">
          {present.map(([label, value]) => (
            <div key={label}>
              <dt className="text-xs text-ink-3">{label}</dt>
              <dd className="mt-0.5 break-words text-sm text-ink">{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}

const DOC_LABEL: Record<DocType, string> = { RESUME: 'Resume', AADHAAR: 'Aadhaar', CERTIFICATE: 'Educational certificate', OTHER: 'Other document' };
const DOC_ORDER: DocType[] = ['RESUME', 'AADHAAR', 'CERTIFICATE', 'OTHER'];

export default function CandidateProfilePage() {
  const { id } = useParams();
  const candidateId = Number(id);
  const toast = useToast();
  const invalidate = useInvalidateCandidates();
  const query = useQuery({
    queryKey: ['candidate', candidateId],
    queryFn: () => api.get<CandidateDetail>(`/candidates/${candidateId}`),
    enabled: Number.isFinite(candidateId),
  });
  const [editing, setEditing] = useState(false);
  const [statusTarget, setStatusTarget] = useState<StatusTarget | null>(null);
  const [ratingTarget, setRatingTarget] = useState<RatingTarget | null>(null);
  const [archiving, setArchiving] = useState(false);
  const [viewing, setViewing] = useState<ViewTarget | null>(null);
  const [scheduling, setScheduling] = useState(false);
  const [interviewEdit, setInterviewEdit] = useState<Interview | null>(null);
  const [contact, setContact] = useState<Tab | null>(null);
  const call = useCallAction();
  const [remark, setRemark] = useState('');
  const [downloading, setDownloading] = useState<number | null>(null);
  const [docType, setDocType] = useState<DocType>('RESUME');
  const [consent, setConsent] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const addRemark = useMutation({
    mutationFn: () => api.post(`/candidates/${candidateId}/remarks`, { remark: remark.trim() }),
    onSuccess: () => {
      setRemark('');
      toast('success', 'Remark added');
      invalidate();
    },
    onError: (e) => toast('error', e.message),
  });

  const upload = useMutation({
    mutationFn: (file: File) => {
      const fd = new FormData();
      fd.append('file', file);
      return api.upload(`/candidates/${candidateId}/documents?type=${docType}${docType === 'AADHAAR' ? `&consent=${consent}` : ''}`, fd);
    },
    onSuccess: () => {
      toast('success', `${DOC_LABEL[docType]} uploaded`);
      setConsent(false);
      invalidate();
    },
    onError: (e) => toast('error', e.message),
  });

  const remove = useMutation({
    mutationFn: (documentId: number) => api.del(`/documents/${documentId}`),
    onSuccess: () => {
      toast('success', 'Document deleted');
      invalidate();
    },
    onError: (e) => toast('error', e.message),
  });

  const download = async (d: DocumentInfo) => {
    setDownloading(d.id);
    try {
      await api.download(`/documents/${d.id}/download`, {}, d.fileName ?? 'document');
    } catch (e) {
      toast('error', (e as Error).message);
    } finally {
      setDownloading(null);
    }
  };

  if (query.isLoading) return <LoadingBlock label="Loading candidate…" />;
  if (query.error || !query.data) return <div className="panel"><ErrorState error={query.error} onRetry={() => query.refetch()} /></div>;
  const c = query.data;
  const resume = c.documents.find((d) => d.docType === 'RESUME');

  return (
    <div className="space-y-6">
      <Link to="/candidates" className="inline-flex items-center gap-1.5 text-sm text-ink-3 hover:text-ink">
        <ArrowLeft size={15} /> Back to candidates
      </Link>

      <section className="panel overflow-hidden">
        <div className="h-20 bg-gradient-to-r from-info-card via-[#2b3a6b] to-panel" />
        <div className="flex flex-col gap-4 px-5 pb-5 md:flex-row md:items-end md:justify-between">
          <div className="flex items-end gap-4">
            <span className="-mt-10 shrink-0 self-start rounded-full ring-4 ring-panel"><Avatar name={c.fullName} size="xl" /></span>
            <div className="pb-1">
              <h2 className="flex flex-wrap items-center gap-2 text-xl font-semibold">{c.fullName} {c.source === 'DEMO' && <DemoTag />}</h2>
              <p className="text-sm text-ink-3">
                <span className="font-mono">{c.candidateCode}</span> · {c.departmentName ?? (c.departmentRaw ? `${c.departmentRaw} (not configured)` : 'Unassigned')} · Applied {fmtDate(c.applicationDate)}
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                <ApplicationPill status={c.applicationStatus} />
                <InterviewPill status={c.interviewStatus} />
                <VerificationPill status={c.verificationStatus} />
                <RatingPill rating={c.profileRating} />
              </div>
              {c.profileRating != null && (
                <p className="mt-1.5 max-w-xl text-xs text-ink-3">
                  Rated by {c.ratedBy ?? '—'}{c.ratedAt && ` · ${fmtRelative(c.ratedAt)}`}
                  {c.ratingRemark && <span className="block text-ink-2">“{c.ratingRemark}”</span>}
                </p>
              )}
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            <div className="flex rounded-xl border border-line bg-raised p-0.5" role="group" aria-label="Contact candidate">
              <button className="btn-ghost px-3 py-1.5" disabled={!!phoneProblem(c.mobile) || call.starting === c.id} title={phoneProblem(c.mobile) ?? `Call ${c.mobile}`} onClick={() => call.start(c.id)}>
                {call.starting === c.id ? <Spinner size={15} /> : <Phone size={15} />} Call
              </button>
              <button className="btn-ghost px-3 py-1.5 enabled:hover:text-[#25d366]" disabled={!!phoneProblem(c.mobile)} title={phoneProblem(c.mobile) ?? 'WhatsApp interview invitation'} onClick={() => setContact('whatsapp')}>
                <MessageCircle size={15} /> WhatsApp
              </button>
              <button className="btn-ghost px-3 py-1.5" disabled={!!emailProblem(c.email)} title={emailProblem(c.email) ?? `Email ${c.email}`} onClick={() => setContact('email')}>
                <Mail size={15} /> Email
              </button>
            </div>
            <button className="btn-secondary" onClick={() => setEditing(true)}><PenSquare size={15} /> Edit</button>
            <button
              className="btn-secondary"
              onClick={() => setStatusTarget({ id: c.id, name: c.fullName, code: c.candidateCode, applicationStatus: c.applicationStatus, verificationStatus: c.verificationStatus })}
            >
              <UserCheck size={15} /> Update status
            </button>
            <button
              className="btn-secondary"
              onClick={() => setRatingTarget({ id: c.id, name: c.fullName, code: c.candidateCode, rating: c.profileRating, remark: c.ratingRemark })}
            >
              <Star size={15} /> {c.profileRating == null ? 'Rate profile' : 'Update rating'}
            </button>
            <button className="btn-secondary" onClick={() => setArchiving(true)}>
              {c.archived ? <ArchiveRestore size={15} /> : <Archive size={15} />} {c.archived ? 'Restore' : 'Archive'}
            </button>
            <button className="btn-secondary" disabled={c.applicationStatus === 'REJECTED'} onClick={() => setScheduling(true)} title={c.applicationStatus === 'REJECTED' ? 'Rejected candidates cannot be scheduled' : undefined}>
              <CalendarPlus size={15} /> Schedule interview
            </button>
            <button className="btn-primary" disabled={!resume || downloading === resume?.id} onClick={() => resume && download(resume)} title={resume ? undefined : 'No resume on file'}>
              {downloading === resume?.id ? <Spinner size={15} /> : <Download size={15} />} Resume
            </button>
          </div>
        </div>
      </section>

      {c.archived && (
        <div className="flex items-start gap-3 rounded-xl border border-line bg-raised px-4 py-3 text-sm text-ink-2">
          <Archive size={18} className="mt-0.5 shrink-0" />
          <div>
            <strong className="text-ink">Archived</strong>
            {c.archivedAt && ` on ${fmtDate(c.archivedAt)}`}
            {c.archivedBy && ` by ${c.archivedBy}`}
            {c.archiveReason && ` - ${c.archiveReason}`}. Hidden from the active candidate list; nothing has been deleted.
          </div>
        </div>
      )}

      {c.dataIssues && (
        <div className="flex items-start gap-3 rounded-xl border border-warn/40 bg-warn/10 px-4 py-3 text-sm text-[#f3c08a]">
          <AlertTriangle size={18} className="mt-0.5 shrink-0" />
          <div>
            <strong>Data needs review.</strong> Some submitted values were missing or invalid and were not imported: {c.dataIssues}
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div className="space-y-6 xl:col-span-2">
          <Section
            title="A. Personal Details"
            icon={<User size={16} />}
            rows={[
              ['Full name', c.fullName],
              ['Date of birth', c.dateOfBirth && fmtDate(c.dateOfBirth)],
              ['Mobile number', c.mobile && <a className="hover:underline" href={`tel:${c.mobile}`}>{c.mobile}</a>],
              ['Email', c.email && <a className="hover:underline" href={`mailto:${c.email}`}>{c.email}</a>],
              ['Address', c.address],
            ]}
          />
          <Section
            title="B. Educational Details"
            icon={<GraduationCap size={16} />}
            rows={[
              ['Qualification', c.qualification],
              ['Institution', c.institution],
              ['Passing year', c.passingYear],
            ]}
          />
          <Section
            title="C. Employment Details"
            icon={<Briefcase size={16} />}
            rows={[
              ['Previous company', c.previousCompany],
              ['Total experience', c.experienceYears !== null ? fmtExperience(c.experienceYears) : null],
              ['Previous designation', c.previousDesignation],
              ['Expected salary', c.expectedSalary],
              ['Skills', c.skills && (
                <span className="flex flex-wrap gap-1.5">
                  {c.skills.split(/[,;]+/).map((s) => s.trim()).filter(Boolean).map((s) => (
                    <span key={s} className="rounded-md bg-raised px-2 py-0.5 text-xs text-ink-2">{s}</span>
                  ))}
                </span>
              )],
            ]}
          />
          <Section
            title="D. Application Details"
            icon={<ClipboardList size={16} />}
            rows={[
              ['Applied department', c.departmentName ?? c.departmentRaw],
              ['Application date', fmtDateTime(c.appliedAt)],
              ['Current status', <ApplicationPill status={c.applicationStatus} />],
              ['Source', c.source === 'GOOGLE_SHEETS' ? 'Google Form' : c.source === 'DEMO' ? 'Demo data (not a real applicant)' : 'Manual'],
            ]}
          >
            <h3 className="mb-2 mt-5 text-sm font-medium text-ink-2">Interview history</h3>
            {c.interviews.length === 0 ? (
              <p className="text-sm text-ink-3">No interviews yet.</p>
            ) : (
              <ol className="space-y-2">
                {c.interviews.map((i) => {
                  const active = i.status === 'SCHEDULED' || i.status === 'RESCHEDULED';
                  return (
                    <li key={i.id} className="flex flex-wrap items-center gap-3 rounded-xl border border-line/70 bg-card px-3 py-2.5">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium">{i.roundName} <span className="font-normal text-ink-3">with {i.interviewer}</span></p>
                        <p className="text-xs text-ink-3">{fmtDateTime(i.scheduledAt)} · {i.durationMinutes} min · {labelOf(INTERVIEW_MODES, i.mode)}{i.location ? ` · ${i.location}` : ''}</p>
                        {i.remarks && <p className="mt-1 text-xs text-ink-2">“{i.remarks}”</p>}
                        {i.feedback && (
                          <p className="mt-1.5 rounded-lg bg-raised px-2.5 py-1.5 text-xs text-ink-2">
                            <strong className="text-ink">Interviewer feedback · {labelOf(RECOMMENDATIONS, i.recommendation)}:</strong> {i.feedback}
                            <span className="block text-[11px] text-ink-3">{i.feedbackBy} · {i.feedbackAt && fmtDateTime(i.feedbackAt)}</span>
                          </p>
                        )}
                      </div>
                      <InterviewPill status={i.status} />
                      {active && <button className="btn-ghost px-2 py-1 text-xs" onClick={() => setInterviewEdit(i)}>Update</button>}
                    </li>
                  );
                })}
              </ol>
            )}
          </Section>
          <Section
            title="E. References"
            icon={<UserCheck size={16} />}
          >
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <ReferenceCard
                title="Professional reference"
                icon={<Briefcase size={14} />}
                rows={[
                  ['Name', c.referenceName],
                  ['Relationship / designation', c.referenceRelation],
                  ['Contact details', c.referenceContact],
                ]}
              />
              <ReferenceCard
                title="Family reference"
                icon={<Users size={14} />}
                rows={[
                  ['Name', c.familyRefName],
                  ['Relationship', c.familyRefRelation],
                  ['Contact number', c.familyRefContact && <a className="hover:underline" href={`tel:${c.familyRefContact}`}>{c.familyRefContact}</a>],
                ]}
              />
            </div>
          </Section>

          <section className="panel p-5">
            <h2 className="mb-1 flex items-center gap-2 text-[15px] font-semibold"><MessagesSquare size={16} className="text-ink-3" /> Communication History</h2>
            <p className="mb-4 text-xs text-ink-3">Calls, WhatsApp and email with this candidate. Records keep the exact message and template version used.</p>
            <CommunicationHistory candidateId={c.id} />
          </section>
        </div>

        <div className="space-y-6">
          <section className="panel p-5">
            <h2 className="mb-4 flex items-center gap-2 text-[15px] font-semibold"><FileText size={16} className="text-ink-3" /> F. Documents</h2>
            {c.documents.length === 0 ? (
              <p className="text-sm text-ink-3">No documents on file.</p>
            ) : (
              <div className="space-y-4">
                {DOC_ORDER.filter((t) => c.documents.some((d) => d.docType === t)).map((t) => (
                  <div key={t}>
                    <h3 className="mb-1.5 text-xs font-medium uppercase tracking-wide text-ink-3">{DOC_LABEL[t]}</h3>
                    <ul className="space-y-2">
                      {c.documents.filter((d) => d.docType === t).map((d) => {
                        const Icon = d.docType === 'AADHAAR' ? IdCard : FileText;
                        return (
                          <li key={d.id} className="flex items-center gap-3 rounded-xl border border-line/70 bg-card px-3 py-2.5">
                            <Icon size={18} className={d.docType === 'AADHAAR' ? 'shrink-0 text-warn' : 'shrink-0 text-ink-3'} />
                            <div className="min-w-0 flex-1">
                              <p className="truncate text-sm" title={d.fileName ?? undefined}>{d.fileName ?? DOC_LABEL[d.docType]}</p>
                              <p className="flex flex-wrap items-center gap-x-1.5 text-xs text-ink-3">
                                <span>{d.storage === 'DRIVE' ? 'Google Form upload' : fmtBytes(d.sizeBytes)}</span>·<span>{fmtDate(d.createdAt)}</span>
                                {d.encrypted && (
                                  <span className="inline-flex items-center gap-1 text-good" title="Encrypted on the server">
                                    <Lock size={11} /> Encrypted
                                  </span>
                                )}
                              </p>
                            </div>
                            <button
                              className="btn-ghost p-2"
                              onClick={() => setViewing({ title: `${c.fullName} - ${d.docType === 'RESUME' ? 'Resume' : 'Document'}`, path: `/documents/${d.id}/download`, fileName: d.fileName ?? 'document' })}
                              aria-label={`View ${d.fileName ?? 'document'}`}
                              title="View"
                            >
                              <Eye size={16} />
                            </button>
                            <button className="btn-ghost p-2" onClick={() => download(d)} disabled={downloading === d.id} aria-label={`Download ${d.fileName ?? 'document'}`} title="Download">
                              {downloading === d.id ? <Spinner size={15} /> : <Download size={15} />}
                            </button>
                            <button
                              className="btn-ghost p-2 hover:text-bad"
                              disabled={remove.isPending}
                              onClick={() =>
                                window.confirm(
                                  `Delete ${DOC_LABEL[d.docType].toLowerCase()} "${d.fileName ?? ''}"?` +
                                    (d.storage === 'DRIVE' ? ' The link is removed; the file stays in Google Drive.' : ' This cannot be undone.'),
                                ) && remove.mutate(d.id)
                              }
                              aria-label={`Delete ${d.fileName ?? 'document'}`}
                              title="Delete"
                            >
                              <Trash2 size={15} />
                            </button>
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                ))}
              </div>
            )}
            <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-line pt-4">
              <select
                className="input w-auto flex-1"
                value={docType}
                onChange={(e) => {
                  setDocType(e.target.value as DocType);
                  setConsent(false);
                }}
                aria-label="Document type"
              >
                {DOC_ORDER.map((t) => (
                  <option key={t} value={t}>{DOC_LABEL[t]}</option>
                ))}
              </select>
              <input
                ref={fileInput}
                type="file"
                className="hidden"
                accept=".pdf,.jpg,.jpeg,.png"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) {
                    if (f.size > 10 * 1024 * 1024) toast('error', 'File exceeds the 10 MB limit');
                    else upload.mutate(f);
                  }
                  e.target.value = '';
                }}
              />
              <button
                className="btn-secondary"
                disabled={upload.isPending || (docType === 'AADHAAR' && !consent)}
                onClick={() => fileInput.current?.click()}
                title={docType === 'AADHAAR' && !consent ? 'Confirm candidate consent first' : undefined}
              >
                {upload.isPending ? <Spinner size={15} /> : <Upload size={15} />} Upload
              </button>
            </div>
            {docType === 'AADHAAR' && (
              <div className="mt-3 rounded-xl border border-warn/30 bg-warn/5 p-3 text-xs text-ink-2">
                <label className="flex items-start gap-2">
                  <input type="checkbox" className="mt-0.5 h-4 w-4 accent-[#22a559]" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
                  <span>The candidate has consented to share a copy of their Aadhaar card for verification.</span>
                </label>
                <p className="mt-2 text-ink-3">
                  Prefer a <strong className="text-ink-2">masked Aadhaar</strong> (only the last 4 digits visible). The file is encrypted on the server, every download is logged,
                  and you can delete it once verification is complete. The Aadhaar number itself is not stored or verified.
                </p>
              </div>
            )}
            <p className="mt-2 text-xs text-ink-3">PDF, JPG or PNG up to 10 MB. Files are checked and kept in private storage; uploads, views, downloads and deletions are logged.</p>
          </section>

          <section className="panel p-5">
            <h2 className="mb-4 flex items-center gap-2 text-[15px] font-semibold"><MessageSquarePlus size={16} className="text-ink-3" /> HR Remarks</h2>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (remark.trim()) addRemark.mutate();
              }}
            >
              <textarea className="input min-h-[80px]" maxLength={2000} placeholder="Add a remark visible to the HR team…" value={remark} onChange={(e) => setRemark(e.target.value)} />
              <div className="mt-2 flex justify-end">
                <button className="btn-primary" disabled={!remark.trim() || addRemark.isPending}>
                  {addRemark.isPending && <Spinner size={14} />} Add remark
                </button>
              </div>
            </form>
            {c.remarks.length === 0 ? (
              <EmptyState title="No remarks yet" />
            ) : (
              <ul className="mt-4 space-y-3">
                {c.remarks.map((r) => (
                  <li key={r.id} className="rounded-xl bg-card px-3 py-2.5">
                    <p className="whitespace-pre-wrap text-sm">{r.remark}</p>
                    <p className="mt-1 text-[11px] text-ink-3">{r.author} · {fmtDateTime(r.createdAt)}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="panel p-5">
            <h2 className="mb-4 flex items-center gap-2 text-[15px] font-semibold"><UserCheck size={16} className="text-ink-3" /> Status history</h2>
            {c.statusHistory.length === 0 ? (
              <p className="text-sm text-ink-3">No status changes yet.</p>
            ) : (
              <ol className="space-y-3">
                {c.statusHistory.map((s, idx) => (
                  <li key={idx} className="text-sm">
                    <span className="flex flex-wrap items-center gap-1.5"><ApplicationPill status={s.fromStatus} /> → <ApplicationPill status={s.toStatus} /></span>
                    {s.note && <span className="mt-1 block text-ink-2">“{s.note}”</span>}
                    <span className="block text-[11px] text-ink-3">{s.changedBy} · {fmtDateTime(s.changedAt)}</span>
                  </li>
                ))}
              </ol>
            )}
          </section>

          <section className="panel p-5">
            <h2 className="mb-1 flex items-center gap-2 text-[15px] font-semibold"><Star size={16} className="text-ink-3" /> Rating history</h2>
            <p className="mb-4 text-xs text-ink-3">Internal to HR. Never shown to the candidate.</p>
            {c.ratingHistory.length === 0 ? (
              <p className="text-sm text-ink-3">Not rated yet.</p>
            ) : (
              <ol className="space-y-3">
                {c.ratingHistory.map((r, idx) => (
                  <li key={idx} className="text-sm">
                    <span className="flex flex-wrap items-center gap-1.5"><RatingPill rating={r.fromRating} /> → <RatingPill rating={r.toRating} /></span>
                    {r.remark && <span className="mt-1 block text-ink-2">“{r.remark}”</span>}
                    <span className="block text-[11px] text-ink-3">{r.ratedBy} · {fmtDateTime(r.ratedAt)}</span>
                  </li>
                ))}
              </ol>
            )}
          </section>

          <section className="panel p-5">
            <h2 className="mb-4 flex items-center gap-2 text-[15px] font-semibold"><History size={16} className="text-ink-3" /> Activity</h2>
            {c.activity.length === 0 ? (
              <p className="text-sm text-ink-3">No activity recorded.</p>
            ) : (
              <ol className="relative space-y-4 border-l border-line pl-4">
                {c.activity.map((a, idx) => (
                  <li key={idx} className="relative">
                    <span className="absolute -left-[21px] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-panel bg-ink-3" />
                    <p className="text-sm text-ink-2">{a.details ?? a.action}</p>
                    <p className="text-[11px] text-ink-3" title={fmtDateTime(a.at)}>{a.actor} · {fmtRelative(a.at)}</p>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </div>

      <EditCandidateModal candidateId={editing ? c.id : null} onClose={() => setEditing(false)} />
      <StatusModal target={statusTarget} onClose={() => setStatusTarget(null)} />
      <RatingModal target={ratingTarget} onClose={() => setRatingTarget(null)} />
      <DocumentViewer target={viewing} onClose={() => setViewing(null)} />
      <ArchiveModal target={archiving ? { id: c.id, name: c.fullName, code: c.candidateCode, archived: c.archived } : null} onClose={() => setArchiving(false)} />
      <ScheduleInterviewModal open={scheduling} onClose={() => setScheduling(false)} candidate={{ id: c.id, name: c.fullName, code: c.candidateCode }} />
      <InterviewStatusModal interview={interviewEdit} onClose={() => setInterviewEdit(null)} />
      <CommunicationDialog
        target={contact ? { candidateId: c.id, tab: contact, title: `Contact ${c.fullName}` } : null}
        onClose={() => setContact(null)}
      />
      {call.modal}
    </div>
  );
}
