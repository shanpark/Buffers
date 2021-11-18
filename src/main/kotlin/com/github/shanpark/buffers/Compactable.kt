package com.github.shanpark.buffers

interface Compactable {
    /**
     * 내부 구현에 따라 다르게 구현될 것이다.
     * 내부적으로 더 이상 필요 없는 공간을 정리하도록 구현하며 그런 동작이 없는 경우 구현하지 않을 수 있다.
     */
    fun compact() {}
}