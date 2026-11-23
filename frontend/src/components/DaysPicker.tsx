const OPTIONS = [7, 30, 90];

// Same range choices on every analytics page, so the buttons live in one place.
export default function DaysPicker({ days, onChange }: { days: number; onChange: (d: number) => void }) {
  return (
    <div className="row days-picker" role="group" aria-label="Time range">
      {OPTIONS.map((d) => (
        <button
          key={d}
          type="button"
          className={d === days ? "" : "secondary"}
          aria-pressed={d === days}
          onClick={() => onChange(d)}
        >
          {d} days
        </button>
      ))}
    </div>
  );
}
