import asyncio
import grpc
import sys
import os
from concurrent.futures import ThreadPoolExecutor

generated_path=os.path.join(os.path.dirname(__file__), 'generated', 'python')
sys.path.append(generated_path)
from generated.python import llm_service_pb2
from generated.python import llm_service_pb2_grpc



from mlx_lm import load, stream_generate
import mlx.core as mx

import uuid



class LLMServicer(llm_service_pb2_grpc.LLMServiceServicer):
    def __init__(self):
        print("Loading model... this may take a minute.")
        self.model, self.tokenizer = load("mlx-community/Meta-Llama-3-8B-Instruct-4bit")
        self.executor=ThreadPoolExecutor(max_workers=os.cpu_count()-1)

    async def StreamGenerate(self, request_iterator, context):
        print("Stream connnection established.")
        async for request in request_iterator:
            prompt=request.prompt
            if request.max_tokens is None:
                max_tokens=8000
            else:
                max_tokens=min(8000, request.max_tokens)    
            print(f"Received prompt: {prompt[:50]}...")
            loop=asyncio.get_event_loop()

            try:
                async for chunk in self._run_generation(prompt, max_tokens, context):
                    yield chunk
            except Exception as e:
                print(f"Error during generation: {e}")    
    

    async def _run_generation(self, prompt, max_tokens, context):
        def sync_gen_wrapper():
            return stream_generate(self.model, self.tokenizer, prompt, max_tokens=max_tokens)
        
        gen = await asyncio.to_thread(sync_gen_wrapper)

        for response in gen:
            if context.done():
                print("Client disconnected. Stopping MLX.")
                return
            
            chunk_data=llm_service_pb2.TokenChunk(text=response.text, is_last=False)
            
            yield llm_service_pb2.TokenResponse(chunk=chunk_data) 
        stop_data = llm_service_pb2.StopSignal(reason="finished")
        yield llm_service_pb2.TokenResponse(stop=stop_data)    

async def serve():
    print("Starting the vllm server")
    server=grpc.aio.server()

    llm_service_pb2_grpc.add_LLMServiceServicer_to_server(LLMServicer(), server)
    listen_addr="[::]:50051"
    server.add_insecure_port(listen_addr)

    print(f"Starting grpc server on {listen_addr}")
    await server.start()

    try:
        await server.wait_for_termination()
    except KeyboardInterrupt:
        await server.stop(0)    

if __name__=="__main__":
    asyncio.run(serve())            