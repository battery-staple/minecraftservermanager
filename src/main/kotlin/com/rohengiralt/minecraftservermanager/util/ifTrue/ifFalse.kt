package com.rohengiralt.minecraftservermanager.util.ifTrue

inline fun <T> Boolean.ifTrue(block: () -> T): T? = if(this) block() else null
inline fun <T> Boolean.ifFalse(block: () -> T): T? = if(!this) block() else null

inline fun Boolean.ifTrue(block: () -> Unit) = if(this) block() else null // This function is included because otherwise
                                                                          // the compiler can complain "type parameter
                                                                          // inferred to Nothing implicitly"
inline fun Boolean.ifFalse(block: () -> Unit) = if(!this) block() else null // Same as above

inline fun Boolean.ifTrueAlso(block: () -> Unit): Boolean {
    if(this) block()
    return this
}

inline fun Boolean.ifFalseAlso(block: () -> Unit): Boolean {
    if(!this) block()
    return this
}