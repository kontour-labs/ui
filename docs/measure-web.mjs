#!/usr/bin/env node
//
// What the docs site costs a browser, measured in one.
//
// Everything else in this repository's performance work is counted on a JVM,
// which is the right call for a recomposition count — the number is the same on
// a phone — and useless for the two symptoms this exists to answer: a site that
// takes seconds to appear, and animation that is rough on web specifically.
// Both are properties of a browser, and until this script there was nothing in
// the repository that had ever opened one.
//
// No dependencies, deliberately. Node 22 has a global `WebSocket`, Chromium is
// already on the machine for the screenshot harness, and the Chrome DevTools
// Protocol is a JSON wire format — so this is a static file server, a browser
// and about four hundred lines, rather than a package tree that has to be kept
// alive across Kotlin upgrades.
//
//   node docs/measure-web.mjs [--dist DIR] [--seconds N] [--json OUT]
//   node docs/measure-web.mjs --wheel 400,500,600   # scroll the page down 600px
//                             [--screenshot OUT.png] [--click X,Y]
//                             [--touch-tap X,Y] [--touch-drag X1,Y1,X2,Y2[,STEPS[,HOLD]]]
//                             [--then-tap X,Y]
//                             [--mobile] [--dark] [--reduce-motion] [--vibration]
//                             [--clipboard] [--console] [--film DIR,COUNT,MS[,AFTER]]
//                             [--eval EXPR]
//
// ### What it can and cannot tell you
//
// **Load time is real.** Fetching, parsing and instantiating several megabytes
// of Wasm is network- and CPU-bound, and headless Chromium does it the same way
// a visible one does. The server here gzips exactly as GitHub Pages does, so
// the transfer sizes are the ones a reader actually pays.
//
// **Frame time is directional only.** There is no GPU in a container, so WebGL
// runs on SwiftShader and every millisecond is software. Comparing this run to a
// phone is meaningless; comparing two runs of this script with one variable
// changed is not, and that is all the backdrop work needs.
//
// **Idle frames are real, and are the most useful number here.** Whether the
// page keeps asking for animation frames when nothing is moving is a property of
// the code, not of the renderer, and it is measured by counting the application's
// own `requestAnimationFrame` calls rather than by timing anything.
//
// ### Driving an interaction
//
// `--click X,Y` presses at a point and then samples 1.5 seconds of frames, which
// is how the one symptom that started this — "big animations are rough, opening a
// side sheet" — gets a number at all. Take a `--screenshot` first to find the
// coordinate; pass both and the shot is taken after the click, which is how you
// check the thing you meant to press was pressed.
//
//   node docs/measure-web.mjs --dist site --path '#/components/side-sheet' \
//     --click 733,576 --screenshot after.png
//
// ### Driving a *finger*
//
// `--touch-tap X,Y` and `--touch-drag X1,Y1,X2,Y2[,STEPS[,HOLD]]` dispatch real
// touch events, and `--mobile` is what makes them land: Compose registers its
// listeners from `navigator.maxTouchPoints` as the page boots, so touch
// emulation has to be on before navigation or every event falls on the floor.
// `HOLD` is milliseconds of stillness before the finger moves, which is the only
// way to reach anything behind a long press.
//
//   node docs/measure-web.mjs --dist site --mobile --path '#/components/reorderable-item' \
//     --touch-drag 200,400,200,560,20,700 --screenshot after.png
//
// `--vibration` prints every `navigator.vibrate` pattern the run produced, in
// order. It is how a haptic is measured rather than asserted: on the web a
// haptic *is* a duration in milliseconds, and a pattern under about 10ms is
// below what a phone's motor can spin up to produce. Pair it with `--mobile`
// and a touch drag:
//
//   node docs/measure-web.mjs --dist site --mobile --vibration \
//     --path '#/components/slider' --touch-drag 200,400,340,400,20
//
// `--dark` emulates `prefers-color-scheme: dark`, which drives the *system* half
// of the site's `settings.dark ?: systemDark`. It cannot reach the in-app
// toggle, and that gap is itself worth knowing about: `styles.css` follows the
// media query alone, so an app-dark page on an OS-light machine has a white
// ground behind a dark application.
//
// What that reports for the side sheet, on this software rasteriser:
//
//   blur on    median 16.7ms, p95 250-267ms, 35 frames in 1.5s
//   blur off   median 16.7ms, p95 100-117ms, 46-50 frames in 1.5s
//
//   first six frames, blur on:   82, 17, 250, 17, 250, 267 ms
//   first six frames, blur off:  68, 17, 317, 17, 217,  17 ms
//
// The median is a lie in both — the animation is a handful of very long frames
// at the start and then sixty hertz. That shape is the finding: it is not a
// uniformly slow animation, it is about six frames that cost most of a second
// between them, and the blur roughly doubles them without being all of them.
import { createServer } from 'node:http'
import { readFile, stat, readdir, writeFile } from 'node:fs/promises'
import { gzipSync } from 'node:zlib'
import { spawn } from 'node:child_process'
import { join, extname, normalize, resolve } from 'node:path'
import { mkdtemp, rm, mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { existsSync, readFileSync } from 'node:fs'

const CHROME = process.env.CHROME_PATH ?? '/opt/pw-browsers/chromium'

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.wasm': 'application/wasm',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
  '.woff2': 'font/woff2',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
}

// Pages compresses text and Wasm and leaves images and fonts alone. Matching it
// matters: gzip is the difference between a 15.9 MB first load and a 5.3 MB one,
// and measuring the uncompressed number would be measuring a site nobody visits.
const COMPRESSIBLE = new Set(['.html', '.js', '.mjs', '.css', '.json', '.wasm', '.svg'])

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`)
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback
}

const DIST = resolve(arg('dist', 'ui-docs/build/dist/wasmJs/productionExecutable'))
const IDLE_SECONDS = Number(arg('seconds', '3'))

/**
 * Link speeds, because a loopback server is not a network.
 *
 * Unthrottled, this whole site arrives in under a second and the reported figure
 * describes a reader who does not exist. The complaint that started this round was
 * three to seven seconds, and reproducing that needs the bandwidth it was measured
 * over: 5.7 MB is 5 seconds at 9 Mbit/s and 29 at 1.6, whatever the code does.
 *
 * Chrome's own DevTools presets, so a number here can be compared to one somebody
 * takes by hand in a browser.
 */
const NETWORKS = {
  none: null,
  fast4g: { download: 9000e3 / 8, upload: 1500e3 / 8, latency: 20 },
  slow4g: { download: 1600e3 / 8, upload: 750e3 / 8, latency: 150 },
}
const NETWORK = arg('network', 'none')

/** A hash route to open instead of the landing page, e.g. `#/gallery`. */
const PATH = arg('path', '')

/**
 * Emulate `prefers-reduced-motion: reduce`.
 *
 * The one page-level switch that should change how much work the library does,
 * and the only way to check from outside that it actually does: a component that
 * registers an animation and then declines to read it looks identical and costs
 * the same.
 */
const REDUCE_MOTION = process.argv.includes('--reduce-motion')

/**
 * Emulate `prefers-color-scheme: dark`.
 *
 * Note what this does and does not reach. The site reads dark from
 * `settings.dark ?: systemDark`, so this drives the *system* half — the same
 * path a visitor with a dark OS takes. It does not touch the in-app toggle, and
 * the difference between the two is itself a thing worth measuring: `styles.css`
 * follows `prefers-color-scheme` alone, so an app-dark page on an OS-light
 * machine has a white ground behind a dark application.
 */
const DARK = process.argv.includes('--dark')

/**
 * Emulate a phone: a phone's viewport, a phone's pixel ratio, and a touchscreen.
 *
 * The touchscreen is the part that matters and the part that has to be set
 * before the page loads. Compose decides which listeners to register from
 * `navigator.maxTouchPoints` as it starts, so a `Input.dispatchTouchEvent`
 * arriving at a page that booted without touch emulation lands on nothing at
 * all — which looks exactly like the bug you were trying to reproduce.
 */
const MOBILE = process.argv.includes('--mobile')

/** A phone, roughly. Nothing here is load-bearing beyond being narrow and dense. */
const PHONE = { width: 390, height: 844, deviceScaleFactor: 3, mobile: true }

async function serve(root) {
  const cache = new Map()
  const server = createServer(async (req, res) => {
    const path = normalize(decodeURIComponent(req.url.split('?')[0])).replace(/^(\.\.[/\\])+/, '')
    let file = join(root, path === '/' ? 'index.html' : path)
    try {
      if ((await stat(file)).isDirectory()) file = join(file, 'index.html')
    } catch {
      res.writeHead(404).end('not found')
      return
    }
    const ext = extname(file)
    if (!cache.has(file)) {
      const raw = await readFile(file)
      cache.set(file, { raw, gz: COMPRESSIBLE.has(ext) ? gzipSync(raw, { level: 9 }) : null })
    }
    const { raw, gz } = cache.get(file)
    const wantsGzip = (req.headers['accept-encoding'] ?? '').includes('gzip') && gz
    const body = wantsGzip ? gz : raw
    res.writeHead(200, {
      'content-type': TYPES[ext] ?? 'application/octet-stream',
      'content-length': body.length,
      ...(wantsGzip ? { 'content-encoding': 'gzip' } : {}),
      // What Pages sends. Left in on purpose: the cache policy is one of the
      // things being measured, so faking a better one here would hide it.
      'cache-control': 'max-age=600',
    })
    res.end(req.method === 'HEAD' ? undefined : body)
  })
  await new Promise((ok) => server.listen(0, '127.0.0.1', ok))
  return { server, port: server.address().port }
}

async function launch() {
  const dir = await mkdtemp(join(tmpdir(), 'measure-web-'))
  const child = spawn(CHROME, [
    '--headless=new',
    '--no-sandbox',
    '--disable-dev-shm-usage',
    // Software WebGL, named rather than inherited, so two runs of this script
    // are comparable even if the machine underneath them is not.
    '--use-gl=angle',
    '--use-angle=swiftshader',
    '--window-size=1280,900',
    '--hide-scrollbars',
    `--user-data-dir=${dir}`,
    '--remote-debugging-port=0',
    'about:blank',
  ], { stdio: ['ignore', 'ignore', 'pipe'] })

  const portFile = join(dir, 'DevToolsActivePort')
  const deadline = Date.now() + 30_000
  while (Date.now() < deadline) {
    if (existsSync(portFile)) {
      const [port] = readFileSync(portFile, 'utf8').split('\n')
      if (port) return { child, dir, port: Number(port) }
    }
    await new Promise((ok) => setTimeout(ok, 100))
  }
  throw new Error('Chromium never wrote a DevToolsActivePort file')
}

/** A CDP session: request/response by id, events by handler, flat sessionIds. */
class Cdp {
  #socket
  #next = 1
  #pending = new Map()
  #handlers = []

  static async connect(url) {
    const cdp = new Cdp()
    cdp.#socket = new WebSocket(url)
    await new Promise((ok, fail) => {
      cdp.#socket.addEventListener('open', ok, { once: true })
      cdp.#socket.addEventListener('error', fail, { once: true })
    })
    cdp.#socket.addEventListener('message', (event) => {
      const message = JSON.parse(event.data)
      if (message.id && cdp.#pending.has(message.id)) {
        const { ok, fail } = cdp.#pending.get(message.id)
        cdp.#pending.delete(message.id)
        message.error ? fail(new Error(message.error.message)) : ok(message.result)
      } else if (message.method) {
        for (const handler of cdp.#handlers) handler(message)
      }
    })
    return cdp
  }

  send(method, params = {}, sessionId) {
    const id = this.#next++
    this.#socket.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }))
    return new Promise((ok, fail) => this.#pending.set(id, { ok, fail }))
  }

  on(handler) { this.#handlers.push(handler) }
  close() { this.#socket.close() }
}

/**
 * Installed before any page script runs.
 *
 * Two jobs. It marks the moments the page cannot report for itself — when the
 * canvas Compose draws into first exists — and it wraps `requestAnimationFrame`
 * so the application's own frame requests can be counted separately from this
 * script's. That second one is the whole point: a page that keeps asking for
 * frames while nothing on it is moving is doing work for nobody, and no timing
 * measurement can distinguish that from a page that is merely slow.
 */
const PROBE = `
window.__probe = { firstRafAt: null, rafCalls: 0, paints: {}, vibrations: [], clipboard: [], console: [], clipItems: 0, clipTouches: [] }

/**
 * Every clipboard write the run produced, in order.
 *
 * The same shape as the vibration recorder above and for the same two reasons.
 * A headless browser's clipboard is permission-gated, so reading it back after
 * the fact answers nothing — and "the action doesn't even work" is a claim about
 * whether a write happened at all, which is only observable at the call.
 *
 * Both surfaces are recorded because the library has two: the selection toolbar
 * goes through Compose's own handler and the right-click menu through
 * LocalClipboardManager, and which of them is wired up is exactly the question.
 */
{
  let held = ''
  const Real = window.ClipboardItem
  const seen = (via, text) => window.__probe.clipboard.push({ via, text: String(text) })
  // Every property the page *reads* off the clipboard object, whether or not it
  // then calls it. This is the difference between "the framework asked for
  // write and did not call it" and "the framework never looked at the clipboard
  // at all" — two explanations for an empty recorder that need completely
  // different fixes, and one round was already spent on the wrong one.
  const touched = (name) => window.__probe.clipTouches.push(String(name))
  try {
    const real = navigator.clipboard
    Object.defineProperty(Navigator.prototype, 'clipboard', {
      configurable: true,
      get: () => new Proxy({
        writeText: (text) => { seen('writeText', text); held = String(text); return Promise.resolve() },
        // Reads back what was written, so a Paste can be measured as well as a
        // Copy. A stub that always returned empty would make every paste look
        // broken whether or not it was.
        readText: () => Promise.resolve(held),
        write: (items) => { seen('write', '(ClipboardItem)'); return Promise.resolve() },
        // A real ClipboardItem rather than an empty list, and the difference is
        // the whole of whether a paste can be measured. Compose's web target
        // reads through read() and not readText(), so a stub returning an empty
        // list makes every paste insert nothing — which is indistinguishable
        // from the defect, and would have been reported as one.
        read: () => Promise.resolve(
          held === '' ? [] : [new Real([['text/plain', new Blob([held], {type: 'text/plain'})]].reduce(
            (o, [type, blob]) => { o[type] = blob; return o }, {},
          ))],
        ),
        addEventListener: () => {},
      }, {
        get(target, property) {
          touched(property)
          return target[property]
        },
        has(target, property) {
          touched('in:' + String(property))
          return property in target
        },
      }),
    })
    void real
  } catch {}
  // document.execCommand('copy') is the older path, and Compose's web target
  // still reaches for it on browsers without the async API.
  const exec = document.execCommand ? document.execCommand.bind(document) : null
  document.execCommand = function (command, ...rest) {
    if (String(command).toLowerCase() === 'copy' || String(command).toLowerCase() === 'cut') {
      seen(String(command).toLowerCase(), document.getSelection ? document.getSelection().toString() : '')
    }
    return exec ? exec(command, ...rest) : false
  }
}

/**
 * How far a copy got before it stopped.
 *
 * "Nothing was written" has at least three causes and the recorder above cannot
 * separate them: the framework decided the browser has no clipboard and never
 * tried; it built an entry and the write itself was refused; or it built one and
 * the coroutine carrying the write was cancelled before it resumed. The last is
 * live here rather than theoretical — Compose's cut() deletes the text and
 * *then* suspends on setClipEntry, which is exactly the shape of the reported
 * symptom, a word that disappears with nothing on the clipboard.
 *
 * Constructing a ClipboardItem is the midpoint of that sequence, so counting the
 * constructions splits the three apart: zero means it never tried, one with no
 * write means it was built and the write did not happen.
 */
{
  const Real = window.ClipboardItem
  if (typeof Real === 'function') {
    const Counting = function (...args) {
      window.__probe.clipItems++
      return new Real(...args)
    }
    Counting.prototype = Real.prototype
    try { Counting.supports = Real.supports ? Real.supports.bind(Real) : undefined } catch {}
    window.ClipboardItem = Counting
  }
}

/**
 * What the page said to the console, which is where a framework explains itself.
 *
 * Written for the clipboard, and general because the reason is general. Compose's
 * web clipboard does not fail by throwing — every branch that cannot write ends
 * in a console.warn saying the browser supports neither Clipboard.write nor
 * Clipboard.writeText, and then returns normally. From outside, a write that was
 * refused and a write that was never attempted look identical: the recorder above
 * logs nothing in both cases. That is the difference between "the library has no
 * clipboard" and "the harness is not a secure context", and it decides whether
 * there is a bug here at all.
 *
 * Patched rather than read over CDP's Runtime.consoleAPICalled, to match the two
 * recorders above and because a patch installed at document start cannot miss
 * anything a listener attached after Runtime.enable might.
 *
 * The original is still called, so --console changes what is *recorded* and not
 * what the page does.
 */
{
  const levels = ['log', 'info', 'warn', 'error']
  for (const level of levels) {
    const original = console[level] ? console[level].bind(console) : null
    console[level] = function (...args) {
      try {
        window.__probe.console.push({
          level,
          text: args.map((a) => {
            try { return typeof a === 'string' ? a : JSON.stringify(a) } catch { return String(a) }
          }).join(' '),
        })
      } catch {}
      if (original) original(...args)
    }
  }
}

/**
 * Haptics, which on the web are navigator.vibrate and nothing else.
 *
 * Two jobs, and the first is the one that is easy to miss. Compose gates its
 * whole web haptic path on the vibrate function existing; headless Chromium has
 * no vibrator and therefore no such function, so without this it takes the
 * no-op branch and a run records nothing while looking like it proved
 * something. Defining one makes the support check pass.
 *
 * The second is that this is the only place the durations can be read. What the
 * library asks for is a pattern in milliseconds, and whether that pattern is
 * long enough for a motor to spin up to is the entire question.
 */
{
  const record = function (pattern) {
    window.__probe.vibrations.push(Array.isArray(pattern) ? pattern.slice() : [pattern])
    return true
  }
  try {
    Object.defineProperty(Navigator.prototype, 'vibrate', {
      configurable: true, writable: true, value: record,
    })
  } catch {
    navigator.vibrate = record
  }
}
const realRaf = window.requestAnimationFrame.bind(window)
window.requestAnimationFrame = (cb) => {
  if (window.__probe.firstRafAt === null) window.__probe.firstRafAt = performance.now()
  window.__probe.rafCalls++
  return realRaf(cb)
}
new PerformanceObserver((list) => {
  for (const e of list.getEntries()) window.__probe.paints[e.name] = e.startTime
}).observe({ type: 'paint', buffered: true })
new PerformanceObserver((list) => {
  for (const e of list.getEntries()) window.__probe.paints['largest-contentful-paint'] = e.startTime
}).observe({ type: 'largest-contentful-paint', buffered: true })

/** Frame pacing, sampled by driving frames — for comparing two runs of this script. */
window.__sample = (ms) => new Promise((done) => {
  const deltas = []
  let last = performance.now()
  const until = last + ms
  const tick = (now) => {
    deltas.push(now - last)
    last = now
    if (now < until) realRaf(tick)
    else done(deltas)
  }
  realRaf(tick)
})

/**
 * Frames the *application* asked for, counted without asking for any.
 *
 * Deliberately not the sampler above: driving requestAnimationFrame in a loop
 * keeps the compositor awake, so a page measured that way always looks busy.
 * This only reads a counter, waits on a timer, and reads it again.
 */
window.__idle = (ms) => new Promise((done) => {
  const before = window.__probe.rafCalls
  setTimeout(() => done(window.__probe.rafCalls - before), ms)
})
`

function kb(n) { return (n / 1024).toFixed(1).padStart(9) }

async function main() {
  if (!existsSync(DIST)) {
    console.error(`no distribution at ${DIST} — run :ui-docs:wasmJsBrowserDistribution first`)
    process.exit(2)
  }

  const { server, port } = await serve(DIST)
  const { child, dir, port: debug } = await launch()
  const version = await (await fetch(`http://127.0.0.1:${debug}/json/version`)).json()
  const cdp = await Cdp.connect(version.webSocketDebuggerUrl)

  const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' })
  const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true })

  const responses = new Map()
  const order = []
  cdp.on(({ method, params }) => {
    if (method === 'Network.responseReceived') {
      const url = params.response.url.replace(`http://127.0.0.1:${port}/`, '')
      if (!responses.has(url)) { responses.set(url, []); order.push(url) }
      responses.get(url).push({
        status: params.response.status,
        type: params.type,
        mime: params.response.mimeType,
        encoding: params.response.headers?.['content-encoding'] ?? '',
        fromCache: params.response.fromDiskCache || params.response.fromPrefetchCache,
      })
    }
  })

  await cdp.send('Page.enable', {}, sessionId)
  await cdp.send('Network.enable', {}, sessionId)
  await cdp.send('Runtime.enable', {}, sessionId)
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: true }, sessionId)
  if (!(NETWORK in NETWORKS)) {
    console.error(`unknown --network ${NETWORK}; pick one of ${Object.keys(NETWORKS).join(', ')}`)
    process.exit(2)
  }
  const link = NETWORKS[NETWORK]
  if (link) {
    await cdp.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: link.latency,
      downloadThroughput: link.download,
      uploadThroughput: link.upload,
    }, sessionId)
  }
  await cdp.send('Page.addScriptToEvaluateOnNewDocument', { source: PROBE }, sessionId)

  // One call, not one per flag. `Emulation.setEmulatedMedia` *replaces* the
  // feature list rather than merging into it, so two calls leave only the
  // second one's answer — and the flag that lost is silently ignored, which is
  // the failure mode this whole script exists to avoid.
  const media = []
  if (REDUCE_MOTION) media.push({ name: 'prefers-reduced-motion', value: 'reduce' })
  if (DARK) media.push({ name: 'prefers-color-scheme', value: 'dark' })
  if (media.length) {
    await cdp.send('Emulation.setEmulatedMedia', { features: media }, sessionId)
  }

  // Before `Page.navigate`, deliberately — see [MOBILE].
  if (MOBILE) {
    await cdp.send('Emulation.setDeviceMetricsOverride', PHONE, sessionId)
    await cdp.send('Emulation.setTouchEmulationEnabled', {
      enabled: true,
      maxTouchPoints: 1,
    }, sessionId)
  }

  const evaluate = async (expression) => {
    const { result, exceptionDetails } = await cdp.send(
      'Runtime.evaluate',
      { expression, awaitPromise: true, returnByValue: true },
      sessionId,
    )
    if (exceptionDetails) {
      throw new Error(
        [exceptionDetails.text, exceptionDetails.exception?.description]
          .filter(Boolean).join(' — ') + ` while evaluating: ${expression.slice(0, 120)}`,
      )
    }
    return result.value
  }

  const started = Date.now()
  await cdp.send('Page.navigate', { url: `http://127.0.0.1:${port}/${PATH}` }, sessionId)

  // `first-contentful-paint` fires for the boot screen in `index.html`, which is
  // static markup and says nothing about the bundle. What a reader is waiting
  // for is Compose, and the honest marker for that is the first animation frame
  // it asks for — the frame it draws the application in.
  //
  // Not the canvas element, which was tried first and does not work: nothing
  // matching `canvas` is ever reachable from `document`, shadow roots walked,
  // while the app is plainly running and drawing. Compose keeps its surface
  // somewhere a page script cannot see.
  const readyDeadline = Date.now() + 120_000
  let probe = {}
  while (Date.now() < readyDeadline) {
    probe = await evaluate(
      '({ firstRafAt: window.__probe.firstRafAt, rafCalls: window.__probe.rafCalls, ' +
        'paints: window.__probe.paints })',
    )
    if (probe.firstRafAt) break
    await new Promise((ok) => setTimeout(ok, 25))
  }
  const wall = Date.now() - started
  const paints = probe.paints ?? {}
  const timing = await evaluate(`(() => {
    const n = performance.getEntriesByType('navigation')[0] ?? {}
    return { domContentLoaded: n.domContentLoadedEventEnd, load: n.loadEventEnd, responseEnd: n.responseEnd }
  })()`)
  const appFrames = await evaluate(`window.__idle(${IDLE_SECONDS * 1000})`)
  const idleDeltas = await evaluate(`window.__sample(1000)`)

  // Read after the idle window rather than at first frame. The font waterfall
  // lands about a second *after* the app is up, so a listing taken at first
  // frame reports a smaller, tidier site than the one that was loaded.
  const resources = await evaluate(`performance.getEntriesByType('resource').map(r =>
    ({ name: r.name, size: r.transferSize, decoded: r.decodedBodySize, start: r.startTime, end: r.responseEnd }))`)

  /**
   * One finger, on the glass.
   *
   * `Input.dispatchTouchEvent` takes the *current* set of touch points, not an
   * event about one of them — so a press is a list of one, a move is that same
   * list with a new position, and a release is the empty list. Getting that
   * wrong produces a gesture the page sees as starting and never finishing,
   * which is indistinguishable from the application ignoring it.
   */
  const touch = async (type, x, y) => {
    await cdp.send('Input.dispatchTouchEvent', {
      type,
      touchPoints: type === 'touchEnd' ? [] : [{ x, y, id: 1 }],
    }, sessionId)
  }

  /** Real milliseconds. A long press is a wall-clock timeout, not a frame count. */
  const wait = (ms) => new Promise((done) => setTimeout(done, ms))

  // `--film DIR,COUNT,MS[,AFTER]` — COUNT screenshots MS apart, written as
  // `DIR/000.png` onward, starting AFTER milliseconds from now.
  //
  // `--screenshot` is taken at the very end, after each gesture's own
  // `__sample(1500)` and after the idle window. That is the right place for a
  // resting state and it cannot see a transient at all: a toast raised by
  // `--touch-tap` and dragged by `--touch-drag` has expired long before the
  // shutter, so three runs with 5px, 60px-up and 60px-down drags came back
  // **byte-identical** — the same empty page, agreeing with itself for the wrong
  // reason. A settle takes about 200ms and nothing here could observe one.
  //
  // **It competes with the gestures for the debugger connection**, and that is
  // not a detail. `Page.captureScreenshot` costs 200-350ms each on a software
  // rasteriser and shares one CDP channel with `Input.dispatchTouchEvent`, so a
  // film started with no delay queues sixteen screenshots ahead of the tap and
  // the page is still at rest when the last frame is taken. Measured: a filmed
  // `--touch-tap` on the toast demo raised no toast at all.
  //
  // So `AFTER` is the useful knob rather than a nicety — aim the film at the
  // moment in question and keep the frame count low. It is a wall-clock film
  // either way: the frame count is a lower bound on what happened rather than a
  // timeline, and for a question that needs exact frames the JVM `Scene`
  // harness has a controlled clock and this does not.
  const filmSpec = arg('film', null)
  let filming = null
  let filmFrames = 0
  // Each gesture below ends by sampling frame times for 1.5s. While filming
  // that is both redundant — the film is the observation — and destructive: a
  // toast raised by `--touch-tap` has expired by the time `--touch-drag` runs,
  // so the drag lands on an empty page and the run measures nothing.
  const settle = async () => (filmSpec ? null : await evaluate(`window.__sample(1500)`))
  if (filmSpec) {
    const [dir, count = 20, gap = 100, after = 0] = filmSpec.split(',')
    await mkdir(dir, { recursive: true })
    filming = (async () => {
      if (Number(after) > 0) await wait(Number(after))
      for (let i = 0; i < Number(count); i++) {
        const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId)
        await writeFile(`${dir}/${String(i).padStart(3, '0')}.png`, Buffer.from(data, 'base64'))
        filmFrames = i + 1
        await wait(Number(gap))
      }
    })()
  }

  const clickAt = arg('click', null)
  const tapAt = arg('touch-tap', null)
  const dragAlong = arg('touch-drag', null)
  let interaction = null

  if (clickAt) {
    const [x, y] = clickAt.split(',').map(Number)
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y }, sessionId)
    for (const type of ['mousePressed', 'mouseReleased']) {
      await cdp.send('Input.dispatchMouseEvent', { type, x, y, button: 'left', clickCount: 1 }, sessionId)
    }
    interaction = await settle()
  }

  if (tapAt) {
    const [x, y] = tapAt.split(',').map(Number)
    await touch('touchStart', x, y)
    await wait(40)
    await touch('touchEnd', x, y)
    interaction = await settle()
  }

  // `--touch-drag x1,y1,x2,y2[,steps[,hold]]`. `hold` is milliseconds to keep
  // the finger still before it moves, which is the only way to reach anything
  // behind a long press.
  if (dragAlong) {
    const [x1, y1, x2, y2, steps = 20, hold = 0] = dragAlong.split(',').map(Number)
    await touch('touchStart', x1, y1)
    if (hold > 0) await wait(hold)
    for (let i = 1; i <= steps; i++) {
      await touch('touchMove', x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps)
      // A frame between moves. Dispatched back to back, Compose sees one jump
      // and the velocity tracker has nothing to work with.
      await wait(16)
    }
    await touch('touchEnd', x2, y2)
    interaction = await settle()
  }

  // `--wheel X,Y,DELTA[,STEPS]`. A wheel rather than a drag, because on a desktop
  // that is how the page scrolls — and the page is the thing several of these
  // measurements need to move. `html, body` carry `overflow: hidden`, so there is
  // no DOM scroll to drive and `--eval` cannot reach a `LazyColumn`: the scroll
  // has to arrive as an input event like any other.
  const wheelAt = arg('wheel', null)
  if (wheelAt) {
    const [x, y, delta, steps = 10] = wheelAt.split(',').map(Number)
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y }, sessionId)
    for (let i = 0; i < steps; i++) {
      await cdp.send('Input.dispatchMouseEvent', {
        type: 'mouseWheel', x, y, deltaX: 0, deltaY: delta / steps,
      }, sessionId)
      // A frame between notches, for the reason `--touch-drag` has one: dispatched
      // back to back, Compose sees a single jump and the velocity tracker has
      // nothing to work with, so nothing flings.
      await wait(16)
    }
    interaction = await settle()
  }

  // Read *after* the gestures, so what is printed is what the interaction
  // produced rather than whatever the page did while loading.
  const expression = arg('eval', null)
  if (expression) {
    const value = await evaluate(`JSON.stringify(${expression})`)
    console.log('')
    console.log(`eval  ${expression}`)
    console.log(`   -> ${value}`)
  }

  // `--then-tap x,y` is a second tap, dispatched *after* the drag rather than
  // before it. Every other gesture flag stands alone; this one exists because
  // some questions are two gestures long and the order is the question. Select
  // text with a long press and a drag, then tap the toolbar it raised: whether
  // the selection is still there when the tap lands is the whole of what a
  // selection toolbar is for.
  const thenTap = arg('then-tap', null)
  if (thenTap) {
    const [x, y] = thenTap.split(',').map(Number)
    await touch('touchStart', x, y)
    await wait(40)
    await touch('touchEnd', x, y)
    interaction = await settle()
  }

  // `--double-click x,y` selects a word, which is the gesture a desktop user
  // actually makes before looking for a selection toolbar. A press-and-drag
  // turns out to focus the field and select nothing, so it cannot ask the
  // question on its own.
  const doubleClickAt = arg('double-click', null)
  if (doubleClickAt) {
    const [x, y] = doubleClickAt.split(',').map(Number)
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y }, sessionId)
    for (const clickCount of [1, 2]) {
      await cdp.send('Input.dispatchMouseEvent', {
        type: 'mousePressed', x, y, button: 'left', buttons: 1, clickCount,
      }, sessionId)
      await cdp.send('Input.dispatchMouseEvent', {
        type: 'mouseReleased', x, y, button: 'left', buttons: 0, clickCount,
      }, sessionId)
      await wait(30)
    }
    interaction = await settle()
  }

  // `--mouse-drag x1,y1,x2,y2[,steps]` presses, travels and releases with the
  // primary button. A finger and a mouse are not the same gesture to a text
  // field — a drag with the button down is what selects a range on a desktop,
  // and `--touch-drag` cannot ask that question.
  const mouseDrag = arg('mouse-drag', null)
  if (mouseDrag) {
    const [x1, y1, x2, y2, steps = 12] = mouseDrag.split(',').map(Number)
    const at = (i) => ({ x: x1 + (x2 - x1) * i / steps, y: y1 + (y2 - y1) * i / steps })
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: x1, y: y1 }, sessionId)
    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mousePressed', x: x1, y: y1, button: 'left', buttons: 1, clickCount: 1,
    }, sessionId)
    for (let i = 1; i <= steps; i++) {
      const { x, y } = at(i)
      await cdp.send('Input.dispatchMouseEvent', {
        type: 'mouseMoved', x, y, button: 'left', buttons: 1,
      }, sessionId)
      await wait(16)
    }
    await cdp.send('Input.dispatchMouseEvent', {
      type: 'mouseReleased', x: x2, y: y2, button: 'left', buttons: 0, clickCount: 1,
    }, sessionId)
    interaction = await settle()
  }

  // `--right-click x,y` dispatches a real secondary click and reports whether
  // anything on the page stopped the browser drawing its own context menu.
  //
  // The native menu cannot be seen from here — it is chrome, not page — so what
  // is measured instead is the fact that decides whether it appears at all:
  // `defaultPrevented` on the `contextmenu` event, read from a listener on
  // `window` in the bubble phase, which runs after every handler inside the
  // canvas has had the event. Not prevented means the browser's menu is what
  // the user gets, whatever the app drew underneath it.
  const rightClickAt = arg('right-click', null)
  if (rightClickAt) {
    const [x, y] = rightClickAt.split(',').map(Number)
    await evaluate(`
      window.__contextmenu = []
      window.__secondary = []
      window.addEventListener('contextmenu', (event) => {
        window.__contextmenu.push({
          prevented: event.defaultPrevented,
          target: event.target && event.target.tagName,
        })
      }, false)
      // Whether the secondary button reaches the page as a pointer event at
      // all. Without this the harness cannot tell "the app ignored the click"
      // from "the click never arrived", and those need different fixes.
      for (const type of ['pointerdown', 'mousedown']) {
        window.addEventListener(type, (event) => {
          if (event.button === 2) {
            window.__secondary.push({ type, target: event.target && event.target.tagName })
          }
        }, true)
      }
      true
    `)
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y }, sessionId)
    for (const type of ['mousePressed', 'mouseReleased']) {
      await cdp.send('Input.dispatchMouseEvent', {
        type, x, y, button: 'right', buttons: 2, clickCount: 1,
      }, sessionId)
    }
    await wait(300)
    const seen = await evaluate('window.__contextmenu')
    const secondary = await evaluate('window.__secondary')
    console.log('')
    console.log(`right-click  at ${x},${y}`)
    if (!seen || seen.length === 0) {
      console.log('             no contextmenu event fired at all')
    } else {
      for (const event of seen) {
        const verdict = event.prevented
          ? 'prevented — the app draws its own'
          : "NOT PREVENTED — the browser's own menu is what the user gets"
        console.log(`             on <${event.target}>: ${verdict}`)
      }
    }
    if (!secondary || secondary.length === 0) {
      console.log('             the secondary button reached the page as NO pointer')
      console.log('             event at all — nothing in the app could have seen it')
    } else {
      const kinds = secondary.map((e) => `${e.type} on <${e.target}>`).join(', ')
      console.log(`             secondary button delivered as: ${kinds}`)
    }
    interaction = await settle()
  }

  // `--last-click X,Y`, which runs after every other gesture.
  //
  // `--click` fires first and `--then-tap` before the pointer gestures, so a menu
  // that has to be *opened* before an item in it can be chosen had no way to be
  // driven: `--right-click` puts the menu up and nothing could then press
  // anything in it. That is the A/B for "the toolbar's Copy writes nothing" —
  // the right-click menu is a different implementation of the same four verbs.
  const lastClick = arg('last-click', null)
  if (lastClick) {
    const [x, y] = lastClick.split(',').map(Number)
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y }, sessionId)
    for (const type of ['mousePressed', 'mouseReleased']) {
      await cdp.send('Input.dispatchMouseEvent', { type, x, y, button: 'left', clickCount: 1 }, sessionId)
    }
    interaction = await settle()
  }

  if (filming) {
    await filming
    console.log('')
    console.log(`film       ${filmFrames} frame(s) written`)
  }

  // These recorders are read here rather than beside the gestures above, and the
  // placement is the measurement.
  //
  // `--then-tap` and `--eval` run *after* the first gesture — that is their whole
  // purpose, "select some text and then tap the toolbar it raised" — so a readout
  // sitting before them reports the state of the world halfway through the thing
  // being measured. The clipboard recorder was written there first and printed
  // "nothing was written" for a tap that had not happened yet, which is a result
  // that looks exactly like the defect it was built to find.
  if (process.argv.includes('--clipboard')) {
    const writes = await evaluate('window.__probe.clipboard')
    if (!writes.length) {
      console.log('clipboard  nothing was written')
    } else {
      console.log(`clipboard  ${writes.length} write(s)`)
      for (const w of writes) console.log(`             ${w.via}: ${JSON.stringify(w.text)}`)
    }
    // The two gates Compose's own web clipboard checks before it writes
    // anything, printed whether or not a write happened, because "nothing was
    // written" means two completely different things depending on them. A
    // loopback origin *is* a secure context per the spec, so if this prints
    // `secure: true` and no write followed, the refusal is not the harness's.
    const gates = await evaluate(
      "JSON.stringify({secure: window.isSecureContext, " +
        "write: typeof navigator.clipboard?.write, " +
        "writeText: typeof navigator.clipboard?.writeText, " +
        "item: typeof ClipboardItem})",
    )
    const built = await evaluate('window.__probe.clipItems')
    const readBack = await evaluate('navigator.clipboard.readText()')
    console.log(`           gates ${gates}`)
    console.log(`           ${built} ClipboardItem(s) built`)
    console.log(`           holding ${JSON.stringify(readBack)}`)
    const touches = await evaluate('window.__probe.clipTouches')
    const counted = new Map()
    for (const t of touches) counted.set(t, (counted.get(t) || 0) + 1)
    console.log(
      `           navigator.clipboard read as: ` +
        (counted.size
          ? [...counted].map(([k, n]) => (n > 1 ? `${k}x${n}` : k)).join(', ')
          : 'never touched'),
    )
    console.log('')
  }

  if (process.argv.includes('--console')) {
    const lines = await evaluate('window.__probe.console')
    console.log('')
    if (!lines.length) {
      console.log('console    the page said nothing')
    } else {
      console.log(`console    ${lines.length} message(s)`)
      for (const l of lines) console.log(`           ${l.level}: ${l.text}`)
    }
    console.log('')
  }

  if (process.argv.includes('--vibration')) {
    const patterns = await evaluate('window.__probe.vibrations')
    const total = patterns.reduce((sum, p) => sum + p.reduce((a, b) => a + b, 0), 0)
    console.log('')
    console.log(`vibration  ${patterns.length} pattern(s), ${total}ms of motor time`)
    if (patterns.length === 0) {
      console.log('           nothing — either no haptic intent fired, or the')
      console.log('           constant it mapped to has no web pattern at all')
    } else {
      const counts = new Map()
      for (const p of patterns) {
        const key = `[${p.join(',')}]`
        counts.set(key, (counts.get(key) || 0) + 1)
      }
      for (const [key, n] of counts) {
        // The number that decides whether any of this is felt. A motor needs
        // roughly 10-20ms to spin up; under that the pulse is issued and
        // nothing reaches the hand.
        const longest = Math.max(...key.slice(1, -1).split(',').map(Number))
        const verdict = longest >= 10 ? 'feelable' : 'BELOW THE MOTOR FLOOR'
        console.log(`           ${String(n).padStart(4)} x ${key.padEnd(20)} ${verdict}`)
      }
    }
  }

  // `--eval EXPR` prints one JavaScript expression, evaluated in the page after
  // everything else has run. For asking the page a question the harness has no
  // dedicated flag for — the computed style of the canvas Compose creates, say,
  // which is not something a screenshot or a gesture can tell you.

  const shot = arg('screenshot', null)
  if (shot) {
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId)
    await writeFile(shot, Buffer.from(data, 'base64'))
  }

  const stats = (deltas) => {
    if (!deltas.length) return null
    const sorted = [...deltas].sort((a, b) => a - b)
    return {
      frames: deltas.length,
      median: sorted[Math.floor(sorted.length / 2)],
      p95: sorted[Math.floor(sorted.length * 0.95)],
      worst: sorted[sorted.length - 1],
      over16: deltas.filter((d) => d > 16.7).length,
      // Where the bad frames fall matters as much as how bad they are. A hitch
      // in the first two frames is composition; one spread through the run is
      // the animation itself.
      worstAt: deltas.indexOf(Math.max(...deltas)),
      first: deltas.slice(0, 6).map((d) => Math.round(d)),
    }
  }

  const transferred = resources.reduce((sum, r) => sum + (r.size || 0), 0)
  const duplicates = [...responses.entries()].filter(([, hits]) => hits.length > 1)

  console.log(`\n  ${DIST}${PATH ? '  ' + PATH : ''}`)
  console.log(
    `  chromium ${version.Browser}, software WebGL, cache disabled, gzip on, ` +
      (link ? `${NETWORK} (${(link.download * 8 / 1e6).toFixed(1)} Mbit/s, ${link.latency}ms)` : 'unthrottled') +
      (REDUCE_MOTION ? ', prefers-reduced-motion: reduce' : '') +
      (DARK ? ', prefers-color-scheme: dark' : '') +
      (MOBILE ? `, ${PHONE.width}x${PHONE.height} touch` : ''),
  )
  console.log('')
  console.log('  LOAD')
  console.log(`    boot screen painted      ${(paints['first-contentful-paint'] ?? NaN).toFixed(0).padStart(7)} ms   (static markup in index.html)`)
  console.log(`    DOMContentLoaded         ${(timing.domContentLoaded ?? NaN).toFixed(0).padStart(7)} ms`)
  console.log(`    first frame requested    ${(probe.firstRafAt ?? NaN).toFixed(0).padStart(7)} ms   <- the app is up`)
  console.log(`    wall clock, navigate to that${String(wall).padStart(4)} ms`)
  console.log(`    transferred              ${kb(transferred)} KiB over ${resources.length} requests\n`)

  console.log('  WHAT WAS FETCHED  (transfer / decoded, in KiB)')
  for (const r of [...resources].sort((a, b) => (b.size || 0) - (a.size || 0))) {
    const name = r.name.replace(`http://127.0.0.1:${port}/`, '')
    if ((r.size || 0) < 1024 && (r.decoded || 0) < 1024) continue
    console.log(`   ${kb(r.size || 0)} ${kb(r.decoded || 0)}  ${name}`)
  }

  console.log('\n  WHEN  (ms from navigation, request start to response end)')
  for (const r of [...resources].sort((a, b) => a.start - b.start)) {
    if ((r.size || 0) < 1024) continue
    const name = r.name.replace(`http://127.0.0.1:${port}/`, '').split('/').pop()
    console.log(`    ${r.start.toFixed(0).padStart(6)} → ${r.end.toFixed(0).padStart(6)}  ${name}`)
  }

  if (duplicates.length) {
    console.log('\n  FETCHED MORE THAN ONCE')
    for (const [url, hits] of duplicates) console.log(`    ${hits.length}x  ${url}`)
  }

  const idleStats = stats(idleDeltas)
  console.log(`\n  IDLE, once the app is up and nothing is moving`)
  console.log(`    frames the app asked for  ${String(appFrames).padStart(6)} in ${IDLE_SECONDS}s, unprompted`)
  console.log(`    frame pacing when driven  median ${idleStats.median.toFixed(1)}ms · p95 ${idleStats.p95.toFixed(1)} · worst ${idleStats.worst.toFixed(1)}`)
  console.log(
    appFrames > IDLE_SECONDS * 10
      ? `\n    An idle page asked for ${appFrames} animation frames in ${IDLE_SECONDS}s.\n` +
        '    Nothing is moving, so every one of them is a whole-canvas rasterisation\n' +
        '    of an unchanged picture, on the one thread the application has.'
      : '\n    The page settles: it stops asking for frames when nothing is moving.',
  )

  if (interaction) {
    const s = stats(interaction)
    console.log(`\n  AFTER CLICKING ${clickAt}`)
    console.log(`    median ${s.median.toFixed(1)}ms · p95 ${s.p95.toFixed(1)} · worst ${s.worst.toFixed(1)} at frame ${s.worstAt} · ${s.over16}/${s.frames} over 16.7ms`)
    console.log(`    first six frames: ${s.first.join(', ')} ms`)
  }

  const out = arg('json', null)
  if (out) {
    await writeFile(out, JSON.stringify({
      dist: DIST, network: NETWORK, paints, probe, timing, resources, transferred,
      duplicates: duplicates.map(([url, hits]) => ({ url, count: hits.length })),
      idle: { ...idleStats, appFrames, seconds: IDLE_SECONDS },
      interaction: interaction ? stats(interaction) : null,
    }, null, 2))
    console.log(`\n  wrote ${out}`)
  }

  cdp.close()
  child.kill()
  server.close()
  await new Promise((ok) => child.once('exit', ok))
  await rm(dir, { recursive: true, force: true }).catch(() => {})
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
