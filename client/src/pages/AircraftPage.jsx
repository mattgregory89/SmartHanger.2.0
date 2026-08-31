// AircraftPage.jsx
import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import StatusBadge from '../components/StatusBadge.jsx';

const tabs = ['Overview', 'Discrepancies', 'Inspections', 'Time Changes', 'Engines', 'Servicing', 'Modifications', 'History', 'Shift Turnover'];

export default function AircraftPage({ user }) {
  const { id } = useParams();
  const [data, setData] = useState(null);
  const [tab, setTab] = useState('Overview');
  const [timeline, setTimeline] = useState([]);
  const [turnover, setTurnover] = useState(null);
  const [message, setMessage] = useState('');

  async function reload() { setData(await api.aircraftDetail(id)); }
  useEffect(() => { reload(); }, [id]);
  useEffect(() => {
    if (tab === 'History') api.timeline(id).then(setTimeline);
    if (tab === 'Shift Turnover') api.turnover(id).then(setTurnover);
  }, [tab, id]);

  if (!data) return <div>Loading aircraft...</div>;
  const a = data.aircraft;
  const open = data.discrepancies.filter(d => d.status === 'OPEN');
  const redX = open.filter(d => d.symbol === 'RED X').length;
  const latestFuel = data.servicing.find(s => s.type === 'FUEL');
  const latestLox = data.servicing.find(s => s.type === 'LOX');

  async function addDiscrepancy(e) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    await api.addDiscrepancy(id, {
      description: form.get('description'), symbol: form.get('symbol'), assignedShop: form.get('shop'), reportedBy: user.full_name
    });
    e.currentTarget.reset(); setMessage('Discrepancy added.'); await reload();
  }

  async function closeDiscrepancy(discrepancyId) {
    const correctiveAction = window.prompt('Enter corrective action / closing statement:');
    if (!correctiveAction) return;
    await api.closeDiscrepancy(discrepancyId, { correctiveAction, closedBy: user.full_name });
    setMessage('Discrepancy closed. Aircraft status recalculated.'); await reload();
  }

  async function addServicing(e) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    await api.addServicing(id, { type: form.get('type'), quantity: form.get('quantity'), unit: form.get('unit'), notes: form.get('notes'), servicedBy: user.full_name });
    e.currentTarget.reset(); setMessage('Servicing record saved.'); await reload();
  }

  async function setStatus(status) {
    await api.setStatus(id, status); setMessage(`Aircraft status updated to ${status}.`); await reload();
  }

  return (
    <>
      <div className="breadcrumb"><Link to="/">Fleet Dashboard</Link> / {a.tail_number}</div>
      <section className="aircraft-hero">
        <div><div className="eyebrow">{a.model}</div><h1>{a.tail_number}</h1><p>{a.location} · Assigned Crew Chief: {a.assigned_crew_chief}</p></div>
        <div className="hero-status"><StatusBadge status={a.status} /><span>Last updated {a.last_updated}</span></div>
      </section>
      {message && <div className="success-banner" onClick={() => setMessage('')}>{message} ×</div>}

      <nav className="tabs">{tabs.map(t => <button key={t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>{t}</button>)}</nav>

      {tab === 'Overview' && <>
        <section className="metric-grid overview-metrics">
          <div className="metric"><span>Aircraft Status</span><strong><StatusBadge status={a.status} /></strong></div>
          <div className="metric"><span>Total Hours</span><strong>{a.total_hours.toLocaleString()}</strong></div>
          <div className="metric"><span>Total Cycles</span><strong>{a.total_cycles.toLocaleString()}</strong></div>
          <div className="metric"><span>Open Discrepancies</span><strong>{open.length}</strong></div>
          <div className="metric"><span>Open Red X</span><strong>{redX}</strong></div>
          <div className="metric"><span>Fuel</span><strong>{latestFuel ? `${latestFuel.quantity.toLocaleString()} ${latestFuel.unit}` : '—'}</strong></div>
          <div className="metric"><span>LOX</span><strong>{latestLox ? `${latestLox.quantity} ${latestLox.unit}` : '—'}</strong></div>
          <div className="metric"><span>Time Changes</span><strong>{data.timeChanges.length}</strong></div>
        </section>
        <section className="panel">
          <div className="panel-title">
            <h2>Quick Actions</h2>
            <span>Common maintenance actions</span>
          </div>

          <div className="quick-actions">
            <button onClick={() => setTab('Discrepancies')}>
              + Add Discrepancy
            </button>

            <button onClick={() => setTab('Servicing')}>
              + Record Servicing
            </button>

            <button onClick={() => setTab('Inspections')}>
              View Inspections
            </button>

            <button onClick={() => setTab('Time Changes')}>
              View Time Changes
            </button>

            <button onClick={() => setTab('Engines')}>
              View Engines
            </button>

            <button onClick={() => setTab('Modifications')}>
              View Modifications
            </button>
          </div>
        </section>
        <div className="two-col">
          <section className="panel"><div className="panel-title"><h2>Current Discrepancies</h2></div>{open.length ? open.map(d => <DiscrepancyCard key={d.id} d={d} close={() => closeDiscrepancy(d.id)} />) : <div className="empty-state">No open discrepancies.</div>}</section>
          <section className="panel"><div className="panel-title"><h2>Manual Status</h2><span>For demo / production control</span></div><div className="status-buttons"><button onClick={() => setStatus('FMC')}>FMC</button><button onClick={() => setStatus('PMC')}>PMC</button><button onClick={() => setStatus('NMC')}>NMC</button></div><p className="hint">Opening a Red X automatically places the aircraft NMC. Closing the last Red X automatically returns an NMC aircraft to FMC.</p></section>
        </div>
      </>}

      {tab === 'Discrepancies' && <>
        <section className="panel"><div className="panel-title"><h2>Open New Discrepancy</h2><span>Enter once; reuse everywhere</span></div>
          <form className="form-grid" onSubmit={addDiscrepancy}>
            <label className="wide">Discrepancy description<input name="description" required placeholder="RH MLG hydraulic line leaking" /></label>
            <label>Symbol<select name="symbol"><option>RED X</option><option>RED DASH</option><option>INFORMATIONAL</option></select></label>
            <label>Assigned shop<select name="shop"><option>CREW CHIEF</option><option>HYDRAULICS</option><option>ELECTRICAL</option><option>AVIONICS</option><option>ENGINES</option><option>SHEET METAL</option></select></label>
            <button className="wide" type="submit">Open Discrepancy</button>
          </form>
        </section>
        <section className="panel"><div className="panel-title"><h2>Discrepancy Records</h2></div>{data.discrepancies.map(d => <DiscrepancyCard key={d.id} d={d} close={() => closeDiscrepancy(d.id)} />)}</section>
      </>}

      {tab === 'Inspections' && <section className="panel"><div className="panel-title"><h2>Inspection Schedule</h2></div><DataTable headers={['Inspection', 'Due', 'Remaining', 'Last Completed', 'Status']} rows={data.inspections.map(i => [i.name, i.due_hours ? `${i.due_hours} hrs` : i.due_date, i.hours_remaining !== null ? `${i.hours_remaining} hrs` : 'Calendar controlled', i.last_completed_date || '—', <StatusBadge status={i.status} />])} /></section>}

      {tab === 'Time Changes' && <section className="panel"><div className="panel-title"><h2>Time-Change Items</h2><span>Hours / calendar controlled</span></div><DataTable headers={['Item', 'Part / Serial', 'Due', 'Remaining', 'Warning']} rows={data.timeChanges.map(t => [t.name, `${t.part_number || '—'} / ${t.serial_number || '—'}`, t.due_hours ? `${t.due_hours} hrs` : t.due_date, t.hours_remaining !== null ? `${t.hours_remaining} hrs` : 'Calendar controlled', t.warning_hours ? `${t.warning_hours} hrs` : '—'])} /></section>}

      {tab === 'Engines' && <section className="panel"><div className="panel-title"><h2>Installed Engines</h2></div><DataTable headers={['Position', 'Serial Number', 'Hours', 'Cycles', 'Installed']} rows={data.engines.map(e => [`#${e.position}`, e.serial_number, e.total_hours.toLocaleString(), e.total_cycles.toLocaleString(), e.installed_date || '—'])} /></section>}

      {tab === 'Servicing' && <>
        <section className="panel"><div className="panel-title"><h2>Record Servicing</h2><span>One form supports any servicing type</span></div>
          <form className="form-grid" onSubmit={addServicing}>
            <label>Type<input name="type" required placeholder="FUEL, LOX, ENGINE OIL #3" /></label>
            <label>Quantity<input name="quantity" required type="number" step="any" /></label>
            <label>Unit<input name="unit" required placeholder="LBS, PERCENT, QUARTS" /></label>
            <label className="wide">Notes<input name="notes" placeholder="Optional servicing notes" /></label>
            <button className="wide">Save Servicing Record</button>
          </form>
        </section>
        <section className="panel"><DataTable headers={['Date', 'Type', 'Quantity', 'Serviced By', 'Notes']} rows={data.servicing.map(s => [s.service_date, s.type, `${s.quantity.toLocaleString()} ${s.unit}`, s.serviced_by, s.notes || '—'])} /></section>
      </>}

      {tab === 'Modifications' && <section className="panel"><div className="panel-title"><h2>Aircraft Modifications</h2></div><DataTable headers={['Modification', 'Title', 'Status', 'Completed', 'Notes']} rows={data.modifications.map(m => [m.mod_number, m.title, <StatusBadge status={m.status} />, m.completed_date || '—', m.notes || '—'])} /></section>}

      {tab === 'History' && <section className="panel"><div className="panel-title"><h2>Aircraft Timeline</h2><span>Combined maintenance history</span></div><div className="timeline">{timeline.map((event, i) => <div className="timeline-event" key={i}><div className="timeline-dot" /><div><small>{event.event_date}</small><strong>{event.event_type}</strong><p>{event.description}</p></div></div>)}</div></section>}

      {tab === 'Shift Turnover' && turnover && <section className="panel turnover"><div className="panel-title"><h2>Shift Turnover</h2><span>At-a-glance continuity</span></div><div className="turnover-header"><div><small>AIRCRAFT</small><strong>{turnover.aircraft.tail_number}</strong></div><div><small>STATUS</small><StatusBadge status={turnover.aircraft.status} /></div><div><small>LOCATION</small><strong>{turnover.aircraft.location}</strong></div></div><h3>Open Maintenance</h3>{turnover.openDiscrepancies.length ? turnover.openDiscrepancies.map(d => <div className="turnover-job" key={d.id}><strong>{d.discrepancy_number} · {d.description}</strong><span><StatusBadge status={d.symbol} /> {d.assigned_shop}</span><small>Reported by {d.reported_by} · {d.reported_date}</small></div>) : <p>No open maintenance.</p>}<h3>Latest Servicing</h3><DataTable headers={['Type', 'Quantity', 'Date', 'Serviced By']} rows={turnover.latestServicing.map(s => [s.type, `${s.quantity} ${s.unit}`, s.service_date, s.serviced_by])} /></section>}
    </>
  );
}

function DiscrepancyCard({ d, close }) {
  return <div className={`discrepancy-card ${d.status === 'CLOSED' ? 'closed' : ''}`}><div><div className="disc-title"><strong>{d.discrepancy_number}</strong><StatusBadge status={d.symbol} /><StatusBadge status={d.status} /></div><p>{d.description}</p><small>{d.assigned_shop} · Reported by {d.reported_by} · {d.reported_date}</small></div>{d.status === 'OPEN' && <button className="secondary" onClick={close}>Close</button>}</div>;
}

function DataTable({ headers, rows }) {
  return <div className="table-wrap"><table><thead><tr>{headers.map(h => <th key={h}>{h}</th>)}</tr></thead><tbody>{rows.map((row, i) => <tr key={i}>{row.map((cell, j) => <td key={j}>{cell}</td>)}</tr>)}</tbody></table></div>;
}
