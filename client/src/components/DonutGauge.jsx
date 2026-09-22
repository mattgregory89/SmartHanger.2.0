import React from 'react';

export default function DonutGauge({
  value = 0,
  total = 1,
  label = 'Readiness',
  subtitle = ''
}) {
  const safeTotal = Math.max(total, 1);
  const pct = Math.max(0, Math.min(1, value / safeTotal));
  const percentLabel = Math.round(pct * 100);

  const radius = 56;
  const stroke = 12;
  const size = 140;
  const circumference = 2 * Math.PI * radius;
  const dash = circumference * pct;

  return (
    <div className="viz-card donut-card">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle
          cx="70"
          cy="70"
          r={radius}
          fill="none"
          stroke="#e7edf3"
          strokeWidth={stroke}
        />
        <circle
          cx="70"
          cy="70"
          r={radius}
          fill="none"
          stroke="#1e5f9e"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circumference - dash}`}
          transform="rotate(-90 70 70)"
        />
        <text x="70" y="63" textAnchor="middle" className="donut-value">
          {percentLabel}%
        </text>
        <text x="70" y="84" textAnchor="middle" className="donut-small">
          FMC
        </text>
      </svg>

      <div className="viz-copy">
        <strong>{label}</strong>
        <p>{value} of {total} aircraft fully mission capable</p>
        {subtitle && <small>{subtitle}</small>}
      </div>
    </div>
  );
}