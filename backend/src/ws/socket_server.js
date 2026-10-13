import { WebSocketServer, WebSocket } from 'ws';

let wssInstance = null;

export function initWebSocketServer(server) {
  wssInstance = new WebSocketServer({ server, path: '/ws' });

  wssInstance.on('connection', (ws) => {
    console.log('⚡ [WebSocket] New client connected (Dashboard/Monitoring Console)');

    ws.send(JSON.stringify({
      type: 'INIT_ACK',
      payload: { message: 'Connected to SheShield Live Alert Stream', timestamp: new Date().toISOString() }
    }));

    ws.on('close', () => {
      console.log('⚡ [WebSocket] Client disconnected');
    });
  });

  return wssInstance;
}

export function broadcastAlert(type, payload) {
  if (!wssInstance) return;

  const msg = JSON.stringify({ type, payload, timestamp: new Date().toISOString() });
  wssInstance.clients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(msg);
    }
  });
}
