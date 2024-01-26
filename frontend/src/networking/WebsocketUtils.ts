export function getAndConfigWebsocket(url: string | URL, onMessage: (this: WebSocket, event: MessageEvent<any>) => any, onClose: (this: WebSocket, ev: CloseEvent) => any) {
    const websocket = new WebSocket(url);

    websocket.onmessage = onMessage;
    websocket.onclose = onClose;

    return websocket;
}