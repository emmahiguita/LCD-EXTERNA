// src/lib/AdbService.ts
// SOLID AdbService Class
// Single Responsibility Principle (SRP): Encapsulates all ADB and Sunshine operations.

export interface AdbDevice {
  serial: string;
  model: string;
  connectionType: string;
  state: string;
}

export interface ActiveDeviceDetails {
  connected: boolean;
  connectionType?: string;
  model?: string;
  androidVersion?: string;
  serial?: string;
  resolution?: string;
  ram?: string;
  storage?: string;
  battery?: number;
  isCharging?: boolean;
  temperature?: string;
  state?: string;
  ip?: string;
}

export interface FetchDevicesResult {
  connected: boolean;
  devices: AdbDevice[];
  activeDevice: ActiveDeviceDetails | null;
  error?: string;
}

export interface ActionResponse {
  success: boolean;
  message?: string;
  error?: string;
  ip?: string;
}

export class AdbService {
  private static getApiUrl(path: string): string {
    if (typeof window !== 'undefined' && window.location.protocol === 'file:') {
      return `http://localhost:3001${path}`;
    }
    return path;
  }

  /**
   * Fetches the list of active ADB devices and their properties.
   */
  public static async fetchDevices(requestedSerial?: string): Promise<FetchDevicesResult> {
    try {
      const serialParam = requestedSerial ? `?serial=${encodeURIComponent(requestedSerial)}` : '';
      const response = await fetch(this.getApiUrl(`/api/device${serialParam}`));
      if (!response.ok) {
        throw new Error(`Server returned HTTP ${response.status}`);
      }
      return await response.json();
    } catch (err: any) {
      return {
        connected: false,
        devices: [],
        activeDevice: null,
        error: err.message || 'Error fetching devices',
      };
    }
  }

  /**
   * Executes a command on the remote phone or local Sunshine server.
   */
  public static async executeAction(
    action: string,
    serial?: string | null,
    deviceIP?: string | null,
    extraParams?: Record<string, any>
  ): Promise<ActionResponse> {
    try {
      const response = await fetch(this.getApiUrl('/api/actions'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action,
          serial: serial || undefined,
          ip: deviceIP || undefined,
          ...extraParams,
        }),
      });

      if (!response.ok) {
        throw new Error(`Server returned HTTP ${response.status}`);
      }
      return await response.json();
    } catch (err: any) {
      return {
        success: false,
        error: err.message || 'Connection error',
      };
    }
  }

  /**
   * Helper to pair with Sunshine PIN.
   */
  public static async pairPin(pin: string): Promise<ActionResponse> {
    return this.executeAction('pair_pin', null, null, { pin });
  }

  /**
   * Helper to initiate stream projection.
   */
  public static async launchStream(): Promise<ActionResponse> {
    return this.executeAction('launch_stream');
  }

  /**
   * Helper to trigger auto-detection broadcast.
   */
  public static async autoDetect(serial: string): Promise<ActionResponse> {
    return this.executeAction('auto_detect', serial);
  }
}
