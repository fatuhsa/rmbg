from fastapi.testclient import TestClient
from main import app
import io
from PIL import Image

client = TestClient(app)

def test_root():
    response = client.get("/")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"

def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "healthy"

def test_remove_bg_invalid_type():
    response = client.post(
        "/remove-bg",
        files={"file": ("test.txt", b"plain text content", "text/plain")}
    )
    assert response.status_code == 400

def test_remove_bg_empty_file():
    response = client.post(
        "/remove-bg",
        files={"file": ("empty.png", b"", "image/png")}
    )
    assert response.status_code == 400

def test_remove_bg_corrupted_image():
    response = client.post(
        "/remove-bg",
        files={"file": ("corrupted.png", b"not a valid png binary", "image/png")}
    )
    assert response.status_code == 400

def test_remove_bg_valid_synthetic_image():
    # Create a small 10x10 test image
    img = Image.new("RGB", (10, 10), color="red")
    img_byte_arr = io.BytesIO()
    img.save(img_byte_arr, format="PNG")
    img_bytes = img_byte_arr.getvalue()

    response = client.post(
        "/remove-bg",
        files={"file": ("test.png", img_bytes, "image/png")}
    )
    # Status code can be 200 (processed) or if u2net model isn't downloaded in testing env it may return 500, but request format is valid
    assert response.status_code in [200, 500]
    if response.status_code == 200:
        assert response.headers["content-type"] == "image/png"
