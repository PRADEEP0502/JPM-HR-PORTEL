import type { ApplicationStatus, ExperienceBucket, InterviewMode, InterviewStatus, VerificationStatus } from './types';

export const APPLICATION_STATUSES: { value: ApplicationStatus; label: string }[] = [
  { value: 'NEW', label: 'New' },
  { value: 'UNDER_REVIEW', label: 'Under Review' },
  { value: 'SHORTLISTED', label: 'Shortlisted' },
  { value: 'SELECTED', label: 'Selected' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'ON_HOLD', label: 'On Hold' },
];

export const INTERVIEW_STATUSES: { value: InterviewStatus; label: string }[] = [
  { value: 'NOT_SCHEDULED', label: 'Not Scheduled' },
  { value: 'SCHEDULED', label: 'Scheduled' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'RESCHEDULED', label: 'Rescheduled' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export const VERIFICATION_STATUSES: { value: VerificationStatus; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'VERIFIED', label: 'Verified' },
];

export const EXPERIENCE_BUCKETS: { value: ExperienceBucket; label: string }[] = [
  { value: 'FRESHER', label: 'Fresher' },
  { value: 'Y0_2', label: 'Up to 2 yrs' },
  { value: 'Y2_5', label: '2–5 yrs' },
  { value: 'Y5_10', label: '5–10 yrs' },
  { value: 'Y10_PLUS', label: '10+ yrs' },
  { value: 'UNKNOWN', label: 'Not provided' },
];

export const INTERVIEW_MODES: { value: InterviewMode; label: string }[] = [
  { value: 'IN_PERSON', label: 'In person' },
  { value: 'VIDEO', label: 'Video call' },
  { value: 'PHONE', label: 'Phone' },
];

export const labelOf = <T extends string>(list: { value: T; label: string }[], v: T | null | undefined) =>
  list.find((x) => x.value === v)?.label ?? (v ? String(v) : '—');

/** Status tones. Every pill also shows its text label, so colour is never the only signal. */
export type Tone = 'blue' | 'violet' | 'aqua' | 'green' | 'red' | 'orange' | 'grey';

export const applicationTone: Record<ApplicationStatus, Tone> = {
  NEW: 'blue',
  UNDER_REVIEW: 'violet',
  SHORTLISTED: 'aqua',
  SELECTED: 'green',
  REJECTED: 'red',
  ON_HOLD: 'orange',
};

export const interviewTone: Record<InterviewStatus, Tone> = {
  NOT_SCHEDULED: 'grey',
  SCHEDULED: 'blue',
  COMPLETED: 'green',
  RESCHEDULED: 'orange',
  CANCELLED: 'red',
};

/** Hex values for charts (dark-mode steps; validated categorical + status palette). */
export const toneHex: Record<Tone, string> = {
  blue: '#3987e5',
  violet: '#9085e9',
  aqua: '#199e70',
  green: '#0ca30c',
  red: '#d03b3b',
  orange: '#e08a2e',
  grey: '#6b6b66',
};

/** Fixed categorical order (never cycled) for identity series such as departments. */
export const CATEGORICAL = ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767'];

const dateFmt = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
const dateTimeFmt = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});
const timeFmt = new Intl.DateTimeFormat('en-IN', { hour: '2-digit', minute: '2-digit' });

/** LocalDate strings ("2026-10-20") are parsed as calendar dates, not UTC instants. */
export function fmtDate(v: string | null | undefined) {
  if (!v) return '—';
  const [y, m, d] = v.split('T')[0].split('-').map(Number);
  return dateFmt.format(new Date(y, m - 1, d));
}
export const fmtDateTime = (v: string | null | undefined) => (v ? dateTimeFmt.format(new Date(v)) : '—');
export const fmtTime = (v: string | null | undefined) => (v ? timeFmt.format(new Date(v)) : '—');

export function fmtRelative(v: string | null | undefined) {
  if (!v) return 'never';
  const diff = Date.now() - new Date(v).getTime();
  const min = Math.round(diff / 60000);
  if (min < 1) return 'just now';
  if (min < 60) return `${min} min ago`;
  const h = Math.round(min / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  return d < 30 ? `${d} d ago` : fmtDate(v);
}

export function fmtExperience(v: number | null | undefined) {
  if (v === null || v === undefined) return '—';
  if (Number(v) === 0) return 'Fresher';
  return `${Number(v)} yr${Number(v) === 1 ? '' : 's'}`;
}

export function initials(name: string | null | undefined) {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export const fmtNumber = (n: number) => new Intl.NumberFormat('en-IN').format(n);

export function fmtBytes(n: number | null | undefined) {
  if (!n) return '';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

/** "2026-10-20T15:30" (datetime-local value) for a Date, in the browser's time zone. */
export function toLocalInput(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Profile Match Rating guideline bands (HR makes the final call). */
export const RATING_BANDS: { min: number; max: number; label: string; tone: Tone }[] = [
  { min: 1, max: 2, label: 'Not Suitable', tone: 'red' },
  { min: 3, max: 4, label: 'Low Match', tone: 'orange' },
  { min: 5, max: 6, label: 'Average Match', tone: 'violet' },
  { min: 7, max: 8, label: 'Good Match', tone: 'aqua' },
  { min: 9, max: 10, label: 'Excellent Match', tone: 'green' },
];

export const ratingBand = (rating: number) => RATING_BANDS.find((b) => rating >= b.min && rating <= b.max)!;
