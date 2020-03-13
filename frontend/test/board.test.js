import { test } from 'node:test';
import assert from 'node:assert/strict';
import { groupBoard, urgency, applyUpdate, pickProgress } from '../src/board.js';

const now = Date.parse('2019-11-29T15:00:00Z');
const order = (orderNo, status, readyBy, extra = {}) => ({ orderNo, status, readyBy, lines: [], ...extra });

test('groups by column and sorts by time left', () => {
  const b = groupBoard([
    order('A', 'PLACED', '2019-11-29T16:00:00Z'),
    order('B', 'ACCEPTED', '2019-11-29T15:10:00Z'),
    order('C', 'AWAITING_SUBSTITUTION', '2019-11-29T15:30:00Z'),
    order('D', 'READY', '2019-11-29T14:00:00Z'),
  ], now);
  assert.deepEqual(b.todo.map((o) => o.orderNo), ['B', 'A']);
  assert.deepEqual(b.picking.map((o) => o.orderNo), ['C']);
  assert.equal(b.ready.length, 1);
  assert.equal(b.todo[0].minutesLeft, 10);
});

test('urgency reflects promise time and lot wait', () => {
  assert.equal(urgency(order('A', 'PICKING', '2019-11-29T14:50:00Z'), now), 'late');
  assert.equal(urgency(order('A', 'PICKING', '2019-11-29T15:10:00Z'), now), 'soon');
  assert.equal(urgency(order('A', 'PICKING', '2019-11-29T16:10:00Z'), now), 'ok');
  assert.equal(urgency(order('A', 'READY', '2019-11-29T14:00:00Z'), now), 'ok');
  assert.equal(urgency(order('A', 'CUSTOMER_ARRIVED', 'x', { arrivedAt: '2019-11-29T14:50:00Z' }), now), 'late');
});

test('arrived customers sort by who has waited longest', () => {
  const b = groupBoard([
    order('X', 'CUSTOMER_ARRIVED', 'x', { arrivedAt: '2019-11-29T14:58:00Z' }),
    order('Y', 'CUSTOMER_ARRIVED', 'x', { arrivedAt: '2019-11-29T14:52:00Z' }),
  ], now);
  assert.deepEqual(b.here.map((o) => o.orderNo), ['Y', 'X']);
});

test('applyUpdate replaces open orders and drops closed ones', () => {
  const cur = [order('A', 'PICKING', 'x'), order('B', 'READY', 'x')];
  assert.equal(applyUpdate(cur, order('A', 'READY', 'x')).find((o) => o.orderNo === 'A').status, 'READY');
  assert.deepEqual(applyUpdate(cur, order('B', 'PICKED_UP', 'x')).map((o) => o.orderNo), ['A']);
  assert.equal(applyUpdate(cur, order('C', 'PLACED', 'x')).length, 3);
});

test('pickProgress counts lines with a recorded pick', () => {
  assert.deepEqual(pickProgress({ lines: [{ pickedQty: 0 }, { pickedQty: null }, { pickedQty: 2 }] }), { picked: 2, total: 3 });
});
