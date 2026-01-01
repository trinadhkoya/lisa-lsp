const { spawn } = require('child_process');
const fs = require('fs');

const server = spawn('node', ['dist/server.js', '--stdio']);
const outputStream = fs.createWriteStream('server_raw_output.txt');

server.stdout.pipe(outputStream);
server.stderr.pipe(process.stderr); // redirect stderr to terminal

// Construct a valid LSP initialize request
const request = {
    jsonrpc: "2.0",
    id: 1,
    method: "initialize",
    params: {
        processId: process.pid,
        rootUri: null,
        capabilities: {}
    }
};

const json = JSON.stringify(request);
const message = `Content-Length: ${Buffer.byteLength(json, 'utf8')}\r\n\r\n${json}`;

console.log('Sending request:', message);
server.stdin.write(message);

setTimeout(() => {
    console.log('Timeout reached, killing server');
    server.kill();
    outputStream.end();
}, 5000);
