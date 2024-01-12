import {CurrentRun, Runner, Server, UserPreferences} from "../APIModels";
import {defaultHeaders, fetchJsonWithTimeoutOrNull, fetchWithTimeout, jsonHeaders} from "./FetchUtils";
import {getHostname} from "../config";

export type BackendStatus = "up" | "offline" | "unauthorized"
export async function getBackendStatus(): Promise<BackendStatus> {
    try {
        const statusResponse = await fetchWithTimeout(`http://${await getHostname()}/api/v2/rest/status`, 3000, {
            method: "GET",
            headers: defaultHeaders
        })

        if (statusResponse.ok) {
            return "up"
        } else if (statusResponse.status === 401 || statusResponse.status === 403) {
            return "unauthorized"
        } else {
            return "offline"
        }
    } catch {
        return "offline"
    }
}

export type AccessError = "loading" | "unavailable"

export async function getServer(serverUUID: string): Promise<Server> {
    const server = await fetchJsonWithTimeoutOrNull<Server>(`http://${await getHostname()}/api/v2/rest/servers/${serverUUID}`, 3000, {
        method: "GET",
        headers: defaultHeaders
    });
    if (server === null) throw Error(`Got invalid response getting server`)
    return server
}

export async function getCurrentRun(serverUUID: string): Promise<CurrentRun | null> {
    const response = await fetchWithTimeout(`http://${await getHostname()}/api/v2/rest/servers/${serverUUID}/currentRun`, 3000, {
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

export async function getRunner(runnerUUID: string): Promise<Runner> {
    const runner = await fetchJsonWithTimeoutOrNull<Runner>(`http://${await getHostname()}/api/v2/rest/runners/${runnerUUID}`, 3000, {
        method: "GET",
        headers: defaultHeaders
    });
    if (runner === null) {
        throw Error(`Got invalid response getting runner`)
    }
    return runner
}

export async function getServers(): Promise<Server[] | null> {
    const response = await fetchWithTimeout(`http://${await getHostname()}/api/v2/rest/servers`, 3000, {
        method: "GET",
        headers: defaultHeaders
    });

    if (!response.ok) return null;

    return JSON.parse(await response.text(), (key, value) => key === "creationTime" ? new Date(value) : value)
}

export async function getCurrentRunWebsocket(runnerUUID: string, serverUUID: string, onMessage: (this: WebSocket, event: MessageEvent<any>) => any): Promise<WebSocket> {
    const websocket = new WebSocket(
        `ws://${await getHostname()}/api/v2/websockets/runners/${runnerUUID}/runs/current?serverId=${serverUUID}`
    )

    websocket.onmessage = onMessage

    return websocket
}

export async function getConsoleWebsocket(runnerUUID: string, runUUID: string, onMessage: (this: WebSocket, event: MessageEvent<any>) => any): Promise<WebSocket> {
    const websocket = new WebSocket(
        `ws://${await getHostname()}/api/v2/websockets/runners/${runnerUUID}/runs/current/${runUUID}/console`
    )

    websocket.onmessage = onMessage

    return websocket
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

export async function getPreferences(): Promise<UserPreferences | null> {
    const response = await fetch(`http://${await getHostname()}/api/v2/rest/users/current/preferences`)

    return response.ok ? response.json() : null
}

export async function updatePreferences(userPreferences: Partial<UserPreferences>): Promise<boolean> {
    const response = await fetch(`http://${await getHostname()}/api/v2/rest/users/current/preferences`, {
        headers: jsonHeaders,
        method: "PATCH",
        body: JSON.stringify(userPreferences)
    })

    return response.ok
}