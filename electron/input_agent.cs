using System;
using System.Runtime.InteropServices;
using System.Diagnostics;
using System.Windows.Automation;

class InputAgent {
    [DllImport("user32.dll")]
    static extern bool SetCursorPos(int X, int Y);

    [DllImport("user32.dll")]
    static extern bool GetCursorPos(out POINT lpPoint);

    [StructLayout(LayoutKind.Sequential)]
    struct POINT {
        public int X;
        public int Y;
    }

    [DllImport("user32.dll")]
    static extern void mouse_event(uint dwFlags, int dx, int dy, uint dwData, uint dwExtraInfo);

    [DllImport("user32.dll")]
    static extern uint SendInput(uint nInputs, [MarshalAs(UnmanagedType.LPArray)] INPUT[] pInputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)]
    struct INPUT {
        public uint type;
        public InputUnion U;
    }

    [StructLayout(LayoutKind.Explicit)]
    struct InputUnion {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public HARDWAREINPUT hi;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct MOUSEINPUT {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct KEYBDINPUT {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct HARDWAREINPUT {
        public uint uMsg;
        public ushort wParamL;
        public ushort wParamH;
    }

    const uint INPUT_MOUSE = 0;
    const uint INPUT_KEYBOARD = 1;
    const uint KEYEVENTF_KEYUP = 0x0002;

    const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    const uint MOUSEEVENTF_LEFTUP = 0x0004;
    const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    const uint MOUSEEVENTF_RIGHTUP = 0x0010;

    static void Main() {
        // Establecer encoding UTF8 para compatibilidad total de caracteres
        Console.InputEncoding = System.Text.Encoding.UTF8;
        Console.OutputEncoding = System.Text.Encoding.UTF8;

        string line;
        while ((line = Console.ReadLine()) != null) {
            try {
                if (string.IsNullOrEmpty(line)) continue;
                string[] parts = line.Trim().Split(' ');
                if (parts.Length == 0) continue;
                string cmd = parts[0];

                if (cmd == "MOVE" && parts.Length >= 3) {
                    int x = int.Parse(parts[1]);
                    int y = int.Parse(parts[2]);
                    SetCursorPos(x, y);
                }
                else if (cmd == "RELMOVE" && parts.Length >= 3) {
                    int dx = int.Parse(parts[1]);
                    int dy = int.Parse(parts[2]);
                    mouse_event(0x0001, dx, dy, 0, 0); // 0x0001 = MOUSEEVENTF_MOVE
                }
                else if (cmd == "CLICK" && parts.Length >= 3) {
                    int x = int.Parse(parts[1]);
                    int y = int.Parse(parts[2]);
                    if (x != -1 && y != -1) {
                        SetCursorPos(x, y);
                    }
                    mouse_event(MOUSEEVENTF_LEFTDOWN | MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
                }
                else if (cmd == "RCLICK" && parts.Length >= 3) {
                    int x = int.Parse(parts[1]);
                    int y = int.Parse(parts[2]);
                    if (x != -1 && y != -1) {
                        SetCursorPos(x, y);
                    }
                    mouse_event(MOUSEEVENTF_RIGHTDOWN | MOUSEEVENTF_RIGHTUP, 0, 0, 0, 0);
                }
                else if (cmd == "MOUSEDOWN") {
                    mouse_event(MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0);
                }
                else if (cmd == "MOUSEUP") {
                    mouse_event(MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
                }
                else if (cmd == "KEY" && parts.Length >= 2) {
                    ushort vk = ushort.Parse(parts[1]);
                    SendKey(vk);
                }
                else if (cmd == "KEYDOWN" && parts.Length >= 2) {
                    ushort vk = ushort.Parse(parts[1]);
                    SendKeyEvent(vk, 0); // Down
                }
                else if (cmd == "KEYUP" && parts.Length >= 2) {
                    ushort vk = ushort.Parse(parts[1]);
                    SendKeyEvent(vk, KEYEVENTF_KEYUP); // Up
                }
                else if (cmd == "SMARTCLICK" && parts.Length >= 3) {
                    int x = int.Parse(parts[1]);
                    int y = int.Parse(parts[2]);
                    if (x == -1 && y == -1) {
                        POINT ptCur;
                        if (GetCursorPos(out ptCur)) {
                            x = ptCur.X;
                            y = ptCur.Y;
                        }
                    }
                    bool clicked = false;
                    try {
                        System.Windows.Point pt = new System.Windows.Point(x, y);
                        AutomationElement el = AutomationElement.FromPoint(pt);
                        if (el != null) {
                            System.Windows.Rect rect = el.Current.BoundingRectangle;
                            if (rect.Width > 0 && rect.Height > 0) {
                                int cx = (int)(rect.Left + rect.Width / 2);
                                int cy = (int)(rect.Top + rect.Height / 2);
                                SetCursorPos(cx, cy);
                                mouse_event(MOUSEEVENTF_LEFTDOWN | MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
                                clicked = true;
                            }
                        }
                    } catch {
                        // Fallback in case of UI Automation errors
                    }
                    if (!clicked) {
                        SetCursorPos(x, y);
                        mouse_event(MOUSEEVENTF_LEFTDOWN | MOUSEEVENTF_LEFTUP, 0, 0, 0, 0);
                    }
                }
                else if (cmd == "RUN" && parts.Length >= 2) {
                    string appName = line.Substring(4).Trim();
                    if (!string.IsNullOrEmpty(appName)) {
                        Process.Start(appName);
                    }
                }
                else if (cmd == "CMD" && parts.Length >= 2) {
                    string target = parts[1];
                    if (target == "youtube") {
                        Process.Start("https://youtube.com");
                    }
                    else if (target == "mute") {
                        SendKey(0xAD); // VK_VOLUME_MUTE
                    }
                    else if (target == "macro_ia") {
                        Process.Start("https://gemini.google.com");
                    }
                    else if (target == "shutdown") {
                        Process.Start("shutdown", "/s /f /t 0");
                    }
                }
            } catch (Exception) {
                // Silenciar excepciones para continuar escuchando stdin
            }
        }
    }

    static void SendKey(ushort vk) {
        SendKeyEvent(vk, 0);
        SendKeyEvent(vk, KEYEVENTF_KEYUP);
    }

    static void SendKeyEvent(ushort vk, uint flags) {
        INPUT[] inputs = new INPUT[1];
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].U.ki.wVk = vk;
        inputs[0].U.ki.wScan = 0;
        inputs[0].U.ki.dwFlags = flags;
        inputs[0].U.ki.time = 0;
        inputs[0].U.ki.dwExtraInfo = IntPtr.Zero;
        SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
    }
}
