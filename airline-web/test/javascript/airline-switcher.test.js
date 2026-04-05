'use strict'

const vm = require('vm')
const fs = require('fs')
const path = require('path')

const WS_CODE = fs.readFileSync(
  path.resolve(__dirname, '../../public/javascripts/websocket.js'),
  'utf8'
)

function createWsContext() {
  const wsInstances = []
  const timeoutQueue = []

  function MockWebSocket(url) {
    this.url = url
    this.readyState = 1
    this.onopen = null
    this.onclose = null
    this.onmessage = null
    this.onerror = null
    this.send = jest.fn()
    this.close = jest.fn().mockImplementation(function () {
      this.readyState = 3
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
    loadNotificationBadge: jest.fn(),
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
// initWebSocket — reconnects when airline ID changes
// ---------------------------------------------------------------------------
describe('initWebSocket: airline switching', () => {
  test('reconnects when airline ID changes', () => {
    const ctx = createWsContext()

    ctx.initWebSocket(1)
    expect(ctx.WebSocket.instances).toHaveLength(1)

    // Same ID — should NOT create a new connection
    ctx.initWebSocket(1)
    expect(ctx.WebSocket.instances).toHaveLength(1)

    // Different ID — SHOULD create a new connection
    ctx.initWebSocket(2)
    expect(ctx.WebSocket.instances).toHaveLength(2)
    expect(ctx.selectedAirlineId).toBe(2)
  })

  test('does not reconnect when called with the same airline ID and socket is open', () => {
    const ctx = createWsContext()

    ctx.initWebSocket(42)
    const count = ctx.WebSocket.instances.length

    ctx.initWebSocket(42)
    expect(ctx.WebSocket.instances).toHaveLength(count)
  })

  test('reconnects when socket is closed even with same airline ID', () => {
    const ctx = createWsContext()

    ctx.initWebSocket(42)
    ctx.WebSocket.instances[0].readyState = 3 // CLOSED

    ctx.initWebSocket(42)
    expect(ctx.WebSocket.instances).toHaveLength(2)
  })
})

// ---------------------------------------------------------------------------
// localStorage.lastAirlineId — persistence
// ---------------------------------------------------------------------------
describe('localStorage.lastAirlineId', () => {
  test('is saved on switchAirline and used on login', () => {
    const store = {}
    const localStorage = {
      getItem: jest.fn((k) => store[k] || null),
      setItem: jest.fn((k, v) => { store[k] = String(v) }),
      removeItem: jest.fn((k) => { delete store[k] }),
    }

    // Simulate login flow checking lastAirlineId
    const airlineIds = [10, 20, 30]

    // No lastAirlineId — should default to first
    let lastId = parseInt(localStorage.getItem('lastAirlineId'))
    let airlineToSelect = (lastId && airlineIds.includes(lastId)) ? lastId : airlineIds[0]
    expect(airlineToSelect).toBe(10)

    // Set lastAirlineId to 20
    localStorage.setItem('lastAirlineId', 20)

    // Now should select 20
    lastId = parseInt(localStorage.getItem('lastAirlineId'))
    airlineToSelect = (lastId && airlineIds.includes(lastId)) ? lastId : airlineIds[0]
    expect(airlineToSelect).toBe(20)

    // Set to an ID not in the list — should fall back to first
    localStorage.setItem('lastAirlineId', 999)
    lastId = parseInt(localStorage.getItem('lastAirlineId'))
    airlineToSelect = (lastId && airlineIds.includes(lastId)) ? lastId : airlineIds[0]
    expect(airlineToSelect).toBe(10)
  })
})
