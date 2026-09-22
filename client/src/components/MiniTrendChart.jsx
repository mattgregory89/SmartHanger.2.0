import React from 'react';

export default function MiniTrendChart({
  points = [],
  title = '',
  labels = []
}) {
  if (!points.length) {
    return <div className="viz-card"><div className="empty-state">No trend data.</div></div>;
  }

  const width = 320;
  const height = 140;
  const padding = 18;

  const max = Math.max(...points, 1);
  const min = Math.min(...points, 0);
  const range = Math.max(max - min, 1);

  const coords = points.map((p, i) => {
    const x = padding + (i * (width - padding * 2)) / Math.max(points.length - 1, 1);
    const y = height - padding - ((p - min) / range) * (height - padding * 2);
    return { x, y, value: p, label: labels[i] || `P${i + 1}` };
  });

  const path = coords.map((c, i) => `${i === 0 ? 'M' : 'L'} ${c.x} ${c.y}`).join(' ');

  return (
    <div className="viz-card">
      {title && <div className="viz-title">{title}</div>}
      <svg width="100%" viewBox={`0 0 ${width} ${height}`} className="trend-svg">
        <path d={path} fill="none" stroke="#1e5f9e" strokeWidth="3" />
        {coords.map((c, i) => (
          <g key={i}>
            <circle cx={c.x} cy={c.y} r="4" fill="#1e5f9e" />
            <text x={c.x} y={height - 2} textAnchor="middle" className="axis-label">
              {c.label}
            </text>
          </g>
        ))}
      </svg>
    </div>
  );
}