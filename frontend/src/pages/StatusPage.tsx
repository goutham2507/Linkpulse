import { useEffect, useState } from "react";
import { getServiceHealth } from "../api/endpoints";
import type { ServiceName, ServiceState } from "../api/types";

const SERVICES: { name: ServiceName; label: string }[] = [
  { name: "shortener", label: "Shortener service" },
  { name: "analytics", label: "Analytics service" },
];
const REFRESH_MS = 10_000;

type Snapshot = Record<ServiceName, ServiceState | null>;

export default function StatusPage() {
  const [states, setStates] = useState<Snapshot>({ shortener: null, analytics: null });
  const [checkedAt, setCheckedAt] = useState<Date | null>(null);

  useEffect(() => {
    let current = true;
    async function check() {
      const [shortener, analytics] = await Promise.all(SERVICES.map((s) => getServiceHealth(s.name)));
      if (!current) return;
      setStates({ shortener, analytics });
      setCheckedAt(new Date());
    }
    void check();
    const id = setInterval(() => void check(), REFRESH_MS);
    return () => {
      current = false;
      clearInterval(id);
    };
  }, []);

  return (
    <>
      <h1>System status</h1>
      <div className="cards">
        {SERVICES.map((s) => {
          const state = states[s.name];
          return (
            <div className="card" key={s.name}>
              <div className="card-label">{s.label}</div>
              <span className={`badge badge-${(state ?? "checking").toLowerCase()}`}>{state ?? "Checking…"}</span>
            </div>
          );
        })}
      </div>
      <p className="muted">
        {checkedAt ? `Last checked ${checkedAt.toLocaleTimeString()}. ` : ""}Refreshes every {REFRESH_MS / 1000}s.
      </p>
    </>
  );
}
