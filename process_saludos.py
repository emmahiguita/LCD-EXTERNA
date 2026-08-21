import os
from PIL import Image, ImageDraw

def make_background_transparent(img, tolerance=40):
    img = img.convert("RGBA")
    width, height = img.size
    
    # Inundación desde los bordes para transparentar el fondo
    temp_img = img.copy()
    
    seeds = [
        (0, 0), (width - 1, 0), (0, height - 1), (width - 1, height - 1),
        (width // 2, 0), (width // 2, height - 1), (0, height // 2), (width - 1, height // 2)
    ]
    
    # Agregar más semillas en los bordes para barrer todo el fondo
    for x in range(0, width, 10):
        seeds.append((x, 0))
        seeds.append((x, height - 1))
    for y in range(0, height, 10):
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

def process_saludoxml(root_dir):
    print("=== INICIANDO PROCESAMIENTO DE SALUDOS ===")
    total_images_processed = 0
    total_frames_generated = 0
    
    # Recorrer todos los directorios dentro de Saludoxml
    for dirpath, _, filenames in os.walk(root_dir):
        # Filtrar solo archivos de imagen
        image_files = [f for f in filenames if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        if not image_files:
            continue
            
        print(f"\nDirectorio: {os.path.relpath(dirpath, root_dir)}")
        
        for img_file in image_files:
            img_path = os.path.join(dirpath, img_file)
            base_name = os.path.splitext(img_file)[0]
            
            # Limpiar caracteres especiales o espacios del nombre base para los frames
            # pero mantenerlo legible
            safe_base_name = base_name.replace(" ", "_").replace("(", "").replace(")", "").replace("…", "")
            if len(safe_base_name) > 30:
                safe_base_name = safe_base_name[:30] # acortar si es muy largo
                
            with Image.open(img_path) as img:
                w, h = img.size
                
                # Cada imagen es de 2752x1536, por lo que una cuadrícula de 3x4
                # nos da exactamente celdas de 688x512
                rows, cols = 3, 4
                cell_w = w // cols
                cell_h = h // rows
                
                print(f"  Procesando spritesheet: {img_file} ({w}x{h}) -> celdas {cell_w}x{cell_h}")
                
                count = 1
                for r in range(rows):
                    for c in range(cols):
                        left = c * cell_w
                        top = r * cell_h
                        right = left + cell_w
                        bottom = top + cell_h
                        
                        # Recortar celda
                        frame = img.crop((left, top, right, bottom))
                        
                        # Transparentar fondo (tolerancia de 40 para barrer fondos grises/blancos)
                        frame_trans = make_background_transparent(frame, tolerance=40)
                        
                        # Guardar frame en el mismo directorio
                        output_filename = f"{safe_base_name}_frame_{count:02d}.png"
                        output_path = os.path.join(dirpath, output_filename)
                        frame_trans.save(output_path)
                        
                        count += 1
                        total_frames_generated += 1
                        
            total_images_processed += 1
            print(f"  ¡Completado! Creados 12 frames para {img_file}")
            
    print("\n========================================")
    print(" PROCESAMIENTO DE SALUDOS COMPLETADO")
    print(f" - Hojas de sprites procesadas: {total_images_processed}")
    print(f" - Frames PNG transparentes creados: {total_frames_generated}")
    print("========================================")

if __name__ == "__main__":
    saludo_root = r"C:\Users\emman\Desktop\Proyectos\SmartDisplay\Android\Saludando\Saludoxml"
    process_saludoxml(saludo_root)
