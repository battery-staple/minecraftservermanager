package com.rohengiralt.minecraftservermanager.util.ifTrue

inline fun <T> Boolean.ifTrue(block: () -> T): T? = if(this) block() else null
inline fun <T> Boolean.ifFalse(block: () -> T): T? = if(!this) block() else null