package byos

class Counter {
    private var count = 0

    fun getIncrementingNumber(): Int {
        return count++
    }
}
