import {hostname} from "../../config";
import {CurrentRun} from "../../APIModels";
import {DEFAULT_TIMEOUT_MS, defaultHeaders, fetchWithTimeout} from "../FetchUtils";
import {getAndConfigWebsocket} from "../WebsocketUtils";

export async function getCurrentRun(serverUUID: string): Promise<CurrentRun | null> {
    const response = await fetchWithTimeout(`http://${await hostname}/api/v2/rest/servers/${serverUUID}/currentRun`, DEFAULT_TIMEOUT_MS, {
        method: "GET",
        headers: defaultHeaders
    });

    if (response.ok)
        return response.json()
    else if (response.status === 404) {
        return null;
    } else {
        throw Error(`Got invalid response getting runner`);
    }
}

export async function getCurrentRunWebsocket(runnerUUID: string, serverUUID: string, onMessage: (this: WebSocket, event: MessageEvent<any>) => any, onClose: (this: WebSocket, ev: CloseEvent) => any): Promise<WebSocket> {
    return getAndConfigWebsocket(
        `ws://${await hostname}/api/v2/websockets/runners/${runnerUUID}/runs/current?serverId=${serverUUID}`,
        onMessage,
        onClose
    )
}

export async function getConsoleWebsocket(runnerUUID: string, runUUID: string, onMessage: (this: WebSocket, event: MessageEvent) => any, onClose: (this: WebSocket, ev: CloseEvent) => any): Promise<WebSocket> {
    return getAndConfigWebsocket(
        `ws://${await hostname}/api/v2/websockets/runners/${runnerUUID}/runs/current/${runUUID}/console`,
        onMessage,
        onClose
    )
}

