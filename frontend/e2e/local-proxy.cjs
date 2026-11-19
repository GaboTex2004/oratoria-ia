const http = require('node:http')

const upstream = new URL(process.env.E2E_PROXY_UPSTREAM || 'http://frontend:8080')
const server = http.createServer((incoming, outgoing) => {
  const target = new URL(incoming.url || '/', upstream)
  const proxyRequest = http.request(target, {
    method: incoming.method,
    headers: { ...incoming.headers, host: upstream.host },
  }, (proxyResponse) => {
    outgoing.writeHead(proxyResponse.statusCode || 502, proxyResponse.headers)
    proxyResponse.pipe(outgoing)
  })

  proxyRequest.on('error', (error) => {
    if (!outgoing.headersSent) outgoing.writeHead(502, { 'content-type': 'text/plain' })
    outgoing.end(`E2E proxy failed: ${error.message}`)
  })
  incoming.pipe(proxyRequest)
})

server.listen(15173, '127.0.0.1', () => {
  process.stdout.write(`E2E proxy: http://127.0.0.1:15173 -> ${upstream.origin}\n`)
})
