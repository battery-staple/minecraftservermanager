import {Server, VersionPhase} from "../../APIModels";
import {
    DEFAULT_TIMEOUT_MS,
    defaultHeaders,
    fetchJsonWithTimeoutOrNull,
    fetchWithTimeout,
    jsonHeaders
} from "../FetchUtils";
import {getHostname} from "../../config";
import {getAndConfigWebsocket} from "../WebsocketUtils";

export async function getServer(serverUUID: string): Promise<Server> {
    const server = await fetchJsonWithTimeoutOrNull<Server>(`http://${await getHostname()}/api/v2/rest/servers/${serverUUID}`, DEFAULT_TIMEOUT_MS, {
        method: "GET",
        headers: defaultHeaders
    });
    if (server === null) throw Error(`Got invalid response getting server`)
    return server
}

export type CreateServerOptions = { name: string, versionPhase: VersionPhase, version: string, runnerUUID: string }
export async function createServer(options: CreateServerOptions): Promise<boolean> {
    const response = await fetchWithTimeout(`http://${await getHostname()}/api/v2/rest/servers`, DEFAULT_TIMEOUT_MS, {
        method: "POST",
        headers: jsonHeaders,
        body: JSON.stringify(options)
    });

    return response.ok
}

export async function deleteServer(serverUUID: string): Promise<boolean> {
    const response = await fetchWithTimeout(`http://${await getHostname()}/api/v2/rest/servers/${serverUUID}`, DEFAULT_TIMEOUT_MS, {
        method: "DELETE",
        headers: defaultHeaders
    });

    return response.ok
}

export async function getServers(): Promise<Server[] | null> {
    const response = await fetchWithTimeout(`http://${await getHostname()}/api/v2/rest/servers`, DEFAULT_TIMEOUT_MS, {
        method: "GET",
        headers: defaultHeaders
    });

    if (!response.ok) return null;

    return JSON.parse(await response.text(), (key, value) => key === "creationTime" ? new Date(value) : value)
}

export async function startServer(server: Server): Promise<boolean> {
    const response = await fetch(`http://${await getHostname()}/api/v2/rest/servers/` + server?.uuid + "/currentRun", {
        headers: jsonHeaders,
        method: "POST",
        body: JSON.stringify({
            "port": 25565,  // TODO: Customizable
            "maxHeapSizeMB": 2048,
            "minHeapSizeMB": 1024
        })
    })

    return response.ok
}

export async function stopServer(server: Server): Promise<boolean> {
    const response = await fetch(`http://${await getHostname()}/api/v2/rest/servers/` + server?.uuid + "/currentRun", {
        headers: defaultHeaders,
        method: "DELETE"
    })

    return response.ok
}

export async function getServersWebsocket(onMessage: (this: WebSocket, event: MessageEvent<any>) => any, onClose: (this: WebSocket, ev: CloseEvent) => any): Promise<WebSocket> {
    return getAndConfigWebsocket(
        `ws://${await getHostname()}/api/v2/websockets/servers`,
        onMessage,
        onClose
    )
}