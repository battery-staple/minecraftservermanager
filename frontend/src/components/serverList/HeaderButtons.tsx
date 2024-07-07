import {JSX} from "react";

export function HeaderButtons(props: { editing: boolean, setEditing: (editing: boolean) => void, setCreating: (creating: boolean) => void }): JSX.Element {

    if (props.editing) {
        return <>
            <button type="button" className="btn btn-primary btn-lg" onClick={() => props.setEditing(false)}>
                Done
            </button>
        </>
    } else {
        return <>
            <button type="button" className="btn btn-secondary btn-lg" onClick={() => props.setEditing(true)}>
                Edit
            </button>
            <button type="button" className="btn btn-primary btn-lg" onClick={() => props.setCreating(true)}>
                New
            </button>
        </>
    }
}