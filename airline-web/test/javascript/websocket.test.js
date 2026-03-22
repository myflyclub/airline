'use strict'

const vm = require('vm')
const fs = require('fs')
const path = require('path')

const WS_CODE = fs.readFileSync(
  path.resolve(__dirname, '../../public/javascripts/websocket.js'),
  'utf8'
)

/**
 * Creates an isolated VM context that mimics the browser globals websocket.js
 * needs. Each call returns a fresh context so tests don't bleed into each other.
 *
 * After vm.runInContext the script's top-level `var` declarations become
 * properties of `ctx`, so ctx.websocket, ctx.reconnectAttempts, etc. are
 * directly readable/writable from test code.
 */
function createContext() {
  const wsInstances = []
  const timeoutQueue = []

  function MockWebSocket(url) {
    this.url = url
    this.readyState = 1 // OPEN
    this.onopen = null
    this.onclose = null
    this.onmessage = null
    this.onerror = null
    this.send = jest.fn()
    this.close = jest.fn().mockImplementation(function () {
      this.readyState = 3 // CLOSED
    })
    wsInstances.push(this)
  }
  MockWebSocket.CLOSED = 3
  MockWebSocket.instances = wsInstances

  const ctx = {
    WebSocket: MockWebSocket,
    window: {
      location: { protocol: 'http:', port: '9000', hostname: 'localhost' },
    },
    updateAirlineInfo: jest.fn(),
    loadAirportsDynamic: jest.fn(),
    updateTime: jest.fn(),
    queuePrompt: jest.fn(),
    queueNotice: jest.fn(),
    queueTutorialByJson: jest.fn(),
    handlePendingActions: jest.fn(),
    // Capture setTimeout callbacks so tests can fire them on demand
    setTimeout: jest.fn().mockImplementation((fn) => timeoutQueue.push(fn)),
    clearTimeout: jest.fn(),
    Math,
    JSON,
    console,
    parseInt,
    parseFloat,
    isNaN,
  }
  ctx._timeoutQueue = timeoutQueue
  vm.createContext(ctx)
  vm.runInContext(WS_CODE, ctx)
  return ctx
}

// ---------------------------------------------------------------------------
// connectWebSocket — cascade reconnect fix
// ---------------------------------------------------------------------------
describe('connectWebSocket: old WebSocket cleanup', () => {
  test('nulls the old onclose handler before creating a new WebSocket', () => {
    const ctx = createContext()

    ctx.connectWebSocket(42)
    const first = ctx.WebSocket.instances[0]
    expect(first.onclose).not.toBeNull()

    ctx.connectWebSocket(42)

    expect(first.onclose).toBeNull()
    expect(ctx.WebSocket.instances).toHaveLength(2)
  })

  test('closes the old WebSocket when it is still open', () => {
    const ctx = createContext()

    ctx.connectWebSocket(42)
    const first = ctx.WebSocket.instances[0]
    first.readyState = 1 // OPEN

    ctx.connectWebSocket(42)

    expect(first.close).toHaveBeenCalled()
  })

  test('does NOT close the old WebSocket when it is already closed', () => {
    const ctx = createContext()

    ctx.connectWebSocket(42)
    const first = ctx.WebSocket.instances[0]
    first.readyState = 3 // already CLOSED

    ctx.connectWebSocket(42)

    expect(first.close).not.toHaveBeenCalled()
  })
})

// ---------------------------------------------------------------------------
// onopen — missed cycle recovery fix
// ---------------------------------------------------------------------------
describe('onopen: reconnect refresh', () => {
  test('does NOT call updateAirlineInfo on the very first connect', () => {
    const ctx = createContext()
    ctx.selectedAirlineId = 42
    // reconnectAttempts starts at 0 — this is a first connect

    ctx.connectWebSocket(42)
    ctx.WebSocket.instances[0].onopen()
    ctx._timeoutQueue.forEach((fn) => fn())

    expect(ctx.updateAirlineInfo).not.toHaveBeenCalled()
    expect(ctx.loadAirportsDynamic).not.toHaveBeenCalled()
  })

  test('calls updateAirlineInfo and loadAirportsDynamic on reconnect', () => {
    const ctx = createContext()
    ctx.selectedAirlineId = 42
    ctx.reconnectAttempts = 2 // simulates having attempted reconnects

    ctx.connectWebSocket(42)
    ctx.WebSocket.instances[0].onopen()
    ctx._timeoutQueue.forEach((fn) => fn())

    expect(ctx.updateAirlineInfo).toHaveBeenCalledWith(42)
    expect(ctx.loadAirportsDynamic).toHaveBeenCalled()
  })

  test('resets reconnectAttempts to 0 after any open', () => {
    const ctx = createContext()
    ctx.reconnectAttempts = 5

    ctx.connectWebSocket(42)
    ctx.WebSocket.instances[0].onopen()

    expect(ctx.reconnectAttempts).toBe(0)
  })

  test('does not refresh if selectedAirlineId is unset on reconnect', () => {
    const ctx = createContext()
    ctx.selectedAirlineId = undefined
    ctx.reconnectAttempts = 1

    ctx.connectWebSocket(42)
    ctx.WebSocket.instances[0].onopen()
    ctx._timeoutQueue.forEach((fn) => fn())

    expect(ctx.updateAirlineInfo).not.toHaveBeenCalled()
  })
})

// ---------------------------------------------------------------------------
// onmessage — cycle push handling
// ---------------------------------------------------------------------------
describe('onmessage: cycleCompleted', () => {
  test('calls updateAirlineInfo with jitter when cycleCompleted arrives', () => {
    const ctx = createContext()
    ctx.selectedAirlineId = 7

    ctx.connectWebSocket(7)
    const ws = ctx.WebSocket.instances[0]
    ws.onmessage({ data: JSON.stringify({ messageType: 'cycleCompleted', cycle: 42 }) })
    ctx._timeoutQueue.forEach((fn) => fn())

    expect(ctx.updateAirlineInfo).toHaveBeenCalledWith(7)
    expect(ctx.loadAirportsDynamic).toHaveBeenCalled()
  })

  test('does not call updateAirlineInfo when cycleCompleted arrives with no airline selected', () => {
    const ctx = createContext()
    ctx.selectedAirlineId = undefined

    ctx.connectWebSocket(7)
    const ws = ctx.WebSocket.instances[0]
    ws.onmessage({ data: JSON.stringify({ messageType: 'cycleCompleted', cycle: 42 }) })

    expect(ctx.updateAirlineInfo).not.toHaveBeenCalled()
  })

  test('calls updateTime when cycleInfo arrives', () => {
    const ctx = createContext()

    ctx.connectWebSocket(7)
    const ws = ctx.WebSocket.instances[0]
    ws.onmessage({ data: JSON.stringify({ messageType: 'cycleInfo', cycle: 5, fraction: 0.5, cycleDurationEstimation: 30000 }) })

    expect(ctx.updateTime).toHaveBeenCalledWith(5, 0.5, 30000)
  })
})
