import os
from PIL import Image, ImageDraw

def make_background_transparent(img, tolerance=35):
    img = img.convert("RGBA")
    width, height = img.size
    
    # Inundación desde los bordes para transparentar el fondo
    temp_img = img.copy()
    
    seeds = [
        (0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1),
        (width // 2, 0), (width // 2, height - 1), (0, height // 2), (width - 1, height // 2)
    ]
    
    for x in range(0, width, 5):
        seeds.append((x, 0))
        seeds.append((x, height - 1))
    for y in range(0, height, 5):
        seeds.append((0, y))
        seeds.append((width - 1, y))
        
    for pt in seeds:
        r, g, b, a = temp_img.getpixel(pt)
        if a != 0:
            ImageDraw.floodfill(temp_img, pt, (0, 0, 0, 0), thresh=tolerance)
            
    # Extraer canal alfa
    _, _, _, alpha = temp_img.split()
    
    # Aplicar transparencia
    result = img.copy()
    result.putalpha(alpha)
    return result

def crop_spritesheet(input_path, rows, cols, output_dir, name_prefix, target_w, target_h, start_num, tolerance=35):
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)
        
    with Image.open(input_path) as img:
        w, h = img.size
        print(f"Procesando {input_path} ({rows}x{cols} celdas)...")
        
        cell_w_float = w / cols
        cell_h_float = h / rows
        
        count = start_num
        for r in range(rows):
            for c in range(cols):
                left = int(round(c * cell_w_float))
                top = int(round(r * cell_h_float))
                right = int(round((c + 1) * cell_w_float))
                bottom = int(round((r + 1) * cell_h_float))
                
                right = min(right, w)
                bottom = min(bottom, h)
                
                frame = img.crop((left, top, right, bottom))
                frame = frame.resize((target_w, target_h), Image.Resampling.LANCZOS)
                frame_trans = make_background_transparent(frame, tolerance=tolerance)
                
                output_path = os.path.join(output_dir, f"{name_prefix}_{count:02d}.png")
                frame_trans.save(output_path)
                print(f"  Guardado: {name_prefix}_{count:02d}.png")
                count += 1
        return count - start_num

if __name__ == "__main__":
    # Rutas de las imágenes cargadas por el usuario y generada por la IA
    img1_path = r"C:\Users\emman\.gemini\antigravity-ide\brain\51485da1-0726-45a6-a572-d3ba271086bc\media__1782977133710.jpg"
    img2_path = r"C:\Users\emman\.gemini\antigravity-ide\brain\51485da1-0726-45a6-a572-d3ba271086bc\media__1782977146274.jpg"
    img3_path = r"C:\Users\emman\.gemini\antigravity-ide\brain\51485da1-0726-45a6-a572-d3ba271086bc\media__1782978282249.jpg"
    img4_path = r"C:\Users\emman\.gemini\antigravity-ide\brain\51485da1-0726-45a6-a572-d3ba271086bc\media__1782978569223.jpg"
    img5_path = r"C:\Users\emman\.gemini\antigravity-ide\brain\51485da1-0726-45a6-a572-d3ba271086bc\media__1782979502678.jpg"
    img6_path = r"C:\Users\emman\.gemini\antigravity-ide\brain\51485da1-0726-45a6-a572-d3ba271086bc\octopus_evolution_part2_1782979578177.png"
    
    # Única carpeta de destino organizada
    out_dir = r"c:\Users\emman\Desktop\Proyectos\SmartDisplay\Android\output_frames\organized_sprites"
    
    # Limpiar carpeta antes de extraer
    if os.path.exists(out_dir):
        for f in os.listdir(out_dir):
            try:
                os.remove(os.path.join(out_dir, f))
            except:
                pass
    else:
        os.makedirs(out_dir)

    print("=== INICIANDO EXTRACCIÓN DE SPRITES ===")
    
    # 1. Procesar Spritesheet 1: Pulpo Fuego (12 imágenes)
    total_1 = crop_spritesheet(img1_path, 3, 4, out_dir, "octopus_flame", 256, 190, 1, tolerance=35)
    
    # 2. Procesar Spritesheet 2: Pulpo Evolución (15 imágenes)
    total_2 = crop_spritesheet(img2_path, 3, 5, out_dir, "octopus_white", 204, 190, 1, tolerance=25)
    
    # 3. Procesar Spritesheet 3: Pulpo Portal (12 imágenes)
    total_3 = crop_spritesheet(img3_path, 3, 4, out_dir, "octopus_portal", 256, 190, 1, tolerance=35)
    
    # 4. Procesar Spritesheet 4: Pulpo Sonriente (12 imágenes)
    total_4 = crop_spritesheet(img4_path, 3, 4, out_dir, "octopus_flame_smile", 256, 190, 1, tolerance=35)
    
    # 5. Procesar Spritesheet 5: Evolución Púrpura Parte 1 (12 imágenes)
    total_5 = crop_spritesheet(img5_path, 3, 4, out_dir, "octopus_purple_evo_1", 256, 190, 1, tolerance=35)
    
    # 6. Procesar Spritesheet 6: Evolución Púrpura Parte 2 - Generada (12 imágenes)
    total_6 = crop_spritesheet(img6_path, 3, 4, out_dir, "octopus_purple_evo_2", 256, 190, 1, tolerance=35)
    
    total_global = total_1 + total_2 + total_3 + total_4 + total_5 + total_6
    print("\n========================================")
    print(f" EXTRACCIÓN COMPLETADA")
    print(f" - Total de imágenes extraídas: {total_global}")
    print(f" - Ubicación de la carpeta: {out_dir}")
    print("========================================")
