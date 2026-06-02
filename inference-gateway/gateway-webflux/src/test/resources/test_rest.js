import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { sleep } from 'k6';

// Custom Metrics
const ttftTrend = new Trend('llm_ttft');
const serializationOverheadTrend = new Trend('serialization_overhead');

export const options = {
  scenarios: {
    constant_load: {
      executor: 'constant-vus',
      vus: 5000,
      duration: '5m',
    },
  },
};

export default function () {
  const url = 'http://localhost:8080/api/v1/generate';

  const payload = JSON.stringify({
    model: 'your-model-name',
    prompt: 'Hello world', // Represents an 8k context window generation trigger
    temperature: 0.7,
    encoding: 'json'
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  // Measure request initiation time
  const startTime = Date.now();
  let firstTokenTime = 0;

  // We use http.batch or a standard response handler with a custom callback if supported,
  // but for HTTP/1.1 or HTTP/2 chunked text streams, k6 passes chunks to a response callback.
  const res = http.post(url, payload, {
    ...params,
    responseCallback: http.expectedStatuses(200),
  });

  // Since k6 natively populates http_req_waiting as TTFB:
  if (res.timings && res.timings.waiting) {
    ttftTrend.add(res.timings.waiting);
  }

  // Measure Serialization Overhead on the client side if tracking processing response
  const endParseTime = Date.now();
  try {
    // If the stream returns multi-line JSON or NDJSON
    const lines = res.body.split('\n').filter(line => line.trim() !== '');
    const parseStart = Date.now();
    lines.forEach(line => {
      if(line.startsWith('data: ')) {
        JSON.parse(line.replace('data: ', ''));
      } else {
        JSON.parse(line);
      }
    });
    const parseEnd = Date.now();
    serializationOverheadTrend.add(parseEnd - parseStart);
  } catch (e) {
    // Fail silently or log error if chunking layout breaks
  }

  sleep(1); // Throttling delay between iterations per VU
}