export function getAndConfigWebsocket(url: string | URL, onMessage: (this: WebSocket, event: MessageEvent<any>) => any, onClose: (this: WebSocket, ev: CloseEvent) => any) {
    if (typeof url === 'string') {
        url = new URL(url);
    }

    url.searchParams.append("name", btoa("User McUserface"))
    url.searchParams.append("password", btoa("Super secure password"))
    const websocket = new WebSocket(url);

    websocket.onmessage = onMessage;
    websocket.onclose = onClose;

    return websocket;
}