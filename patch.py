import re

path = r'app\src\main\java\com\limelight\ui\OverlayFabController.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Remove the spurious FrameWrap field and the duplicate fabMainWrapperView
content = re.sub(r'    private final FrameWrap\s+\w+;.*?\n', '', content)
content = re.sub(r'\n    // NOTA:[^\n]+\n    // [^\n]+\n    // [^\n]+\n    private final View fabMainWrapperView;', '', content)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

# Verify
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()
if 'FrameWrap' in text:
    print('ERROR: FrameWrap still found')
elif 'fabMainWrapperView' in text:
    print('OK: fabMainWrapperView found, FrameWrap removed')
else:
    print('ERROR: fabMainWrapperView not found')
