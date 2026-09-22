const API = '/api';
const QUEUE_KEY = 'smarthangarOfflineQueue';

function getQueue() {
  try { return JSON.parse(localStorage.getItem(QUEUE_KEY) || '[]'); }
  catch { return []; }
}

function saveQueue(queue) {
  localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
  window.dispatchEvent(new CustomEvent('smarthangar-queue-changed'));
}

function queueMutation(path, options) {
  const item = {
    id: crypto.randomUUID(),
    path,
    method: options.method || 'POST',
    body: options.body ? JSON.parse(options.body) : null,
    queuedAt: new Date().toISOString()
  };
  const queue = getQueue();
  queue.push(item);
  saveQueue(queue);
  return { queued: true, clientEventId: item.id, queuedAt: item.queuedAt };
}

async function request(path, options = {}, { queueWhenOffline = false } = {}) {
  if (queueWhenOffline && !navigator.onLine) return queueMutation(path, options);

  try {
    const response = await fetch(`${API}${path}`, {
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options
    });

    const data = await response.json();
    if (!response.ok) throw new Error(data.message || 'Request failed');
    return data;
  } catch (error) {
    if (queueWhenOffline && error instanceof TypeError) return queueMutation(path, options);
    throw error;
  }
}

async function flushOfflineQueue() {
  if (!navigator.onLine) return { synced: 0, remaining: getQueue().length };
  const queue = getQueue();
  const remaining = [];
  let synced = 0;

  for (const item of queue) {
    try {
      const response = await fetch(`${API}${item.path}`, {
        method: item.method,
        headers: { 'Content-Type': 'application/json' },
        body: item.body == null ? undefined : JSON.stringify(item.body)
      });
      if (!response.ok) throw new Error('Sync failed');
      await fetch(`${API}/sync/events`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          clientEventId: item.id,
          eventType: `${item.method} ${item.path}`,
          payload: item.body,
          deviceName: 'BROWSER-OFFLINE-QUEUE'
        })
      });
      synced += 1;
    } catch {
      remaining.push(item);
    }
  }

  saveQueue(remaining);
  return { synced, remaining: remaining.length };
}

export const offline = {
  count: () => getQueue().length,
  list: getQueue,
  flush: flushOfflineQueue
};

export const api = {
  login: (username, password) => request('/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  dashboard: () => request('/dashboard'),
  aircraft: () => request('/aircraft'),
  aircraftDetail: id => request(`/aircraft/${id}`),
  timeline: id => request(`/aircraft/${id}/timeline`),
  turnover: id => request(`/aircraft/${id}/turnover`),
  search: q => request(`/search?q=${encodeURIComponent(q)}`),
  ledger: () => request('/ledger'),
  validateLedger: () => request('/ledger/validate'),
  setStatus: (id, status) => request(`/aircraft/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }, { queueWhenOffline: true }),
  addDiscrepancy: (id, body) => request(`/aircraft/${id}/discrepancies`, { method: 'POST', body: JSON.stringify(body) }, { queueWhenOffline: true }),
  closeDiscrepancy: (id, body) => request(`/discrepancies/${id}/close`, { method: 'PATCH', body: JSON.stringify(body) }, { queueWhenOffline: true }),
  addAction: (id, body) => request(`/discrepancies/${id}/actions`, { method: 'POST', body: JSON.stringify(body) }, { queueWhenOffline: true }),
  discrepancyActions: id => request(`/discrepancies/${id}/actions`),
  addServicing: (id, body) => request(`/aircraft/${id}/servicing`, { method: 'POST', body: JSON.stringify(body) }, { queueWhenOffline: true }),
  addInspection: (id, body) => request(`/aircraft/${id}/inspections`, { method: 'POST', body: JSON.stringify(body) }, { queueWhenOffline: true }),
  completeInspection: (id, body) => request(`/inspections/${id}/complete`, { method: 'PATCH', body: JSON.stringify(body) }, { queueWhenOffline: true }),
  addTimeChange: (id, body) => request(`/aircraft/${id}/time-changes`, { method: 'POST', body: JSON.stringify(body) }, { queueWhenOffline: true }),
  addModification: (id, body) => request(`/aircraft/${id}/modifications`, { method: 'POST', body: JSON.stringify(body) }, { queueWhenOffline: true }),
  updateModification: (id, body) => request(`/modifications/${id}`, { method: 'PATCH', body: JSON.stringify(body) }, { queueWhenOffline: true }),
  aiSuggestion: (id, prompt = '') => request(`/discrepancies/${id}/ai-suggestion`, { method: 'POST', body: JSON.stringify({ prompt }) }),
  decideAiSuggestion: (id, body) => request(`/ai-suggestions/${id}/decision`, { method: 'PATCH', body: JSON.stringify(body) }),
  syncHistory: () => request('/sync/events'),
  aiStatus: () => request('/ai/status'),
  offlineDemoPackage: (aircraftId = 5) => request(`/offline-demo/package?aircraftId=${aircraftId}`),
  syncBatch: body => request('/sync/batch', { method: 'POST', body: JSON.stringify(body) }),
  analytics: () => request('/analytics'),
  aiCloseoutDraft: (id, workPerformed) =>
  request(
    `/discrepancies/${id}/ai-closeout`,
    {
      method: 'POST',
      body: JSON.stringify({ workPerformed })
    }
  ),
};

