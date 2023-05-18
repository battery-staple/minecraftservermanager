package com.rohengiralt.minecraftservermanager.observation

fun interface Observer {
    fun update()

    fun subscribeTo(observer: Observable): Unit = observer.addWeakSubscriber(this)
}