declare module 'jmuxer' {
  interface JMuxerOptions {
    node: HTMLVideoElement | string;
    mode?: 'video' | 'audio' | 'both';
    flushingTime?: number;
    fps?: number;
    debug?: boolean;
    onError?: () => void;
    onVideoDecode?: (data: unknown) => void;
    onAudioDecode?: (data: unknown) => void;
  }

  interface FeedOptions {
    video?: Uint8Array;
    audio?: Uint8Array;
    duration?: number;
  }

  class JMuxer {
    constructor(options: JMuxerOptions);
    feed(data: FeedOptions): void;
    destroy(): void;
    ready(): boolean;
  }

  export default JMuxer;
}
