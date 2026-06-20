const WebSocket = require('ws');

const wsUrl = 'ws://127.0.0.1:3002';
console.log(`Connecting to WebSocket server at ${wsUrl}...`);

const ws = new WebSocket(wsUrl);

let frameCount = 0;
let totalBytes = 0;
const start = Date.now();
let lastFrameTime = Date.now();
const latencies = [];

ws.on('open', () => {
  console.log('Connected to WebSocket server!');
  // Register as mobile client to trigger PC screen capture loop
  ws.send(JSON.stringify({ type: 'register', client: 'mobile' }));
  
  // Run test for 10 seconds, then output results
  setTimeout(() => {
    const durationSec = (Date.now() - start) / 1000;
    const avgFps = frameCount / durationSec;
    const avgSizeKB = frameCount > 0 ? (totalBytes / frameCount / 1024) : 0;
    const avgLatency = latencies.length > 0 ? (latencies.reduce((a,b)=>a+b, 0) / latencies.length) : 0;
    
    console.log('\n=======================================');
    console.log('      SMARTDISPLAY AI STRESS TEST      ');
    console.log('=======================================');
    console.log(`Duration: ${durationSec.toFixed(2)} seconds`);
    console.log(`Total Frames Received: ${frameCount}`);
    console.log(`Average Framerate: ${avgFps.toFixed(2)} FPS`);
    console.log(`Total Data Transferred: ${(totalBytes / 1024 / 1024).toFixed(2)} MB`);
    console.log(`Average Frame Size: ${avgSizeKB.toFixed(2)} KB`);
    console.log(`Average Frame Inter-arrival Latency: ${avgLatency.toFixed(2)} ms`);
    console.log('=======================================\n');
    
    ws.close();
    process.exit(0);
  }, 10000);
});

ws.on('message', (data) => {
  try {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'pc_frame') {
      frameCount++;
      const bytes = Buffer.byteLength(msg.image, 'base64');
      totalBytes += bytes;
      
      const now = Date.now();
      const elapsed = now - lastFrameTime;
      latencies.push(elapsed);
      lastFrameTime = now;
      
      process.stdout.write(`Received Frame #${frameCount} | Size: ${(bytes / 1024).toFixed(1)} KB | Inter-arrival: ${elapsed}ms\r`);
    }
  } catch (err) {
    console.error('Error parsing frame:', err.message);
  }
});

ws.on('error', (err) => {
  console.error('WebSocket Error:', err);
});

ws.on('close', () => {
  console.log('\nConnection closed.');
});
