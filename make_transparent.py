from PIL import Image
import os

def remove_background(input_path, output_path):
    print(f"Processing {input_path}...")
    try:
        img = Image.open(input_path).convert("RGBA")
        datas = img.getdata()

        new_data = []
        # Get the color of the top-left pixel to assume as background
        bg_color = datas[0]
        threshold = 30  # Tolerance for background color matching

        for item in datas:
            # Check if pixel is close to background color
            if all(abs(item[i] - bg_color[i]) < threshold for i in range(3)):
                new_data.append((255, 255, 255, 0)) # Transparent
            else:
                new_data.append(item)

        img.putdata(new_data)
        img.save(output_path, "PNG")
        print(f"Saved transparent icon to {output_path}")
        return True
    except Exception as e:
        print(f"Failed to process {input_path}: {e}")
        return False

# Paths
source = "lisa_neon_text.png"
vscode_icon = "vscode/assets/icon.png"
intellij_base = "intellij/src/main/resources/META-INF"

def resize_and_save(img, width, height, path):
    resized = img.resize((width, height), Image.Resampling.LANCZOS)
    resized.save(path, "PNG")
    print(f"Saved {width}x{height} to {path}")

if os.path.exists(source):
    # 1. Process Transparency (if not already transparent, but we start from source)
    # Note: If source has background, remove_background handles it.
    # If source is already transparent (like our generated ones), we might just load it.
    # Ideally, we remove background first, then resize that result.
    
    # Revert: Skip background removal as quality was poor. Use original source.
    # success = remove_background(source, vscode_icon)
    
    # Just resize source to vscode destination standard size
    base_img = Image.open(source)
    resize_and_save(base_img, 512, 512, vscode_icon)
    
    # Continue with IntelliJ icons
    if True: # success check bypassed
        
        # IntelliJ Variants
        # 1x (40x40)
        resize_and_save(base_img, 40, 40, os.path.join(intellij_base, "pluginIcon.png"))
        resize_and_save(base_img, 40, 40, os.path.join(intellij_base, "pluginIcon_dark.png"))
        
        # 2x (80x80)
        resize_and_save(base_img, 80, 80, os.path.join(intellij_base, "pluginIcon@2x.png"))
        resize_and_save(base_img, 80, 80, os.path.join(intellij_base, "pluginIcon_dark@2x.png"))

        # Explicit LISA Icon for Tool Window
        icons_dir = "intellij/src/main/resources/icons"
        if not os.path.exists(icons_dir):
            os.makedirs(icons_dir)
        resize_and_save(base_img, 40, 40, os.path.join(icons_dir, "lisa.png"))
        
        # 3x (120x120) - Extra high res just in case
        resize_and_save(base_img, 120, 120, os.path.join(intellij_base, "pluginIcon@3x.png"))
        
        print("Generated all icon variants (Reverted to solid background).")
else:
    print(f"Could not find source: {source}")
