import clsx from 'clsx';
import type { ReactNode } from 'react';
import {
  APPLICATION_STATUSES,
  INTERVIEW_STATUSES,
  applicationTone,
  interviewTone,
  labelOf,
  type Tone,
} from '../../lib/format';
import type { ApplicationStatus, InterviewStatus, VerificationStatus } from '../../lib/types';

const toneClass: Record<Tone, string> = {
  blue: 'bg-info/15 text-[#8fb4f5] ring-info/30',
  violet: 'bg-violet/15 text-[#b9b1f5] ring-violet/30',
  aqua: 'bg-aqua/15 text-[#6fd9b4] ring-aqua/30',
  green: 'bg-good/15 text-[#6fd99a] ring-good/30',
  red: 'bg-bad/15 text-[#f29a9a] ring-bad/30',
  orange: 'bg-warn/15 text-[#f3b877] ring-warn/30',
  grey: 'bg-white/5 text-ink-2 ring-white/10',
};

export function Pill({ tone, children, className }: { tone: Tone; children: ReactNode; className?: string }) {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset whitespace-nowrap',
        toneClass[tone],
        className,
      )}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-current" aria-hidden />
      {children}
    </span>
  );
}

export const ApplicationPill = ({ status }: { status: ApplicationStatus }) => (
  <Pill tone={applicationTone[status]}>{labelOf(APPLICATION_STATUSES, status)}</Pill>
);

export const InterviewPill = ({ status }: { status: InterviewStatus }) => (
  <Pill tone={interviewTone[status]}>{labelOf(INTERVIEW_STATUSES, status)}</Pill>
);

export const VerificationPill = ({ status }: { status: VerificationStatus }) =>
  status === 'VERIFIED' ? <Pill tone="green">Verified</Pill> : <Pill tone="orange">Pending verification</Pill>;

export const DemoTag = () => (
  <span
    title="Sample record created in demo mode - not a real applicant"
    className="rounded border border-warn/40 px-1 text-[10px] font-semibold uppercase tracking-wide text-warn"
  >
    Demo
  </span>
);
