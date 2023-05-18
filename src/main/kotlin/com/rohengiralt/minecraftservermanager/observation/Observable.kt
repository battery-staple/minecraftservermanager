package com.rohengiralt.minecraftservermanager.observation

interface Observable {
    fun addWeakSubscriber(observer: Observer)
    fun addStrongSubscriber(observer: Observer)
    fun removeSubscriber(observer: Observer)
}