import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { getLinkStats } from "../api/endpoints";
import type { LinkStats } from "../api/types";
import BarChart from "../components/BarChart";
import DaysPicker from "../components/DaysPicker";
import ErrorBanner from "../components/ErrorBanner";
import StatCard from "../components/StatCard";

export default function LinkStatsPage() {
  const { code = "" } = useParams();
  const [days, setDays] = useState(30);
  const [data, setData] = useState<LinkStats | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let current = true; // drop answers that arrive after the code or range changed
    getLinkStats(code, days)
      .then((res) => {
        if (!current) return;
        setData(res);
        setError(null);
      })
      .catch((err: unknown) => {
        if (!current) return;
        setData(null);
        setError(err instanceof Error ? err.message : "Could not load stats.");
      });
    return () => {
      current = false;
    };
  }, [code, days]);

  const peak = data ? data.daily.reduce((best, d) => (d.clicks > best.clicks ? d : best), data.daily[0]) : null;

  return (
    <>
      <p>
        <Link to="/links">← My Links</Link>
      </p>
      <h1>Stats for {code}</h1>
      <DaysPicker days={days} onChange={setDays} />
      <ErrorBanner message={error} />
      {data && (
        <>
          <div className="cards">
            <StatCard label={`Clicks (last ${days} days)`} value={data.totalClicks} />
            <StatCard label="Busiest day" value={peak && peak.clicks > 0 ? `${peak.day} (${peak.clicks})` : "—"} />
          </div>
          <BarChart data={data.daily} />
        </>
      )}
    </>
  );
}
