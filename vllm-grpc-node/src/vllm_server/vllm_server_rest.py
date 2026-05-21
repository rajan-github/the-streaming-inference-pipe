from fastapi import FastAPI, Request
from fastapi.responses import StreamingResponse
import uvicorn
import json
import asyncio
from pydantic import BaseModel
from typing import Optional

from vllm_server import LLMServicer


from mlx_lm import load, stream_generate
import mlx.core as mx

app=FastAPI()
servicer = LLMServicer()

class CompletionRequest(BaseModel):
    prompt:str
    max_tokens: Optional[int]=8000


@app.post("/v1/generate")
async def stream_completion(request_body: CompletionRequest, request: Request):
    prompt=request_body.prompt
    maxTokens=min(8000, request_body.max_tokens)

    async def event_generator():
        try:
            sync_gen=await asyncio.to_thread(lambda: stream_generate(servicer.model, servicer.tokenizer, prompt, max_tokens=maxTokens))
            
            for response in sync_gen:
                if await request.is_disconnected():
                    print("Rest client disconnected.")
                    break
                payload={
                    "text": response.text,
                    "is_last": False
                }
                yield f"data: {json.dumps(payload)}\n\n"
            yield "data: {\"is_last\": true, \"reason\": \"finished\"}\n\n"

        except Exception as e:
            yield f"data: {json.dumps({'error': str(e)})}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream") 

def run_rest():
    print("running rest server on port: 8000")
    uvicorn.run(app, host="0.0.0.0", port=8000)    


if __name__=="__main__":
    asyncio.run(run_rest())           
