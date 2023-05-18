package com.rohengiralt.minecraftservermanager.dataStructure.observableCollection

import com.rohengiralt.minecraftservermanager.observation.Observable
import com.rohengiralt.minecraftservermanager.observation.Publisher
import com.rohengiralt.minecraftservermanager.observation.SimplePublisher

class ObservableMutableSet<E>(
    private val reference: MutableSet<E>,
    private val publisher: Publisher
) : MutableSet<E>, Set<E> by reference, Observable by publisher, ObservableCollection<E> {
    constructor(collection: Collection<E> = emptySet(), publisher: Publisher) :
            this(collection.toMutableSet(), publisher)

    constructor(collection: Collection<E> = listOf()) : this(collection, SimplePublisher())

    private fun <T> publishing(block: () -> T): T = block().also { publisher.publish() }
    override fun add(element: E): Boolean = publishing { reference.add(element) }
    override fun addAll(elements: Collection<E>): Boolean = publishing { reference.addAll(elements) }
    override fun clear(): Unit = publishing { reference.clear() }
    override fun remove(element: E): Boolean = publishing { reference.remove(element) }
    override fun removeAll(elements: Collection<E>): Boolean = publishing { reference.removeAll(elements) }
    override fun retainAll(elements: Collection<E>): Boolean = publishing { reference.retainAll(elements) }

    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {
        private val reference = this@ObservableMutableSet.reference.iterator()
        override fun hasNext(): Boolean = reference.hasNext()
        override fun next(): E = reference.next()
        override fun remove() = publishing { reference.remove() }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <E> Collection<E>.toObservableMutableSet(publisher: Publisher? = null): ObservableMutableSet<E> =
    publisher?.let { ObservableMutableSet(collection = this, it) } ?: ObservableMutableSet(collection = this)

fun <E> observableMutableSetOf(vararg elements: E): ObservableMutableSet<E> =
    ObservableMutableSet(elements.toMutableSet())