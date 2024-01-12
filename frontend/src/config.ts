export type Config = {
    hostname: string
}

export async function getConfig(): Promise<Config> {
    let response: Response = await fetch("/config/config.json");
    if (response.ok) {
        return response.json()
    } else {
        throw new Error(`Failed to fetch config, received ${response}`)
    }
}

export async function getHostname(): Promise<string> {
    const config = await getConfig()
    return config.hostname
}