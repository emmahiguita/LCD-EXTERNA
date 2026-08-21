from PIL import Image
import os

# Ruta de la imagen cargada por el usuario
input_file = r"C:\Users\emman\.gemini\antigravity-ide\brain\51485da1-0726-45a6-a572-d3ba271086bc\media__1782975989399.jpg"
output_folder = r"c:\Users\emman\Desktop\Proyectos\SmartDisplay\Android\output_frames"

if not os.path.exists(output_folder):
    os.makedirs(output_folder)

with Image.open(input_file) as img:
    w, h = img.size
    cell_w = w // 4
    cell_h = h // 3
    
    count = 1
    for r in range(3):
        for c in range(4):
            left = c * cell_w
            top = r * cell_h
            right = left + cell_w
            bottom = top + cell_h
            
            frame = img.crop((left, top, right, bottom))
            frame.save(f"{output_folder}/frame_{count:02d}.png")
            count += 1
            
print(f"¡Recorte completado con éxito en la carpeta {output_folder}!")
