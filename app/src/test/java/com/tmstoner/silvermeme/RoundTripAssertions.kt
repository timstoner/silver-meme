package com.tmstoner.silvermeme

import com.tmstoner.silvermeme.data.model.TodoItem
import org.junit.Assert.assertEquals

fun assertTodoRoundTrip(expected: TodoItem, actual: TodoItem) {
    assertEquals(expected.id, actual.id)
    assertEquals(expected.title, actual.title)
    assertEquals(expected.content, actual.content)
    assertEquals(expected.dueDate, actual.dueDate)
    assertEquals(expected.priority, actual.priority)
    assertEquals(expected.location, actual.location)
    assertEquals(expected.tags, actual.tags)
    assertEquals(expected.isCompleted, actual.isCompleted)
}
