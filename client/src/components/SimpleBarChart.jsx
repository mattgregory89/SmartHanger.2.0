import React from 'react';

export default function SimpleBarChart({
  data = [],
  title = '',
  emptyText = 'No data.'
}) {
  const max = Math.max(...data.map(d => Number(d.value) || 0), 1);

  return (
    <div className="viz-card">
      {title && <div className="viz-title">{title}</div>}

      {!data.length ? (
        <div className="empty-state">{emptyText}</div>
      ) : (
        <div className="bar-chart">
          {data.map((item, index) => {
            const value = Number(item.value) || 0;
            const width = `${(value / max) * 100}%`;

            return (
              <div className="bar-row" key={`${item.label}-${index}`}>
                <div className="bar-label">{item.label}</div>
                <div className="bar-track">
                  <div className="bar-fill" style={{ width }} />
                </div>
                <div className="bar-value">{value}</div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}