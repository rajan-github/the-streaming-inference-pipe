# Sending Request
curl -X POST "http://<ip-address>:8080/api/v1/generate" -H "Content-Type: application/json" -d '{"prompt": "Hello world", "temperature": 0.7, "encoding": "protobuf"}'
