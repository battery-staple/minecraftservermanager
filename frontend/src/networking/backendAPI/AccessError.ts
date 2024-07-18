/**
 * Represents an error in fetching some resource.
 */
export type AccessError = "loading" | "unavailable"

/**
 * Represents a resource that may or may not have been successfully accessed.
 */
export type Accessed<T> = T | AccessError

/**
 * Checks whether a value is an access error
 */
export function isError<T>(val: Accessed<T>): val is AccessError {
    return val === "loading" || val === "unavailable"
}
