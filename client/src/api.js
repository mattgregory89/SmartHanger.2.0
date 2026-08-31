const API = '/api';

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });

  const data = await response.json();
  if (!response.ok) throw new Error(data.message || 'Request failed');
  return data;
}

export const api = {
  login: (username, password) => request('/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  dashboard: () => request('/dashboard'),
  aircraft: () => request('/aircraft'),
  aircraftDetail: id => request(`/aircraft/${id}`),
  timeline: id => request(`/aircraft/${id}/timeline`),
  turnover: id => request(`/aircraft/${id}/turnover`),
  search: q => request(`/search?q=${encodeURIComponent(q)}`),
  setStatus: (id, status) => request(`/aircraft/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  addDiscrepancy: (id, body) => request(`/aircraft/${id}/discrepancies`, { method: 'POST', body: JSON.stringify(body) }),
  closeDiscrepancy: (id, body) => request(`/discrepancies/${id}/close`, { method: 'PATCH', body: JSON.stringify(body) }),
  addAction: (id, body) => request(`/discrepancies/${id}/actions`, { method: 'POST', body: JSON.stringify(body) }),
  discrepancyActions: id => request(`/discrepancies/${id}/actions`),
  addServicing: (id, body) => request(`/aircraft/${id}/servicing`, { method: 'POST', body: JSON.stringify(body) })
};
