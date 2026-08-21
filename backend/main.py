import io
from fastapi import FastAPI, UploadFile, File
from fastapi.responses import StreamingResponse
from rembg import remove
from PIL import Image

app = FastAPI(title="BG Remover API")

@app.get("/")
def root():
    return {"status": "ok", "message": "BG Remover API"}

@app.post("/remove-bg")
async def remove_background(file: UploadFile = File(...)):
    input_bytes = await file.read()
    output_bytes = remove(input_bytes)
    return StreamingResponse(
        io.BytesIO(output_bytes),
        media_type="image/png",
        headers={"Content-Disposition": "attachment; filename=removed_bg.png"}
    )
