import uuid
import io
from datetime import datetime, timezone
from pathlib import Path

from PIL import Image

from config import settings


def save_photo(file_bytes: bytes, original_filename: str) -> tuple[str, str, int, int, int]:
    """Save original photo and generate thumbnail.

    Returns: (original_path, thumbnail_path, file_size, width, height)
    """
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    day_dir = settings.photos_dir / today
    day_dir.mkdir(parents=True, exist_ok=True)

    uid = uuid.uuid4().hex[:12]
    ext = Path(original_filename).suffix or ".jpg"
    save_name = f"{uid}{ext}"
    original_path = str(day_dir / save_name)

    with open(original_path, "wb") as f:
        f.write(file_bytes)

    # Get image dimensions and generate thumbnail
    img = Image.open(io.BytesIO(file_bytes))
    width, height = img.size
    file_size = len(file_bytes)

    thumb_name = f"{uid}_thumb.jpg"
    thumb_path = str(day_dir / thumb_name)
    _make_thumbnail(img, thumb_path, max_size=512)

    return original_path, thumb_path, file_size, width, height


def _make_thumbnail(img: Image.Image, save_path: str, max_size: int = 512):
    """Generate a JPEG thumbnail, fitting within max_size x max_size."""
    img_copy = img.copy()
    img_copy.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)
    if img_copy.mode in ("RGBA", "P"):
        img_copy = img_copy.convert("RGB")
    img_copy.save(save_path, "JPEG", quality=80)
