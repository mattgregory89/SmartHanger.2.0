import React, { useEffect, useMemo, useState } from 'react';
import { api } from '../api.js';
import StatusBadge from '../components/StatusBadge.jsx';
import SyncDiagram from '../components/SyncDiagram.jsx';

const PACKAGE_KEY = 'smarthangarOfflineAircraftPackage';
const EVENTS_KEY = 'smarthangarOfflineAircraftEvents';
const DEVICE = 'SMART-HANGAR-AIRCRAFT-DEMO-017';

function loadJson(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key) || 'null') ?? fallback; }
  catch { return fallback; }
}

export default function OfflineDemoPage({ user }) {
  const [pkg, setPkg] = useState(() => loadJson(PACKAGE_KEY, null));
  const [events, setEvents] = useState(() => loadJson(EVENTS_KEY, []));
  const [simulatedOffline, setSimulatedOffline] = useState(false);
  const [message, setMessage] = useState('');
  const [syncResult, setSyncResult] = useState(null);

  async function refreshPackage() {
    const next = await api.offlineDemoPackage(5);
    localStorage.setItem(PACKAGE_KEY, JSON.stringify(next));
    setPkg(next);
    return next;
  }

  useEffect(() => { if (!pkg) refreshPackage().catch(e => setMessage(e.message)); }, []);
  useEffect(() => { localStorage.setItem(EVENTS_KEY, JSON.stringify(events)); }, [events]);

  const snapshot = pkg?.snapshot;
  const localDiscrepancies = useMemo(() => events
    .filter(e => e.eventType === 'CREATE_DISCREPANCY')
    .map(e => ({ id: e.localEntityId, ...e.payload, discrepancy_number: 'LOCAL-PENDING', status: 'OPEN', local: true })), [events]);

  function queue(eventType, localEntityId, payload) {
    const item = { clientEventId: crypto.randomUUID(), eventType, localEntityId, payload, queuedAt: new Date().toISOString() };
    setEvents(prev => [...prev, item]);
    return item;
  }

  function addOfflineDiscrepancy(e) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const localId = `local-disc-${crypto.randomUUID()}`;
    queue('CREATE_DISCREPANCY', localId, {
      aircraftId: 5,
      description: f.get('description'),
      symbol: f.get('symbol'),
      assignedShop: f.get('shop'),
      reportedBy: user?.full_name || 'Offline Demo Maintainer'
    });
    e.currentTarget.reset();
    setMessage('Saved to the aircraft-local queue. Nothing has been sent to the hangar server yet.');
  }

  function addOfflineServicing(e) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    queue('CREATE_SERVICING', `local-service-${crypto.randomUUID()}`, {
      aircraftId: 5,
      type: f.get('type'), quantity: Number(f.get('quantity')), unit: f.get('unit'),
      servicedBy: user?.full_name || 'Offline Demo Maintainer', notes: f.get('notes')
    });
    e.currentTarget.reset();
    setMessage('Servicing record stored locally for later sync.');
  }

  function addAction(localDiscrepancyId) {
    const actionText = window.prompt('Enter the maintenance action documented while offline:');
    if (!actionText) return;
    queue('CREATE_ACTION', `local-action-${crypto.randomUUID()}`, {
      localDiscrepancyId, actionText, performedBy: user?.full_name || 'Offline Demo Maintainer'
    });
    setMessage('Maintenance action linked to the local discrepancy and queued.');
  }

  function closeLocal(localDiscrepancyId) {
    const correctiveAction = window.prompt('Enter the offline closing statement:');
    if (!correctiveAction) return;
    queue('CLOSE_DISCREPANCY', `local-close-${crypto.randomUUID()}`, {
      localDiscrepancyId, correctiveAction, closedBy: user?.full_name || 'Offline Demo Maintainer'
    });
    setMessage('Close event queued. The server will resolve the local discrepancy ID during sync.');
  }

  async function reconnectAndSync() {
    setMessage('Reconnecting and reconciling local events…');
    const result = await api.syncBatch({ deviceName: DEVICE, events });
    setSyncResult(result);
    const completed = new Set(result.results.filter(r => ['APPLIED', 'DUPLICATE'].includes(r.status)).map(r => r.clientEventId));
    setEvents(prev => prev.filter(e => !completed.has(e.clientEventId)));
    setSimulatedOffline(false);
    await refreshPackage();
    setMessage(`Sync complete: ${result.applied} applied, ${result.duplicates} duplicate-safe, ${result.failed} failed.`);
  }

  if (!pkg) return <div>Loading aircraft-local demo package…</div>;
  const aircraft = snapshot.aircraft;
  const remoteOpen = snapshot.discrepancies.filter(d => d.status === 'OPEN');

  return (
    <>
      <div className="page-heading">
        <div><h1>Offline Aircraft Integration Demo</h1><p>Aircraft-local maintenance → reconnect → central SmartHangar record</p></div>
        <StatusBadge status={simulatedOffline ? 'OFFLINE' : 'CONNECTED'} />
      </div>

      {message && <div className="success-banner" onClick={() => setMessage('')}>{message} ×</div>}

      <section className="panel offline-demo-hero">
        <div>
          <div className="eyebrow">Fictional demo dataset · {pkg.device.device_name}</div>
          <h2>{aircraft.tail_number} · {aircraft.model}</h2>
          <p>{aircraft.location} · Cached at {aircraft.last_updated}</p>
        </div>
        <div className="demo-controls">
          {!simulatedOffline
            ? <button onClick={() => { setSimulatedOffline(true); setMessage('Network loss simulated. New maintenance is now local-only.'); }}>Simulate Network Loss</button>
            : <button onClick={reconnectAndSync} disabled={!events.length}>Reconnect & Sync {events.length ? `(${events.length})` : ''}</button>}
          <button className="secondary" onClick={refreshPackage} disabled={simulatedOffline}>Refresh Cached Package</button>
        </div>
      </section>

      <section className="metric-grid overview-metrics">
        <div className="metric"><span>Connection</span><strong>{simulatedOffline ? 'LOCAL' : 'SERVER'}</strong></div>
        <div className="metric"><span>Aircraft Status</span><strong><StatusBadge status={aircraft.status} /></strong></div>
        <div className="metric"><span>Server Open MX</span><strong>{remoteOpen.length}</strong></div>
        <div className="metric"><span>Local Pending Events</span><strong>{events.length}</strong></div>
      </section>

      <section className="panel">
        <div className="panel-title">
          <h2>Offline Synchronization</h2>
          <span>Visual demo of aircraft-local maintenance</span>
        </div>
        <SyncDiagram
          offline={simulatedOffline}
          queuedCount={events.length}
          syncResult={syncResult}
        />
      </section>

      {!simulatedOffline && <section className="panel"><div className="empty-state">Press <strong>Simulate Network Loss</strong> to enter maintenance on the aircraft-local workspace, then reconnect to integrate it into the central database and ledger.</div></section>}

      {simulatedOffline && <div className="two-col">
        <section className="panel">
          <div className="panel-title"><h2>Local Discrepancy</h2><span>Stored on aircraft device</span></div>
          <form className="form-grid compact-form" onSubmit={addOfflineDiscrepancy}>
            <label className="wide">Description<input name="description" required placeholder="APU oil quantity indication intermittent" /></label>
            <label>Symbol<select name="symbol"><option>RED X</option><option>RED DASH</option><option>INFORMATIONAL</option></select></label>
            <label>Shop<select name="shop"><option>CREW CHIEF</option><option>HYDRAULICS</option><option>ELECTRICAL</option><option>AVIONICS</option><option>ENGINES</option></select></label>
            <button className="wide">Save Locally</button>
          </form>
        </section>

        <section className="panel">
          <div className="panel-title"><h2>Local Servicing</h2><span>Stored on aircraft device</span></div>
          <form className="form-grid compact-form" onSubmit={addOfflineServicing}>
            <label>Type<input name="type" required defaultValue="FUEL" /></label>
            <label>Quantity<input name="quantity" type="number" required defaultValue="48000" /></label>
            <label>Unit<input name="unit" required defaultValue="LBS" /></label>
            <label className="wide">Notes<input name="notes" defaultValue="Recorded while disconnected from hangar network" /></label>
            <button className="wide">Save Locally</button>
          </form>
        </section>
      </div>}

      <section className="panel">
        <div className="panel-title"><h2>Aircraft-Local Queue</h2><span>{events.length} unsynchronized event(s)</span></div>
        {!events.length ? <div className="empty-state">No local changes waiting for sync.</div> :
          <div className="stack-list">{events.map(event => <div className="list-item" key={event.clientEventId}>
            <div><strong>{event.eventType}</strong><p>{event.payload.description || event.payload.actionText || event.payload.correctiveAction || `${event.payload.type || ''} ${event.payload.quantity || ''} ${event.payload.unit || ''}`}</p><small>{event.localEntityId} · {event.queuedAt}</small></div>
            <StatusBadge status="PENDING" />
          </div>)}</div>}
      </section>

      {localDiscrepancies.length > 0 && <section className="panel">
        <div className="panel-title"><h2>Locally Created Discrepancies</h2><span>Actions can reference temporary IDs</span></div>
        {localDiscrepancies.map(d => <div className="discrepancy-card" key={d.id}>
          <div><div className="disc-title"><strong>LOCAL-PENDING</strong><StatusBadge status={d.symbol} /></div><p>{d.description}</p><small>{d.assignedShop} · temporary ID {d.id}</small></div>
          <div className="ai-actions"><button className="secondary" onClick={() => addAction(d.id)}>Add Local Action</button><button className="secondary" onClick={() => closeLocal(d.id)}>Close Locally</button></div>
        </div>)}
      </section>}

      <section className="panel">
        <div className="panel-title"><h2>Cached Aircraft Data</h2><span>Available with no network</span></div>
        <div className="two-col">
          <div><h3>Open maintenance at disconnect</h3>{remoteOpen.length ? remoteOpen.map(d => <p key={d.id}><strong>{d.discrepancy_number}</strong> · {d.description}</p>) : <p>No open maintenance in cached snapshot.</p>}</div>
          <div><h3>Cached servicing</h3>{snapshot.servicing.slice(0, 4).map(s => <p key={s.id}><strong>{s.type}</strong> · {s.quantity} {s.unit} · {s.service_date}</p>)}</div>
        </div>
      </section>

      {syncResult && <section className="panel">
        <div className="panel-title"><h2>Last Reconciliation Receipt</h2><span>Idempotent server response</span></div>
        <pre className="json-preview">{JSON.stringify(syncResult, null, 2)}</pre>
      </section>}
    </>
  );
}
