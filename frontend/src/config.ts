import {DEFAULT_TIMEOUT_MS, defaultHeaders, fetchWithTimeout} from "./networking/FetchUtils";

export type Config = {
    hostname: string
}

/**
 * Fetches the app's configuration from the server.
 * @param knownHostname the hostname, if known (such as in debug mode).
 *                      If null, a relative path will be used for the request.
 */
export async function getConfig(knownHostname: string | null = null): Promise<Config> {
    const hostname = knownHostname !== null ? knownHostname : ""

    let response: Response = await fetchWithTimeout(`${hostname}/config/config.json`, DEFAULT_TIMEOUT_MS, { headers: defaultHeaders });
    if (response.ok) {
        return response.json()
    } else {
        throw new Error(`Failed to fetch config, received ${response}`)
    }
}

export const hostname = getHostname();

async function getHostname(): Promise<string> {
    if (process.env.NODE_ENV === "development") {
        return "localhost:8080"; // TODO: Customizable
    }

    const config = await getConfig()
    return config.hostname
}