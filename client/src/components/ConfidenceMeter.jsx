import React from 'react';

export default function ConfidenceMeter({ value = 0 }) {
  const pct = Math.max(0, Math.min(100, Math.round((Number(value) || 0) * 100)));

  return (
    <div className="confidence-meter">
      <div className="confidence-meter-top">
        <strong>AI Confidence</strong>
        <span>{pct}%</span>
      </div>
      <div className="confidence-track">
        <div className="confidence-fill" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}