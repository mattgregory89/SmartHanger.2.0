import React, { useEffect, useState } from 'react';
import { offline } from '../api.js';

export default function OfflineStatus() {
  const [online, setOnline] = useState(navigator.onLine);
  const [pending, setPending] = useState(offline.count());
  const [syncing, setSyncing] = useState(false);

  async function refresh() {
    setOnline(navigator.onLine);
    setPending(offline.count());
  }

  async function syncNow() {
    if (!navigator.onLine || syncing) return;
    setSyncing(true);
    await offline.flush();
    setSyncing(false);
    refresh();
  }

  useEffect(() => {
    const onOnline = async () => { setOnline(true); await syncNow(); };
    const onOffline = () => setOnline(false);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    window.addEventListener('smarthangar-queue-changed', refresh);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
      window.removeEventListener('smarthangar-queue-changed', refresh);
    };
  }, []);

  return (
    <div className={`offline-status ${online ? 'online' : 'offline'}`}>
      <span>{online ? 'Online' : 'Offline mode'}</span>
      {pending > 0 && <strong>{pending} pending</strong>}
      {online && pending > 0 && <button type="button" onClick={syncNow}>{syncing ? 'Syncing…' : 'Sync now'}</button>}
    </div>
  );
}
