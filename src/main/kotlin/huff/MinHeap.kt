package huff

internal class MinHeap<T>(private val order: Comparator<T>) {
    private var items = arrayOfNulls<Any?>(16)
    var size = 0
        private set

    fun isEmpty() = size == 0

    fun push(item: T) {
        if (size == items.size) items = items.copyOf(size * 2)
        items[size] = item
        siftUp(size)
        size++
    }

    @Suppress("UNCHECKED_CAST")
    fun pop(): T {
        check(size > 0) { "pop from empty heap" }
        val top = items[0] as T
        size--
        items[0] = items[size]
        items[size] = null
        if (size > 0) siftDown(0)
        return top
    }

    @Suppress("UNCHECKED_CAST")
    private fun at(i: Int) = items[i] as T

    private fun siftUp(start: Int) {
        var i = start
        while (i > 0) {
            val parent = (i - 1) / 2
            if (order.compare(at(i), at(parent)) >= 0) break
            swap(i, parent)
            i = parent
        }
    }

    private fun siftDown(start: Int) {
        var i = start
        while (true) {
            val left = 2 * i + 1
            val right = left + 1
            var smallest = i
            if (left < size && order.compare(at(left), at(smallest)) < 0) smallest = left
            if (right < size && order.compare(at(right), at(smallest)) < 0) smallest = right
            if (smallest == i) return
            swap(i, smallest)
            i = smallest
        }
    }

    private fun swap(a: Int, b: Int) {
        val t = items[a]
        items[a] = items[b]
        items[b] = t
    }
}
