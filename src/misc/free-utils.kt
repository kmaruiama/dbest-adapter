/*
arquivo usado para centralizar funcoes que sao uteis para todo o projeto, utilitarios gerais.
muito do que esta codado aqui eh redundante pois os objetos ja suportam chamadas de metodo interno
exemplo:
stringQualquer.isNotEmpty()
mas preferi centralizar uma funcao isNotEmpty aqui, ja que como a maior parte do projeto eh codado de maneira funcional,
prefiri manter assim para fins de consistencia geral do projeto.
*/

package dbest.misc

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

// o valor, a menos que a condicao diga que ele eh o default (ai devolve null)
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

/*
as funcoes abaixo nao mutam nada: devolvem uma NOVA colecao com a mudanca aplicada,
no mesmo espirito do session.copy. A sobrecarga para Set existe para preservar o tipo
(Set + item continua sendo Set, e nao um Collection generico).
*/

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

/*
as funcoes abaixo recebem lambdas, entao todas sao inline para garantir custo zero:
o compilador cola o loop direto no ponto de chamada, gerando o mesmo bytecode
que chamar o metodo (collection.fold(), collection.filter(), etc) diretamente.
*/

// reduz a colecao a um unico valor, aplicando operation(acumulado, item) da esquerda para a direita
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

// transforma o valor se presente; null vira o default
inline fun <T, R> transformOr(value: T?, transform: (T) -> R, default: R): R {
    return if (value == null) default else transform(value)
}

// sobrecarga para Set, preservando o tipo (filtrar um Set continua dando um Set)
inline fun <T> filterCollection(set: Set<T>, predicate: (T) -> Boolean): Set<T> {
    return set.filterTo(LinkedHashSet(), predicate)
}

// any eh a primitiva dos quantificadores: none(p) == !any(p), all(p) == !any(!p)
inline fun <T> anyInCollection(collection: Collection<T>, predicate: (T) -> Boolean): Boolean {
    return collection.any(predicate)
}

// nao sei onde colocar isso
fun <T> varargToCollection(vararg items: T): List<T> {
    return items.toList()
}