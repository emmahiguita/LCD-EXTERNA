import re

file_path = r"C:\Users\emman\Desktop\Proyectos\SmartDisplay\Android\app\src\main\res\layout\overlay_window_controls.xml"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Add clickable, focusable, and selectableItemBackground to ImageViews that act as buttons
content = re.sub(
    r'(<ImageView[^>]*?android:id="@+id/btnWin[^>]*?)(\/?>)',
    r'\1\n                    android:background="?attr/selectableItemBackgroundBorderless"\n                    android:clickable="true"\n                    android:focusable="true"\2',
    content
)

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Updated overlay_window_controls.xml with ripple effects.")
