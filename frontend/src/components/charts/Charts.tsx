import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  type TooltipProps,
} from 'recharts';
import { fmtNumber } from '../../lib/format';

const AXIS = { stroke: '#383835', tick: { fill: '#8a8a85', fontSize: 11 }, tickLine: false };
const GRID = '#26272b';

export interface Datum {
  label: string;
  value: number;
  color?: string;
  key?: string;
}

function TooltipBox({ active, payload, label }: TooltipProps<number, string>) {
  if (!active || !payload?.length) return null;
  const p = payload[0];
  const name = (p.payload as Datum).label ?? label;
  return (
    <div className="rounded-lg border border-line bg-raised px-3 py-2 text-xs shadow-card">
      <div className="text-ink-3">{name}</div>
      <div className="mt-0.5 flex items-center gap-2 text-sm font-semibold text-ink">
        <span className="h-2 w-2 rounded-full" style={{ background: (p.payload as Datum).color ?? p.color ?? '#3987e5' }} />
        {fmtNumber(Number(p.value))}
      </div>
    </div>
  );
}

const cursor = { fill: 'rgba(255,255,255,0.04)' };

/** Category tick that wraps multi-word labels ("Not Scheduled") onto two lines instead of colliding. */
function WrapTick({ x, y, payload }: { x?: number; y?: number; payload?: { value: string } }) {
  const words = String(payload?.value ?? '').split(' ');
  const lines = words.length > 1 ? [words.slice(0, Math.ceil(words.length / 2)).join(' '), words.slice(Math.ceil(words.length / 2)).join(' ')] : words;
  return (
    <text x={x} y={(y ?? 0) + 10} textAnchor="middle" fill="#8a8a85" fontSize={11}>
      {lines.map((l, i) => (
        <tspan key={i} x={x} dy={i === 0 ? 0 : 13}>{l}</tspan>
      ))}
    </text>
  );
}

export function TrendArea({ data, color = '#3987e5', height = 240 }: { data: Datum[]; color?: string; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data} margin={{ top: 8, right: 8, left: -18, bottom: 0 }}>
        <defs>
          <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={color} stopOpacity={0.35} />
            <stop offset="100%" stopColor={color} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid vertical={false} stroke={GRID} />
        <XAxis dataKey="label" {...AXIS} interval="preserveStartEnd" minTickGap={12} />
        <YAxis {...AXIS} allowDecimals={false} axisLine={false} width={44} />
        <Tooltip content={<TooltipBox />} cursor={{ stroke: '#5c5c58', strokeDasharray: '3 3' }} />
        <Area type="monotone" dataKey="value" stroke={color} strokeWidth={2} fill="url(#trendFill)" activeDot={{ r: 5, strokeWidth: 2, stroke: '#16171a' }} />
      </AreaChart>
    </ResponsiveContainer>
  );
}

export function VerticalBars({ data, height = 240, color = '#3987e5' }: { data: Datum[]; height?: number; color?: string }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 8, left: -18, bottom: 0 }} barCategoryGap="28%">
        <CartesianGrid vertical={false} stroke={GRID} />
        <XAxis dataKey="label" {...AXIS} interval={0} tick={<WrapTick />} height={36} />
        <YAxis {...AXIS} allowDecimals={false} axisLine={false} width={44} />
        <Tooltip content={<TooltipBox />} cursor={cursor} />
        <Bar dataKey="value" radius={[4, 4, 0, 0]} maxBarSize={44}>
          {data.map((d, i) => (
            <Cell key={i} fill={d.color ?? color} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export function HorizontalBars({ data, color = '#3987e5', onSelect }: { data: Datum[]; color?: string; onSelect?: (d: Datum) => void }) {
  const height = Math.max(160, data.length * 38 + 24);
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 36, left: 0, bottom: 0 }} barCategoryGap="24%">
        <CartesianGrid horizontal={false} stroke={GRID} />
        <XAxis type="number" {...AXIS} allowDecimals={false} axisLine={false} />
        <YAxis type="category" dataKey="label" {...AXIS} width={128} tick={{ fill: '#b9b9b2', fontSize: 12 }} />
        <Tooltip content={<TooltipBox />} cursor={cursor} />
        <Bar
          dataKey="value"
          radius={[0, 4, 4, 0]}
          maxBarSize={22}
          fill={color}
          label={{ position: 'right', fill: '#b9b9b2', fontSize: 11 }}
          onClick={(d: unknown) => onSelect?.(d as Datum)}
          className={onSelect ? 'cursor-pointer' : undefined}
        />
      </BarChart>
    </ResponsiveContainer>
  );
}

/** Donut with a full legend (label + count + share), so identity never depends on colour alone. */
export function Donut({ data, centerLabel, onSelect }: { data: Datum[]; centerLabel: string; onSelect?: (d: Datum) => void }) {
  const total = data.reduce((a, d) => a + d.value, 0);
  return (
    <div className="flex flex-col items-center gap-4 sm:flex-row">
      <div className="relative h-44 w-44 shrink-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Tooltip content={<TooltipBox />} />
            <Pie
              data={data.filter((d) => d.value > 0)}
              dataKey="value"
              nameKey="label"
              innerRadius="64%"
              outerRadius="100%"
              paddingAngle={1.5}
              stroke="#16171a"
              strokeWidth={2}
              onClick={(d: unknown) => onSelect?.(d as Datum)}
              className={onSelect ? 'cursor-pointer' : undefined}
            >
              {data
                .filter((d) => d.value > 0)
                .map((d, i) => (
                  <Cell key={i} fill={d.color} />
                ))}
            </Pie>
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-2xl font-semibold">{fmtNumber(total)}</span>
          <span className="text-[11px] text-ink-3">{centerLabel}</span>
        </div>
      </div>
      <ul className="w-full space-y-1.5 text-sm">
        {data.map((d) => (
          <li key={d.label}>
            <button
              type="button"
              disabled={!onSelect}
              onClick={() => onSelect?.(d)}
              className="flex w-full items-center gap-2 rounded-lg px-1.5 py-1 text-left enabled:hover:bg-raised"
            >
              <span className="h-2.5 w-2.5 shrink-0 rounded-sm" style={{ background: d.color }} />
              <span className="flex-1 text-ink-2">{d.label}</span>
              <span className="font-medium tabular-nums text-ink">{d.value}</span>
              <span className="w-10 text-right text-xs tabular-nums text-ink-3">{total ? Math.round((d.value / total) * 100) : 0}%</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Rail chart in the style of the reference: light, rounded columns on the dark panel. */
export function WeekColumns({ data, height = 190 }: { data: Datum[]; height?: number }) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} margin={{ top: 8, right: 4, left: -24, bottom: 0 }} barCategoryGap="38%">
        <defs>
          <linearGradient id="weekFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#f2f2ef" stopOpacity={0.95} />
            <stop offset="100%" stopColor="#f2f2ef" stopOpacity={0.15} />
          </linearGradient>
        </defs>
        <CartesianGrid vertical={false} stroke={GRID} />
        <XAxis dataKey="label" {...AXIS} />
        <YAxis {...AXIS} allowDecimals={false} axisLine={false} width={40} />
        <Tooltip content={<TooltipBox />} cursor={cursor} />
        <Bar dataKey="value" fill="url(#weekFill)" radius={[8, 8, 8, 8]} maxBarSize={18} />
      </BarChart>
    </ResponsiveContainer>
  );
}
