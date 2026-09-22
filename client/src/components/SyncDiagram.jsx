import React from 'react';

export default function SyncDiagram({
  offline = false,
  queuedCount = 0,
  syncResult = null
}) {
  const applied = syncResult?.appliedCount || 0;
  const duplicate = syncResult?.duplicateCount || 0;
  const failed = syncResult?.failedCount || 0;

  return (
    <div className="sync-diagram">
      <div className="sync-node">
        <div className="sync-icon">✈</div>
        <strong>Aircraft Device</strong>
        <small>{offline ? 'Disconnected local workspace' : 'Connected to hangar network'}</small>
        <div className="sync-pill">Queued: {queuedCount}</div>
      </div>

      <div className={`sync-link ${offline ? 'offline' : 'online'}`}>
        <div className="sync-link-line" />
        <span>{offline ? 'NO LINK' : 'SYNC ACTIVE'}</span>
      </div>

      <div className="sync-node">
        <div className="sync-icon">☁</div>
        <strong>Central Hangar System</strong>
        <small>Database + ledger + audit trail</small>
        <div className="sync-receipt">
          <div>Applied: {applied}</div>
          <div>Duplicate: {duplicate}</div>
          <div>Failed: {failed}</div>
        </div>
      </div>
    </div>
  );
}