package de.eso.rxplayer

import java.lang.reflect.Proxy

@Suppress("UNCHECKED_CAST")
fun <T> proxy(clazz: Class<T>): T {
    return Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz)) { _, _, _ ->
        throw UnsupportedOperationException("Mock")
    } as T
}