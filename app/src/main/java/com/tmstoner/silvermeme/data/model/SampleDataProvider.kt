package com.tmstoner.silvermeme.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Provides sample TODO items with lorem ipsum content for first-time app startup.
 * These items are only created if the vault folder is empty.
 */
object SampleDataProvider {

    fun generateSampleTodos(): List<TodoItem> {
        val now = LocalDateTime.now()
        val tomorrow = LocalDate.now().plusDays(1)
        val nextWeek = LocalDate.now().plusDays(7)
        val nextMonth = LocalDate.now().plusDays(30)

        return listOf(
            TodoItem(
                id = UUID.randomUUID().toString(),
                title = "Review project proposal",
                content = """
                    Lorem ipsum dolor sit amet, consectetur adipiscing elit. 
                    Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
                    
                    Key points to review:
                    - Timeline and milestones
                    - Budget allocation
                    - Team assignments
                """.trimIndent(),
                dueDate = tomorrow,
                priority = Priority.HIGH,
                location = "Office",
                tags = listOf("work", "important"),
                isCompleted = false,
                filePath = "Tasks/Review project proposal.md",
                createdAt = now,
                updatedAt = now,
                checklist = listOf(
                    ChecklistItem("Read through proposal", false),
                    ChecklistItem("Check budget details", false),
                    ChecklistItem("Review timeline", false)
                ),
                recurrence = "none",
                loe = 5
            ),
            TodoItem(
                id = UUID.randomUUID().toString(),
                title = "Prepare presentation slides",
                content = """
                    Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris 
                    nisi ut aliquip ex ea commodo consequat.
                    
                    Slides needed:
                    - Title slide with team members
                    - Quarterly results overview
                    - Next quarter goals and objectives
                    - Q&A preparation
                """.trimIndent(),
                dueDate = nextWeek,
                priority = Priority.MEDIUM,
                location = "Home",
                tags = listOf("work", "presentation"),
                isCompleted = false,
                filePath = "Tasks/Prepare presentation slides.md",
                createdAt = now,
                updatedAt = now,
                checklist = listOf(
                    ChecklistItem("Create title slide", false),
                    ChecklistItem("Add charts and graphs", false),
                    ChecklistItem("Practice presentation", false)
                ),
                recurrence = "none",
                loe = 8
            ),
            TodoItem(
                id = UUID.randomUUID().toString(),
                title = "Update documentation",
                content = """
                    Duis aute irure dolor in reprehenderit in voluptate velit esse cillum 
                    dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non 
                    proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
                    
                    Documentation sections to update:
                    - API reference guide
                    - Installation instructions
                    - Troubleshooting section
                """.trimIndent(),
                dueDate = nextMonth,
                priority = Priority.LOW,
                location = null,
                tags = listOf("documentation", "dev"),
                isCompleted = false,
                filePath = "Tasks/Update documentation.md",
                createdAt = now,
                updatedAt = now,
                checklist = emptyList(),
                recurrence = "none",
                loe = 3
            ),
            TodoItem(
                id = UUID.randomUUID().toString(),
                title = "Buy groceries",
                content = """
                    Sed ut perspiciatis unde omnis iste natus error sit voluptatem 
                    accusantium doloremque laudantium.
                    
                    Shopping list:
                    - Fresh vegetables
                    - Fruits (apples, bananas)
                    - Dairy products
                    - Pantry staples
                """.trimIndent(),
                dueDate = tomorrow,
                priority = Priority.MEDIUM,
                location = "Supermarket",
                tags = listOf("personal", "errands"),
                isCompleted = false,
                filePath = "Tasks/Buy groceries.md",
                createdAt = now,
                updatedAt = now,
                checklist = listOf(
                    ChecklistItem("Make shopping list", true),
                    ChecklistItem("Check pantry supplies", false),
                    ChecklistItem("Go shopping", false)
                ),
                recurrence = "weekly:SAT",
                loe = 1
            ),
            TodoItem(
                id = UUID.randomUUID().toString(),
                title = "Call dentist for appointment",
                content = """
                    Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, 
                    sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt.
                    
                    Remember to:
                    - Ask about cleaning appointment
                    - Check insurance coverage
                    - Ask about new patient offers
                """.trimIndent(),
                dueDate = nextWeek,
                priority = Priority.MEDIUM,
                location = null,
                tags = listOf("personal", "health"),
                isCompleted = false,
                filePath = "Tasks/Call dentist for appointment.md",
                createdAt = now,
                updatedAt = now,
                checklist = listOf(
                    ChecklistItem("Find dentist phone number", false),
                    ChecklistItem("Call during business hours", false),
                    ChecklistItem("Confirm appointment date", false)
                ),
                recurrence = "none",
                loe = 1
            ),
            TodoItem(
                id = UUID.randomUUID().toString(),
                title = "Review team feedback",
                content = """
                    At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis 
                    praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias 
                    excepturi sint occaecati cupiditate non provident.
                    
                    Feedback categories to review:
                    - Performance reviews
                    - 360-degree feedback
                    - One-on-one comments
                    - Areas for improvement
                """.trimIndent(),
                dueDate = null,
                priority = Priority.LOW,
                location = "Office",
                tags = listOf("work", "hr"),
                isCompleted = false,
                filePath = "Tasks/Review team feedback.md",
                createdAt = now,
                updatedAt = now,
                checklist = listOf(
                    ChecklistItem("Read all feedback forms", false),
                    ChecklistItem("Identify patterns", false),
                    ChecklistItem("Create action plan", false)
                ),
                recurrence = "none",
                loe = 5
            )
        )
    }
}

