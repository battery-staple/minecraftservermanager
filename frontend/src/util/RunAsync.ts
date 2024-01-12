export function runAsync(block: () => Promise<void>) {
    // noinspection JSIgnoredPromiseFromCall
    block()
}