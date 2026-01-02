from PIL import Image
import os

def remove_white_bg(input_p, output_p):
    print(f"Processing {input_p}...")
    try:
        img = Image.open(input_p).convert("RGBA")
        datas = img.getdata()
        new_data = []
        for item in datas:
            # Check for white (or near white)
            if item[0] > 240 and item[1] > 240 and item[2] > 240:
                new_data.append((255, 255, 255, 0))
            else:
                new_data.append(item)
        img.putdata(new_data)
        img.save(output_p, "PNG")
        return True
    except Exception as e:
        print(f"Error: {e}")
        return False

def resize_and_save(img, width, height, path):
    resized = img.resize((width, height), Image.Resampling.LANCZOS)
    resized.save(path, "PNG")
    print(f"Saved {width}x{height} to {path}")

# Paths
source = "lisa_ribbon_source.png"
vscode_icon = "vscode/assets/icon.png"
intellij_base = "intellij/src/main/resources/META-INF"
icons_dir = "intellij/src/main/resources/icons"

if os.path.exists(source):
    temp_transparent = "temp_transparent.png"
    if remove_white_bg(source, temp_transparent):
        base_img = Image.open(temp_transparent)
        
        # VS Code
        resize_and_save(base_img, 512, 512, vscode_icon)
        
        # IntelliJ
        resize_and_save(base_img, 40, 40, os.path.join(intellij_base, "pluginIcon.png"))
        resize_and_save(base_img, 40, 40, os.path.join(intellij_base, "pluginIcon_dark.png"))
        resize_and_save(base_img, 80, 80, os.path.join(intellij_base, "pluginIcon@2x.png"))
        resize_and_save(base_img, 80, 80, os.path.join(intellij_base, "pluginIcon_dark@2x.png"))
        resize_and_save(base_img, 120, 120, os.path.join(intellij_base, "pluginIcon@3x.png"))
        
        # Tool Window Icon
        if not os.path.exists(icons_dir): os.makedirs(icons_dir)
        resize_and_save(base_img, 32, 32, os.path.join(icons_dir, "lisa.png")) # 32px for new UI
        
        # Cleanup
        os.remove(temp_transparent)
        print("All icons updated from Ribbon Source.")
    else:
        print("Failed to remove bg")
else:
    print(f"Source not found: {source}")
