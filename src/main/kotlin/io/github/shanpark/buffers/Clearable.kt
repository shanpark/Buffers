package io.github.shanpark.buffers

interface Clearable {
    /**
     * 모든 내부 상태 및 데이터를 삭제하고 처음 상태로 되돌린다.
     */
    fun clear()
}