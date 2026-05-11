# running the server

## 1. Create the directory (the -p flag creates parent folders if missing)
mkdir -p src/vllm_server/generated/python

## 2. Run the protoc command (Note: I fixed the 'vll_server' typo below)
python -m grpc_tools.protoc \
  -I proto \
  --python_out=src/vllm_server/generated/python \
  --grpc_python_out=src/vllm_server/generated/python \
  proto/llm_service.proto

## 3. Start the server
python src/vllm_server/vllm_server.py  