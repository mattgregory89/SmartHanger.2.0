import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import StatusBadge from '../components/StatusBadge.jsx';
import DonutGauge from '../components/DonutGauge.jsx';
import SimpleBarChart from '../components/SimpleBarChart.jsx';

export default function DashboardPage() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.dashboard().then(setData).catch(e => setError(e.message));
  }, []);

  const redX = data?.openDiscrepancies?.filter(d => d.symbol === 'RED X').length || 0;

  const statusData = useMemo(() => {
    if (!data) return [];
    return [
      { label: 'FMC', value: data.counts.FMC || 0 },
      { label: 'PMC', value: data.counts.PMC || 0 },
      { label: 'NMC', value: data.counts.NMC || 0 }
    ];
  }, [data]);

  const shopData = useMemo(() => {
    if (!data) return [];
    const counts = {};
    for (const d of data.openDiscrepancies) {
      const key = d.assigned_shop || 'UNASSIGNED';
      counts[key] = (counts[key] || 0) + 1;
    }
    return Object.entries(counts)
      .map(([label, value]) => ({ label, value }))
      .sort((a, b) => b.value - a.value);
  }, [data]);

  if (error) return <div className="error">{error}</div>;
  if (!data) return <div>Loading dashboard...</div>;

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Fleet Dashboard</h1>
          <p>Current aircraft maintenance picture</p>
        </div>
        <div className="updated">Demo fleet · {data.aircraft.length} aircraft</div>
      </div>

      <section className="metric-grid">
        <div className="metric"><span>Total Aircraft</span><strong>{data.aircraft.length}</strong></div>
        <div className="metric metric-good"><span>FMC</span><strong>{data.counts.FMC}</strong></div>
        <div className="metric metric-warn"><span>PMC</span><strong>{data.counts.PMC}</strong></div>
        <div className="metric metric-bad"><span>NMC</span><strong>{data.counts.NMC}</strong></div>
        <div className="metric"><span>Open Discrepancies</span><strong>{data.openDiscrepancies.length}</strong></div>
        <div className="metric"><span>Open Red X</span><strong>{redX}</strong></div>
      </section>

      <div className="three-col">
        <section className="panel">
          <div className="panel-title"><h2>Readiness</h2><span>Mission capable rate</span></div>
          <DonutGauge
            value={data.counts.FMC || 0}
            total={data.aircraft.length}
            label="Fleet Readiness"
            subtitle="Visual summary for the demo audience"
          />
        </section>

        <section className="panel">
          <div className="panel-title"><h2>Status Distribution</h2><span>Aircraft by status</span></div>
          <SimpleBarChart data={statusData} />
        </section>

        <section className="panel">
          <div className="panel-title"><h2>Open MX by Shop</h2><span>Current workload</span></div>
          <SimpleBarChart data={shopData} emptyText="No open discrepancies." />
        </section>
      </div>

      <section className="panel">
        <div className="panel-title"><h2>Fleet Status</h2><span>Aircraft-centered view</span></div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Aircraft</th>
                <th>Model</th>
                <th>Status</th>
                <th>Location</th>
                <th>Hours</th>
                <th>Crew Chief</th>
              </tr>
            </thead>
            <tbody>
              {data.aircraft.map(ac => (
                <tr key={ac.id}>
                  <td><Link className="tail-link" to={`/aircraft/${ac.id}`}>{ac.tail_number}</Link></td>
                  <td>{ac.model}</td>
                  <td><StatusBadge status={ac.status} /></td>
                  <td>{ac.location}</td>
                  <td>{ac.total_hours.toLocaleString(undefined, { minimumFractionDigits: 1 })}</td>
                  <td>{ac.assigned_crew_chief}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <div className="two-col">
        <section className="panel">
          <div className="panel-title"><h2>Needs Attention</h2><span>Open discrepancies</span></div>
          <div className="stack-list">
            {data.openDiscrepancies.map(d => (
              <Link key={d.id} className="list-item" to={`/aircraft/${d.aircraft_id}`}>
                <div>
                  <strong>{d.tail_number} · {d.discrepancy_number}</strong>
                  <p>{d.description}</p>
                </div>
                <StatusBadge status={d.symbol} />
              </Link>
            ))}
          </div>
        </section>

        <section className="panel">
          <div className="panel-title"><h2>Upcoming Inspections</h2><span>Planning view</span></div>
          <div className="stack-list">
            {data.dueInspections.slice(0, 6).map(i => (
              <Link key={i.id} className="list-item" to={`/aircraft/${i.aircraft_id}`}>
                <div>
                  <strong>{i.tail_number} · {i.name}</strong>
                  <p>{i.hours_remaining !== null ? `${i.hours_remaining} hours remaining` : `Due ${i.due_date}`}</p>
                </div>
              </Link>
            ))}
          </div>
        </section>
      </div>
    </>
  );
}