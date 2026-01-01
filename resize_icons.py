from PIL import Image
import os

source_path = "intellij/src/main/resources/META-INF/pluginIcon_original.png"
dest_dir = "intellij/src/main/resources/META-INF"

if not os.path.exists(source_path):
    print(f"Error: {source_path} not found")
    exit(1)

try:
    with Image.open(source_path) as img:
        # 40x40 Standard
        img.resize((40, 40), Image.Resampling.LANCZOS).save(os.path.join(dest_dir, "pluginIcon.png"))
        
        # 80x80 Retina (@2x)
        img.resize((80, 80), Image.Resampling.LANCZOS).save(os.path.join(dest_dir, "pluginIcon@2x.png"))
        
        # Dark theme variants (using same icon for now)
        img.resize((40, 40), Image.Resampling.LANCZOS).save(os.path.join(dest_dir, "pluginIcon_dark.png"))
        img.resize((80, 80), Image.Resampling.LANCZOS).save(os.path.join(dest_dir, "pluginIcon_dark@2x.png"))
        
    print("Icons generated successfully!")
except Exception as e:
    print(f"Error processing image: {e}")
