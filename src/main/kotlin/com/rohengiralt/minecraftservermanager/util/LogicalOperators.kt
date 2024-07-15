package com.rohengiralt.minecraftservermanager.util

infix fun Boolean.implies(other: Boolean) = !this || other
infix fun Boolean.iff(other: Boolean) = this == other