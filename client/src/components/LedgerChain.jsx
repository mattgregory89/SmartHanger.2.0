import React from 'react';

export default function LedgerChain({ blocks = [], valid = true }) {
  const preview = blocks.slice(Math.max(0, blocks.length - 5));

  return (
    <div className="ledger-chain">
      {!preview.length ? (
        <div className="empty-state">No ledger blocks found.</div>
      ) : (
        preview.map((block, index) => (
          <React.Fragment key={block.index ?? index}>
            <div className={`ledger-block ${valid ? 'valid' : 'invalid'}`}>
              <strong>Block {block.index ?? index}</strong>
              <small>{String(block.timestamp || '').slice(0, 19).replace('T', ' ')}</small>
              <div className="ledger-hash">
                <span>Hash</span>
                <code>{String(block.hash || '').slice(0, 12)}...</code>
              </div>
              <div className="ledger-hash">
                <span>Prev</span>
                <code>{String(block.previousHash || '').slice(0, 12)}...</code>
              </div>
            </div>
            {index < preview.length - 1 && <div className="ledger-arrow">→</div>}
          </React.Fragment>
        ))
      )}
    </div>
  );
}