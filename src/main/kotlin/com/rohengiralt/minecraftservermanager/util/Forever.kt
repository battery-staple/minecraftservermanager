package com.rohengiralt.minecraftservermanager.util

/**
 * Runs [block], repeating forever.
 * Passes it an index of the current iteration, starting from zero.
 */
inline fun forever(block: (i: Int) -> Unit): Nothing {
    var i = 0
    while(true) block(i++)
}