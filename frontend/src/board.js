// Pure board logic, shared by the UI and the tests.

export const COLUMNS = [
  { key: 'todo', title: 'To pick', statuses: ['PLACED', 'ACCEPTED'] },
  { key: 'picking', title: 'Picking', statuses: ['PICKING', 'AWAITING_SUBSTITUTION'] },
  { key: 'ready', title: 'Ready', statuses: ['READY'] },
  { key: 'here', title: 'Customer here', statuses: ['CUSTOMER_ARRIVED'] },
];

export function minutesLeft(order, now) {
  return Math.round((Date.parse(order.readyBy) - now) / 60000);
}

/** Arrived customers first (they're waiting in the lot), then by how close the promise time is. */
export function groupBoard(orders, now) {
  const out = Object.fromEntries(COLUMNS.map((c) => [c.key, []]));
  for (const o of orders) {
    const col = COLUMNS.find((c) => c.statuses.includes(o.status));
    if (col) out[col.key].push({ ...o, minutesLeft: minutesLeft(o, now), urgency: urgency(o, now) });
  }
  out.here.sort((a, b) => Date.parse(a.arrivedAt) - Date.parse(b.arrivedAt));
  for (const k of ['todo', 'picking', 'ready']) out[k].sort((a, b) => a.minutesLeft - b.minutesLeft);
  return out;
}

export function urgency(order, now) {
  if (order.status === 'CUSTOMER_ARRIVED') {
    const waited = (now - Date.parse(order.arrivedAt)) / 60000;
    return waited > 5 ? 'late' : 'soon';
  }
  if (order.status === 'READY') return 'ok';
  const left = minutesLeft(order, now);
  if (left < 0) return 'late';
  if (left < 20) return 'soon';
  return 'ok';
}

export function pickProgress(order) {
  const picked = order.lines.filter((l) => l.pickedQty != null).length;
  return { picked, total: order.lines.length };
}

/** Merge a pushed order into board state; closed orders drop off. */
export function applyUpdate(orders, updated) {
  const open = COLUMNS.some((c) => c.statuses.includes(updated.status));
  const rest = orders.filter((o) => o.orderNo !== updated.orderNo);
  return open ? [...rest, updated] : rest;
}
