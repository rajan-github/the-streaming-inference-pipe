import grpc from 'k6/net/grpc';
import { Trend } from 'k6/metrics';
import { sleep } from 'k6';

const client = new grpc.Client();
// Load your local protobuf definition
client.load([], 'api.proto');

const grpcTtftTrend = new Trend('grpc_ttft');
const grpcSerializationTrend = new Trend('grpc_serialization_overhead');

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
  // Connect to the gRPC server (adjust port as needed)
  client.connect('localhost:50051', { plaintext: true });

  const payload = {
    model: 'your-model-name',
    prompt: 'Hello world',
    temperature: 0.7,
    encoding: 'protobuf'
  };

  const startTime = Date.now();
  let firstTokenReceived = false;

  // Change 'api.v1.Generator/GenerateStream' to match your actual proto package/service/method
  const stream = new grpc.Stream(client, 'api.v1.Generator/GenerateStream');

  stream.on('data', (data) => {
    if (!firstTokenReceived) {
      const ttft = Date.now() - startTime;
      grpcTtftTrend.add(ttft);
      firstTokenReceived = true;
    }

    // Protobuf deserialization is executed natively by k6's Go core before
    // passing the clean JS object here. We track the iteration touch overhead.
    const startParse = Date.now();
    const probe = data.token;
    grpcSerializationTrend.add(Date.now() - startParse);
  });

  stream.on('error', (err) => {
    // Handle or log stream errors (e.g., context deadlines)
  });

  stream.on('end', () => {
    client.close();
  });

  // Initiate the stream by writing the request payload
  stream.write(payload);

  // Keep the VU active while the stream processes
  sleep(5);
}