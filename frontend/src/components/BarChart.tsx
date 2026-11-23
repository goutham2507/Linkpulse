import type { DayCount } from "../api/types";

const WIDTH = 720;
const HEIGHT = 220;
const PAD = { top: 12, right: 8, bottom: 26, left: 36 };

// Hand-rolled SVG: no chart library keeps the bundle small and works under the strict CSP (no inline styles).
export default function BarChart({ data }: { data: DayCount[] }) {
  if (data.length === 0) return null;
  const max = Math.max(1, ...data.map((d) => d.clicks));
  const plotW = WIDTH - PAD.left - PAD.right;
  const plotH = HEIGHT - PAD.top - PAD.bottom;
  const slot = plotW / data.length;
  const barW = Math.max(1, slot * 0.7);
  const total = data.reduce((sum, d) => sum + d.clicks, 0);

  return (
    <svg
      className="chart"
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      role="img"
      aria-label={`Clicks per day: ${total} in total over ${data.length} days`}
    >
      <line className="chart-axis" x1={PAD.left} y1={PAD.top + plotH} x2={WIDTH - PAD.right} y2={PAD.top + plotH} />
      <text className="chart-label" x={PAD.left - 6} y={PAD.top + 8} textAnchor="end">
        {max}
      </text>
      <text className="chart-label" x={PAD.left - 6} y={PAD.top + plotH} textAnchor="end">
        0
      </text>
      {data.map((d, i) => {
        const h = (d.clicks / max) * plotH;
        return (
          <rect
            key={d.day}
            className="chart-bar"
            x={PAD.left + i * slot + (slot - barW) / 2}
            y={PAD.top + plotH - h}
            width={barW}
            height={h}
          >
            <title>{`${d.day}: ${d.clicks} click${d.clicks === 1 ? "" : "s"}`}</title>
          </rect>
        );
      })}
      <text className="chart-label" x={PAD.left} y={HEIGHT - 6}>
        {data[0].day}
      </text>
      <text className="chart-label" x={WIDTH - PAD.right} y={HEIGHT - 6} textAnchor="end">
        {data[data.length - 1].day}
      </text>
    </svg>
  );
}
