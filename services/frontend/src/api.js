// API layer — all calls to the query-router backend
const BASE = '/api';

export async function searchDocuments(query) {
  const res = await fetch(`${BASE}/search?q=${encodeURIComponent(query)}`);
  if (!res.ok) throw new Error(`Search failed: ${res.status}`);
  return res.json();
}

export async function indexDocument(doc) {
  const res = await fetch(`${BASE}/index`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(doc),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(text || `Index failed: ${res.status}`);
  return text;
}

export async function getClusterTopology() {
  const res = await fetch(`${BASE}/cluster/topology`);
  if (!res.ok) throw new Error(`Topology fetch failed: ${res.status}`);
  return res.json();
}

export async function getHealth() {
  try {
    const res = await fetch(`${BASE}/health`, { signal: AbortSignal.timeout(3000) });
    return res.ok;
  } catch {
    return false;
  }
}

export async function stopNode(nodeId) {
  const res = await fetch(`${BASE}/node/${nodeId}/stop`, { method: 'POST' });
  const text = await res.text();
  if (!res.ok) throw new Error(text || `Stop failed: ${res.status}`);
  return text;
}

export async function startNode(nodeId) {
  const res = await fetch(`${BASE}/node/${nodeId}/start`, { method: 'POST' });
  const text = await res.text();
  if (!res.ok) throw new Error(text || `Start failed: ${res.status}`);
  return text;
}

export async function getNodeStatus(nodeId) {
  const res = await fetch(`${BASE}/node/${nodeId}/status`);
  if (!res.ok) throw new Error(`Status fetch failed: ${res.status}`);
  return res.json();
}
