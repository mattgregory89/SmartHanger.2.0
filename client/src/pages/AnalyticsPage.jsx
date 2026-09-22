import React, { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import StatusBadge from '../components/StatusBadge.jsx';
import SimpleBarChart from '../components/SimpleBarChart.jsx';
import LedgerChain from '../components/LedgerChain.jsx';
import MiniTrendChart from '../components/MiniTrendChart.jsx';

export default function AnalyticsPage() {
  const [data, setData] = useState(null);
  const [ledger, setLedger] = useState([]);
  const [ledgerValid, setLedgerValid] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.analytics().then(setData).catch(e => setError(e.message));
    api.ledger().then(setLedger).catch(() => {});
    api.validateLedger().then(r => setLedgerValid(Boolean(r.valid))).catch(() => setLedgerValid(false));
  }, []);

  const aiDecisionData = useMemo(() => {
    if (!data) return [];
    return data.aiDecisions.map(r => ({ label: r.decision, value: Number(r.count) }));
  }, [data]);

  const syncData = useMemo(() => {
    if (!data) return [];
    return data.offlineSync.map(r => ({ label: r.status || 'UNKNOWN', value: Number(r.count) }));
  }, [data]);

  const workloadData = useMemo(() => {
    if (!data) return [];
    return data.shopWorkload.map(r => ({
      label: r.shop || 'UNASSIGNED',
      value: Number(r.open_count)
    }));
  }, [data]);

  const fleetBurdenPoints = useMemo(() => {
    if (!data) return [];
    return data.planningAlerts.map(r => Number(r.open_discrepancies || 0));
  }, [data]);

  const fleetBurdenLabels = useMemo(() => {
    if (!data) return [];
    return data.planningAlerts.map(r => r.tail_number);
  }, [data]);

  if (error) return <div className="error">{error}</div>;
  if (!data) return <div>Loading analytics…</div>;

  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Maintenance Analytics</h1>
          <p>Planning indicators from SmartHangar records</p>
        </div>
        <StatusBadge status={ledgerValid ? 'LEDGER VALID' : 'LEDGER ERROR'} />
      </div>

      <section className="metric-grid overview-metrics">
        <div className="metric"><span>Ledger Blocks</span><strong>{data.ledgerBlocks}</strong></div>
        <div className="metric"><span>AI Decisions</span><strong>{data.aiDecisions.reduce((n, x) => n + Number(x.count), 0)}</strong></div>
        <div className="metric"><span>Offline Sync Events</span><strong>{data.offlineSync.reduce((n, x) => n + Number(x.count), 0)}</strong></div>
        <div className="metric"><span>Repeat Patterns</span><strong>{data.repeatPatterns.length}</strong></div>
      </section>

      <div className="three-col">
        <section className="panel">
          <div className="panel-title"><h2>Shop Workload</h2><span>Open discrepancies</span></div>
          <SimpleBarChart data={workloadData} />
        </section>

        <section className="panel">
          <div className="panel-title"><h2>AI Decisions</h2><span>Human-in-the-loop audit</span></div>
          <SimpleBarChart data={aiDecisionData} emptyText="No AI decisions yet." />
        </section>

        <section className="panel">
          <div className="panel-title"><h2>Offline Sync Outcomes</h2><span>Reconnect behavior</span></div>
          <SimpleBarChart data={syncData} emptyText="No sync events yet." />
        </section>
      </div>

      <div className="two-col">
        <section className="panel">
          <div className="panel-title"><h2>Open Discrepancy Burden</h2><span>By aircraft</span></div>
          <MiniTrendChart
            points={fleetBurdenPoints}
            labels={fleetBurdenLabels}
            title="Open discrepancy load across the fleet"
          />
        </section>

        <section className="panel">
          <div className="panel-title"><h2>Ledger Chain</h2><span>Visual blockchain preview</span></div>
          <LedgerChain blocks={ledger} valid={ledgerValid} />
        </section>
      </div>

      <section className="panel">
        <div className="panel-title"><h2>Fleet Planning Alerts</h2><span>Not an airworthiness determination</span></div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Aircraft</th>
                <th>Status</th>
                <th>Open MX</th>
                <th>Red X</th>
              </tr>
            </thead>
            <tbody>
              {data.planningAlerts.map(r => (
                <tr key={r.aircraft_id}>
                  <td>{r.tail_number}</td>
                  <td><StatusBadge status={r.status} /></td>
                  <td>{r.open_discrepancies}</td>
                  <td>{r.red_x_count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="hint">{data.note}</p>
      </section>
    </>
  );
}