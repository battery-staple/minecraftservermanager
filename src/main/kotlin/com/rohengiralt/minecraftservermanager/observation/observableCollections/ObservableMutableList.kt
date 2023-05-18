package com.rohengiralt.minecraftservermanager.dataStructure.observableCollection

import com.rohengiralt.minecraftservermanager.observation.Observable
import com.rohengiralt.minecraftservermanager.observation.Publisher
import com.rohengiralt.minecraftservermanager.observation.SimplePublisher

//TODO: __**TEST**__
class ObservableMutableList<E>(
    private val reference: MutableList<E>,
    private val publisher: Publisher,
) : MutableList<E>, List<E> by reference, Observable by publisher, ObservableCollection<E> {
    constructor(collection: Collection<E> = listOf(), publisher: Publisher) :
            this(collection.toMutableList(), publisher)

    constructor(collection: Collection<E> = listOf()) : this(collection, SimplePublisher())

    override val size: Int get() = reference.size

    override fun get(index: Int): E = reference[index]

    override fun add(index: Int, element: E): Unit = publishing {
        reference.add(index, element)
    }

    override fun removeAt(index: Int): E = publishing {
        reference.removeAt(index)
    }

    override fun set(index: Int, element: E): E = publishing {
        reference.set(index, element)
    }

    private fun <T> publishing(block: () -> T): T = block().also { publisher.publish() }

    override fun add(element: E): Boolean = publishing {
        reference.add(element)
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean = publishing {
        reference.addAll(index, elements)
    }

    override fun addAll(elements: Collection<E>): Boolean = publishing {
        reference.addAll(elements)
    }

    override fun clear(): Unit = publishing {
        reference.clear()
    }

    override fun remove(element: E): Boolean = publishing {
        reference.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean = publishing {
        reference.removeAll(elements.toSet())
    }

    override fun retainAll(elements: Collection<E>): Boolean = publishing {
        reference.retainAll(elements)
    }

    override fun iterator(): MutableIterator<E> = Itr(this)

    override fun listIterator(): MutableListIterator<E> = Itr(this)

    override fun listIterator(index: Int): MutableListIterator<E> = Itr(this, index)

    override fun subList(fromIndex: Int, toIndex: Int): ObservableMutableList<E> =
        ObservableMutableList(reference.subList(fromIndex, toIndex), publisher)

    class Itr<T>(
        private val list: ObservableMutableList<T>,
        private var index: Int = 0,
    ) : MutableListIterator<T> {
        var last = -1

        override fun hasNext(): Boolean = nextIndex() in 0 until list.size
        override fun nextIndex(): Int = index
        override fun next(): T = list[(index++).also { last = it }]

        override fun hasPrevious(): Boolean = previousIndex() in 0 until list.size
        override fun previousIndex(): Int = index - 1
        override fun previous(): T = list[(--index).also { last = it }]

        override fun add(element: T) {
            list.add(index, element)
            ++index
            last = -1
        }

        override fun remove() {
            check(last != -1) { "Call next() or previous() before removing element from the iterator." }

            list.removeAt(last)
            index = last
            last = -1
        }

        override fun set(element: T) {
            check(last != -1) { "Call next() or previous() before removing element from the iterator." }

            list[last] = element
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is List<*>) return false
        if (reference != other) return false

        return true
    }

    override fun hashCode(): Int {
        return reference.hashCode()
    }

    override fun toString(): String = "ObservableMutableList$reference"
}


//@Suppress("FunctionName")
//inline fun <reified T> ObservableMutableList(
//    size: Int,
//    init: (Int) -> T,
//): ObservableMutableList<T> =
//    MutableList(size, init).toObservable()

@Suppress("NOTHING_TO_INLINE")
inline fun <E> Collection<E>.toObservableMutableList(publisher: Publisher? = null): ObservableMutableList<E> =
    publisher?.let { ObservableMutableList(this, it) } ?: ObservableMutableList(this)

//@Suppress("NOTHING_TO_INLINE")
//inline fun <E> Iterable<E>.toObservable(subscriber: OldViewModel<*>?) =
//    this.toMutableList().toObservable(subscriber)
//
//inline fun <reified E> observableMutableListOf(
//    vararg elements: E,
//    subscriber: OldViewModel<*>? = null,
//) = elements.toMutableList().toObservable(subscriber)
