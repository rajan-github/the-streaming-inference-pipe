curl -X POST "http://localhost:8080/api/v1/generate" \
-H "Content-Type: application/json" \
-d '{
"model": "your-model-name",
"prompt": "Hello world",
"temperature": 0.7,
"encoding": "protobuf"
}'
