export type Server = {
    uuid: string,
    name: string,
    versionPhase: string,
    version: string,
    runnerUUID: string,
    creationTime: Date
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

export type UserPreferences = {
    serverSortStrategy: SortStrategy
}

export type SortStrategy = "NEWEST" | "OLDEST" | "ALPHABETICAL"
export let DEFAULT_SORT_STRATEGY: SortStrategy = "ALPHABETICAL"