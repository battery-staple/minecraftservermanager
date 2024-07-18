import {Dispatch, SetStateAction} from "react";

/**
 * Shorthand for the type of a React state's setter.
 */
export type Setter<T> = Dispatch<SetStateAction<T>>