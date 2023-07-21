export type Server = {
    uuid: string,
    name: string,
    versionPhase: string,
    version: string,
    runnerUUID: string
}

export type CurrentRun = {
    uuid: string,
    serverId: string,
    runnerId: string,
    environment: Environment,
    address: ServerAddress
}

export type ServerAddress = {
    host: string,
    port: number,
    path: string,
    fullAddress: string
}

export type Environment = {
    port: number,
    maxHeapSizeMB: number
    minHeapSizeMB: number
}

export type Runner = {
    uuid: string,
    name: string,
    domain: string,
}
