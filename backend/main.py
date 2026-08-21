import io
import logging
from fastapi import FastAPI, UploadFile, File, HTTPException, status
from fastapi.responses import StreamingResponse
from starlette.concurrency import run_in_threadpool
from rembg import remove, new_session
from PIL import Image

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("rmbg")

app = FastAPI(title="BG Remover API", version="1.0.0")

# Pre-initialize u2net session to avoid re-allocating models per request
try:
    session = new_session("u2net")
except Exception as e:
    logger.warning(f"Could not pre-initialize model session: {e}")
    session = None

MAX_FILE_SIZE = 15 * 1024 * 1024  # 15 MB

@app.get("/")
def root():
    return {"status": "ok", "message": "BG Remover API is running"}

@app.get("/health")
def health():
    return {"status": "healthy"}

def process_remove_bg(image_bytes: bytes) -> bytes:
    # Verify valid image before processing
    try:
        with Image.open(io.BytesIO(image_bytes)) as img:
            img.verify()
    except Exception as e:
        raise ValueError(f"Invalid image format or corrupted file: {e}")

    if session is not None:
        return remove(image_bytes, session=session)
    return remove(image_bytes)

@app.post("/remove-bg")
async def remove_background(file: UploadFile = File(...)):
    if file.content_type and not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported file type '{file.content_type}'. Please upload an image."
        )

    input_bytes = await file.read()
    if not input_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded file is empty."
        )

    if len(input_bytes) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="File size exceeds maximum allowed limit (15MB)."
        )

    try:
        output_bytes = await run_in_threadpool(process_remove_bg, input_bytes)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )
    except Exception as e:
        logger.error(f"Inference error: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to remove image background."
        )

    return StreamingResponse(
        io.BytesIO(output_bytes),
        media_type="image/png",
        headers={"Content-Disposition": "attachment; filename=removed_bg.png"}
    )

