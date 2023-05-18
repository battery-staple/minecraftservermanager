package com.rohengiralt.minecraftservermanager.observation

import java.lang.ref.WeakReference
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

abstract class Publisher : Observable {
    abstract fun publish()

    inner class FieldPublishingProperty<T>(
        initialValue: T,
        var get: (FieldPublishingProperty<T>.() -> T)? = null,
        private val set: (FieldPublishingProperty<T>.(value: T) -> Unit)? = null,
        private val onlyPublishOnChange: Boolean = false
    ) : ReadWriteProperty<Any?, T> {
        var field: T = initialValue

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = get?.invoke(this) ?: field

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            field.let { oldField ->
                (set ?: { field = it }).invoke(this, value)
                if (onlyPublishOnChange) {
                    if (oldField != value) publish()
                } else publish()
            }
        }
    }

    inner class PublishingProperty<T>(
        private val get: () -> T,
        private val set: (value: T) -> Unit,
        private val onlyPublishOnChange: Boolean = false,
    ) : ReadWriteProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = get()

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (onlyPublishOnChange) {
                get().let { oldValue ->
                    set(value)
                    if (oldValue != value) publish()
                }
            } else {
                set(value)
                publish()
            }
        }
    }

    inline fun <T> published(
        initialValue: T,
        noinline get: (FieldPublishingProperty<T>.() -> T)? = null,
        noinline set: (FieldPublishingProperty<T>.(value: T) -> Unit)? = null,
        onlyPublishOnChange: Boolean = false,
    ): ReadWriteProperty<Any?, T> =
        FieldPublishingProperty(initialValue, get, set, onlyPublishOnChange)

    inline fun <T> published(
        noinline get: () -> T,
        noinline set: (value: T) -> Unit,
        onlyPublishOnChange: Boolean = false,
    ): ReadWriteProperty<Any?, T> =
        PublishingProperty(get, set, onlyPublishOnChange)

    inline fun <T> published(
        delegate: ReadWriteProperty<Any?, T>,
        onlyPublishOnChange: Boolean = false,
    ): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> = PropertyDelegateProvider { thisRef, property ->
        PublishingProperty(
            { delegate.getValue(thisRef, property) },
            { value -> delegate.setValue(thisRef, property, value) },
            onlyPublishOnChange
        )
    }

    inline fun <T> published(
        delegate: KMutableProperty0<T>,
        onlyPublishOnChange: Boolean = false,
    ): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> = PropertyDelegateProvider { thisRef, property ->
        PublishingProperty(
            { delegate.getValue(thisRef, property) },
            { value -> delegate.setValue(thisRef, property, value) },
            onlyPublishOnChange
        )
    }

    inline fun <T> published(
        noinline getDelegate: () -> KMutableProperty0<T>,
        onlyPublishOnChange: Boolean,
    ): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> = PropertyDelegateProvider { thisRef, property ->
        PublishingProperty(
            { getDelegate().getValue(thisRef, property) },
            { value -> getDelegate().setValue(thisRef, property, value) },
            onlyPublishOnChange
        )
    }

    inline fun <T> published( // Separate function rather than default argument to allow trailing closure syntax
        noinline getDelegate: () -> KMutableProperty0<T>,
    ): PropertyDelegateProvider<Any?, ReadWriteProperty<Any?, T>> =
        published(getDelegate, false)
}

open class SimplePublisher : Publisher() {
    private val weakSubscribers =
        mutableListOf<WeakReference<Observer>>() // Using Set here wouldn't allow two objects that compare == but not ===
    private val strongSubscribers =
        mutableListOf<Observer>()

    private val subscribers: Iterable<Observer> get() = weakSubscribers.mapNotNull { it.get() } + strongSubscribers

    override fun publish() {
        subscribers.forEach { it.update() }
    }

    override fun addWeakSubscriber(observer: Observer) {
        if (subscribers.any { it === observer }) return
        weakSubscribers.add(WeakReference(observer))
    }

    override fun addStrongSubscriber(observer: Observer) {
        if (subscribers.any { it === observer }) return
        strongSubscribers.add(observer)
    }

    override fun removeSubscriber(observer: Observer) {
        with(weakSubscribers) {
            indexOfFirst { it.get() === observer }.let { index ->
                if (index != -1) removeAt(index)
            }
        }
        with(strongSubscribers) {
            indexOfFirst { it === observer }.let { index ->
                if (index != -1) removeAt(index)
            }
        }
    }
}

open class PassthroughPublisher(
    private val publisher: Publisher,
) : Publisher(), Observable by publisher, Observer {
    @Suppress("PublicApiImplicitType")
    override fun publish() = publisher.publish()

    @Suppress("PublicApiImplicitType")
    override fun update() = publish()

    inline fun <T : Observable> publishedSubscribing(
        initialValue: T,
        noinline get: (FieldPublishingProperty<T>.() -> T)? = null,
        noinline set: (FieldPublishingProperty<T>.(value: T) -> Unit)? = null,
        onlyPublishOnChange: Boolean = false,
    ): ReadWriteProperty<Any?, T> =
        FieldPublishingProperty(initialValue, get, { value ->
            field.removeSubscriber(this@PassthroughPublisher)
            set?.invoke(this, value)
            subscribeTo(field)
        }, onlyPublishOnChange)
            .also { subscribeTo(initialValue) }

    // Does it really make sense to have this (if get() changes often, etc.)?
//    inline fun <T : Observable> publishedSubscribing(
//        noinline get: () -> T,
//        noinline set: (value: T) -> Unit,
//        onlyPublishOnChange: Boolean = false,
//    ): ReadWriteProperty<Any?, T> =
//        PublishingProperty(get, { value ->
//            get().removeSubscriber(this@PassthroughPublisher)
//            set(value)
//            subscribeTo(get())
//        }, onlyPublishOnChange)

    inline fun <T> publishedMaybeSubscribing(
        initialValue: T,
        noinline get: (FieldPublishingProperty<T>.() -> T)? = null,
        noinline set: (FieldPublishingProperty<T>.(value: T) -> Unit)? = null,
        onlyPublishOnChange: Boolean = false,
    ): ReadWriteProperty<Any?, T> =
        FieldPublishingProperty(initialValue, get, { value ->
            (field as? Observable)?.removeSubscriber(this@PassthroughPublisher)
            set?.invoke(this, value) ?: run { field = value }
            (field as? Observable)?.addWeakSubscriber(this@PassthroughPublisher)
        }, onlyPublishOnChange)
            .also { (initialValue as? Observable)?.addWeakSubscriber(this@PassthroughPublisher) }

//    inline fun <T> publishedMaybeSubscribing(
//        noinline get: () -> T,
//        noinline set: (value: T) -> Unit,
//        onlyPublishOnChange: Boolean = false,
//    ): ReadWriteProperty<Any?, T> =
//        PublishingProperty(get, { value ->
//            (get() as? Observable)?.removeSubscriber(this)
//            set(value)
//            (get() as? Observable)?.addWeakSubscriber(this)
//        }, onlyPublishOnChange)
}