package dbest.kernel.util

fun <T> isEmpty(collection: Collection<T>): Boolean {
    return collection.isEmpty()
}

fun isEmpty(text: CharSequence): Boolean {
    return text.isEmpty()
}

fun isBlank(text: CharSequence): Boolean {
    return text.isBlank()
}

fun isEmpty(map: Map<*, *>): Boolean {
    return map.isEmpty()
}

fun <T> valueUnless(value: T, isDefault: Boolean): T? {
    return if (isDefault) null else value
}

fun <T> orDefault(value: T?, default: T): T {
    return if (value == null) default else value
}

fun <T> concatCollections(first: List<T>, second: List<T>): List<T> {
    return first + second
}

fun <T> reverseCollection(list: List<T>): List<T> {
    return list.asReversed()
}

fun <K> existsInCollection(key: K, map: Map<K, *>): Boolean {
    return map.contains(key)
}

fun <T> existsInCollection(item: T, collection: Collection<T>): Boolean {
    return collection.contains(item)
}

fun <T> collectionPlusItem(item: T, collection: Collection<T>): Collection<T> {
    return collection + item
}

fun <T> collectionPlusItem(item: T, set: Set<T>): Set<T> {
    return set + item
}

fun <T> collectionPlusItem(item: T, list: List<T>): List<T> {
    return list + item
}

fun <T> lastInCollection(list: List<T>): T? {
    return list.lastOrNull()
}

fun <T> collectionMinusLastItem(list: List<T>): List<T> {
    return list.dropLast(1)
}

fun <T> takeLastItems(count: Int, list: List<T>): List<T> {
    return list.takeLast(count)
}

fun <T> collectionMinusItem(item: T, set: Set<T>): Set<T> {
    return set - item
}

fun <K, V> mapPlusEntry(key: K, value: V, map: Map<K, V>): Map<K, V> {
    return map + (key to value)
}

fun <K, V> mapMinusKey(key: K, map: Map<K, V>): Map<K, V> {
    return map - key
}

inline fun <T, R> foldCollection(initial: R, collection: Collection<T>, operation: (R, T) -> R): R {
    return collection.fold(initial, operation)
}

inline fun <T> filterCollection(collection: Collection<T>, predicate: (T) -> Boolean): List<T> {
    return collection.filter(predicate)
}

inline fun <T, R> mapCollection(collection: Collection<T>, transform: (T) -> R): List<R> {
    return collection.map(transform)
}

inline fun <K, V, K2, V2> mapEntries(map: Map<K, V>, transform: (K, V) -> Pair<K2, V2>): Map<K2, V2> {
    return map.entries.associate({ (key, value) -> transform(key, value) })
}

inline fun <T> firstInCollection(collection: Collection<T>, predicate: (T) -> Boolean): T? {
    return collection.firstOrNull(predicate)
}

inline fun <T, R : Comparable<R>> sortCollectionBy(collection: Collection<T>, crossinline selector: (T) -> R): List<T> {
    return collection.sortedBy(selector)
}

inline fun <T, R> transformOr(value: T?, transform: (T) -> R, default: R): R {
    return if (value == null) default else transform(value)
}

inline fun <T> filterCollection(set: Set<T>, predicate: (T) -> Boolean): Set<T> {
    return set.filterTo(LinkedHashSet(), predicate)
}

inline fun <T> anyInCollection(collection: Collection<T>, predicate: (T) -> Boolean): Boolean {
    return collection.any(predicate)
}

fun <T> varargToCollection(vararg items: T): List<T> {
    return items.toList()
}