// AircraftPage.jsx
import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api.js';
import StatusBadge from '../components/StatusBadge.jsx';
import VoiceFillButton from '../components/VoiceFillButton.jsx';
import AircraftSilhouette from '../components/AircraftSilhouette.jsx';
import ConfidenceMeter from '../components/ConfidenceMeter.jsx';

const tabs = ['Overview', 'Discrepancies', 'Inspections', 'Time Changes', 'Engines', 'Servicing', 'Modifications', 'History', 'Shift Turnover'];

function inferIssueAreas(discrepancies) {
  const areas = {};

  function setArea(name, severity) {
    if (!areas[name]) {
      areas[name] = severity;
      return;
    }
    if (areas[name] !== 'high' && severity === 'high') {
      areas[name] = 'high';
    }
  }

  for (const d of discrepancies.filter(x => x.status === 'OPEN')) {
    const text = `${d.description || ''} ${d.assigned_shop || ''}`.toLowerCase();
    const severity = d.symbol === 'RED X' ? 'high' : 'medium';

    if (/engine|oil|apu/.test(text)) setArea('engine', severity);
    if (/hydraulic|gear|brake|mlg|nlg/.test(text)) setArea('gear', severity);
    if (/wing|flap|aileron|flight control/.test(text)) setArea('wing', severity);
    if (/tail|elevator|rudder/.test(text)) setArea('tail', severity);
    if (/fuselage|cargo|door|ramp|skin/.test(text)) setArea('body', severity);
    if (/cockpit|window|windshield|nose|avionic|radio|light|elect/.test(text)) setArea('nose', severity);
  }

  return areas;
}

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
    async function loadTabData() {
      try {
        if (tab === 'History') {
          const result = await api.timeline(id);
          setTimeline(result ?? []);
        }

        if (tab === 'Shift Turnover') {
          const result = await api.turnover(id);
          setTurnover(result ?? null);
        }
      } catch (error) {
        console.error(`Failed to load ${tab}:`, error);
        setMessage(`${tab} could not be loaded: ${error?.message || 'Unknown error'}`);
      }
    }

    loadTabData();
  }, [tab, id]);

  if (!data) return <div>Loading aircraft...</div>;
  const a = data.aircraft;

  const discrepancies = data.discrepancies ?? [];
  const inspections = data.inspections ?? [];
  const timeChanges = data.timeChanges ?? [];
  const engines = data.engines ?? [];
  const servicing = data.servicing ?? [];
  const modifications = data.modifications ?? [];

  const open = discrepancies.filter(d => d.status === 'OPEN');
  const redX = open.filter(d => d.symbol === 'RED X').length;

  const latestFuel = servicing.find(s => s.type === 'FUEL');
  const latestLox = servicing.find(s => s.type === 'LOX');

  const issueAreas = inferIssueAreas(discrepancies);

  async function addDiscrepancy(e) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const result = await api.addDiscrepancy(id, {
      description: form.get('description'), symbol: form.get('symbol'), assignedShop: form.get('shop'), reportedBy: user.full_name
    });
    e.currentTarget.reset();
    if (result.queued) { setMessage('Offline: discrepancy queued and will sync when connection returns.'); return; }
    setMessage('Discrepancy added.'); await reload();
  }


  async function addServicing(e) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    const result = await api.addServicing(id, { type: form.get('type'), quantity: form.get('quantity'), unit: form.get('unit'), notes: form.get('notes'), servicedBy: user.full_name });
    e.currentTarget.reset();
    if (result.queued) { setMessage('Offline: servicing record queued for sync.'); return; }
    setMessage('Servicing record saved.'); await reload();
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

      <nav className="tabs">
        {tabs.map(t => (
          <button
            type="button"
            key={t}
            className={tab === t ? 'active' : ''}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </nav>

      {tab === 'Overview' && <>
        <section className="metric-grid overview-metrics">
          <div className="metric"><span>Aircraft Status</span><strong><StatusBadge status={a.status} /></strong></div>
          <div className="metric"><span>Total Hours</span><strong>{a.total_hours.toLocaleString()}</strong></div>
          <div className="metric"><span>Total Cycles</span><strong>{a.total_cycles.toLocaleString()}</strong></div>
          <div className="metric"><span>Open Discrepancies</span><strong>{open.length}</strong></div>
          <div className="metric"><span>Open Red X</span><strong>{redX}</strong></div>
          <div className="metric"><span>Fuel</span><strong>{latestFuel ? `${latestFuel.quantity.toLocaleString()} ${latestFuel.unit}` : '—'}</strong></div>
          <div className="metric"><span>LOX</span><strong>{latestLox ? `${latestLox.quantity} ${latestLox.unit}` : '—'}</strong></div>
          <div className="metric"><span>Time Changes</span><strong>{timeChanges.length}</strong></div>
        </section>
        <section className="panel">
          <div className="panel-title">
            <h2>Quick Actions</h2>
            <span>Common maintenance actions</span>
          </div>
          <section className="panel">
            <div className="panel-title">
              <h2>Aircraft Condition Graphic</h2>
              <span>Visualized maintenance hotspots</span>
            </div>
            <AircraftSilhouette areas={issueAreas} />
          </section>

          <div className="quick-actions">
            <button type="button" onClick={() => setTab('Discrepancies')}>
              + Add Discrepancy
            </button>

            <button type="button" onClick={() => setTab('Servicing')}>
              + Record Servicing
            </button>

            <button type="button" onClick={() => setTab('Inspections')}>
              View Inspections
            </button>

            <button type="button" onClick={() => setTab('Time Changes')}>
              View Time Changes
            </button>

            <button type="button" onClick={() => setTab('Engines')}>
              View Engines
            </button>

            <button type="button" onClick={() => setTab('Modifications')}>
              View Modifications
            </button>
          </div>
        </section>
        <div className="two-col">
          <section className="panel"><div className="panel-title"><h2>Current Discrepancies</h2></div>{open.length ? open.map(d => <DiscrepancyCard key={d.id} d={d} user={user} />) : <div className="empty-state">No open discrepancies.</div>}</section>
          <section className="panel"><div className="panel-title"><h2>Manual Status</h2><span>For demo / production control</span></div><div className="status-buttons"><button type="button" onClick={() => setStatus('FMC')}>FMC</button><button type="button" onClick={() => setStatus('PMC')}>PMC</button><button type="button" onClick={() => setStatus('NMC')}>NMC</button></div><p className="hint">Opening a Red X automatically places the aircraft NMC. Closing the last Red X automatically returns an NMC aircraft to FMC.</p></section>
        </div>
      </>}

      {tab === 'Discrepancies' && <>
        <section className="panel"><div className="panel-title"><h2>Open New Discrepancy</h2><span>Enter once; reuse everywhere</span></div>
          <form className="form-grid" onSubmit={addDiscrepancy}>
            <label className="wide">Discrepancy description<div className="voice-field"><input id="new-discrepancy-description" name="description" required placeholder="RH MLG hydraulic line leaking" /><VoiceFillButton targetId="new-discrepancy-description" label="Dictate discrepancy" /></div></label>
            <label>Symbol<select name="symbol"><option>RED X</option><option>RED DASH</option><option>INFORMATIONAL</option></select></label>
            <label>Assigned shop<select name="shop"><option>CREW CHIEF</option><option>HYDRAULICS</option><option>ELECTRICAL</option><option>AVIONICS</option><option>ENGINES</option><option>SHEET METAL</option></select></label>
            <button className="wide" type="submit">Open Discrepancy</button>
          </form>
        </section>
        <section className="panel"><div className="panel-title"><h2>Discrepancy Records</h2></div>{discrepancies.map(d => <DiscrepancyCard key={d.id} d={d} user={user} />)}</section>
      </>}

      {tab === 'Inspections' && <section className="panel"><div className="panel-title"><h2>Inspection Schedule</h2></div><DataTable headers={['Inspection', 'Due', 'Remaining', 'Last Completed', 'Status']} rows={inspections.map(i => [i.name, i.due_hours ? `${i.due_hours} hrs` : i.due_date, i.hours_remaining !== null ? `${i.hours_remaining} hrs` : 'Calendar controlled', i.last_completed_date || '—', <StatusBadge status={i.status} />])} /></section>}

      {tab === 'Time Changes' && <section className="panel"><div className="panel-title"><h2>Time-Change Items</h2><span>Hours / calendar controlled</span></div><DataTable headers={['Item', 'Part / Serial', 'Due', 'Remaining', 'Warning']} rows={timeChanges.map(t => [t.name, `${t.part_number || '—'} / ${t.serial_number || '—'}`, t.due_hours ? `${t.due_hours} hrs` : t.due_date, t.hours_remaining !== null ? `${t.hours_remaining} hrs` : 'Calendar controlled', t.warning_hours ? `${t.warning_hours} hrs` : '—'])} /></section>}

      {tab === 'Engines' && <section className="panel"><div className="panel-title"><h2>Installed Engines</h2></div><DataTable headers={['Position', 'Serial Number', 'Hours', 'Cycles', 'Installed']} rows={engines.map(e => [`#${e.position}`, e.serial_number, e.total_hours.toLocaleString(), e.total_cycles.toLocaleString(), e.installed_date || '—'])} /></section>}

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
        <section className="panel"><DataTable headers={['Date', 'Type', 'Quantity', 'Serviced By', 'Notes']} rows={servicing.map(s => [s.service_date, s.type, `${s.quantity.toLocaleString()} ${s.unit}`, s.serviced_by, s.notes || '—'])} /></section>
      </>}

      {tab === 'Modifications' && <section className="panel"><div className="panel-title"><h2>Aircraft Modifications</h2></div><DataTable headers={['Modification', 'Title', 'Status', 'Completed', 'Notes']} rows={modifications.map(m => [m.mod_number, m.title, <StatusBadge status={m.status} />, m.completed_date || '—', m.notes || '—'])} /></section>}

      {tab === 'History' && <section className="panel"><div className="panel-title"><h2>Aircraft Timeline</h2><span>Combined maintenance history</span></div><div className="timeline">{timeline.map((event, i) => <div className="timeline-event" key={i}><div className="timeline-dot" /><div><small>{event.event_date}</small><strong>{event.event_type}</strong><p>{event.description}</p></div></div>)}</div></section>}

      {tab === 'Shift Turnover' && (
        turnover ? (
          <section className="panel turnover">
            <div className="panel-title">
              <h2>Shift Turnover</h2>
              <span>At-a-glance continuity</span>
            </div>

            <div className="turnover-header">
              <div>
                <small>AIRCRAFT</small>
                <strong>{turnover.aircraft?.tail_number || a.tail_number}</strong>
              </div>
              <div>
                <small>STATUS</small>
                <StatusBadge status={turnover.aircraft?.status || a.status} />
              </div>
              <div>
                <small>LOCATION</small>
                <strong>{turnover.aircraft?.location || a.location || '—'}</strong>
              </div>
            </div>

            <h3>Open Maintenance</h3>
            {(turnover.openDiscrepancies ?? []).length ? (
              (turnover.openDiscrepancies ?? []).map(d => (
                <div className="turnover-job" key={d.id}>
                  <strong>{d.discrepancy_number} · {d.description}</strong>
                  <span><StatusBadge status={d.symbol} /> {d.assigned_shop}</span>
                  <small>Reported by {d.reported_by} · {d.reported_date}</small>
                </div>
              ))
            ) : (
              <p>No open maintenance.</p>
            )}

            <h3>Latest Servicing</h3>
            <DataTable
              headers={['Type', 'Quantity', 'Date', 'Serviced By']}
              rows={(turnover.latestServicing ?? []).map(s => [
                s.type,
                `${s.quantity} ${s.unit}`,
                s.service_date,
                s.serviced_by
              ])}
            />
          </section>
        ) : (
          <section className="panel">
            <div className="empty-state">Loading shift turnover...</div>
          </section>
        )
      )}
    </>
  );
}

function DiscrepancyCard({ d, user }) {
  const [aiDraft, setAiDraft] = useState(null);
  const [aiBusy, setAiBusy] = useState(false);
  const [aiError, setAiError] = useState('');

  const [showCloseout, setShowCloseout] = useState(false);
  const [workPerformed, setWorkPerformed] = useState('');
  const [closeoutDraft, setCloseoutDraft] = useState('');
  const [closeoutBusy, setCloseoutBusy] = useState(false);
  const [closeoutError, setCloseoutError] = useState('');
  const [followOnRecommended, setFollowOnRecommended] = useState(false);
  const [followOnDescription, setFollowOnDescription] = useState('');
  const [followOnShop, setFollowOnShop] = useState('CREW CHIEF');
  const [followOnSymbol, setFollowOnSymbol] = useState('RED X');
  const [createFollowOn, setCreateFollowOn] = useState(false);

  async function getAiDraft() {
    setAiBusy(true);
    setAiError('');

    try {
      const result = await api.aiSuggestion(d.id);

      console.log('AI draft result:', result);

      setAiDraft(result);
    } catch (error) {
      console.error('AI draft request failed:', error);

      setAiDraft(null);
      setAiError(error?.message || 'AI draft request failed.');
    } finally {
      setAiBusy(false);
    }
  }

  async function decide(decision) {
    if (!aiDraft) return;

    try {
      await api.decideAiSuggestion(
        aiDraft.auditId,
        {
          decision,
          decidedBy: user?.full_name || 'Demo Maintainer'
        }
      );

      if (decision === 'ACCEPTED') {
        await api.addAction(
          d.id,
          {
            actionText: aiDraft.suggestedAction,
            performedBy: user?.full_name || 'Demo Maintainer'
          }
        );
      }

      setAiDraft(null);
      setAiError('');
    } catch (error) {
      console.error('AI decision failed:', error);
      setAiError(error?.message || 'Could not process AI decision.');
    }
  }

  async function generateCloseoutDraft() {
    if (!workPerformed.trim()) {
      setCloseoutError(
        'Enter the maintenance actually performed before generating a closeout draft.'
      );
      return;
    }

    setCloseoutBusy(true);
    setCloseoutError('');

    try {
      const result = await api.aiCloseoutDraft(
        d.id,
        workPerformed
      );

      console.log('AI closeout draft:', result);

      setCloseoutDraft(
        result.correctiveAction || ''
      );
      const recommended =
        Boolean(result.followOnDiscrepancyRecommended);

      setFollowOnRecommended(recommended);

      setFollowOnDescription(
        result.followOnDescription || ''
      );

      setFollowOnShop(
        result.followOnShop || 'CREW CHIEF'
      );

      setFollowOnSymbol(
        result.followOnSymbol || 'RED X'
      );

      setCreateFollowOn(recommended);
    } catch (error) {
      console.error('AI closeout request failed:', error);

      setCloseoutError(
        error?.message || 'Could not generate AI closeout draft.'
      );
    } finally {
      setCloseoutBusy(false);
    }
  }

  async function finishCloseout() {
    if (!closeoutDraft.trim()) {
      setCloseoutError(
        'Review or enter a corrective action before closing the discrepancy.'
      );
      return;
    }

    try {
      if (
        createFollowOn &&
        followOnRecommended &&
        followOnDescription.trim()
      ) {
        await api.addDiscrepancy(
          d.aircraft_id,
          {
            description: followOnDescription.trim(),
            symbol: followOnSymbol,
            assignedShop: followOnShop,
            reportedBy:
              user?.full_name || 'Demo Maintainer'
          }
        );
      }

      const result = await api.closeDiscrepancy(
        d.id,
        {
          correctiveAction: closeoutDraft,
          closedBy:
            user?.full_name || 'Demo Maintainer'
        }
      );

      if (result.queued) {
        setCloseoutError(
          'Offline: close action queued and will sync when connectivity returns.'
        );
        return;
      }

      window.location.reload();

    } catch (error) {
      console.error(
        'Close discrepancy failed:',
        error
      );

      setCloseoutError(
        error?.message ||
        'Could not close discrepancy.'
      );
    }
  }

  return (
    <div className={`discrepancy-card ${d.status === 'CLOSED' ? 'closed' : ''}`}>
      <div>
        <div className="disc-title">
          <strong>{d.discrepancy_number}</strong>
          <StatusBadge status={d.symbol} />
          <StatusBadge status={d.status} />
        </div>

        <p>{d.description}</p>

        <small>
          {d.assigned_shop} · Reported by {d.reported_by} · {d.reported_date}
        </small>

        {d.status === 'OPEN' && (
          <div className="ai-actions">
            <button
              className="secondary"
              onClick={getAiDraft}
              disabled={aiBusy}
            >
              {aiBusy ? 'Drafting…' : 'AI Draft'}
            </button>

            <button
              className="secondary"
              onClick={() => {
                setShowCloseout(true);
                setCloseoutError('');
              }}
            >
              AI Closeout
            </button>
          </div>
        )}

        {aiError && (
          <div className="ai-draft">
            <strong>AI request failed</strong>
            <p>{aiError}</p>
          </div>
        )}

        {aiDraft && (
          <div className="ai-draft">
            <strong>AI-assisted maintenance draft</strong>

            <div className="ai-meta">
              <StatusBadge status={aiDraft.live ? 'LIVE AI' : 'DEMO AI'} />
              <StatusBadge status={aiDraft.riskLevel || 'REVIEW'} />
              <span>{aiDraft.suggestedShop || 'Shop review'}</span>
            </div>

            <ConfidenceMeter value={aiDraft.confidence} />

            {aiDraft.summary && (
              <p>
                <strong>Summary:</strong> {aiDraft.summary}
              </p>
            )}

            <p>
              <strong>Draft action:</strong> {aiDraft.suggestedAction}
            </p>

            {!!aiDraft.suggestedCodes?.length && (
              <div className="code-chips">
                {aiDraft.suggestedCodes.map((code, i) => (
                  <span
                    className="code-chip"
                    key={`${code}-${i}`}
                  >
                    {code}
                  </span>
                ))}
              </div>
            )}

            {aiDraft.verificationSteps?.length > 0 && (
              <>
                <p>
                  <strong>Human verification:</strong>
                </p>

                <ul className="ai-verification">
                  {aiDraft.verificationSteps.map((step, i) => (
                    <li key={i}>{step}</li>
                  ))}
                </ul>
              </>
            )}

            {aiDraft.rationale && (
              <p className="hint">
                <strong>Why:</strong> {aiDraft.rationale}
              </p>
            )}

            {aiDraft.fallbackReason && (
              <p className="hint">
                <strong>Fallback:</strong> {aiDraft.fallbackReason}
              </p>
            )}

            <div className="ai-actions">
              <button onClick={() => decide('ACCEPTED')}>
                Accept & Add Action
              </button>

              <button
                className="secondary"
                onClick={() => decide('REJECTED')}
              >
                Reject
              </button>

              <button
                className="secondary"
                onClick={() => decide('BYPASSED')}
              >
                Bypass AI
              </button>
            </div>
          </div>
        )}

        {showCloseout && d.status === 'OPEN' && (
          <div className="ai-draft">
            <strong>AI-assisted Corrective Action</strong>

            <p className="hint">
              Enter only the maintenance actually performed.
              SmartHangar will turn those notes into a corrective-action draft.
            </p>

            <label>
              <strong>Actual Work Performed</strong>

              <div className="voice-field">
                <textarea
                  value={workPerformed}
                  onChange={e => setWorkPerformed(e.target.value)}
                  placeholder="Example: Found loose B-nut on RH MLG hydraulic line. Tightened connection, cleaned area, and performed leak check with no further leakage noted."
                  rows={5}
                />

                <VoiceFillButton
                  label="Dictate Work"
                  onTranscript={text =>
                    setWorkPerformed(previous =>
                      previous
                        ? `${previous} ${text}`
                        : text
                    )
                  }
                />
              </div>
            </label>

            <div className="ai-actions">
              <button
                onClick={generateCloseoutDraft}
                disabled={closeoutBusy}
              >
                {closeoutBusy
                  ? 'Drafting…'
                  : 'Generate AI Closeout Draft'}
              </button>

              <button
                className="secondary"
                onClick={() => {
                  setShowCloseout(false);
                  setWorkPerformed('');
                  setCloseoutDraft('');
                  setCloseoutError('');
                }}
              >
                Cancel
              </button>
            </div>

            {closeoutError && (
              <div className="error">
                {closeoutError}
              </div>
            )}

            {closeoutDraft && (
              <>
                <label>
                  <strong>Corrective Action Draft</strong>

                  <textarea
                    value={closeoutDraft}
                    onChange={e => setCloseoutDraft(e.target.value)}
                    rows={7}
                  />
                </label>

                <p className="hint">
                  Review and edit this statement before closing the discrepancy.
                  The AI should only restate maintenance that you actually entered.
                </p>

                <div className="ai-actions">
                  <button onClick={finishCloseout}>
                    Review Complete — Close Discrepancy
                  </button>
                </div>
              </>
            )}
            {followOnRecommended && (
              <div className="follow-on-card">
                <div className="panel-title">
                  <div>
                    <h3>Follow-On Maintenance Identified</h3>
                    <span>AI suggestion — maintainer approval required</span>
                  </div>
                </div>

                <label className="follow-on-toggle">
                  <input
                    type="checkbox"
                    checked={createFollowOn}
                    onChange={e => setCreateFollowOn(e.target.checked)}
                  />

                  Create follow-on discrepancy when closing
                </label>

                {createFollowOn && (
                  <div className="form-grid compact-form">
                    <label className="wide">
                      Follow-On Discrepancy

                      <textarea
                        value={followOnDescription}
                        onChange={e =>
                          setFollowOnDescription(e.target.value)
                        }
                        rows={4}
                      />
                    </label>

                    <label>
                      Symbol

                      <select
                        value={followOnSymbol}
                        onChange={e =>
                          setFollowOnSymbol(e.target.value)
                        }
                      >
                        <option>RED X</option>
                        <option>RED DASH</option>
                        <option>INFORMATIONAL</option>
                      </select>
                    </label>

                    <label>
                      Assigned Shop

                      <select
                        value={followOnShop}
                        onChange={e =>
                          setFollowOnShop(e.target.value)
                        }
                      >
                        <option>CREW CHIEF</option>
                        <option>HYDRAULICS</option>
                        <option>ELECTRICAL</option>
                        <option>AVIONICS</option>
                        <option>ENGINES</option>
                        <option>SHEET METAL</option>
                      </select>
                    </label>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {d.status === 'OPEN' && !showCloseout && (
        <button
          className="secondary"
          onClick={() => setShowCloseout(true)}
        >
          Close Discrepancy
        </button>
      )}
    </div>
  );
}

function DataTable({ headers, rows = [] }) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {headers.map(header => (
              <th key={header}>{header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length ? (
            rows.map((row, i) => (
              <tr key={i}>
                {row.map((cell, j) => (
                  <td key={j}>{cell}</td>
                ))}
              </tr>
            ))
          ) : (
            <tr>
              <td colSpan={headers.length}>
                <div className="empty-state">No records found.</div>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
