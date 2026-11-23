import { useEffect, useState } from "react";
import { Link } from "react-router";
import { getStatsSummary } from "../api/endpoints";
import type { StatsSummary } from "../api/types";
import BarChart from "../components/BarChart";
import DaysPicker from "../components/DaysPicker";
import ErrorBanner from "../components/ErrorBanner";
import StatCard from "../components/StatCard";

export default function DashboardPage() {
  const [days, setDays] = useState(30);
  const [data, setData] = useState<StatsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Ignore a slow answer for an old range, so quickly switching 7/30/90 can't show stale data.
    let current = true;
    getStatsSummary(days)
      .then((res) => {
        if (!current) return;
        setData(res);
        setError(null);
      })
      .catch((err: unknown) => {
        if (current) setError(err instanceof Error ? err.message : "Could not load analytics.");
      });
    return () => {
      current = false;
    };
  }, [days]);

  const perDay = data ? (data.totalClicks / data.daily.length).toFixed(1) : "—";

  return (
    <>
      <h1>Dashboard</h1>
      <DaysPicker days={days} onChange={setDays} />
      <ErrorBanner message={error} />
      {data && (
        <>
          <div className="cards">
            <StatCard label="Links" value={data.totalLinks} />
            <StatCard label={`Clicks (last ${days} days)`} value={data.totalClicks} />
            <StatCard label="Clicks per day" value={perDay} />
          </div>
          <h2>Clicks per day</h2>
          <BarChart data={data.daily} />
          <h2>Top links</h2>
          {data.topLinks.length === 0 ? (
            <p className="muted">No clicks in this period yet.</p>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Clicks</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.topLinks.map((t) => (
                    <tr key={t.code}>
                      <td>{t.code}</td>
                      <td>{t.clicks}</td>
                      <td>
                        <Link to={`/links/${encodeURIComponent(t.code)}/stats`}>Details</Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </>
  );
}
