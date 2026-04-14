from PIL import Image
import os

source_icon = r"C:\Users\DaRkAngeL\.gemini\antigravity\brain\0fb9600c-6e05-4076-ba32-5e42e91885de\app_icon_1775523028457.png"
base_dir = r"c:\Users\DaRkAngeL\Desktop\os\repos\agent-phone-app\android\app\src\main\res"

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

if not os.path.exists(source_icon):
    print(f"Source icon not found at {source_icon}")
    exit(1)

img = Image.open(source_icon)

for folder, size in sizes.items():
    target_dir = os.path.join(base_dir, folder)
    if not os.path.exists(target_dir):
        os.makedirs(target_dir)
    
    # Square icon
    target_path = os.path.join(target_dir, "ic_launcher.png")
    out = img.resize((size, size), Image.Resampling.LANCZOS)
    out.save(target_path, "PNG")
    print(f"Saved {target_path} ({size}x{size})")
    
    # Round icon (we'll just use the same image for the square/round demo)
    target_path_round = os.path.join(target_dir, "ic_launcher_round.png")
    out.save(target_path_round, "PNG")
    print(f"Saved {target_path_round} ({size}x{size})")
