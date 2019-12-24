import React, { useEffect, useState, useCallback } from 'react';
import { createRoot } from 'react-dom/client';
import { COLUMNS, groupBoard, pickProgress, applyUpdate } from './board.js';
import './styles.css';

const STORE = new URLSearchParams(location.search).get('store') || 'S042';
const ASSOCIATE = localStorage.getItem('associate') || 'associate';

async function api(path, body) {
  const res = await fetch(`/api${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Associate': ASSOCIATE },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.detail || res.statusText);
  return data;
}

function useNow(ms = 30000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), ms); return () => clearInterval(t); }, [ms]);
  return now;
}

function OrderCard({ order, onOpen }) {
  const { picked, total } = pickProgress(order);
  return (
    <button className={`card ${order.urgency}`} onClick={() => onOpen(order)}>
      <div className="row"><strong>{order.orderNo}</strong><span>{order.customerName}</span></div>
      {order.status === 'CUSTOMER_ARRIVED'
        ? <div className="big">Spot {order.arrivalSpot || 'counter'}</div>
        : order.status === 'READY'
          ? <div>At {order.stagingLocation}</div>
          : <div>{order.minutesLeft < 0 ? `${-order.minutesLeft} min late` : `${order.minutesLeft} min left`} · {picked}/{total} picked</div>}
      {order.status === 'AWAITING_SUBSTITUTION' && <div className="tag">waiting on customer</div>}
    </button>
  );
}

function PickSheet({ order, onChange, onClose }) {
  const [error, setError] = useState(null);
  const [code, setCode] = useState('');
  const run = async (fn) => {
    setError(null);
    try { onChange(await fn()); } catch (e) { setError(e.message); }
  };
  const no = order.orderNo;
  return (
    <div className="sheet">
      <header><h2>{no} · {order.customerName}</h2><button onClick={onClose}>Close</button></header>
      {error && <p className="error">{error}</p>}
      {order.status === 'PLACED' && <button className="primary" onClick={() => run(() => api(`/orders/${no}/claim`, {}))}>Claim & start picking</button>}
      <ul className="lines">
        {[...order.lines].sort((a, b) => (a.aisle || '').localeCompare(b.aisle || '')).map((l) => (
          <li key={l.id}>
            <span className="aisle">{l.aisle}</span>
            <span>{l.name} × {l.qty}</span>
            {order.status === 'PICKING' && (
              <span className="actions">
                <button onClick={() => run(() => api(`/orders/${no}/lines/${l.id}/pick`, { found: l.qty }))}>All</button>
                <button onClick={() => run(() => api(`/orders/${no}/lines/${l.id}/pick`, { found: 0 }))}>None</button>
                {l.pickedQty != null && l.pickedQty < l.qty && l.substitutionState === 'NONE' && (
                  <button onClick={() => {
                    const sku = prompt('Substitute SKU'); const name = sku && prompt('Substitute name');
                    if (sku && name) run(() => api(`/orders/${no}/lines/${l.id}/substitute`, { sku, name }));
                  }}>Sub…</button>
                )}
              </span>
            )}
            <span className="state">{l.pickedQty ?? '–'}/{l.qty} {l.substitutionState !== 'NONE' && `· sub ${l.substitutionState.toLowerCase()}`}</span>
          </li>
        ))}
      </ul>
      {order.status === 'PICKING' && (
        <button className="primary" onClick={() => { const loc = prompt('Staging location', 'SHELF-1'); if (loc) run(() => api(`/orders/${no}/stage`, { location: loc })); }}>Stage order</button>
      )}
      {(order.status === 'READY' || order.status === 'CUSTOMER_ARRIVED') && (
        <form onSubmit={(e) => { e.preventDefault(); run(() => api(`/orders/${no}/handoff`, { code })); }}>
          <input inputMode="numeric" maxLength={6} placeholder="Customer code" value={code} onChange={(e) => setCode(e.target.value)} />
          <button className="primary" disabled={code.length !== 6}>Hand off</button>
        </form>
      )}
    </div>
  );
}

function App() {
  const [orders, setOrders] = useState([]);
  const [open, setOpen] = useState(null);
  const now = useNow();

  useEffect(() => { api(`/stores/${STORE}/board`).then(setOrders).catch(console.error); }, []);
  useEffect(() => {
    const es = new EventSource(`/api/stores/${STORE}/stream`);
    es.onmessage = (m) => setOrders((cur) => applyUpdate(cur, JSON.parse(m.data)));
    return () => es.close();
  }, []);

  const onChange = useCallback((o) => { setOrders((cur) => applyUpdate(cur, o)); setOpen(o); }, []);
  const board = groupBoard(orders, now);

  return (
    <main>
      <h1>Pickup board · store {STORE}</h1>
      <div className="columns">
        {COLUMNS.map((c) => (
          <section key={c.key}>
            <h3>{c.title} <small>{board[c.key].length}</small></h3>
            {board[c.key].map((o) => <OrderCard key={o.orderNo} order={o} onOpen={setOpen} />)}
          </section>
        ))}
      </div>
      {open && <PickSheet order={open} onChange={onChange} onClose={() => setOpen(null)} />}
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
